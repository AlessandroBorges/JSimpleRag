# Fix: Liquibase Circular Dependency Error

**Data**: 2025-10-14

**Erro**: `Circular depends-on relationship between 'liquibase' and 'entityManagerFactory'`

**Status**: ✅ RESOLVIDO

---

## 🔴 Problema Original

Ao iniciar a aplicação no Eclipse, o seguinte erro ocorria:

```
Error creating bean with name 'entityManagerFactory':
Failed to initialize dependency 'liquibase' of LoadTimeWeaverAware bean 'entityManagerFactory':
Circular depends-on relationship between 'liquibase' and 'entityManagerFactory'
```

---

## 🔍 Causa Raiz

O erro ocorre quando há **configurações conflitantes** entre JPA e Liquibase no `application.properties`:

```properties
# ❌ CONFIGURAÇÃO CONFLITANTE
spring.jpa.generate-ddl=true                      # JPA gerando DDL
spring.jpa.hibernate.ddl-auto=update              # Hibernate gerenciando schema
spring.jpa.defer-datasource-initialization=true   # Defer initialization
spring.liquibase.enabled=true                     # Liquibase também habilitado
```

### Por que isso causa problema?

1. **Liquibase** precisa rodar **ANTES** do JPA para criar/atualizar o schema
2. **JPA** com `generate-ddl=true` também tenta gerenciar o schema
3. **defer-datasource-initialization=true** faz o Spring tentar inicializar o datasource depois
4. Isso cria uma **dependência circular**:
   - Liquibase depende do EntityManager
   - EntityManager depende do Liquibase

---

## ✅ Solução

Escolher **UMA** abordagem de gerenciamento de schema:

### Opção 1: Database First com Liquibase (RECOMENDADO)

Esta é a abordagem adotada pelo projeto JSimpleRag.

**Configuração correta**:

```properties
# JPA/Hibernate Configuration - Database First with Liquibase
spring.jpa.hibernate.ddl-auto=validate            # ✅ Apenas valida (não modifica)
spring.jpa.generate-ddl=false                     # ✅ JPA não gera DDL
spring.jpa.defer-datasource-initialization=false  # ✅ Inicializa imediatamente

# Liquibase Configuration
spring.liquibase.enabled=true                     # ✅ Liquibase gerencia schema
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml
spring.liquibase.drop-first=false
```

**Fluxo de inicialização**:
1. ✅ Liquibase executa migrações → Cria/atualiza schema
2. ✅ EntityManager é criado → Valida schema existente
3. ✅ Aplicação inicia normalmente

---

### Opção 2: Code First com JPA (Alternativa)

Se você preferir que o JPA gerencie o schema (não recomendado para produção):

```properties
# JPA/Hibernate Configuration - Code First
spring.jpa.hibernate.ddl-auto=update              # ✅ Hibernate gerencia schema
spring.jpa.generate-ddl=true                      # ✅ JPA gera DDL
spring.jpa.defer-datasource-initialization=false

# Liquibase Configuration
spring.liquibase.enabled=false                    # ❌ Desabilita Liquibase
```

**⚠️ Não recomendado porque**:
- Menos controle sobre migrações
- Dificulta rollbacks
- Não há histórico de mudanças
- Problemas em ambientes de produção

---

## 📁 Arquivos Modificados

### application.properties

**Antes** (❌ Conflitante):

```properties
spring.jpa.hibernate.ddl-auto=${DDL_AUTO:validate}
spring.jpa.generate-ddl=true                      # ❌ Conflito
spring.jpa.defer-datasource-initialization=true   # ❌ Conflito
spring.liquibase.enabled=${LIQUIBASE_ENABLED:true}
```

**Depois** (✅ Correto):

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.generate-ddl=false                     # ✅ Desabilitado
spring.jpa.defer-datasource-initialization=false  # ✅ Desabilitado
spring.liquibase.enabled=${LIQUIBASE_ENABLED:true}
spring.liquibase.drop-first=false
```

---

## 🧪 Como Verificar a Solução

### 1. Limpar e Compilar

```bash
mvn clean compile
```

### 2. Iniciar a Aplicação

**No Eclipse**:
- Botão direito no projeto → Run As → Spring Boot App

**Linha de comando**:

```bash
mvn spring-boot:run
```

### 3. Verificar Logs

**✅ Sucesso - Você deve ver**:

```
Starting JSimpleRagApplication...
Liquibase: Successfully acquired change log lock
Liquibase: Creating database history table...
Liquibase: Reading from rag_db.databasechangelog
Liquibase: Successfully released change log lock
Started JSimpleRagApplication in X.XXX seconds
```

**❌ Se ainda houver erro**:
- Veja a seção [Troubleshooting](#troubleshooting) abaixo

---

## 🔧 Troubleshooting

### Erro: "Validation failed: Table 'xxx' not found"

**Causa**: Schema não foi criado pelo Liquibase

**Solução**:

```bash
# Verificar se o banco de dados existe
psql -U rag_rw -d db_rag -c "\dt"

