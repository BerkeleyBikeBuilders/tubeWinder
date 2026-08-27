@echo off
rem Open Tube Winder.
rem
rem Builds the jar first if it is missing or older than the sources, so this works straight after a
rem clone or a pull with nothing else to run. See tools\launch.ps1 for the detail.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\launch.ps1"
