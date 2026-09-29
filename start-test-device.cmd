@echo off
setlocal
set "PYTHONPATH=%~dp0simulator\runtime"
python -c "import winrt.windows.devices.bluetooth; import winrt.windows.devices.bluetooth.genericattributeprofile; import winrt.windows.storage.streams" >nul 2>nul
if errorlevel 1 (
    echo Installing BLE simulator requirements on this notebook...
    python -m pip install --target "%~dp0simulator\runtime" -r "%~dp0simulator\requirements.txt"
    if errorlevel 1 (
        echo Could not install the required Python packages.
        pause
        exit /b 1
    )
)
echo Starting BLE Mission test device on this notebook...
echo Leave this window open while testing on the phone.
python -u "%~dp0simulator\ble_mission_simulator.py"
echo.
echo The test device has stopped.
pause
