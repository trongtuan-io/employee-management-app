# Script dong bo vi tri thuc te cua May tinh sang May ao Android (Emulator)
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

Write-Host "=== DONG BO VI TRI THUC TE MAY TINH -> MAY AO ANDROID ===" -ForegroundColor Green

try {
    # 1. Lay vi tri IP/Wifi thuc te cua may tinh hien tai
    $geoInfo = Invoke-RestMethod -Uri "http://ip-api.com/json/" -TimeoutSec 5
    if ($geoInfo -and $geoInfo.status -eq "success") {
        $lat = $geoInfo.lat
        $lon = $geoInfo.lon
        $city = $geoInfo.city
        $country = $geoInfo.country

        Write-Host " [v] Vi tri may tinh hien tai: $city, $country" -ForegroundColor Yellow
        Write-Host " [v] Toa do: Lat = $lat, Lon = $lon" -ForegroundColor Yellow

        # 2. Gui toa do sang May ao Emulator qua ADB (chu y: adb geo fix <longitude> <latitude>)
        $result = & $adbPath emu geo fix $lon $lat
        if ($result -contains "OK") {
            Write-Host " [SUCCESS] Da dong bo thanh cong sang May ao Android!" -ForegroundColor Green
        } else {
            Write-Host " [!] Ket qua ADB: $result" -ForegroundColor Cyan
        }
    } else {
        Write-Host " [X] Khong lay duoc vi tri tu mang." -ForegroundColor Red
    }
} catch {
    Write-Host " [X] Loi: $_" -ForegroundColor Red
}
