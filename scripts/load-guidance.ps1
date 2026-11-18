param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Token = $env:PAPERTRAIL_MANAGER_TOKEN
)

$ErrorActionPreference = 'Stop'
$address = [Uri]$BaseUrl
if (-not $address.IsAbsoluteUri -or $address.Scheme -notin @('http', 'https')) {
    throw 'Informe uma URL HTTP ou HTTPS.'
}
if ($address.Scheme -eq 'http' -and -not $address.IsLoopback) {
    throw 'Use HTTPS para carregar os dados fora da maquina local.'
}
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'Configure PAPERTRAIL_MANAGER_TOKEN com um token MANAGER.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{Authorization = 'Bearer ' + $Token}
$sourcePath = Join-Path $PSScriptRoot '../src/main/resources/guidance/official-services.json'
$data = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $sourcePath).Path) | ConvertFrom-Json

function Invoke-Api([string]$Path, [string]$Method = 'GET', $Body = $null) {
    $arguments = @{Uri = $BaseUrl + $Path; Method = $Method; Headers = $headers; TimeoutSec = 45; MaximumRedirection = 0}
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json; charset=utf-8'
        $arguments.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 12 -Compress))
    }
    $response = Invoke-WebRequest @arguments -UseBasicParsing
    if ($response.RawContentLength -gt 0) {
        [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json
    }
}

function Get-Pages([string]$Path) {
    $page = 0
    do {
        $response = Invoke-Api ($Path + '?page=' + $page + '&size=100')
        $response.content
        $page++
    } while ($page -lt $response.page.totalPages)
}

$category = @(Get-Pages '/categories' | Where-Object {$_.name -eq $data.category})
if ($category.Count -gt 1) { throw 'Existe mais de uma categoria com o nome da carga.' }
if ($category.Count -eq 0) { $category = @(Invoke-Api '/categories' 'POST' @{name = $data.category}) }

foreach ($definition in $data.services) {
    $service = @(Get-Pages '/services' | Where-Object {
        $_.title -eq $definition.title -and $_.cityName -eq $definition.cityName
    })
    if ($service.Count -gt 1) { throw ('Servico duplicado: ' + $definition.key) }
    if ($service.Count -eq 0) {
        $service = @(Invoke-Api '/services' 'POST' @{
            categoryId = $category[0].id; title = $definition.title
            description = $definition.description; cityName = $definition.cityName
        })
    }
    $serviceId = $service[0].id
    $requirements = @(Get-Pages ('/services/' + $serviceId + '/requirements'))
    foreach ($requirement in $definition.requirements) {
        $existing = @($requirements | Where-Object {$_.title -eq $requirement.title})
        if ($existing.Count -gt 1) { throw ('Requisito duplicado: ' + $requirement.title) }
        $body = @{title = $requirement.title; required = $requirement.required; sourceUrl = $definition.sourceUrl}
        if ($existing.Count -eq 0) {
            Invoke-Api ('/services/' + $serviceId + '/requirements') 'POST' $body | Out-Null
        } elseif ($existing[0].required -ne $requirement.required -or $existing[0].sourceUrl -ne $definition.sourceUrl) {
            Invoke-Api ('/services/' + $serviceId + '/requirements/' + $existing[0].id) 'PUT' $body | Out-Null
        }
    }

    $version = (Invoke-Api ('/services/' + $serviceId)).version
    $chunks = @(Get-Pages ('/services/' + $serviceId + '/guidance-chunks'))
    foreach ($chunk in $definition.chunks) {
        $title = $definition.sourceTitle + ' | ' + $chunk.topic
        $existing = @($chunks | Where-Object {$_.sourceTitle -eq $title})
        if ($existing.Count -gt 1) { throw ('Trecho duplicado: ' + $title) }
        $body = @{
            content = $chunk.content; sourceUrl = $definition.sourceUrl; sourceTitle = $title
            reviewedAt = $data.reviewedAt; version = $version
        }
        if ($existing.Count -eq 0) {
            Invoke-Api ('/services/' + $serviceId + '/guidance-chunks') 'POST' $body | Out-Null
        } elseif ($existing[0].content -ne $chunk.content -or $existing[0].version -ne $version -or
            $existing[0].sourceUrl -ne $definition.sourceUrl -or
            ([DateTimeOffset]$existing[0].reviewedAt) -ne ([DateTimeOffset]$data.reviewedAt)) {
            Invoke-Api ('/services/' + $serviceId + '/guidance-chunks/' + $existing[0].id) 'PUT' $body | Out-Null
        }
    }
    Write-Output ($definition.key + ': serviceId=' + $serviceId + ', version=' + $version)
}
