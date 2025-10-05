# Análise: Code First vs Liquibase - JSimpleRag

## 🔄 **Conflitos Fundamentais**

### **1. Controle de Schema**
```properties
# Code First
spring.jpa.hibernate.ddl-auto=update  # Hibernate controla
spring.liquibase.enabled=false

# vs

# Database First
spring.jpa.hibernate.ddl-auto=validate # Liquibase controla
spring.liquibase.enabled=true
```

**Problema**: Ambos querem ser a "fonte da verdade" para o schema.

### **2. Versionamento vs Automatização**

**Code First:**
```java
// Mudança na entidade
@Column(name = "embedding_vector", columnDefinition = "vector(2048)") // Era 1536
private float[] embeddingVector;
```
- ✅ Mudança **automática** no próximo restart
- ❌ **Sem rastro** de quando/como mudou
- ❌ **Impossível rollback** granular

**Liquibase:**
```xml
<changeSet id="006-update-vector-dimension" author="jsimplerag">
    <sql>ALTER TABLE doc_embedding ALTER COLUMN embedding_vector TYPE vector(2048);</sql>
    <rollback>ALTER TABLE doc_embedding ALTER COLUMN embedding_vector TYPE vector(1536);</rollback>
</changeSet>
```
- ✅ **Histórico completo** de mudanças
- ✅ **Rollback preciso** possível
- ❌ **Manutenção manual** trabalhosa

## 🎯 **Implicações no JSimpleRag**

### **Problema 1: Enum METADADOS**
Enum TipoEmbedding foi atualizado com `METADADOS("metadados")`, mas:

```xml
<!-- 002-create-enums.xml - NÃO inclui 'metadados' -->
CREATE TYPE tipo_embedding AS ENUM ('documento', 'capitulo', 'trecho',
                                   'perguntas_respostas', 'resumo', 'outros');
```

**Code First**: Hibernate tentará criar o enum com `metadados`
**Liquibase**: XML não conhece este valor

### **Problema 2: Vector Dimensions**
```java
// Entidade atual
@Column(columnDefinition = "vector(1536)")
private float[] embeddingVector;
```

```xml
<!-- XML atual -->
embedding_vector vector(1536)
```

Se você mudar para `vector(2048)` no Java:
- **Code First**: Tentará `ALTER TABLE` automaticamente
- **Liquibase**: Não saberá da mudança, conflito no próximo deploy

### **Problema 3: Constraints Complexas**
```java
// Na entidade Biblioteca
@PrePersist
@PreUpdate
private void validateWeights() {
    if (Math.abs((pesoSemantico + pesoTextual) - 1.0f) > 0.001f)
        throw new IllegalStateException("Soma deve ser 1.0");
}
```

```xml
<!-- No Liquibase -->
<sql>ALTER TABLE biblioteca ADD CONSTRAINT peso_total_check
     CHECK (peso_semantico + peso_textual = 1.0);</sql>
```

**Duplicação**: Validação tanto no Java quanto no banco!

## ⚖️ **Cenários de Conflito Real**

### **Cenário 1: Deploy em Produção**
```bash
# Desenvolvimento com Code First
spring.jpa.hibernate.ddl-auto=update
# Schema atualizado automaticamente

# Produção com Liquibase
spring.liquibase.enabled=true
spring.jpa.hibernate.ddl-auto=validate
# ERRO: Schema divergente!
```

### **Cenário 2: Rollback de Feature**
```java
// Versão 1.0: Campo adicionado via Code First
@Column(name = "nova_funcionalidade")
private String novaFuncionalidade;

// Versão 1.1: Precisamos remover este campo
// Code First: Como fazer rollback?
// Liquibase: changeSet com rollback definido
```

### **Cenário 3: Team Collaboration**
```java
// Dev A: Adiciona campo via Code First
@Column(name = "campo_dev_a")
private String campoDevA;

// Dev B: Não sabe da mudança, faz pull
// Banco local ainda não tem o campo
// Aplicação falha ao iniciar
```

## 🛠️ **Estratégias de Resolução**

### **Opção 1: Code First Puro (Simples mas Arriscado)**
```properties
# Todos os ambientes
spring.jpa.hibernate.ddl-auto=update
spring.liquibase.enabled=false
```

**Prós:**
- Desenvolvimento ágil
- Sem manutenção de XMLs
- Sincronização automática

