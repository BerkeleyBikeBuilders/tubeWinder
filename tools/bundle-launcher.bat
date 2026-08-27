@echo off
rem Tube Winder - Berkeley Bike Builders filament winder.
rem
rem Double-click this file. Nothing needs to be installed; the Java runtime this needs is in the
rem runtime folder next to it. Keep the whole folder together.

if not exist "%~dp0runtime\bin\javaw.exe" (
    echo The runtime folder is missing.
    echo Extract the whole TubeWinder folder out of the zip, then run this again.
    pause
    exit /b 1
)

if not exist "%~dp0TubeWinder.jar" (
    echo TubeWinder.jar is missing from this folder.
    pause
    exit /b 1
)

start "" "%~dp0runtime\bin\javaw.exe" -jar "%~dp0TubeWinder.jar"
