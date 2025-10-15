#!/bin/bash
set -e  # Exit on error

echo "🔧 Reinstalando JSimpleLLM..."

# Paths
JSIMPLELLM_PATH="/mnt/f/1-ProjetosIA/github/JSimpleLLM"
JAR_PATH="$JSIMPLELLM_PATH/target/JSimpleLLM-0.0.1-SNAPSHOT.jar"
POM_PATH="$JSIMPLELLM_PATH/pom.xml"

# Verificar se JAR existe
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR não encontrado. Compilando..."
    cd "$JSIMPLELLM_PATH"
    mvn clean package -DskipTests
fi

# Instalar
echo "📦 Instalando no repositório local..."
mvn install:install-file \
  -Dfile="$JAR_PATH" \
  -DpomFile="$POM_PATH"

echo "✅ JSimpleLLM instalado com sucesso!"
echo ""
echo "Próximos passos:"
echo "  cd /mnt/f/1-ProjetosIA/github/JSimpleRag"
echo "  mvn clean compile"
