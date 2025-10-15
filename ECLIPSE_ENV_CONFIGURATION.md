# Configuração de Environment no Eclipse

**Data**: 2025-10-14
**Problema**: Eclipse não carrega automaticamente o arquivo `.env`
**Status**: ✅ RESOLVIDO

---

## 🔴 Problema

Ao rodar a aplicação no Eclipse, o seguinte erro ocorre:

```
java.lang.IllegalArgumentException: jdbcUrl is required with driverClassName.
```

**Causa**: O Eclipse não carrega automaticamente variáveis de ambiente do arquivo `.env`, diferente do terminal que usa `spring-dotenv`.

---

## ✅ Solução 1: Usar Valores Padrão no application.properties (IMPLEMENTADO)

Esta é a solução mais simples e já foi aplicada.

### Mudança realizada:

```properties
# Antes (valores genéricos)
spring.datasource.url=jdbc:postgresql://${DB_HOST:192.168.0.102}:${DB_PORT:5432}/${DB_NAME:db_rag}
spring.datasource.username=${DB_USERNAME:rag_rw}
spring.datasource.password=${DB_PASSWORD:pass123}

# Depois (valores do .env como defaults)
spring.datasource.url=jdbc:postgresql://${DB_HOST:alessandro-X99}:${DB_PORT:5432}/${DB_NAME:db_rag}
spring.datasource.username=${DB_USERNAME:rag_user}
spring.datasource.password=${DB_PASSWORD:rag_pass}

# Adicionado para Liquibase
spring.datasource.jdbc-url=${spring.datasource.url}
```

**Vantagens**:
- ✅ Funciona imediatamente no Eclipse
- ✅ Não precisa configurar nada no IDE
- ✅ Valores padrão vêm do `.env` do projeto

**Desvantagens**:
- ⚠️ Se você mudar o `.env`, precisa mudar também o `application.properties`
- ⚠️ Senhas ficam visíveis (mas já estão no `.env` mesmo)

---

## ✅ Solução 2: Configurar Variables no Eclipse

Se você preferir manter o `application.properties` genérico e configurar no Eclipse:

### Passo a Passo:

1. **Abrir Run Configurations**:
   - Botão direito no projeto `JSimpleRag`
   - **Run As** → **Run Configurations...**

2. **Selecionar sua configuração**:
   - No painel esquerdo, expanda **Spring Boot App**
   - Selecione `JSimpleRagApplication` (ou crie nova)

3. **Adicionar Environment Variables**:
   - Clique na aba **Environment**
   - Clique em **Add...**
   - Adicione cada variável:

   ```
   DB_HOST = alessandro-X99
   DB_PORT = 5432
   DB_NAME = db_rag
   DB_USERNAME = rag_user
   DB_PASSWORD = rag_pass

   LLM_PROVIDER_CLASS = LM_STUDIO
   LLM_API_URL = http://localhost:1234/v1
   EMBEDDING_MODEL = text-embedding-snowflake
   EMBEDDING_DIMENSION = 1024

   SPRING_PROFILES_ACTIVE = dev
   LOG_LEVEL = DEBUG
   ```

4. **Aplicar e Rodar**:
   - Clique em **Apply**
   - Clique em **Run**

### Screenshot de referência:

```
Run Configurations
├── Spring Boot App
│   └── JSimpleRagApplication
│       ├── [x] Main
│       ├── [x] Arguments
│       ├── [x] Environment  ← SELECIONE ESTA ABA
│       │   ├── Add...       ← CLIQUE AQUI
│       │   ├── Remove
│       │   └── Select...
│       └── [x] Common
```

**Vantagens**:
- ✅ `application.properties` fica genérico
- ✅ Fácil mudar valores sem editar arquivos
- ✅ Cada desenvolvedor pode ter seus próprios valores

**Desvantagens**:
- ⚠️ Precisa configurar uma vez por workspace
- ⚠️ Se compartilhar workspace, configuração some

---

## ✅ Solução 3: Profile Específico para Eclipse

