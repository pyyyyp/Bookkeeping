#!/usr/bin/env pwsh
#
# 追溯矩阵校验（T-004）
#
# 校验 docs/90-trace/traceability.md 与 docs/ 是否自洽。规则：
#
#   规则 1  矩阵里出现的每个 REQ-xxx 都必须有对应文件（docs/10-requirements/）
#   规则 2  每个 REQ-*.md 里的每条 AC-x 都必须在矩阵的「需求 → 实现」表里被登记
#   规则 3  矩阵「需求 → 实现」表里标 ✅ 的行，必须有非空的「提交」与「测试」列
#   规则 4  矩阵里出现的每个 T-xxx / ADR-xxxx 都必须有对应文件（T-004 卡新增）
#
# 为什么是 PowerShell 而不是 bash：本机开发环境是 Windows，而 GitHub 的 ubuntu runner
# 预装 pwsh —— 一个脚本两边都能跑。两份实现（.sh + .ps1）迟早会漂移，那比只支持一种更糟。
#
# 退出码：0 = 通过；1 = 有违规。违规明细逐条打印，格式为
#         <规则号> <文件>:<行> —— <说明>
#
# 设计原则（来自 T-003 / T-006 的教训）：**校验脚本自己不能静默失效**。
# 因此它会在结尾打印「实际检查了多少东西」，并且当矩阵解析出 0 个编号时直接判失败——
# 一个什么都没解析到的校验脚本，比没有校验脚本更危险。

[CmdletBinding()]
param(
    # 仓库根目录；默认取本脚本的上级目录
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$matrixRelativePath = 'docs/90-trace/traceability.md'
$matrixPath = Join-Path $RepoRoot $matrixRelativePath

$violations = [System.Collections.Generic.List[string]]::new()

function Add-Violation {
    param([string]$Rule, [string]$Location, [string]$Message)
    $violations.Add("$Rule $Location —— $Message")
}

if (-not (Test-Path -LiteralPath $matrixPath)) {
    Write-Output "追溯校验失败：找不到矩阵文件 $matrixRelativePath"
    exit 1
}

$matrixLines = Get-Content -LiteralPath $matrixPath -Encoding UTF8

# ─────────────────────────────────────────────────────────────
# 先剥掉两类「不是数据」的内容，但**保持行号不变**（把整块换成空行），
# 这样报出来的行号仍然指向真实文件：
#
#   1. HTML 注释块 <!-- ... -->：矩阵里用它放了**格式示例**。
#      示例不是事实，不该被当成引用去校验（实测：示例里的 REQ-004 / T-012 / T-018
#      会让校验直接报 3 条假违规）。
#   2. 围栏代码块 ``` ... ```：里面的内容本来就不是表格行。
#
# 注意：剥掉之后必须让「什么都没解析到」变成失败（见文件末尾的守卫），
# 否则剥离逻辑一旦写错，校验就会安静地永远通过。
# ─────────────────────────────────────────────────────────────

$cleanLines = @(
    $inComment = $false
    $inFence = $false
    for ($i = 0; $i -lt $matrixLines.Count; $i++) {
        $line = $matrixLines[$i]
        if ($inComment) {
            if ($line -match '-->') { $inComment = $false }
            ''
            continue
        }
        if ($inFence) {
            if ($line.TrimStart().StartsWith('```')) { $inFence = $false }
            ''
            continue
        }
        if ($line -match '<!--') {
            if ($line -notmatch '-->') { $inComment = $true }
            ''
            continue
        }
        if ($line.TrimStart().StartsWith('```')) { $inFence = $true; ''; continue }
        $line
    }
)

# ─────────────────────────────────────────────────────────────
# 解析：把 markdown 表格按「章节」切开，再拆成单元格
# ─────────────────────────────────────────────────────────────

function Get-TableRows {
    param([string[]]$Lines, [int]$StartIndex, [int]$EndIndex)
    $rows = @()
    for ($i = $StartIndex; $i -lt $EndIndex; $i++) {
        $line = $Lines[$i]
        if (-not $line.TrimStart().StartsWith('|')) { continue }
        # 分隔行 |---|---|
        if ($line -match '^\s*\|[\s:\-\|]+\|\s*$') { continue }
        $cells = @($line.Trim().Trim('|') -split '\|' | ForEach-Object { $_.Trim() })
        # 全空行（模板里的占位行）跳过
        if (@($cells | Where-Object { $_ -ne '' }).Count -eq 0) { continue }
        $rows += [pscustomobject]@{ Line = $i + 1; Cells = $cells }
    }
    return $rows
}

# 找章节边界：从 "## <标题>" 到下一个 "## "
function Get-SectionRange {
    param([string[]]$Lines, [string]$Heading)
    $start = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i].Trim() -eq $Heading) { $start = $i + 1; break }
    }
    if ($start -lt 0) { return $null }
    $end = $Lines.Count
    for ($i = $start; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i].StartsWith('## ')) { $end = $i; break }
    }
    return @{ Start = $start; End = $end }
}

