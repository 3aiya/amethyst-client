<#
.SYNOPSIS
    Builds every Minecraft-version project and publishes one GitHub release per Minecraft version.

.DESCRIPTION
    Every folder named like a Minecraft version (1.21.1, 26.2, ...) that contains a gradle.properties
    is a separate Gradle project. For each one this script:
      1. sets mod_version (when -Version is given),
      2. runs gradlew build,
      3. copies the jar to dist\amethystclient-<version>+mc<mc>.jar,
      4. publishes GitHub release  v<version>-mc<mc>  with that jar attached,
      5. deletes older releases (and their tags) for the SAME Minecraft version,
         so each Minecraft version only ever has its newest release.
    The new release is created before the old one is deleted, so a failure never leaves a
    Minecraft version without a release.

.EXAMPLE
    .\build-release.ps1 -Version 1.0.1
    Bump every project to 1.0.1, build, commit + push, and replace the releases.

.EXAMPLE
    .\build-release.ps1
    Rebuild the current version and re-upload the jars to the existing releases.

.EXAMPLE
    .\build-release.ps1 -Version 1.0.1 -McVersions 26.2 -SkipUpload
    Only build 26.2 locally, nothing is pushed or published.
#>
param(
    # New mod version (e.g. 1.0.1). Omit it to keep the version already in gradle.properties.
    [string]$Version,
    # Only these Minecraft versions (folder names). Default: all of them.
    [string[]]$McVersions,
    # Build only; don't commit, push or touch GitHub releases.
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$ModId = 'amethystclient'
$ModName = 'Amethyst Client'
$Dist = Join-Path $Root 'dist'
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host "ERROR: $text" -ForegroundColor Red; exit 1 }

function Invoke-Native {
    # Runs a native command and stops the script if it fails.
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { Fail "$Exe $($Arguments -join ' ') failed (exit code $LASTEXITCODE)" }
}

function Get-Property($file, $name) {
    $match = Select-String -Path $file -Pattern "^$name=(.*)$" | Select-Object -First 1
    if ($match) { return $match.Matches[0].Groups[1].Value.Trim() }
    return $null
}

# ---------------------------------------------------------------------------------------------
# Find the Minecraft-version projects
# ---------------------------------------------------------------------------------------------
$projects = Get-ChildItem $Root -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+(\.\d+)?$' -and (Test-Path (Join-Path $_.FullName 'gradle.properties')) } |
    Sort-Object { [version]$_.Name }

if ($McVersions) {
    $unknown = $McVersions | Where-Object { $projects.Name -notcontains $_ }
    if ($unknown) { Fail "No project folder for: $($unknown -join ', ')" }
    $projects = $projects | Where-Object { $McVersions -contains $_.Name }
}
if (-not $projects) { Fail 'No Minecraft version folders found.' }

# ---------------------------------------------------------------------------------------------
# Work out the mod version
# ---------------------------------------------------------------------------------------------
if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+([-.][0-9A-Za-z.-]+)?$') { Fail "'$Version' is not a version like 1.0.1" }
    Step "Setting mod_version=$Version"
    foreach ($p in $projects) {
        $props = Join-Path $p.FullName 'gradle.properties'
        $text = [IO.File]::ReadAllText($props)
        $text = [regex]::Replace($text, '(?m)^mod_version=.*$', "mod_version=$Version")
        [IO.File]::WriteAllText($props, $text, $Utf8NoBom)
        Write-Host "  $($p.Name)"
    }
} else {
    $versions = $projects | ForEach-Object { Get-Property (Join-Path $_.FullName 'gradle.properties') 'mod_version' } | Sort-Object -Unique
    if (@($versions).Count -ne 1) { Fail "Projects have different mod_version values ($($versions -join ', ')). Pass -Version to set one." }
    $Version = @($versions)[0]
}
Write-Host "Mod version: $Version    Minecraft: $($projects.Name -join ', ')"

# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------
New-Item -ItemType Directory -Force $Dist | Out-Null
$built = @()
foreach ($p in $projects) {
    $mc = $p.Name
    Step "Building Minecraft $mc"
    Push-Location $p.FullName
    try {
        & .\gradlew.bat build --console=plain -q
        if ($LASTEXITCODE -ne 0) { Fail "Build failed for Minecraft $mc" }
    } finally {
        Pop-Location
    }

    $jar = Join-Path $p.FullName "build\libs\$ModId-$Version.jar"
    if (-not (Test-Path $jar)) { Fail "Expected jar not found: $jar" }

    # Remove jars from earlier versions for this Minecraft version, then copy the new one.
    Get-ChildItem $Dist -Filter "$ModId-*+mc$mc.jar" | Remove-Item -Force
    $out = Join-Path $Dist "$ModId-$Version+mc$mc.jar"
    Copy-Item $jar $out -Force
    Write-Host "  -> dist\$(Split-Path $out -Leaf)" -ForegroundColor Green

    $modJson = Get-Content (Join-Path $p.FullName 'src\main\resources\fabric.mod.json') -Raw | ConvertFrom-Json
    $built += [pscustomobject]@{
        Mc        = $mc
        Jar       = $out
        Tag       = "v$Version-mc$mc"
        # Shown on GitHub, e.g. "Amethyst Client 26.1.2 v1.0.1"
        Title     = "$ModName $mc v$Version"
        Loader    = $modJson.depends.fabricloader
        Java      = $modJson.depends.java
        FabricApi = Get-Property (Join-Path $p.FullName 'gradle.properties') 'fabric_version'
    }
}

