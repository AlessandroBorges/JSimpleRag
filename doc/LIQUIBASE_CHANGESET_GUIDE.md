# Guia de Changesets do Liquibase

**Data**: 2025-10-14
**Propósito**: Como criar e gerenciar changesets do Liquibase no JSimpleRag

---

## 📋 Índice

1. [O que é um Changeset](#o-que-e-um-changeset)
2. [Estrutura de Arquivos](#estrutura-de-arquivos)
3. [Como Criar um Novo Changeset](#como-criar-um-novo-changeset)
4. [Exemplo: Alterar Tipo de Coluna](#exemplo-alterar-tipo-de-coluna)
5. [Comandos Maven Liquibase](#comandos-maven-liquibase)
6. [Boas Práticas](#boas-praticas)
7. [Troubleshooting](#troubleshooting)

---

## O que e um Changeset?

Um **changeset** é uma unidade atômica de mudança no banco de dados. Pense nele como um "commit" para o schema do banco.

### Características:

- ✅ **Versionado**: Cada changeset tem um ID único
- ✅ **Rastreável**: Liquibase salva quais changesets já foram executados
- ✅ **Reversível**: Pode ter rollback (opcional)
- ✅ **Idempotente**: Se já foi executado, não roda novamente

---

## Estrutura de Arquivos

```
src/main/resources/db/changelog/
├── db.changelog-master.xml          # ← Master file (include todos)
├── 001-create-extensions.xml        # Extensions (pgvector)
├── 002-create-enums.xml             # Enums (tipo_biblioteca, etc)
├── 003-create-tables.xml            # Tabelas principais
├── 004-create-indexes.xml           # Índices
├── 005-create-triggers.xml          # Triggers (tsvector)
├── 006-create-user-project-chat-tables.xml  # Tabelas secundárias
├── 007-fix-libray-id-typo.xml       # Correção de typo
└── 008-alter-library-peso-columns.xml  # ← NOVO: Alterar tipo de dados
```

---

## Como Criar um Novo Changeset

### Passo 1: Escolher o número do arquivo

Use o próximo número sequencial disponível:
- Último arquivo: `007-fix-libray-id-typo.xml`
- Novo arquivo: `008-alter-library-peso-columns.xml`

### Passo 2: Criar o arquivo XML

**Template básico**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="008-nome-descritivo" author="seu_nome">
        <comment>Descrição do que este changeset faz</comment>

        <!-- Suas mudanças aqui -->

        <rollback>
            <!-- Como reverter esta mudança (opcional) -->
        </rollback>
    </changeSet>

</databaseChangeLog>
```

### Passo 3: Adicionar ao master

Edite `db.changelog-master.xml`:

```xml
<!-- Adicione no final, antes do </databaseChangeLog> -->
<include file="db/changelog/008-alter-library-peso-columns.xml"/>
```

### Passo 4: Rodar a aplicação

A mudança será aplicada automaticamente quando a aplicação iniciar.

---

## Exemplo: Alterar Tipo de Coluna

### Cenário:

Alterar as colunas `peso_semantico` e `peso_textual` da tabela `library` de `float8` para `float4`.

### Arquivo: 008-alter-library-peso-columns.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="008-alter-library-peso-columns-to-float4" author="claude_code">
        <comment>
            Alter peso_semantico and peso_textual columns to float4 type
        </comment>

        <modifyDataType
            tableName="library"
            columnName="peso_semantico"
            newDataType="float4"/>

        <modifyDataType
            tableName="library"
            columnName="peso_textual"
            newDataType="float4"/>

        <rollback>
            <modifyDataType
                tableName="library"
                columnName="peso_semantico"
                newDataType="float8"/>

            <modifyDataType
                tableName="library"
                columnName="peso_textual"
                newDataType="float8"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

### SQL Equivalente:

```sql
ALTER TABLE library ALTER COLUMN peso_semantico TYPE float4;
ALTER TABLE library ALTER COLUMN peso_textual TYPE float4;
```

---

## Comandos Maven Liquibase

### Ver status das migrações

```bash
mvn liquibase:status
```

**Saída esperada**:

```
1 change sets have not been applied to rag_rw@jdbc:postgresql://alessandro-X99:5432/db_rag
     db/changelog/008-alter-library-peso-columns.xml::008-alter-library-peso-columns-to-float4::claude_code
```

### Ver SQL que será executado (dry-run)

```bash
mvn liquibase:updateSQL
```

Isso cria um arquivo `target/liquibase/migrate.sql` com o SQL que será executado.

### Aplicar migrações manualmente

```bash
mvn liquibase:update
```

**Quando usar**: Se você desabilitou Liquibase no `application.properties` ou quer aplicar manualmente.

### Reverter última migração (rollback)

```bash
# Reverter 1 changeset
mvn liquibase:rollback -Dliquibase.rollbackCount=1

# Reverter até uma data
mvn liquibase:rollback -Dliquibase.rollbackDate=2025-10-14

# Reverter até um tag
mvn liquibase:rollback -Dliquibase.rollbackTag=v1.0
```

### Validar changesets

```bash
mvn liquibase:validate
```

Verifica se os changesets estão corretos antes de aplicar.

### Limpar checksums

```bash
mvn liquibase:clearCheckSums
```

**Quando usar**: Se você modificou um changeset já aplicado (não recomendado, mas às vezes necessário em dev).

---

## Boas Praticas

### 1. ✅ Nunca Modifique Changesets Já Aplicados

**❌ ERRADO**:

```xml
<!-- Este changeset já foi aplicado em produção -->
<changeSet id="001-create-table-user">
    <createTable tableName="user">
        <column name="id" type="bigint"/>
        <column name="email" type="varchar(255)"/>  ← Mudei de 100 para 255
    </createTable>
</changeSet>
```

**✅ CORRETO**:

```xml
<!-- Criar novo changeset para a mudança -->
<changeSet id="009-alter-user-email-length">
    <modifyDataType tableName="user" columnName="email" newDataType="varchar(255)"/>
</changeSet>
```

### 2. ✅ Use IDs Descritivos

**❌ RUIM**:

```xml
<changeSet id="1" author="dev">
```

**✅ BOM**:

```xml
<changeSet id="008-alter-library-peso-columns-to-float4" author="claude_code">
```

### 3. ✅ Sempre Adicione Comentários

```xml
<changeSet id="008-..." author="...">
    <comment>
        Alter peso_semantico and peso_textual columns to float4 type.
        Reason: float4 uses less storage and sufficient precision for weights (0.0 to 1.0).
    </comment>
    <!-- ... -->
</changeSet>
```

### 4. ✅ Forneça Rollback Quando Possível

```xml
<changeSet id="008-..." author="...">
    <modifyDataType tableName="library" columnName="peso_semantico" newDataType="float4"/>

    <rollback>
        <modifyDataType tableName="library" columnName="peso_semantico" newDataType="float8"/>
    </rollback>
</changeSet>
```

### 5. ✅ Nomeie Arquivos Sequencialmente

```
001-create-extensions.xml
002-create-enums.xml
003-create-tables.xml
...
008-alter-library-peso-columns.xml
009-add-new-index.xml       ← Próximo
010-add-audit-columns.xml   ← Depois
```

### 6. ✅ Separe Changesets por Funcionalidade

**❌ EVITE**:

```xml
<!-- Um changeset fazendo muitas coisas diferentes -->
<changeSet id="008-big-changes">
    <modifyDataType tableName="library" .../>
    <addColumn tableName="user" .../>
    <createTable tableName="audit" .../>
    <createIndex tableName="documento" .../>
</changeSet>
```

**✅ PREFIRA**:

```xml
<!-- Changesets pequenos e focados -->
<changeSet id="008-alter-library-peso-columns">
    <modifyDataType tableName="library" .../>
</changeSet>

<changeSet id="009-add-user-last-login">
    <addColumn tableName="user" .../>
</changeSet>
```

### 7. ✅ Use Preconditions Quando Necessário

```xml
<changeSet id="008-..." author="...">
    <preConditions onFail="MARK_RAN">
        <columnExists tableName="library" columnName="peso_semantico"/>
    </preConditions>

    <modifyDataType tableName="library" columnName="peso_semantico" newDataType="float4"/>
</changeSet>
```

---

## Operações Comuns

### Adicionar Coluna

```xml
<changeSet id="009-add-library-description" author="seu_nome">
    <addColumn tableName="library">
        <column name="description" type="text">
            <constraints nullable="true"/>
        </column>
    </addColumn>

    <rollback>
        <dropColumn tableName="library" columnName="description"/>
    </rollback>
</changeSet>
```

### Remover Coluna

```xml
<changeSet id="010-drop-library-old-field" author="seu_nome">
    <dropColumn tableName="library" columnName="old_field"/>

    <rollback>
        <addColumn tableName="library">
            <column name="old_field" type="varchar(100)"/>
        </addColumn>
    </rollback>
</changeSet>
```

### Renomear Coluna

```xml
<changeSet id="011-rename-library-nome-to-name" author="seu_nome">
    <renameColumn
        tableName="library"
        oldColumnName="nome"
        newColumnName="name"
        columnDataType="varchar(255)"/>

    <rollback>
        <renameColumn
            tableName="library"
            oldColumnName="name"
            newColumnName="nome"
            columnDataType="varchar(255)"/>
    </rollback>
</changeSet>
```

### Adicionar Índice

```xml
<changeSet id="012-add-index-library-nome" author="seu_nome">
    <createIndex indexName="idx_library_nome" tableName="library">
        <column name="nome"/>
    </createIndex>

    <rollback>
        <dropIndex indexName="idx_library_nome" tableName="library"/>
    </rollback>
</changeSet>
```

### Adicionar Foreign Key

```xml
<changeSet id="013-add-fk-documento-library" author="seu_nome">
    <addForeignKeyConstraint
        constraintName="fk_documento_library"
        baseTableName="documento"
        baseColumnNames="library_id"
        referencedTableName="library"
        referencedColumnNames="id"
        onDelete="CASCADE"/>

    <rollback>
        <dropForeignKeyConstraint
            constraintName="fk_documento_library"
            baseTableName="documento"/>
    </rollback>
</changeSet>
```

### Adicionar Constraint

```xml
<changeSet id="014-add-check-peso-range" author="seu_nome">
    <sql>
        ALTER TABLE library
        ADD CONSTRAINT check_peso_semantico_range
        CHECK (peso_semantico >= 0.0 AND peso_semantico <= 1.0);

        ALTER TABLE library
        ADD CONSTRAINT check_peso_textual_range
        CHECK (peso_textual >= 0.0 AND peso_textual <= 1.0);
    </sql>

    <rollback>
        <sql>
            ALTER TABLE library DROP CONSTRAINT IF EXISTS check_peso_semantico_range;
            ALTER TABLE library DROP CONSTRAINT IF EXISTS check_peso_textual_range;
        </sql>
    </rollback>
</changeSet>
```

### Executar SQL Customizado

```xml
<changeSet id="015-update-default-pesos" author="seu_nome">
    <sql>
        UPDATE library
        SET peso_semantico = 0.6,
            peso_textual = 0.4
        WHERE peso_semantico IS NULL;
    </sql>

    <!-- Rollback complexo pode não ser possível -->
    <rollback>
        <comment>Manual rollback required - data was modified</comment>
    </rollback>
</changeSet>
```

---

## Troubleshooting

### Erro: "Checksum mismatch"

**Causa**: Você modificou um changeset que já foi aplicado

**Solução 1** (Desenvolvimento):

```bash
mvn liquibase:clearCheckSums
```

**Solução 2** (Recomendada):
- Reverta a mudança no changeset
- Crie um novo changeset com a correção

---

### Erro: "Precondition Failed"

**Causa**: Uma precondição não foi satisfeita

**Exemplo**:

```xml
<preConditions onFail="HALT">
    <tableExists tableName="library"/>
</preConditions>
```

**Solução**: Verificar se a tabela realmente existe ou ajustar a precondição

---

### Erro: "Change Set already ran"

**Causa**: Changeset já foi executado

**Verificar**:

```sql
SELECT * FROM databasechangelog WHERE id = '008-alter-library-peso-columns-to-float4';
```

**Solução**: Normal! Liquibase pula changesets já executados.

---

### Erro: SQL Syntax Error

**Causa**: SQL inválido no changeset

**Solução**:
1. Teste o SQL manualmente no psql
2. Corrija o changeset
3. Se já foi aplicado, crie novo changeset de correção

---

### Rollback não funciona

**Causa**: Rollback não foi definido ou é complexo

**Solução**:
- Para mudanças simples (alter, add column): Liquibase gera rollback automático
- Para SQL customizado: Você precisa definir rollback manual

---

## Verificação de Changesets

### Antes de Commitar:

```bash
# 1. Validar sintaxe XML
mvn liquibase:validate

# 2. Ver SQL que será executado
mvn liquibase:updateSQL

# 3. Verificar target/liquibase/migrate.sql

# 4. Se OK, commitar
git add src/main/resources/db/changelog/
git commit -m "feat: alter library peso columns to float4"
```

### Após Aplicar:

```sql
-- Verificar que foi aplicado
SELECT * FROM databasechangelog WHERE id LIKE '008%';

-- Verificar estrutura da tabela
\d library

-- Testar se os dados estão corretos
SELECT peso_semantico, peso_textual FROM library LIMIT 5;
```

---

## Fluxo de Trabalho Completo

### 1. Identificar necessidade de mudança

- Exemplo: Colunas `peso_*` precisam ser `float4` em vez de `float8`

### 2. Criar novo arquivo changeset

```bash
# Criar arquivo
touch src/main/resources/db/changelog/008-alter-library-peso-columns.xml

# Editar com seu editor favorito
code src/main/resources/db/changelog/008-alter-library-peso-columns.xml
```

### 3. Escrever o changeset

- Use template
- Adicione comentários
- Inclua rollback

### 4. Adicionar ao master

```xml
<!-- db.changelog-master.xml -->
<include file="db/changelog/008-alter-library-peso-columns.xml"/>
```

### 5. Validar

```bash
mvn liquibase:validate
mvn liquibase:updateSQL
```

### 6. Testar localmente

```bash
# Iniciar aplicação
mvn spring-boot:run

# Verificar logs
# Logs devem mostrar: "Changeset db/changelog/008-... ran successfully"
```

### 7. Verificar no banco

```sql
-- Conectar
psql -h alessandro-X99 -U rag_rw -d db_rag

-- Verificar changeset aplicado
SELECT * FROM databasechangelog WHERE id LIKE '008%';

-- Verificar estrutura
\d library
```

### 8. Commitar

```bash
git add src/main/resources/db/changelog/
git commit -m "feat: alter library peso columns to float4"
git push
```

---

## Referências

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Liquibase Best Practices](https://docs.liquibase.com/concepts/bestpractices.html)
- [Liquibase XML Format](https://docs.liquibase.com/concepts/changelogs/xml-format.html)
- [PostgreSQL Data Types](https://www.postgresql.org/docs/current/datatype.html)

---

## Checklist de Novo Changeset

Antes de commitar:

- [ ] Arquivo nomeado sequencialmente (ex: 008-...)
- [ ] ID do changeset único e descritivo
- [ ] Author preenchido
- [ ] Comment explicando a mudança
- [ ] Rollback definido (quando possível)
- [ ] Adicionado ao `db.changelog-master.xml`
- [ ] `mvn liquibase:validate` passou
- [ ] `mvn liquibase:updateSQL` gerou SQL correto
- [ ] Testado localmente (aplicação iniciou)
- [ ] Verificado no banco de dados
- [ ] Documentado (se mudança significativa)

---

**Criado por**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0
