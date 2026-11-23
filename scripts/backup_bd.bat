@echo off
REM Script de respaldo automatico de la base de datos LibRed_v22
set FECHA=%date:~-4,4%%date:~-7,2%%date:~-10,2%_%time:~0,2%%time:~3,2%
set FECHA=%FECHA: =0%
if not exist "backups" mkdir backups

echo [%date% %time%] Iniciando respaldo de base de datos LibRed_v22...
mysqldump -u root -p%DB_PASSWORD% libred_db > backups\libred_backup_%FECHA%.sql
echo [%date% %time%] Respaldo completado: backups\libred_backup_%FECHA%.sql
