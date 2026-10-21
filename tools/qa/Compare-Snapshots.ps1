param(
    [Parameter(Mandatory = $true)][string]$Before,
    [Parameter(Mandatory = $true)][string]$After,
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$beforePath = (Resolve-Path -LiteralPath $Before).Path
$afterPath = (Resolve-Path -LiteralPath $After).Path
$left = Get-Content -LiteralPath $beforePath -Raw -Encoding UTF8 | ConvertFrom-Json
$right = Get-Content -LiteralPath $afterPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($left.format_version -ne 1 -or $right.format_version -ne 1) { throw 'Formato de captura incompatible.' }
if ($left.database -ne $right.database) { throw 'Las capturas corresponden a bases distintas.' }
function Get-RowKey($Row, [string]$Table) {
    if ($Table -eq 'rol_permisos') { return ('{0}:{1}' -f $Row.rol_id, $Row.permiso_id) }
    if ($Table -eq 'schema_migrations') { return [string]$Row.migration_name }
    return [string]$Row.id
}
function Get-Map($Rows, [string]$Table) {
    $map = @{}
    foreach ($row in @($Rows)) { if ($null -ne $row) { $map[(Get-RowKey $row $Table)] = $row } }
    return $map
}
function Test-ValueEqual($Previous, $Current) {
    if ($null -eq $Previous -and $null -eq $Current) { return $true }
    if ($null -eq $Previous -or $null -eq $Current) { return $false }
    $numericTypes = @('Byte', 'SByte', 'Int16', 'UInt16', 'Int32', 'UInt32', 'Int64', 'UInt64', 'Single', 'Double', 'Decimal')
    $previousNumeric = [Type]::GetTypeCode($Previous.GetType()).ToString() -in $numericTypes
    $currentNumeric = [Type]::GetTypeCode($Current.GetType()).ToString() -in $numericTypes
    if ($previousNumeric -and $currentNumeric) { return [decimal]$Previous -eq [decimal]$Current }
    return ($Previous | ConvertTo-Json -Compress -Depth 20) -ceq ($Current | ConvertTo-Json -Compress -Depth 20)
}
$tables = [ordered]@{}
foreach ($table in @('products', 'transactions', 'transaction_items', 'users', 'roles', 'permisos', 'rol_permisos', 'people', 'audit', 'schema_migrations')) {
    $old = Get-Map $left.$table $table
    $new = Get-Map $right.$table $table
    $added = @(); $removed = @(); $changed = @()
    foreach ($key in @($new.Keys | Sort-Object)) {
        if (-not $old.ContainsKey($key)) { $added += $new[$key]; continue }
        $fields = @()
        foreach ($property in $new[$key].PSObject.Properties) {
            $previous = $old[$key].($property.Name)
            if (-not (Test-ValueEqual $previous $property.Value)) {
                $fields += [ordered]@{ field = $property.Name; before = $previous; after = $property.Value }
            }
        }
        if ($fields.Count -gt 0) { $changed += [ordered]@{ key = $key; fields = $fields } }
    }
    foreach ($key in @($old.Keys | Sort-Object)) { if (-not $new.ContainsKey($key)) { $removed += $old[$key] } }
    $tables[$table] = [ordered]@{ added = $added; removed = $removed; changed = $changed }
}
function Get-MovementTotals($Snapshot) {
    $headers = Get-Map $Snapshot.transactions 'transactions'
    $totals = @{}
    foreach ($item in @($Snapshot.transaction_items)) {
        $header = $headers[[string]$item.transaction_id]
        if ($null -eq $header -or $header.status -ne 'active' -or $item.status -ne 'active') { continue }
        $direction = if ($header.type -eq 'in') { 1 } elseif ($header.type -in @('out', 'loan')) { -1 } else { 0 }
        $key = [string]$item.product_id
        if (-not $totals.ContainsKey($key)) { $totals[$key] = [decimal]0 }
        $totals[$key] += [decimal]$item.quantity * $direction
    }
    return $totals
}
$oldProducts = Get-Map $left.products 'products'
$newProducts = Get-Map $right.products 'products'
$oldMovements = Get-MovementTotals $left
$newMovements = Get-MovementTotals $right
$stock = @()
foreach ($key in @( @($oldProducts.Keys) + @($newProducts.Keys) | Sort-Object -Unique)) {
    $oldValue = if ($oldProducts.ContainsKey($key)) { [decimal]$oldProducts[$key].current_stock } else { [decimal]0 }
    $newValue = if ($newProducts.ContainsKey($key)) { [decimal]$newProducts[$key].current_stock } else { [decimal]0 }
    $movementDelta = [decimal]$newMovements[$key] - [decimal]$oldMovements[$key]
    $stockDelta = $newValue - $oldValue
    if ($stockDelta -ne 0 -or $movementDelta -ne 0) {
        $code = if ($newProducts.ContainsKey($key)) { $newProducts[$key].code } else { $oldProducts[$key].code }
        $stock += [ordered]@{ product_id = $key; code = $code; before = $oldValue; after = $newValue; stock_delta = $stockDelta; transaction_delta = $movementDelta; residual = $stockDelta - $movementDelta }
    }
}
$result = [ordered]@{
    database = $right.database
    before = $left.checkpoint
    after = $right.checkpoint
    before_sha256 = (Get-FileHash -LiteralPath $beforePath -Algorithm SHA256).Hash
    after_sha256 = (Get-FileHash -LiteralPath $afterPath -Algorithm SHA256).Hash
    checks_before = $left.checks
    checks_after = $right.checks
    stock = $stock
    tables = $tables
    interpretation = 'Residual distinto de cero requiere revisar creación de productos, ajustes manuales o importaciones; por sí solo no demuestra un error.'
}
if (-not $OutputPath) { $OutputPath = Join-Path ([IO.Path]::GetDirectoryName($afterPath)) ('DIFF_' + [IO.Path]::GetFileNameWithoutExtension($afterPath) + '.json') }
if (Test-Path -LiteralPath $OutputPath) { throw 'El archivo de comparación ya existe; use otro OutputPath.' }
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host ('Comparación: ' + $left.checkpoint + ' -> ' + $right.checkpoint)
foreach ($table in $tables.Keys) {
    $diff = $tables[$table]
    if (@($diff.added).Count + @($diff.removed).Count + @($diff.changed).Count -gt 0) {
        Write-Host ('{0}: +{1} / -{2} / modificados {3}' -f $table, @($diff.added).Count, @($diff.removed).Count, @($diff.changed).Count)
    }
}
Write-Host ('Informe: ' + $OutputPath)
