#!/usr/bin/env pwsh
# Smart Knee OA – Maven Wrapper (uses locally downloaded Maven 3.9.9)

$mvnExe = "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.9\bin\mvn.cmd"

if (-not (Test-Path $mvnExe)) {
    Write-Error "Maven not found at: $mvnExe — Please re-run setup."
    exit 1
}

# Forward all arguments
& $mvnExe @args
