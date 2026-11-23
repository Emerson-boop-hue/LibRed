@echo off
REM Limpieza de archivos de log con mas de 30 dias de antiguedad
echo [%date% %time%] Ejecutando limpieza de logs antiguos...
forfiles /p "logs" /s /m *.log /D -30 /C "cmd /c del @path" 2>nul
echo [%date% %time%] Mantenimiento de logs finalizado.