$requirementSection = Get-SectionRange -Lines $cleanLines -Heading '## 需求 → 实现'
if ($null -eq $requirementSection) {
    Add-Violation '规则0' $matrixRelativePath '找不到「## 需求 → 实现」章节——矩阵结构变了，校验需要同步更新'
    $requirementRows = @()
}
else {
    $requirementRows = Get-TableRows -Lines $cleanLines -StartIndex $requirementSection.Start -EndIndex $requirementSection.End
}

# ─────────────────────────────────────────────────────────────
# 规则 1 / 4：矩阵里引用的编号必须有对应文件
# ─────────────────────────────────────────────────────────────

$referencedRequirements = [System.Collections.Generic.HashSet[string]]::new()
$referencedTasks = [System.Collections.Generic.HashSet[string]]::new()
$referencedAdrs = [System.Collections.Generic.HashSet[string]]::new()

for ($i = 0; $i -lt $cleanLines.Count; $i++) {
    $line = $cleanLines[$i]
    # 结尾的 (?![0-9A-Za-z]) 用来排除**占位写法**：`REQ-00x`、`T-00x` 这类
    # 「还没建」的标记不是引用，不该被要求存在对应文件（实测：REQ-00x 曾被误报）。
    foreach ($m in [regex]::Matches($line, 'REQ-\d+(?![0-9A-Za-z])')) { [void]$referencedRequirements.Add($m.Value) }
    foreach ($m in [regex]::Matches($line, '(?<![A-Za-z0-9-])T-\d+(?![0-9A-Za-z])')) { [void]$referencedTasks.Add($m.Value) }
    foreach ($m in [regex]::Matches($line, 'ADR-\d+(?![0-9A-Za-z])')) { [void]$referencedAdrs.Add($m.Value) }
}

# 编号位数：本项目用 3 位（REQ-004 / T-012 / ADR-0007），见 docs/README.md
function Get-PaddedNumber {
    param([string]$Id)
    $digits = ($Id -replace '^[A-Z]+-', '')
    return $digits.PadLeft(3, '0')
}

function Test-IdHasFile {
    param([string]$Directory, [string]$Prefix, [string]$Id)
    $padded = Get-PaddedNumber -Id $Id
    $pattern = "$Prefix-$padded-*.md"
    $dir = Join-Path $RepoRoot $Directory
    if (-not (Test-Path -LiteralPath $dir)) { return $false }
    # @(...) 是必需的：只匹配到一个文件时 Get-ChildItem 返回**标量**，
    # 而 Set-StrictMode 下访问标量的 .Count 会直接抛错（实测踩到过）。
    return @(Get-ChildItem -LiteralPath $dir -Filter $pattern -File).Count -gt 0
}

foreach ($req in $referencedRequirements) {
    if (-not (Test-IdHasFile -Directory 'docs/10-requirements' -Prefix 'REQ' -Id $req)) {
        Add-Violation '规则1' $matrixRelativePath "矩阵引用了 $req，但 docs/10-requirements/ 下没有对应文件"
    }
}
foreach ($task in $referencedTasks) {
    if (-not (Test-IdHasFile -Directory 'docs/40-tasks' -Prefix 'T' -Id $task)) {
        Add-Violation '规则4' $matrixRelativePath "矩阵引用了 $task，但 docs/40-tasks/ 下没有对应文件"
    }
}
foreach ($adr in $referencedAdrs) {
    if (-not (Test-IdHasFile -Directory 'docs/30-architecture' -Prefix 'ADR' -Id $adr)) {
        Add-Violation '规则4' $matrixRelativePath "矩阵引用了 $adr，但 docs/30-architecture/ 下没有对应文件"
    }
}

# ─────────────────────────────────────────────────────────────
# 规则 2：每个 REQ 文件里的每条 AC 都要在矩阵里登记
# ─────────────────────────────────────────────────────────────

$requirementDirectory = Join-Path $RepoRoot 'docs/10-requirements'
$acChecked = 0
$requirementFiles = @()
if (Test-Path -LiteralPath $requirementDirectory) {
    # 排除 _TEMPLATE-*.md；@(...) 同样是为了避免「只有一个匹配时变成标量」
    $requirementFiles = @(Get-ChildItem -LiteralPath $requirementDirectory -Filter 'REQ-*.md' -File)
}

