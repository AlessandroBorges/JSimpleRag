@echo on
setlocal

REM Reinstalando JSimpleLLM...

REM Paths
set "JSIMPLELLM_PATH=F:\1-ProjetosIA\github\JSimpleLLM"
set "JAR_PATH=%JSIMPLELLM_PATH%\target\JSimpleLLM-0.0.1-SNAPSHOT.jar"
set "POM_PATH=%JSIMPLELLM_PATH%\pom.xml"

REM Verificar se JAR existe
if not exist "%JAR_PATH%" (
    echo JAR nao encontrado. Compilando...
    pushd "%JSIMPLELLM_PATH%"
    mvn clean package -DskipTests
    popd
)

REM Instalar
echo Instalando no repositorio local...
echo %JAR_PATH%
mvn install:install-file -Dfile="%JAR_PATH%" -DpomFile="%POM_PATH%"

echo JSimpleLLM instalado com sucesso!
echo.
echo Proximos passos: 
echo   cd f:
echo   cd f:\1-ProjetosIA\github\JSimpleRag
echo   mvn clean compile

endlocal
pause
