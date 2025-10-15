# Fix: Database Permissions Error

**Data**: 2025-10-14
**Erro**: `ERRO: permissão negada para tabela databasechangelog`
**Status**: ✅ RESOLVIDO

---

## 🔴 Problema Original

Ao iniciar a aplicação, o seguinte erro ocorria:

```
liquibase.exception.DatabaseException:
Error executing SQL SELECT MD5SUM FROM public.databasechangelog WHERE MD5SUM IS NOT NULL
ERRO: permissão negada para tabela databasechangelog
```

---

## 🔍 Causa Raiz

O erro ocorre por **incompatibilidade entre usuário e dono das tabelas**:

### Situação encontrada:

```sql
db_rag=> \dt
                  List of tables
 Esquema |         Nome          |  Tipo  |  Dono
---------+-----------------------+--------+--------
 public  | chapter               | tabela | rag_rw   ← Dono das tabelas
 public  | databasechangelog     | tabela | rag_rw
 public  | databasechangeloglock | tabela | rag_rw
 ...
```

### Configuração usada:

```properties
# application.properties (INCORRETO)
spring.datasource.username=rag_user  # ❌ Usuário sem permissões
spring.datasource.password=rag_pass
```

### Problema:

1. **Tabelas criadas** pelo usuário `rag_rw` (owner)
2. **Aplicação tentando acessar** com usuário `rag_user`
3. **`rag_user` não tem permissões** para acessar tabelas de `rag_rw`

---

## ✅ Solução

Usar o **mesmo usuário que criou as tabelas** (`rag_rw`).

### Mudança no application.properties:

```properties
# Antes (❌ INCORRETO)
spring.datasource.username=${DB_USERNAME:rag_user}
spring.datasource.password=${DB_PASSWORD:rag_pass}

# Depois (✅ CORRETO)
spring.datasource.username=${DB_USERNAME:rag_rw}
spring.datasource.password=${DB_PASSWORD:pass123}
```

### Mudança no .env:

```bash
# Antes (❌ INCORRETO)
DB_USERNAME=rag_user
DB_PASSWORD=rag_pass

# Depois (✅ CORRETO)
DB_USERNAME=rag_rw
DB_PASSWORD=pass123
```

---

## 🔧 Por que isso aconteceu?

### Contexto:

1. O projeto JSimpleRag foi configurado inicialmente com dois usuários:
   - **`rag_rw`**: Usuário com permissões de leitura/escrita (Read-Write)
   - **`rag_user`**: Usuário genérico mencionado no `.env.example`

2. As tabelas foram criadas usando `rag_rw` (via Liquibase ou script SQL)

3. O `.env` tinha `rag_user` como padrão, mas esse usuário:
   - ❌ Não foi criado no PostgreSQL
   - ❌ Ou foi criado sem permissões nas tabelas

### Lições aprendidas:

- ✅ Usar **um único usuário** para criar e acessar tabelas
- ✅ Se usar múltiplos usuários, **conceder permissões explicitamente**
- ✅ **Testar conexão** antes de rodar a aplicação

---

## 🎯 Alternativas de Solução

### Opção 1: Usar rag_rw (Implementada)

**Pros**:
- ✅ Solução imediata
- ✅ Sem necessidade de alterar banco
- ✅ Sem permissões complexas

**Contras**:
- ⚠️ Aplicação com permissões de owner (mais poder do que precisa)

---

### Opção 2: Conceder Permissões ao rag_user

Se você quiser usar `rag_user` (princípio do menor privilégio):

#### SQL para conceder permissões:

```sql
-- Conectar como superuser ou rag_rw
-- psql -h alessandro-X99 -U postgres -d db_rag

-- Conceder permissões ao rag_user
GRANT USAGE ON SCHEMA public TO rag_user;

-- Conceder permissões em todas as tabelas existentes
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rag_user;

-- Conceder permissões em tabelas futuras
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rag_user;

-- Conceder permissões em sequences (para IDs auto-incrementais)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rag_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO rag_user;

-- Verificar permissões
\dp
```

#### Depois, voltar para rag_user no application.properties:

```properties
spring.datasource.username=rag_user
spring.datasource.password=rag_pass
```

**Pros**:
- ✅ Menor privilégio (segurança)
- ✅ Separação de responsabilidades

**Contras**:
- ⚠️ Mais complexo
- ⚠️ Precisa manter permissões sincronizadas

---

### Opção 3: Recriar Tabelas com rag_user

Se você está no início do projeto e pode apagar tudo:

```sql
-- Conectar como superuser
-- psql -h alessandro-X99 -U postgres -d db_rag

-- Dropar todas as tabelas
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Conceder owner do schema ao rag_user
GRANT ALL ON SCHEMA public TO rag_user;

-- Sair e reconectar como rag_user
-- psql -h alessandro-X99 -U rag_user -d db_rag

-- Rodar Liquibase novamente (vai criar tabelas como rag_user)
```

**Pros**:
- ✅ Começa do zero com usuário correto
- ✅ Simples

**Contras**:
- ❌ Perde todos os dados
- ❌ Só viável no início do projeto

---

## 🧪 Como Verificar a Solução

### 1. Testar conexão

```bash
# Via psql
psql -h alessandro-X99 -U rag_rw -d db_rag -c "SELECT COUNT(*) FROM databasechangelog;"

# Deve retornar um número (ex: 7)
```

### 2. Rodar a aplicação

**No Eclipse**:
- Botão direito → Run As → Spring Boot App