foreach ($file in $requirementFiles) {
    $idMatch = [regex]::Match($file.Name, '^REQ-\d+')
    if (-not $idMatch.Success) { continue }
    $requirementId = $idMatch.Value

    $content = Get-Content -LiteralPath $file.FullName -Encoding UTF8
    $acs = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($line in $content) {
        # 验收标准以 "### AC-n <标题>" 形式出现（见 _TEMPLATE-REQ.md）
        foreach ($m in [regex]::Matches($line, '^#{2,4}\s+(AC-\d+)')) { [void]$acs.Add($m.Groups[1].Value) }
    }

    # 守卫：一个需求文件解析出 0 条 AC 时**直接失败**。
    # 起因是真实踩过的坑：REQ-002 第一版把 AC 写成表格（不是标题），
    # 正则一条都没匹配到 —— 规则 2 于是**静默跳过整个文件**，
    # 报告里只是"检查 AC 10 条"看起来很正常。校验漏检比校验不存在更危险。
    if ($acs.Count -eq 0) {
        Add-Violation '规则2' "docs/10-requirements/$($file.Name)" `
            "没有解析出任何 AC：标题必须是 '### AC-n <标题>'（模板见 _TEMPLATE-REQ.md）。注意脚本不会因为格式错误而跳过检查"
        continue
    }

    foreach ($ac in $acs) {
        $acChecked++
        $registered = $false
        foreach ($row in $requirementRows) {
            if ($row.Cells.Count -lt 2) { continue }
            $rowRequirement = $row.Cells[0]
            $rowAcs = $row.Cells[1]
            if ($rowRequirement -notmatch [regex]::Escape($requirementId)) { continue }
            if ($rowAcs -match "(?<![A-Za-z0-9-])$([regex]::Escape($ac))(?![0-9])") { $registered = $true; break }
        }
        if (-not $registered) {
            Add-Violation '规则2' "docs/10-requirements/$($file.Name)" `
                "$requirementId 的 $ac 没有出现在矩阵的「需求 → 实现」表里（需求写了验收标准，就必须在追溯矩阵里登记）"
        }
    }
}

# ─────────────────────────────────────────────────────────────
# 规则 3：✅ 的行必须有「提交」与「测试」
# ─────────────────────────────────────────────────────────────

$doneRowsChecked = 0
foreach ($row in $requirementRows) {
    if ($row.Cells.Count -lt 8) {
        Add-Violation '规则0' "$matrixRelativePath`:$($row.Line)" `
            "「需求 → 实现」表的列数不是 8（需求/AC/上下文/聚合/任务/提交/测试/状态），实际 $($row.Cells.Count) 列"
        continue
    }
    $status = $row.Cells[7]
    if ($status -notmatch '✅') { continue }
    $doneRowsChecked++
    $commit = $row.Cells[5]
    $test = $row.Cells[6]
    if ([string]::IsNullOrWhiteSpace($commit) -or $commit -eq '-') {
        Add-Violation '规则3' "$matrixRelativePath`:$($row.Line)" `
            "标了 ✅ 但「提交」列是空的——完成状态必须有可查的提交"
    }
    if ([string]::IsNullOrWhiteSpace($test) -or $test -eq '-') {
        Add-Violation '规则3' "$matrixRelativePath`:$($row.Line)" `
            "标了 ✅ 但「测试」列是空的——完成状态必须有可查的验证证据"
    }
}

# ─────────────────────────────────────────────────────────────
# 防「静默失效」：一个什么都没解析到的校验脚本比没有更危险
# ─────────────────────────────────────────────────────────────

$totalIds = $referencedRequirements.Count + $referencedTasks.Count + $referencedAdrs.Count
if ($totalIds -eq 0) {
    Add-Violation '规则0' $matrixRelativePath `
        '没有从矩阵里解析出任何 REQ / T / ADR 编号——矩阵结构或本脚本的正则已经失效，请先修校验'
}

# ─────────────────────────────────────────────────────────────
# 输出
# ─────────────────────────────────────────────────────────────

if ($violations.Count -gt 0) {
    Write-Output "追溯校验失败：$($violations.Count) 处违规"
    Write-Output ''
    foreach ($violation in $violations) { Write-Output "  $violation" }
    Write-Output ''
    Write-Output '每条违规都写明了规则号、文件与行号。修法见 docs/90-trace/traceability.md 的「校验规则」。'
    exit 1
}

Write-Output ('追溯校验通过：矩阵 {0} 行；引用编号 REQ {1} / T {2} / ADR {3}；' -f `
        $matrixLines.Count, $referencedRequirements.Count, $referencedTasks.Count, $referencedAdrs.Count)
Write-Output ('                 需求文件 {0} 个（检查 AC {1} 条）；✅ 行 {2} 条' -f `
        $requirementFiles.Count, $acChecked, $doneRowsChecked)
exit 0
