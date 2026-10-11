# Verification AND structural review for the product module.
#
# Part A - structural review: checks that every source file sits in the right
#          package directory, that naming conventions hold, and that no stray
#          or misplaced files have accumulated.
# Part B - functional verification: product CRUD, filtering, pick-frequency
#          accumulation, ABC classification (70% / 90% thresholds), category
#          tree and guards.
# Part C - cleanup: all test data is removed and the demo data is restored to
#          its original ABC classification.

$ErrorActionPreference = 'Continue'
$root    = 'D:\DeepSeekXM\XM01'
$backend = Join-Path $root '03-源码\backend'
$srcRoot = Join-Path $backend 'src\main\java\com\lijunjie\wms'
$mvn     = 'D:\Dev\maven\bin\mvn.cmd'
$java    = 'D:\Dev\jdk8\bin\java.exe'
$jar     = Join-Path $backend 'target\wms-1.0.0.jar'
$base    = 'http://127.0.0.1:8080'
$mysqlExe = 'D:\Dev\mysql8\bin\mysql.exe'

$env:JAVA_HOME = 'D:\Dev\jdk8'
$dbUser = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { 'wms' }
$dbPass = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { 'Wms@2026App' }

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-42} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-42} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}
function Api($method, $url, $body) {
    $p = @{ Uri = $url; Method = $method; TimeoutSec = 20; UseBasicParsing = $true; SkipHttpErrorCheck = $true }
    if ($body) { $p.Body = $body; $p.ContentType = 'application/json' }
    try { $r = Invoke-WebRequest @p } catch { return @{ status = 0; json = $null; raw = $_.Exception.Message } }
    $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch { }
    return @{ status = [int]$r.StatusCode; json = $j; raw = $r.Content }
}
function RunSql($sql) {
    $f = Join-Path $env:TEMP 'wms_product_verify.sql'
    [System.IO.File]::WriteAllText($f, $sql, (New-Object System.Text.UTF8Encoding($false)))
    & cmd /c "`"$mysqlExe`" -u root -pWms@2026Root --port=3306 --protocol=TCP --default-character-set=utf8mb4 < `"$f`" 2>nul"
    Remove-Item $f -Force -ErrorAction SilentlyContinue
}

# ======================================================================
Write-Host "=== PART A. Structural review ==="
Write-Host ""
Write-Host "A1. Package layout (each layer must live in its own package)"

$expectDirs = @{
    'controller' = 'controller'
    'service'    = 'service'
    'service\impl' = 'impl'
    'mapper'     = 'mapper'
    'entity'     = 'entity'
    'dto'        = 'dto'
    'common'     = 'common'
}
foreach ($d in @('controller','service','service\impl','mapper','entity','dto','common','common\util','common\exception')) {
    $full = Join-Path $srcRoot $d
    Check ("package dir: " + $d) (Test-Path $full) $(if (Test-Path $full) { ((Get-ChildItem $full -File).Count.ToString() + " file(s)") } else { "MISSING" })
}