**Linha de comando**:
```bash
mvn spring-boot:run
```

### 3. Verificar logs

**✅ Sucesso - Você deve ver**:
```
Liquibase: Successfully acquired change log lock
Liquibase: Reading from db_rag.databasechangelog
Liquibase: Successfully released change log lock
Started JSimpleRagApplication in X.XXX seconds
```

**❌ Se ainda houver erro**:
- Veja [Troubleshooting](#troubleshooting) abaixo

---

## 🔧 Troubleshooting

### Erro: "password authentication failed for user rag_rw"

**Causa**: Senha incorreta

**Verificar senha**:
```bash
# Testar conexão
psql -h alessandro-X99 -U rag_rw -d db_rag

# Se pedir senha, tente:
# - pass123
# - rag_pass
# - Verifique no docker-compose.yml ou scripts de criação
```

**Solução**: Ajustar senha no `application.properties` e `.env`

---

### Erro: "role rag_rw does not exist"

**Causa**: Usuário não foi criado

**Criar usuário**:
```sql
-- Como superuser
CREATE USER rag_rw WITH PASSWORD 'pass123';

-- Conceder permissões
GRANT ALL PRIVILEGES ON DATABASE db_rag TO rag_rw;
GRANT ALL PRIVILEGES ON SCHEMA public TO rag_rw;
```

---

### Erro persiste com rag_rw

**Diagnóstico**:
```sql
-- Conectar como rag_rw
psql -h alessandro-X99 -U rag_rw -d db_rag

-- Verificar permissões
SELECT * FROM databasechangelog LIMIT 1;

-- Se retornar dados, permissões OK
-- Se retornar erro, precisa conceder permissões
```

**Solução**:
```sql
-- Como superuser
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO rag_rw;
```

---

## 📊 Comparação dos Usuários

| Aspecto | rag_rw (Owner) | rag_user (Limited) |
|---------|----------------|-------------------|
| **Uso** | ✅ Desenvolvimento | ✅ Produção recomendado |
| **Permissões** | ✅ Total (owner) | ⚠️ Limitado (precisa grant) |
| **Segurança** | ⚠️ Poder demais | ✅ Menor privilégio |
| **Complexidade** | ✅ Simples | ⚠️ Requer manutenção |
| **Liquibase** | ✅ Funciona direto | ⚠️ Precisa permissões DDL |

---

## 🎯 Recomendações

### Para Desenvolvimento Local:
- ✅ **Use `rag_rw`** (simplicidade)
- ✅ Não precisa gerenciar permissões complexas
- ✅ Foco no desenvolvimento, não em segurança

### Para Produção:
- ✅ **Use usuário limitado** (ex: `rag_app`)
- ✅ Conceda apenas `SELECT, INSERT, UPDATE, DELETE`
- ✅ **Não conceda** `CREATE, DROP, ALTER`
- ✅ Use outro usuário para migrações (ex: `rag_admin`)

### Estrutura ideal em produção:

```
rag_admin (owner)
  ↓
  Executa migrações Liquibase
  Cria/altera tabelas

rag_app (limited)
  ↓
  Usado pela aplicação
  Apenas CRUD nas tabelas
```

---

## 📝 Checklist de Verificação

Após aplicar a correção:

- [ ] `application.properties` usa `rag_rw`
- [ ] `.env` usa `rag_rw`
- [ ] Senha correta: `pass123`
- [ ] Conexão via psql funciona
- [ ] Aplicação inicia sem erros de permissão
- [ ] Liquibase executa migrações
- [ ] Logs mostram "Successfully released change log lock"

---

## 🔗 Referências

- [PostgreSQL GRANT](https://www.postgresql.org/docs/current/sql-grant.html)
- [PostgreSQL Roles](https://www.postgresql.org/docs/current/user-manag.html)
- [Liquibase with PostgreSQL](https://docs.liquibase.com/start/tutorials/postgresql.html)
- [Principle of Least Privilege](https://en.wikipedia.org/wiki/Principle_of_least_privilege)

---

## 📚 Script de Criação de Usuários (Referência)

Para criar uma estrutura completa de usuários:

```sql
-- Como superuser (postgres)

-- 1. Criar usuários
CREATE USER rag_admin WITH PASSWORD 'admin_secure_password';
CREATE USER rag_app WITH PASSWORD 'app_secure_password';
CREATE USER rag_readonly WITH PASSWORD 'readonly_password';

-- 2. Conceder permissões ao admin (migrações)
GRANT ALL PRIVILEGES ON DATABASE db_rag TO rag_admin;
ALTER DATABASE db_rag OWNER TO rag_admin;

-- 3. Conceder permissões ao app (CRUD)
GRANT CONNECT ON DATABASE db_rag TO rag_app;
GRANT USAGE ON SCHEMA public TO rag_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rag_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rag_app;

-- 4. Conceder permissões ao readonly (relatórios)
GRANT CONNECT ON DATABASE db_rag TO rag_readonly;
GRANT USAGE ON SCHEMA public TO rag_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO rag_readonly;

-- 5. Configurar permissões padrão para tabelas futuras
ALTER DEFAULT PRIVILEGES FOR ROLE rag_admin IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rag_app;

ALTER DEFAULT PRIVILEGES FOR ROLE rag_admin IN SCHEMA public
    GRANT SELECT ON TABLES TO rag_readonly;

ALTER DEFAULT PRIVILEGES FOR ROLE rag_admin IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO rag_app;
```

---

**Resolvido por**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0
