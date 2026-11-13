@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET ___MVNW_WHOM_MSYS2_CYGWIN_EXECUTES_ME___=%__MVNW_ARG0_NAME__%
@SET __MVNW_CMD__=
@IF NOT "%MVNW_LAUNCHER%"=="" (
  @SET __MVNW_CMD__=%MVNW_LAUNCHER%
)
@IF "%__MVNW_CMD__%"=="" (
  @SET __MVNW_CMD__=mvnw.ps1
)
@powershell -NonInteractive -ExecutionPolicy Bypass -File "%~dp0%__MVNW_CMD__%" %*
