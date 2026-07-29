$ErrorActionPreference = "Stop"
$runtimeRoot = "D:\InventoryRuntime"
$projectRoot = "D:\codeWorkpace\InventoryManagement"
$logRoot = Join-Path $runtimeRoot "logs"
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

function Test-Port([int]$Port) {
    try {
        $client = [Net.Sockets.TcpClient]::new()
        $client.Connect("127.0.0.1", $Port)
        $client.Dispose()
        return $true
    } catch {
        return $false
    }
}

function Start-LocalProcess {
    param(
        [string]$Name,
        [int]$Port,
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [hashtable]$Environment = @{}
    )
    if (Test-Port $Port) {
        Write-Host "[OK] $Name is already listening on $Port"
        return
    }
    foreach ($item in $Environment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($item.Key, $item.Value, "Process")
    }
    Start-Process -FilePath $FilePath -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput (Join-Path $logRoot "$Name-stdout.log") `
        -RedirectStandardError (Join-Path $logRoot "$Name-stderr.log") `
        -WindowStyle Hidden
    Write-Host "[START] $Name"
}

Start-LocalProcess "mysql" 3306 `
    "$runtimeRoot\mysql\mysql-8.4.10-winx64\bin\mysqld.exe" `
    @("--defaults-file=$runtimeRoot\mysql\my.ini") "$runtimeRoot\mysql"

Start-LocalProcess "minio" 9000 "$runtimeRoot\minio\minio.exe" `
    @("server", "$runtimeRoot\minio\data", "--address", ":9000", "--console-address", ":9001") `
    "$runtimeRoot\minio" `
    @{ MINIO_ROOT_USER = "minioadmin"; MINIO_ROOT_PASSWORD = "minioadmin" }

Start-LocalProcess "qdrant" 6333 "$runtimeRoot\qdrant\bin\qdrant.exe" `
    @("--config-path", "$runtimeRoot\qdrant\config.yaml", "--disable-telemetry") `
    "$runtimeRoot\qdrant"

Start-LocalProcess "siglip" 8000 "$runtimeRoot\siglip\venv\Scripts\python.exe" `
    @("-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", "8000") `
    "$projectRoot\embedding-service" `
    @{
        HF_HOME = "$runtimeRoot\siglip\models"
        SIGLIP_MODEL = "google/siglip2-base-patch16-224"
        SIGLIP_MODEL_REVISION = "75de2d55ec2d0b4efc50b3e9ad70dba96a7b2fa2"
    }

Start-LocalProcess "backend" 8080 "$runtimeRoot\java\jdk-21.0.11+10\bin\java.exe" `
    @("-jar", "$projectRoot\backend\target\inventory-api-1.0.0.jar") `
    "$projectRoot\backend" `
    @{
        DB_URL = "jdbc:mysql://127.0.0.1:3306/inventory?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
        DB_USERNAME = "inventory"
        DB_PASSWORD = "inventory123"
        MINIO_ENDPOINT = "http://127.0.0.1:9000"
        MINIO_PUBLIC_ENDPOINT = "https://frp-bar.com:64891"
        MINIO_REGION = "us-east-1"
        MINIO_ACCESS_KEY = "minioadmin"
        MINIO_SECRET_KEY = "minioadmin"
        QDRANT_URL = "http://127.0.0.1:6333"
        EMBEDDING_URL = "http://127.0.0.1:8000"
        SIGLIP_MODEL_VERSION = "google/siglip2-base-patch16-224@75de2d55ec2d0b4efc50b3e9ad70dba96a7b2fa2"
        QDRANT_COLLECTION = "products_siglip2_base_patch16_224_75de2d55"
        IMAGE_SEARCH_THRESHOLD = "0.55"
        LOG_PATH = $logRoot
    }

$node = "C:\Users\XEH\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
Start-LocalProcess "frontend" 5173 $node `
    @("node_modules\vite\bin\vite.js", "--host", "127.0.0.1", "--port", "5173") `
    "$projectRoot\frontend"

Start-Sleep -Seconds 5
Write-Host "Application: http://127.0.0.1:5173"
Write-Host "MinIO:      http://127.0.0.1:9001"
Write-Host "Qdrant:     http://127.0.0.1:6333/dashboard"
Write-Host "SigLIP2:    http://127.0.0.1:8000/health"
