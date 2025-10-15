# Guia de Integração de Dependências Maven Locais

**Data**: 2025-10-14
**Contexto**: Integração do JSimpleLLM (projeto Maven local) com JSimpleRag

---

## 📋 Índice

1. [Visão Geral](#visao-geral)
2. [Conceitos Importantes](#conceitos-importantes)
3. [Método 1: Instalação Manual (Recomendado)](#metodo-1-instalacao-manual-recomendado)
4. [Método 2: Build do Projeto Dependente](#metodo-2-build-do-projeto-dependente)
5. [Método 3: Multi-Module Maven (Avançado)](#metodo-3-multi-module-maven-avancado)
6. [Troubleshooting](#troubleshooting)
7. [Checklist de Verificação](#checklist-de-verificacao)

---

## Visão Geral

### Visao Geral

Este guia documenta como integrar **JSimpleLLM** (biblioteca local) com **JSimpleRag** (projeto principal) usando Maven.

### Problema Original

```
[ERROR] Could not resolve dependencies for project bor.tools:simplerag:jar:0.0.1-SNAPSHOT
[ERROR]   Could not find artifact bor.tools:JSimpleLLM:jar:0.0.1-SNAPSHOT
```

### Solução

Instalar o JAR e POM do JSimpleLLM no repositório Maven local (`~/.m2/repository`).

---

## Conceitos Importantes

### 1. Repositório Maven Local

**Localização**: `~/.m2/repository/` (Linux/Mac) ou `C:\Users\<user>\.m2\repository\` (Windows)

Este é onde o Maven armazena todas as dependências baixadas e os projetos instalados localmente.

### 2. Dependências Transitivas

Quando você instala um JAR, ele pode ter suas próprias dependências. O JSimpleLLM depende de:
- `jackson-databind`
- `jackson-datatype-jsr310`
- `okhttp`
- `jtokkit`
- `slf4j-api`

**IMPORTANTE**: Se você instalar apenas o JAR sem o POM, essas dependências não serão reconhecidas!

### 3. GAV Coordinates

Todo artefato Maven é identificado por:
- **G**roupId: `bor.tools`
- **A**rtifactId: `JSimpleLLM`
- **V**ersion: `0.0.1-SNAPSHOT`

---

## Método 1: Instalação Manual (Recomendado)

## Metodo 1: Instalacao Manual (Recomendado)

Este é o método usado para resolver o problema original.

### Passo 1: Verificar o JAR compilado

```bash
# Navegue até o projeto JSimpleLLM
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM

# Verifique se o JAR existe
ls -lh target/JSimpleLLM-0.0.1-SNAPSHOT.jar
```

**Saída esperada**:

```
-rw-r--r-- 1 user user 123K Oct 14 17:00 target/JSimpleLLM-0.0.1-SNAPSHOT.jar
```

### Passo 2: Instalar no Repositório Local

```bash
# Instale o JAR COM o POM (IMPORTANTE!)
mvn install:install-file \
  -Dfile=/mnt/f/1-ProjetosIA/github/JSimpleLLM/target/JSimpleLLM-0.0.1-SNAPSHOT.jar \
  -DpomFile=/mnt/f/1-ProjetosIA/github/JSimpleLLM/pom.xml
```

**Por que usar `-DpomFile`?**
- ✅ Inclui todas as dependências transitivas
- ✅ Mantém metadata correto (groupId, artifactId, version)
- ✅ Funciona com profiles e properties do Maven

**Alternativa (NÃO RECOMENDADA)**:

```bash
# Sem o POM - cria POM mínimo SEM dependências transitivas
mvn install:install-file \
  -Dfile=JSimpleLLM-0.0.1-SNAPSHOT.jar \
  -DgroupId=bor.tools \
  -DartifactId=JSimpleLLM \
  -Dversion=0.0.1-SNAPSHOT \
  -Dpackaging=jar \
  -DgeneratePom=true  # ❌ Gera POM vazio, sem dependências!
```

**Saída esperada**:

```
[INFO] Installing .../JSimpleLLM-0.0.1-SNAPSHOT.jar to ~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/JSimpleLLM-0.0.1-SNAPSHOT.jar
[INFO] Installing .../pom.xml to ~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/JSimpleLLM-0.0.1-SNAPSHOT.pom
[INFO] BUILD SUCCESS
```

### Passo 3: Adicionar Dependência no JSimpleRag

Edite `JSimpleRag/pom.xml`:

```xml
<dependencies>
    <!-- Outras dependências... -->

    <!-- JSimpleLLM dependency (local Maven project) -->
    <dependency>
        <groupId>bor.tools</groupId>
        <artifactId>JSimpleLLM</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>

    <!-- Outras dependências... -->
</dependencies>
```

### Passo 4: Verificar a Integração

```bash
# No projeto JSimpleRag
cd /mnt/f/1-ProjetosIA/github/JSimpleRag

# Limpar e compilar
mvn clean compile

# Rodar testes
mvn test
```

**Saída esperada**:

```
[INFO] BUILD SUCCESS
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
```

---

## Método 2: Build do Projeto Dependente
## Metodo 2: Build do Projeto Dependente

Este método usa o ciclo de vida Maven completo.

### Passo 1: Build e Install do JSimpleLLM

```bash
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM

# Build completo com instalação no repositório local
mvn clean install -DskipTests
```

Este comando:
1. Limpa o target anterior
2. Compila o código
3. Roda testes (pulados com `-DskipTests`)
4. Cria o JAR
5. Instala JAR + POM no `~/.m2/repository`

**Se houver erro "Failed to delete target"**:

```bash
# Feche o Eclipse/IDE primeiro, ou:
mvn install -DskipTests  # Sem clean
```

### Passo 2: Usar no JSimpleRag

Igual ao Método 1 - Passo 3 e 4.

---

## Método 3: Multi-Module Maven (Avançado)
## Metodo 3: Multi-Module Maven (Avancado)

Para projetos que sempre evoluem juntos, considere um projeto multi-módulo.

### Estrutura de Diretórios

```
workspace/
├── parent-project/
│   ├── pom.xml              # Parent POM
│   ├── JSimpleLLM/          # Módulo 1
│   │   └── pom.xml
│   └── JSimpleRag/          # Módulo 2
│       └── pom.xml
```

### Parent POM

```xml
<project>
    <groupId>bor.tools</groupId>
    <artifactId>parent-project</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>JSimpleLLM</module>
        <module>JSimpleRag</module>
    </modules>
</project>
```

### Vantagens

- ✅ Build automático de ambos: `mvn clean install` na raiz
- ✅ Versões sincronizadas
- ✅ Resolução automática de dependências
- ✅ IDE reconhece mudanças instantaneamente

### Desvantagens

- ❌ Requer reestruturação de diretórios
- ❌ Mais complexo para novos desenvolvedores
- ❌ Menos flexível para versões independentes

---

## Troubleshooting

### Problema 1: ClassNotFoundException

**Erro**:
```
Caused by: java.lang.ClassNotFoundException: com.knuddels.jtokkit.api.Encoding
```

**Causa**: JAR instalado sem o POM (dependências transitivas faltando)

**Solução**:

```bash
# Reinstale COM o POM
mvn install:install-file \
  -Dfile=JSimpleLLM-0.0.1-SNAPSHOT.jar \
  -DpomFile=pom.xml  # ← IMPORTANTE!
```

---

### Problema 2: Could not find artifact

**Erro**:

```
Could not find artifact bor.tools:JSimpleLLM:jar:0.0.1-SNAPSHOT
```

**Diagnóstico**:

```bash
# Verifique se está instalado
ls ~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/
```

**Esperado**:

```
JSimpleLLM-0.0.1-SNAPSHOT.jar
JSimpleLLM-0.0.1-SNAPSHOT.pom
_remote.repositories
```

**Solução**:

```bash
# Instale novamente
mvn install:install-file -Dfile=... -DpomFile=...
```

---

### Problema 3: Wrong Version

**Erro**:

```
Could not find artifact bor.tools:JSimpleLLM:jar:1.0.0
```

**Causa**: Versão no pom.xml do JSimpleRag não corresponde à versão instalada

**Verificar versão instalada**:

```bash
ls ~/.m2/repository/bor/tools/JSimpleLLM/
```

**Solução**: Ajuste a versão no `pom.xml`: 

```xml
<dependency>
    <groupId>bor.tools</groupId>
    <artifactId>JSimpleLLM</artifactId>
    <version>0.0.1-SNAPSHOT</version>  <!-- ← Versão correta -->
</dependency>
```

---

### Problema 4: Dependência Circular Liquibase

**Erro**:

```
Circular depends-on relationship between 'liquibase' and 'entityManagerFactory'
```

**Causa**: Teste carregando contexto Spring completo desnecessariamente

**Solução**: Use contexto mínimo para testes de configuração:

```java
@SpringBootTest(
    classes = {
        LLMServiceConfig.class,
        MultiLLMServiceConfig.class
    }
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class LLMServiceConfigTest {
    // ...
}
```

---

### Problema 5: Changes not Reflected

**Situação**: Alterou o código do JSimpleLLM mas o JSimpleRag não vê as mudanças

**Causa**: Precisa reinstalar após cada mudança

**Solução**:

```bash
# No JSimpleLLM
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM
mvn clean install -DskipTests

# No JSimpleRag
cd /mnt/f/1-ProjetosIA/github/JSimpleRag
mvn clean compile  # Força recompilação
```

**Dica**: No Eclipse/IntelliJ, use "Maven → Update Project" após reinstalar

---

## Checklist de Verificação
## Checklist de Verificacao

### ✅ Antes de Instalar

- [ ] Projeto JSimpleLLM compila sem erros
- [ ] JAR existe em `target/JSimpleLLM-0.0.1-SNAPSHOT.jar`
- [ ] Você conhece o GroupId, ArtifactId e Version corretos
- [ ] Você tem o caminho completo para o POM

### ✅ Durante Instalação

- [ ] Comando `mvn install:install-file` foi bem-sucedido
- [ ] Mensagem "Installing ... to ~/.m2/repository/..." apareceu
- [ ] BUILD SUCCESS no final

### ✅ Após Instalação

- [ ] Arquivos existem em `~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/`
  - [ ] `JSimpleLLM-0.0.1-SNAPSHOT.jar`
  - [ ] `JSimpleLLM-0.0.1-SNAPSHOT.pom`
- [ ] Dependência adicionada no `pom.xml` do JSimpleRag
- [ ] `mvn compile` no JSimpleRag funciona
- [ ] Testes passam sem `ClassNotFoundException`

---

## Comandos de Referência Rápida

### Instalação Completa (Passo a Passo)

```bash
# 1. Compilar JSimpleLLM (se necessário)
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM
mvn clean package -DskipTests

# 2. Instalar no repositório local
mvn install:install-file \
  -Dfile=/mnt/f/1-ProjetosIA/github/JSimpleLLM/target/JSimpleLLM-0.0.1-SNAPSHOT.jar \
  -DpomFile=/mnt/f/1-ProjetosIA/github/JSimpleLLM/pom.xml

# 3. Verificar instalação
ls -lh ~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/

# 4. Adicionar dependência no pom.xml do JSimpleRag
# (Editar manualmente o arquivo)

# 5. Compilar JSimpleRag
cd /mnt/f/1-ProjetosIA/github/JSimpleRag
mvn clean compile

# 6. Rodar testes
mvn test
```

### Verificação de Dependências

```bash
# Ver árvore de dependências
mvn dependency:tree

# Ver apenas JSimpleLLM e suas dependências
mvn dependency:tree | grep -A 10 JSimpleLLM

# Forçar atualização de dependências
mvn dependency:resolve -U

# Listar todas as dependências baixadas
mvn dependency:list
```

### Limpeza e Reinstalação

```bash
# Remover do repositório local
rm -rf ~/.m2/repository/bor/tools/JSimpleLLM/

# Reinstalar
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM
mvn clean install -DskipTests
```

---

## Exemplo Completo: Fluxo de Trabalho Diário

### Cenário: Você alterou código no JSimpleLLM

```bash
# 1. Commit suas mudanças (opcional mas recomendado)
cd /mnt/f/1-ProjetosIA/github/JSimpleLLM
git add .
git commit -m "Implementei feature X"

# 2. Build e install
mvn clean install -DskipTests

# 3. Voltar ao JSimpleRag
cd /mnt/f/1-ProjetosIA/github/JSimpleRag

# 4. Limpar e recompilar para pegar nova versão
mvn clean compile

# 5. Rodar testes
mvn test

# 6. Se testes passarem, commit do JSimpleRag
git add .
git commit -m "Atualizei uso da feature X do JSimpleLLM"
```

---

## Scripts de Automação (Opcional)

### Script para Reinstalar JSimpleLLM

Crie `reinstall-jsimplellm.sh`:

```bash
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
```

**Usar**:

```bash
chmod +x reinstall-jsimplellm.sh
./reinstall-jsimplellm.sh
```

---

## Boas Práticas

### 1. Versionamento

**Sempre use SNAPSHOT para desenvolvimento**:

```xml
<version>0.0.1-SNAPSHOT</version>
```

- SNAPSHOT indica versão em desenvolvimento
- Maven sempre busca a versão mais recente de SNAPSHOT
- Para releases, remova o `-SNAPSHOT`

### 2. Documentação no POM

```xml
<dependency>
    <groupId>bor.tools</groupId>
    <artifactId>JSimpleLLM</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <!-- Local Maven project: Thin Java layer for LLM access -->
    <!-- Install: mvn install:install-file -Dfile=... -DpomFile=... -->
</dependency>
```

### 3. README do Projeto

Adicione no `README.md` do JSimpleRag:

```markdown
## Dependências Locais

Este projeto depende de **JSimpleLLM**, um projeto Maven local.

### Setup Inicial

1. Clone o JSimpleLLM:

   ```bash
   git clone https://github.com/your-org/JSimpleLLM
   ```

2. Instale no repositório Maven local:

   ```bash
   cd JSimpleLLM
   mvn clean install -DskipTests
   ```

3. Compile o JSimpleRag:

   ```bash
   cd JSimpleRag
   mvn clean compile
   ```

Veja [LOCAL_MAVEN_DEPENDENCY_GUIDE.md](LOCAL_MAVEN_DEPENDENCY_GUIDE.md) para mais detalhes.

```

### 4. CI/CD

Se usar GitHub Actions, Jenkins, etc.:

```yaml
# .github/workflows/build.yml
steps:
  - name: Checkout JSimpleLLM
    uses: actions/checkout@v3
    with:
      repository: your-org/JSimpleLLM
      path: JSimpleLLM

  - name: Install JSimpleLLM
    run: |
      cd JSimpleLLM
      mvn clean install -DskipTests

  - name: Checkout JSimpleRag
    uses: actions/checkout@v3
    with:
      path: JSimpleRag

  - name: Build JSimpleRag
    run: |
      cd JSimpleRag
      mvn clean verify
```

---

## Recursos Adicionais

### Maven Documentation

- [Maven Install Plugin](https://maven.apache.org/plugins/maven-install-plugin/)
- [Maven Dependency Plugin](https://maven.apache.org/plugins/maven-dependency-plugin/)
- [Maven Local Repository](https://maven.apache.org/guides/introduction/introduction-to-repositories.html)

### Ferramentas Úteis

```bash
# Ver conteúdo do repositório local
tree ~/.m2/repository/bor/tools/JSimpleLLM/

# Ver dependências de um JAR
jar -tf JSimpleLLM-0.0.1-SNAPSHOT.jar | grep -i class

# Ver POM instalado
cat ~/.m2/repository/bor/tools/JSimpleLLM/0.0.1-SNAPSHOT/JSimpleLLM-0.0.1-SNAPSHOT.pom
```

---

## Perguntas Frequentes

### Q: Preciso reinstalar sempre que altero o JSimpleLLM?

**R**: Sim, para desenvolvimento ativo. Alternativas:
- Use método multi-module (Maven Reactor)
- Configure IDE para auto-build (Eclipse: "Project → Build Automatically")

### Q: Posso ter múltiplas versões instaladas?

**R**: Sim! Cada versão vai para seu próprio diretório:
```
~/.m2/repository/bor/tools/JSimpleLLM/
├── 0.0.1-SNAPSHOT/
├── 0.0.2-SNAPSHOT/
└── 1.0.0/
```

### Q: Como compartilhar com a equipe?

**R**: Opções:
1. **Nexus/Artifactory** (repositório corporativo)
2. **GitHub Packages** (repositório privado)
3. **Documentar no README** (cada dev instala localmente)

### Q: E se o JSimpleLLM tiver testes falhando?

**R**: Use `-DskipTests`:

```bash
mvn clean install -DskipTests
```

Mas **corrija os testes** depois!

---

## Histórico de Mudanças

| Data       | Autor       | Mudança                                    |
|------------|-------------|--------------------------------------------|
| 2025-10-14 | Claude Code | Criação inicial do guia                    |
| 2025-10-14 | Claude Code | Adicionado troubleshooting e scripts       |

---

**Criado por**: Claude Code
**Baseado em**: Sessão de troubleshooting de integração JSimpleLLM → JSimpleRag
**Versão**: 1.0