# Se não existir, rodar as migrações manualmente
mvn liquibase:update
```

---

### Erro: "Liquibase change log not found"

**Causa**: Arquivo `db.changelog-master.xml` não encontrado

**Verificar**:

```bash
ls -la src/main/resources/db/changelog/db.changelog-master.xml
```

**Solução**: Criar o arquivo se não existir (veja estrutura abaixo)

---

### Erro: "Connection refused" ao PostgreSQL

**Causa**: Banco de dados não está rodando

**Solução**:

```bash
# Via Docker
docker-compose up -d postgres

# Ou verificar status
docker ps | grep postgres
```

---

### Erro persiste após correção

**Soluções**:

1. **Limpar workspace do Eclipse**:
   - Project → Clean...
   - Selecione o projeto
   - OK

2. **Limpar Maven**:

  ```bash
   mvn clean
  ```

3. **Refresh do projeto**:
   - Botão direito no projeto → Maven → Update Project
   - Force Update of Snapshots/Releases
   - OK

4. **Reiniciar Eclipse**:
   - Às vezes o Eclipse fica com cache antigo

---

## 📂 Estrutura do Liquibase

### Arquivos necessários:

```
src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.xml          # Master file
        ├── 001-create-extensions.xml
        ├── 002-create-enums.xml
        ├── 003-create-tables.xml
        ├── 004-create-indexes.xml
        ├── 005-create-triggers.xml
        ├── 006-create-user-project-chat-tables.xml
        └── 007-fix-libray-id-typo.xml
```

### db.changelog-master.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <include file="db/changelog/001-create-extensions.xml"/>
    <include file="db/changelog/002-create-enums.xml"/>
    <include file="db/changelog/003-create-tables.xml"/>
    <include file="db/changelog/004-create-indexes.xml"/>
    <include file="db/changelog/005-create-triggers.xml"/>
    <include file="db/changelog/006-create-user-project-chat-tables.xml"/>
    <include file="db/changelog/007-fix-libray-id-typo.xml"/>

</databaseChangeLog>
```

---

## 🎯 Boas Práticas

### 1. Separar Ambientes

Crie profiles para diferentes ambientes:

**application-dev.properties**:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.liquibase.enabled=true
logging.level.liquibase=DEBUG
```

**application-prod.properties**:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.liquibase.enabled=true
logging.level.liquibase=INFO
```

### 2. Nunca usar ddl-auto=update em produção

```properties
# ❌ NUNCA EM PRODUÇÃO
spring.jpa.hibernate.ddl-auto=update

# ✅ SEMPRE EM PRODUÇÃO
spring.jpa.hibernate.ddl-auto=validate
```

### 3. Testar migrações antes de deploy

```bash
# Verificar o que será executado
mvn liquibase:status

# Rodar migrações em dry-run
mvn liquibase:updateSQL

# Executar migrações
mvn liquibase:update
```

---

## 📊 Comparação de Abordagens

| Aspecto | Liquibase (Database First) | JPA DDL (Code First) |
|---------|---------------------------|----------------------|
| **Controle** | ✅ Total controle das mudanças | ❌ Limitado ao Hibernate |
| **Rollback** | ✅ Suportado | ❌ Difícil/impossível |
| **Histórico** | ✅ Versionamento completo | ❌ Sem histórico |
| **Produção** | ✅ Recomendado | ❌ Não recomendado |
| **Complexidade** | ⚠️ Requer manutenção de XMLs | ✅ Automático |
| **Flexibilidade** | ✅ Suporta SQL customizado | ❌ Limitado ao JPA |
| **Multi-DB** | ✅ Abstração de banco | ⚠️ Depende do dialeto |

---

## 🔗 Referências

- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization)
- [Liquibase Documentation](https://docs.liquibase.com/home.html)
- [Hibernate DDL Auto](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#configurations-hbmddl)
- [Spring Boot Common Application Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)

---

## ✅ Checklist de Verificação

Após aplicar a correção:

- [ ] application.properties atualizado
- [ ] `spring.jpa.generate-ddl=false`
- [ ] `spring.jpa.hibernate.ddl-auto=validate`
- [ ] `spring.jpa.defer-datasource-initialization=false`
- [ ] `spring.liquibase.enabled=true`
- [ ] Projeto limpo: `mvn clean compile`
- [ ] Aplicação inicia sem erros
- [ ] Logs mostram Liquibase executando migrações
- [ ] Banco de dados acessível
- [ ] Testes passam: `mvn test`

---

## 📝 Notas Adicionais

### Quando usar cada abordagem?

**Use Liquibase (Database First)**:
- ✅ Projetos em produção
- ✅ Equipes com DBAs
- ✅ Necessidade de rollbacks
- ✅ Schema complexo com procedures, triggers
- ✅ Múltiplos ambientes (dev, staging, prod)

**Use JPA DDL (Code First)**:
- ✅ Protótipos rápidos
- ✅ Desenvolvimento local apenas
- ✅ Schema muito simples
- ❌ NUNCA em produção

---

**Resolvido por**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0