Write-Host ""
Write-Host "A2. Only the Spring Boot entry class may sit at the package root"
# WmsApplication belongs at com.lijunjie.wms by Spring Boot convention; any other
# class at that level means a file was placed in the wrong package.
$rootLevelJava = Get-ChildItem $srcRoot -File -Filter '*.java' -ErrorAction SilentlyContinue
$unexpected = $rootLevelJava | Where-Object { $_.BaseName -ne 'WmsApplication' }
Check "only WmsApplication at package root" ($unexpected.Count -eq 0) `
      ("root classes=" + $rootLevelJava.Count + " -> " + (($rootLevelJava | ForEach-Object { $_.BaseName }) -join ','))
$unexpected | ForEach-Object { Write-Host ("      misplaced: " + $_.Name) }

Write-Host ""
Write-Host "A3. Naming conventions"
$controllers = Get-ChildItem (Join-Path $srcRoot 'controller') -File -Filter '*.java'
$badController = $controllers | Where-Object { $_.BaseName -notlike '*Controller' }
Check "controllers end with 'Controller'" ($badController.Count -eq 0) ("checked " + $controllers.Count + " files")

$mappers = Get-ChildItem (Join-Path $srcRoot 'mapper') -File -Filter '*.java'
$badMapper = $mappers | Where-Object { $_.BaseName -notlike '*Mapper' }
Check "mappers end with 'Mapper'" ($badMapper.Count -eq 0) ("checked " + $mappers.Count + " files")

$entities = Get-ChildItem (Join-Path $srcRoot 'entity') -File -Filter '*.java'
$badEntity = $entities | Where-Object { $_.BaseName -notmatch '^(Sys|Base|Inv|Biz)' }
Check "entities use a domain prefix" ($badEntity.Count -eq 0) ("checked " + $entities.Count + " files")
if ($badEntity.Count -gt 0) { $badEntity | ForEach-Object { Write-Host ("      unusual name: " + $_.Name) } }

$impls = Get-ChildItem (Join-Path $srcRoot 'service\impl') -File -Filter '*.java'
$badImpl = $impls | Where-Object { $_.BaseName -notlike '*Impl' }
Check "service impls end with 'Impl'" ($badImpl.Count -eq 0) ("checked " + $impls.Count + " files")

Write-Host ""
Write-Host "A4. Every class declares the package matching its directory"
$mismatch = @()
foreach ($f in (Get-ChildItem $srcRoot -Recurse -File -Filter '*.java')) {
    $rel = $f.DirectoryName.Substring($srcRoot.Length).TrimStart('\')
    $expectedPkg = 'com.lijunjie.wms' + $(if ($rel) { '.' + ($rel -replace '\\', '.') } else { '' })
    $head = (Get-Content $f.FullName -TotalCount 5) -join "`n"
    if ($head -notmatch [regex]::Escape('package ' + $expectedPkg + ';')) {
        $mismatch += ($f.Name + " (expected " + $expectedPkg + ")")
    }
}
Check "package declarations match directories" ($mismatch.Count -eq 0) ("checked " + (Get-ChildItem $srcRoot -Recurse -File -Filter '*.java').Count + " files")
$mismatch | ForEach-Object { Write-Host ("      " + $_) }

Write-Host ""
Write-Host "A5. Every public class has a Javadoc header"
$noDoc = @()
foreach ($f in (Get-ChildItem $srcRoot -Recurse -File -Filter '*.java')) {
    $text = Get-Content $f.FullName -Raw
    if ($text -notmatch '/\*\*') { $noDoc += $f.Name }
}
Check "all classes documented" ($noDoc.Count -eq 0) ("undocumented: " + $noDoc.Count)
$noDoc | ForEach-Object { Write-Host ("      " + $_) }

