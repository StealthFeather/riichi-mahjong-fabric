param(
    [int]$Limit = 0,
    [int]$BatchSize = 0,
    [string]$ArchivePath = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent

$Archive    = "$Root\scraw2019.zip"
$Db         = "$Root\tenhou2019.db"
$OutDir     = "$Root\themahjong\src\test\resources\com\themahjong\replay\tenhou\2019"
$ToolDir    = "$Root\houou-logs"
$ArchiveUrl = "https://tenhou.net/sc/raw/scraw2019.zip"

if (-not (Get-Command houou-logs -ErrorAction SilentlyContinue)) {
    if (-not (Test-Path $ToolDir)) {
        Write-Host "Cloning houou-logs..."
        git clone https://github.com/Apricot-S/houou-logs.git $ToolDir
    }
    Write-Host "Installing houou-logs..."
    Push-Location $ToolDir
    pip install .
    Pop-Location
}

if ($ArchivePath -ne "" -and (Test-Path $ArchivePath)) {
    Write-Host "Using provided archive: $ArchivePath"
    if ($ArchivePath -ne $Archive) { Copy-Item $ArchivePath $Archive }
} elseif (-not (Test-Path $Archive)) {
    Write-Host "Downloading $ArchiveUrl (~498 MB)..."
    Invoke-WebRequest -Uri $ArchiveUrl -OutFile $Archive -UseBasicParsing
} else {
    Write-Host "Archive already present: $Archive"
}

if (-not (Test-Path $Db)) {
    Write-Host "Importing log IDs into $Db..."
    houou-logs import $Db $Archive
} else {
    Write-Host "DB already exists, skipping import to preserve downloaded state."
}

New-Item -ItemType Directory -Force $OutDir | Out-Null

if ($BatchSize -gt 0) {
    $downloaded = 0
    while ($Limit -eq 0 -or $downloaded -lt $Limit) {
        $batchLimit = if ($Limit -gt 0) { [Math]::Min($BatchSize, $Limit - $downloaded) } else { $BatchSize }
        Write-Host "Downloading batch ($batchLimit games)..."
        $tmpErr = [System.IO.Path]::GetTempFileName()
        $ErrorActionPreference = "Continue"
        houou-logs download $Db "--players" "4" "--length" "h" "--limit" $batchLimit 2> $tmpErr
        $ErrorActionPreference = "Stop"
        $dlOutput = Get-Content $tmpErr; Remove-Item $tmpErr
        ($dlOutput | Select-String "Number of logs downloaded:") | Write-Host
        $match = $dlOutput | Select-String "Number of logs downloaded: (\d+)"
        $newlyDownloaded = if ($match) { [int]$match.Matches[0].Groups[1].Value } else { 0 }
        if ($newlyDownloaded -eq 0) { break }
        Write-Host "Exporting..."
        houou-logs export $Db $OutDir "--players" "4" "--length" "h"
        $downloaded += $newlyDownloaded
        $Count = (Get-ChildItem $OutDir -Filter "*.xml" -ErrorAction SilentlyContinue).Count
        Write-Host "Exported $Count XML files so far."
    }
} else {
    $DownloadArgs = @($Db, "--players", "4", "--length", "h")
    if ($Limit -gt 0) { $DownloadArgs += "--limit", $Limit }
    Write-Host "Downloading games (4-player hanchan$(if ($Limit -gt 0) { ", limit $Limit" }))..."
    houou-logs download @DownloadArgs
    Write-Host "Exporting XML to $OutDir..."
    $ExportArgs = @($Db, $OutDir, "--players", "4", "--length", "h")
    if ($Limit -gt 0) { $ExportArgs += "--limit", $Limit }
    houou-logs export @ExportArgs
}

$Count = (Get-ChildItem $OutDir -Filter "*.xml" -ErrorAction SilentlyContinue).Count
Write-Host "Done. $Count XML files written to $OutDir"