if ($SkipUpload) {
    Step 'Done (build only, -SkipUpload given)'
    exit 0
}

# ---------------------------------------------------------------------------------------------
# Commit + push, so the release tags point at the code they were built from
# ---------------------------------------------------------------------------------------------
Step 'Checking git and GitHub'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail 'GitHub CLI (gh) is not installed.' }
# gh writes its status to stderr; in Windows PowerShell 5.1 redirecting native stderr under
# ErrorActionPreference=Stop would abort the script, so relax it just for this check.
$ErrorActionPreference = 'Continue'
& gh auth status *> $null
$authExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($authExit -ne 0) { Fail 'Not logged in to GitHub. Run: gh auth login' }

# Point the README's download links at the versions being released, so they go out in the same commit.
$readme = Join-Path $Root 'README.md'
if (Test-Path $readme) {
    $text = [IO.File]::ReadAllText($readme)
    foreach ($b in $built) {
        $mcRegex = [regex]::Escape($b.Mc)
        # Tag in URLs: v1.0.0-mc26.2/ or v1.0.0-mc26.2)  (the lookahead keeps 1.21.1 from matching 1.21.11)
        $text = [regex]::Replace($text, "v[0-9][0-9A-Za-z.\-]*-mc$mcRegex(?=[/)])", $b.Tag)
        # Jar file name: amethystclient-1.0.0+mc26.2.jar
        $text = [regex]::Replace($text, "$ModId-[0-9][0-9A-Za-z.\-]*\+mc$mcRegex\.jar", (Split-Path $b.Jar -Leaf))
    }
    [IO.File]::WriteAllText($readme, $text, $Utf8NoBom)
}

Push-Location $Root
try {
    $dirty = git status --porcelain
    if ($dirty) {
        Write-Host 'Committing changes:'
        $dirty | ForEach-Object { Write-Host "  $_" }
        Invoke-Native git @('add', '-A')
        Invoke-Native git @('commit', '-q', '-m', "Release v$Version")
    }
    Invoke-Native git @('push', '-q', 'origin', 'HEAD')
    $commit = (git rev-parse HEAD).Trim()

    # ---------------------------------------------------------------------------------------------
    # Publish: one release per Minecraft version, older ones for that version are removed
    # ---------------------------------------------------------------------------------------------
    $existing = @(gh release list --limit 1000 --json tagName --jq '.[].tagName')
    if ($LASTEXITCODE -ne 0) { Fail 'Could not list GitHub releases.' }

    # The highest Minecraft version gets GitHub's "Latest" badge.
    $newestMc = ($built | Sort-Object { [version]$_.Mc } | Select-Object -Last 1).Mc

    foreach ($b in $built) {
        Step "Release $($b.Tag)"
        if ($existing -contains $b.Tag) {
            # Same version released before: just replace the jar.
            Invoke-Native gh @('release', 'upload', $b.Tag, $b.Jar, '--clobber')
            Invoke-Native gh @('release', 'edit', $b.Tag, '--title', $b.Title)
            Write-Host '  Updated jar on existing release' -ForegroundColor Green
        } else {
            $notes = @"
**$ModName $Version** for **Minecraft $($b.Mc)**

Caches the server's resource pack per server. The pack is only downloaded and re-applied when the server's SHA-1 changes.

### Requirements
- Minecraft $($b.Mc)
- Fabric Loader $($b.Loader)
- [Fabric API](https://modrinth.com/mod/fabric-api) (built against $($b.FabricApi))
- Java $($b.Java)

### Install
Put ``$(Split-Path $b.Jar -Leaf)`` and Fabric API in ``.minecraft/mods/``. Remove any older ``$ModId`` jar.
"@
            $notesFile = [IO.Path]::GetTempFileName()
            [IO.File]::WriteAllText($notesFile, $notes, $Utf8NoBom)
            try {
                $latest = if ($b.Mc -eq $newestMc) { '--latest' } else { '--latest=false' }
                Invoke-Native gh @('release', 'create', $b.Tag, $b.Jar,
                    '--title', $b.Title,
                    '--notes-file', $notesFile,
                    '--target', $commit,
                    $latest)
            } finally {
                Remove-Item $notesFile -Force
            }
            Write-Host '  Created' -ForegroundColor Green
        }

        # Now that the new release exists, delete older releases for this Minecraft version.
        $pattern = '^v.+-mc' + [regex]::Escape($b.Mc) + '$'
        foreach ($old in ($existing | Where-Object { $_ -match $pattern -and $_ -ne $b.Tag })) {
            Invoke-Native gh @('release', 'delete', $old, '--cleanup-tag', '--yes')
            Write-Host "  Removed old release $old" -ForegroundColor Yellow
        }
    }

    $repo = (gh repo view --json url --jq '.url').Trim()
    Step "Done: $repo/releases"
} finally {
    Pop-Location
}
