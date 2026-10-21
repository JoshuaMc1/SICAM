param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]{1,80}$')]
    [string]$Checkpoint,
    [string]$OutputDirectory,
    [string]$SecureDirectory
)
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectDirectory 'qa-evidence' }
$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
Push-Location -LiteralPath $projectDirectory
try {
    $java = (Get-Command java -ErrorAction Stop).Source
    $null = Get-Command mvn -ErrorAction Stop
    $targetDirectory = Join-Path $projectDirectory 'target'
    if (-not (Test-Path -LiteralPath (Join-Path $targetDirectory 'classes/com/joshuamc1/sicam/config/MySQLConfigManager.class'))) {
        & mvn -B '-DskipTests' compile
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar SICAM.' }
    }
    $classpathFile = Join-Path $targetDirectory 'qa-classpath.txt'
    if (-not (Test-Path -LiteralPath $classpathFile)) {
        & mvn -B dependency:build-classpath '-Dmdep.outputFile=target/qa-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo obtener el classpath de SICAM.' }
    }
    $dependencies = [IO.File]::ReadAllText($classpathFile).Trim()
    $classpath = (Join-Path $targetDirectory 'classes') + [IO.Path]::PathSeparator + $dependencies
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
    $baseName = $Checkpoint + '_' + $stamp
    $snapshotPath = Join-Path $OutputDirectory ($baseName + '.json')
    $javaArguments = @()
    if ($SecureDirectory) { $javaArguments += ('-Dapp.secure.dir=' + (Resolve-Path -LiteralPath $SecureDirectory).Path) }
    $javaArguments += @('--class-path', $classpath, (Join-Path $PSScriptRoot 'CaptureQaSnapshot.java'), (Join-Path $PSScriptRoot 'snapshot.sql'), $snapshotPath, $Checkpoint)
    & $java @javaArguments
    if ($LASTEXITCODE -ne 0) { throw 'Falló la captura. No se ha escrito en la base de datos.' }
    $snapshot = Get-Content -LiteralPath $snapshotPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $sourcePaths = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'src/main') -Recurse -File)
    $sourcePaths += Get-Item -LiteralPath (Join-Path $projectDirectory 'pom.xml')
    $manifest = [ordered]@{
        checkpoint = $Checkpoint
        snapshot = [IO.Path]::GetFileName($snapshotPath)
        captured_at = (Get-Date).ToString('o')
        snapshot_sha256 = (Get-FileHash -LiteralPath $snapshotPath -Algorithm SHA256).Hash
        source_hashes = @($sourcePaths | Sort-Object FullName | ForEach-Object {
            [ordered]@{ path = $_.FullName.Substring($projectDirectory.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
        })
    }
    $manifestPath = Join-Path $OutputDirectory ($baseName + '.manifest.json')
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
    Write-Host ('Checkpoint: ' + $snapshot.checkpoint)
    Write-Host ('Base: ' + $snapshot.database + ' | MySQL: ' + $snapshot.mysql_version)
    Write-Host ('Productos: ' + @($snapshot.products).Count + ' | Transacciones: ' + @($snapshot.transactions).Count)
    Write-Host ('Comprobaciones: ' + ($snapshot.checks | ConvertTo-Json -Compress))
    Write-Host ('Manifiesto: ' + $manifestPath)
} finally { Pop-Location }