**Contras:**
- Zero controle em produção
- Sem versionamento
- Rollbacks impossíveis
- Mudanças podem quebrar dados existentes

### **Opção 2: Liquibase Puro (Controle Total)**
```properties
# Todos os ambientes
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.enabled=true
```

**Prós:**
- Controle total do schema
- Versionamento completo
- Rollbacks precisos
- Auditoria de mudanças

**Contras:**
- Desenvolvimento lento
- Manutenção trabalhosa
- Duplicação (Java + XML)
- Curva de aprendizado

### **Opção 3: Híbrida (Recomendada para JSimpleRag)**

#### **Desenvolvimento:**
```properties
# application-dev.properties
spring.jpa.hibernate.ddl-auto=update
spring.liquibase.enabled=false
```

#### **CI/CD Pipeline:**
```bash
# 1. Gerar DDL das entidades
./mvnw clean compile -Dspring.jpa.properties.javax.persistence.schema-generation.scripts.action=create

# 2. Comparar com schema atual
# 3. Gerar changesets Liquibase automaticamente
# 4. Review manual obrigatório
```

#### **Produção:**
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.enabled=true
```

## 🎯 **Implementação Prática para JSimpleRag**

### **1. Resolver Enum METADADOS**
```xml
<!-- Novo changeset -->
<changeSet id="006-add-metadados-enum" author="jsimplerag">
    <sql>ALTER TYPE tipo_embedding ADD VALUE 'metadados';</sql>
    <rollback>
        <!-- PostgreSQL não permite remover valores de enum facilmente -->
        <comment>Manual rollback required</comment>
    </rollback>
</changeSet>
```

### **2. Configurar Profile-Specific**
```yaml
# application-dev.yml
spring:
  profiles: dev
  jpa:
    hibernate:
      ddl-auto: update
  liquibase:
    enabled: false

---
# application-prod.yml
spring:
  profiles: prod
  jpa:
    hibernate:
      ddl-auto: validate
  liquibase:
    enabled: true
```

### **3. Script de Sincronização**
```bash
#!/bin/bash
# sync-schema.sh

echo "🔄 Gerando DDL atual das entidades..."
./mvnw clean compile -Dspring.jpa.properties.javax.persistence.schema-generation.scripts.action=create

echo "📊 Comparando com Liquibase..."
# Comparação manual ou ferramenta como Flyway diff

echo "⚠️ Review necessário antes de commit!"
```

### **4. Validação no CI**
```yaml
# .github/workflows/ci.yml
test:
  steps:
    - name: Test Schema Sync
      run: |
        # Aplicar Liquibase
        ./mvnw liquibase:update

        # Validar com Hibernate
        ./mvnw spring-boot:run -Dspring.jpa.hibernate.ddl-auto=validate
```

## 📋 **Recomendações Específicas**

### **Para o JSimpleRag:**
1. **Mantenha Code First** em desenvolvimento
2. **Use Liquibase** para versionamento e produção
3. **Automatize a sincronização** entre os dois
4. **Documente** todas as mudanças de schema
5. **Teste** mudanças em ambiente staging primeiro

### **Decisão por Tipo de Mudança:**

| Tipo de Mudança | Estratégia | Justificativa |
|------------------|------------|---------------|
| **Nova entidade** | Code First → Liquibase | Desenvolvimento rápido, depois formalizar |
| **Novo campo simples** | Code First → Liquibase | Hibernate gera DDL correto |
| **Mudança de tipo** | Liquibase apenas | Pode quebrar dados existentes |
| **Constraints complexas** | Liquibase apenas | Hibernate pode gerar DDL subótimo |
| **Índices de performance** | Liquibase apenas | Controle fino necessário |
| **Triggers/Functions** | Liquibase apenas | Hibernate não suporta |

## 🚀 **Conclusão**

A abordagem híbrida permite aproveitar:
- **Agilidade do Code First** no desenvolvimento
- **Controle do Liquibase** para ambientes críticos
- **Flexibilidade** para diferentes tipos de mudança
- **Rastreabilidade** de todas as alterações

**Regra de Ouro**: Se a mudança pode impactar dados existentes ou performance, use Liquibase. Se é desenvolvimento iterativo, use Code First.

---

**Última atualização**: 2025-01-05
**Projeto**: JSimpleRag v0.0.1-SNAPSHOT