Write-Host ""
Write-Host "A6. No leftover temporary files in the project"
$junk = Get-ChildItem $root -Recurse -File -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like '_*' -and $_.Name -ne '__init__.py' -and $_.FullName -notmatch '__pycache__|\\\.git\\' }
Check "no underscore-prefixed temp files" ($junk.Count -eq 0) ("found: " + $junk.Count)
$junk | ForEach-Object { Write-Host ("      " + $_.FullName.Replace($root + '\', '')) }

Write-Host ""
Write-Host "A7. Layer dependencies are one-directional"
# controller must not import mapper directly; service impl must not import controller
$violations = @()
foreach ($f in (Get-ChildItem (Join-Path $srcRoot 'controller') -File -Filter '*.java')) {
    if ((Get-Content $f.FullName -Raw) -match 'import\s+com\.lijunjie\.wms\.mapper\.') { $violations += ("controller -> mapper: " + $f.Name) }
}
foreach ($f in (Get-ChildItem (Join-Path $srcRoot 'service') -Recurse -File -Filter '*.java')) {
    if ((Get-Content $f.FullName -Raw) -match 'import\s+com\.lijunjie\.wms\.controller\.') { $violations += ("service -> controller: " + $f.Name) }
}
foreach ($f in (Get-ChildItem (Join-Path $srcRoot 'mapper') -File -Filter '*.java')) {
    if ((Get-Content $f.FullName -Raw) -match 'import\s+com\.lijunjie\.wms\.service\.') { $violations += ("mapper -> service: " + $f.Name) }
}
Check "no reverse layer dependencies" ($violations.Count -eq 0) ("violations: " + $violations.Count)
$violations | ForEach-Object { Write-Host ("      " + $_) }

# ======================================================================
Write-Host ""
Write-Host "=== PART B. Functional verification ==="

Write-Host ""
Write-Host "B1. Build"
Push-Location $backend
$buildOut = & $mvn -B -DskipTests clean package 2>&1 | Out-String
$buildCode = $LASTEXITCODE
Pop-Location
if ($buildCode -ne 0) {
    Write-Host "build failed:" -ForegroundColor Red
    ($buildOut -split "`r?`n" | Select-Object -Last 20) | ForEach-Object { Write-Host ("  | " + $_) }
    exit 1
}
$warns = ($buildOut -split "`r?`n") | Where-Object { $_ -match '\[WARNING\]' -and $_ -notmatch 'platform encoding|File encoding' }
Check "build succeeds" $true ("jar = {0:N2} MB" -f ((Get-Item $jar).Length / 1MB))
Check "no compiler warnings" ($warns.Count -eq 0) ("warnings = " + $warns.Count)

Write-Host ""
Write-Host "B2. Start application"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.Arguments = '-jar "' + $jar + '"'
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.EnvironmentVariables['JAVA_HOME'] = 'D:\Dev\jdk8'
$psi.EnvironmentVariables['SPRING_DATASOURCE_USERNAME'] = $dbUser
$psi.EnvironmentVariables['SPRING_DATASOURCE_PASSWORD'] = $dbPass
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$outTask = $proc.StandardOutput.ReadToEndAsync()
$errTask = $proc.StandardError.ReadToEndAsync()
$ready = $false
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 3
    if ($proc.HasExited) { break }
    if ((Api 'Get' "$base/api/health/ping" $null).status -eq 200) { $ready = $true; break }
}
if (-not $ready) {
    Write-Host "  application not ready" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    try { Write-Host ((($outTask.Result) -split "`r?`n" | Select-Object -Last 20) -join "`n") } catch { }
    exit 2
}
Write-Host ("  ready after {0}s (pid {1})" -f ($i * 3), $proc.Id)

Write-Host ""
Write-Host "B3. Demo product data"
$r = Api 'Get' "$base/api/products?limit=100" $null
Check "15 demo products" ($r.json.data.Count -eq 15) ("count=" + $r.json.data.Count)
$freqs = $r.json.data | ForEach-Object { [int]$_.pickFreq }
$desc = ($freqs -join ',') -eq (($freqs | Sort-Object -Descending) -join ',')
Check "list sorted by pickFreq desc" $desc ("first=" + $freqs[0] + " last=" + $freqs[-1])
Check "highest frequency is 480" ($freqs[0] -eq 480) ("top pickFreq=" + $freqs[0])

$r = Api 'Get' "$base/api/products/1" $null
Check "product detail works" ($r.json.data.productCode -eq 'P0001') ("code=" + $r.json.data.productCode)

$r = Api 'Get' "$base/api/products?abcClass=A&limit=100" $null
Check "filter by ABC class A" ($r.json.data.Count -gt 0) ("count=" + $r.json.data.Count)
$r = Api 'Get' "$base/api/products?keyword=P0001&limit=100" $null
Check "keyword search works" ($r.json.data.Count -ge 1) ("count=" + $r.json.data.Count)

Write-Host ""
Write-Host "B4. Category tree"
$r = Api 'Get' "$base/api/categories/tree" $null
Check "tree returned" ($r.json.data.Count -ge 3) ("root nodes=" + $r.json.data.Count)
$withChildren = $r.json.data | Where-Object { $_.children.Count -gt 0 }
Check "tree has child nodes" ($withChildren.Count -ge 1) ("parents with children=" + $withChildren.Count)
$totalNodes = 0
function Count-Nodes($nodes) { $c = 0; foreach ($n in $nodes) { $c += 1 + (Count-Nodes $n.children) }; return $c }
$totalNodes = Count-Nodes $r.json.data
Check "tree covers all 5 categories" ($totalNodes -eq 5) ("nodes=" + $totalNodes)

$r = Api 'Post' "$base/api/categories" '{"categoryName":"verify-tmp-cat","parentId":0,"sort":99}'
Check "create category works" ($r.json.code -eq 200) ("id=" + $r.json.data.id)
$tmpCat = $r.json.data.id
$r = Api 'Delete' "$base/api/categories/1" $null
Check "delete parent category blocked (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Delete' "$base/api/categories/$tmpCat" $null
Check "delete empty category works" ($r.json.code -eq 200) ("code=" + $r.json.code)

Write-Host ""
Write-Host "B5. Pick frequency accumulation"
$code = 'VP' + (Get-Random -Minimum 100000 -Maximum 999999)
$r = Api 'Post' "$base/api/products" ("{""productCode"":""$code"",""productName"":""verify product"",""unit"":""pcs"",""pickFreq"":0,""abcClass"":""C"",""status"":1}")
Check "create product works" ($r.json.code -eq 200) ("id=" + $r.json.data.id + " code=" + $r.json.data.productCode)
$prodId = $r.json.data.id

$r = Api 'Post' "$base/api/products/$prodId/pick-freq?delta=5" $null
Check "pick freq 0 +5 = 5" ($r.json.data.pickFreq -eq 5) ("pickFreq=" + $r.json.data.pickFreq)
$r = Api 'Post' "$base/api/products/$prodId/pick-freq?delta=7" $null
Check "pick freq 5 +7 = 12" ($r.json.data.pickFreq -eq 12) ("pickFreq=" + $r.json.data.pickFreq)
$r = Api 'Post' "$base/api/products/$prodId/pick-freq?delta=0" $null
Check "zero delta rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/products/999999/pick-freq?delta=1" $null
Check "unknown product rejected (404)" ($r.status -eq 404) ("http=" + $r.status + " msg=" + $r.json.message)

Write-Host ""
Write-Host "B6. ABC classification (70% / 90% thresholds)"
$r = Api 'Post' "$base/api/products/abc-classify" $null
Check "classification runs" ($r.json.code -eq 200) ("updated=" + $r.json.data.updatedCount)
$counts = $r.json.data.classCounts
$ratio = $r.json.data.classFrequencyRatio
$sum = [int]$counts.A + [int]$counts.B + [int]$counts.C
Check "all products classified" ($sum -eq 16) ("A=" + $counts.A + " B=" + $counts.B + " C=" + $counts.C + " total=" + $sum)
Check "A class frequency ratio ~70%" ([math]::Abs([double]$ratio.A - 70.0) -le 25.0) ("A ratio=" + $ratio.A + "%")
# By construction A covers up to 70% of the cumulative frequency, so its product
# count is NOT necessarily the smallest (a few high-frequency items can fill it).
# Assert the meaningful property instead: A must carry the majority of the volume.
Check "A class carries 55%-85% of frequency" ([double]$ratio.A -ge 55.0 -and [double]$ratio.A -le 85.0) `
      ("A ratio=" + $ratio.A + "%")
Check "C class has the most products" ([int]$counts.C -ge [int]$counts.A -and [int]$counts.C -ge [int]$counts.B) `
      ("A=" + $counts.A + " B=" + $counts.B + " C=" + $counts.C)
Check "C class has highest count" ([int]$counts.C -ge [int]$counts.B) ("C=" + $counts.C + " B=" + $counts.B)

$r = Api 'Get' "$base/api/products/abc-summary" $null
Check "abc summary endpoint works" ($r.json.data.Count -ge 1) ("rows=" + $r.json.data.Count)

$r = Api 'Post' "$base/api/products/abc-classify" $null
Check "classification is idempotent" ($r.json.code -eq 200) ("second run updated=" + $r.json.data.updatedCount)

Write-Host ""
Write-Host "B7. Validation and duplicates"
$r = Api 'Post' "$base/api/products" '{"productName":"no code"}'
Check "missing code rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/products" ("{""productCode"":""$code"",""productName"":""dup""}")
Check "duplicate code rejected (409)" ($r.status -eq 409) ("http=" + $r.status + " msg=" + $r.json.message)
$r = Api 'Post' "$base/api/products" '{"productCode":"VBAD1","productName":"bad abc","abcClass":"X"}'
Check "invalid abc class rejected (400)" ($r.status -eq 400) ("http=" + $r.status + " msg=" + $r.json.message)

# Wrong content type must yield 415, not 500. Sending a form-encoded body while
# the endpoint expects JSON is the simplest way to trigger it.
$formResp = $null
try {
    $formResp = Invoke-WebRequest -Uri "$base/api/products" -Method Post -Body @{ productName = 'x' } `
        -TimeoutSec 15 -UseBasicParsing -SkipHttpErrorCheck
} catch { }
Check "wrong content type -> 415" ($null -ne $formResp -and [int]$formResp.StatusCode -eq 415) `
      ("http=" + $(if ($formResp) { [int]$formResp.StatusCode } else { "no response" }))

# ======================================================================
Write-Host ""
Write-Host "=== PART C. Cleanup ==="
RunSql "DELETE FROM wms.base_product WHERE product_code = '$code';"
$r = Api 'Get' "$base/api/products?limit=100" $null
Check "test product removed" ($r.json.data.Count -eq 15) ("count=" + $r.json.data.Count)
RunSql "UPDATE wms.base_product SET abc_class='A' WHERE product_code IN ('P0001','P0002','P0003','P0004','P0005'); UPDATE wms.base_product SET abc_class='B' WHERE product_code IN ('P0006','P0007','P0008','P0009','P0010','P0011'); UPDATE wms.base_product SET abc_class='C' WHERE product_code IN ('P0012','P0013','P0014','P0015');"
$r = Api 'Get' "$base/api/products/abc-summary" $null
Check "demo ABC distribution restored" ($r.json.data.Count -eq 3) ("rows=" + $r.json.data.Count)
$r = Api 'Get' "$base/api/warehouses" $null
Check "demo warehouses untouched" ($r.json.data.Count -eq 1) ("count=" + $r.json.data.Count)

Write-Host ""
Write-Host "=== Stop application ==="
if (-not $proc.HasExited) {
    $proc.Kill(); try { $proc.WaitForExit(10000) | Out-Null } catch { }
    Write-Host "  stopped"
}
Start-Sleep -Seconds 1
$log = ''
try { $log = $outTask.Result } catch { }
$errors = ($log -split "`r?`n") | Where-Object { $_ -match ' ERROR ' }
Check "no ERROR in application log" ($errors.Count -eq 0) ("error lines = " + $errors.Count)
if ($errors.Count -gt 0) { $errors | Select-Object -First 5 | ForEach-Object { Write-Host ("      " + $_.Trim()) } }

Write-Host ""
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host "PRODUCT MODULE VERIFICATION AND REVIEW PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "PRODUCT MODULE VERIFICATION AND REVIEW FAILED" -ForegroundColor Red; exit 3 }
