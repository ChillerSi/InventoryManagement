$runtimeRoot = "D:\InventoryRuntime"
$projectRoot = "D:\codeWorkpace\InventoryManagement"

Get-CimInstance Win32_Process |
    Where-Object {
        $_.CommandLine -and (
            $_.CommandLine.Contains($runtimeRoot) -or
            ($_.CommandLine.Contains($projectRoot) -and $_.CommandLine.Contains("vite"))
        )
    } |
    ForEach-Object {
        if ($_.ProcessId -ne $PID) {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "[STOP] $($_.Name) PID=$($_.ProcessId)"
        }
    }