Criar um profile específico para desenvolvimento no Eclipse:

### Criar application-eclipse.properties:

```properties
# src/main/resources/application-eclipse.properties

# Database Configuration
spring.datasource.url=jdbc:postgresql://alessandro-X99:5432/db_rag
spring.datasource.username=rag_user
spring.datasource.password=rag_pass
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.jdbc-url=${spring.datasource.url}

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.generate-ddl=false
spring.jpa.defer-datasource-initialization=false
spring.jpa.show-sql=true

# Liquibase
spring.liquibase.enabled=true

# LLM Configuration
llmservice.provider.name=LM_STUDIO
llmservice.provider.api.url=http://localhost:1234/v1
llmservice.provider.embedding.model=text-embedding-snowflake
llmservice.provider.embedding.dimension=1024

# Logging
logging.level.bor.tools.simplerag=DEBUG
```

### Configurar no Eclipse:

1. **Run Configurations** → **Arguments** tab
2. Em **Program arguments**, adicione:
   ```
   --spring.profiles.active=eclipse
   ```

**Vantagens**:
- ✅ Separação total entre ambientes
- ✅ Não modifica `application.properties` principal
- ✅ Valores específicos do Eclipse

**Desvantagens**:
- ⚠️ Mais um arquivo para manter

---

## 🔧 Troubleshooting

### Erro: Connection refused

**Causa**: PostgreSQL não está acessível no host especificado

**Verificar**:
```bash
# Ping no host
ping alessandro-X99

# Testar conexão PostgreSQL
psql -h alessandro-X99 -U rag_user -d db_rag

# Verificar se está rodando
docker ps | grep postgres
```

**Solução**: Ajustar `DB_HOST` para:
- `localhost` se PostgreSQL está na mesma máquina
- IP do host se está em máquina diferente

---

### Erro: Authentication failed

**Causa**: Usuário ou senha incorretos

**Verificar no .env**:
```bash
cat .env | grep DB_
```

**Solução**: Ajustar valores no `application.properties` ou Run Configuration

---

### Variáveis não sendo substituídas

**Sintoma**: Logs mostram `${DB_HOST:...}` literal

**Causa**: Spring não está processando placeholders

**Solução**: Verificar que o arquivo está em `src/main/resources/`

---

## 📊 Comparação das Soluções

| Aspecto | Solução 1 (Defaults) | Solução 2 (Eclipse Env) | Solução 3 (Profile) |
|---------|---------------------|------------------------|---------------------|
| **Simplicidade** | ✅✅✅ Muito simples | ⚠️ Média | ⚠️ Média |
| **Manutenção** | ⚠️ Precisa sincronizar | ✅ Independente | ✅ Independente |
| **Portabilidade** | ✅ Funciona em qualquer IDE | ⚠️ Específico Eclipse | ✅ Funciona em qualquer IDE |
| **Segurança** | ⚠️ Senhas no properties | ⚠️ Senhas na config | ⚠️ Senhas no profile |
| **Flexibilidade** | ⚠️ Limitada | ✅✅ Alta | ✅✅ Alta |

---

## 💡 Recomendação

Para **desenvolvimento no Eclipse**: Use **Solução 1** (já implementada)
- Simples
- Funciona imediatamente
- Fácil de manter

Para **equipes grandes**: Use **Solução 3** (Profile específico)
- Cada desenvolvedor cria seu próprio `application-eclipse.properties`
- Adicione ao `.gitignore`

---

## 🎯 Checklist de Verificação

Após configurar:

- [ ] Aplicação inicia sem erro de jdbcUrl
- [ ] Logs mostram conexão com banco de dados
- [ ] Liquibase executa migrações
- [ ] Swagger abre em http://localhost:8080/swagger-ui.html
- [ ] Health check responde em http://localhost:8080/actuator/health

---

## 📚 Referências

- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Spring Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Eclipse Run Configurations](https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Freference%2Fref-run-configurations.htm)

---

**Resolvido por**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0
