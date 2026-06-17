$process = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "bootRun" -PassThru -NoNewWindow
$ready = $false
for ($i=0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/index.html" -ErrorAction SilentlyContinue
    if ($resp.StatusCode -eq 200) { $ready = $true; break }
}
if ($ready) {
    echo "Server is ready."
    $body = '{"email":"test99@test.com","password":"123","nickname":"test"}'
    try {
        $result = Invoke-RestMethod -Uri http://localhost:8080/api/auth/signup -Method Post -Body $body -ContentType "application/json"
        echo "Response: $result"
    } catch {
        echo "Error: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.BaseStream.Position = 0
            $reader.DiscardBufferedData()
            $responseBody = $reader.ReadToEnd();
            echo "Body: $responseBody"
        }
    }
} else {
    echo "Server did not start in time."
}
Stop-Process -Id $process.Id -Force
$p = (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue).OwningProcess; if ($p) { Stop-Process -Id $p -Force }
