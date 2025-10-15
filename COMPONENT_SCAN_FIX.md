# Fix: DocumentConverter Bean Not Found

**Data**: 2025-10-15
**Erro**: `No qualifying bean of type 'bor.tools.utils.DocumentConverter' available`
**Status**: ✅ RESOLVIDO

---

## 🔴 Problema Original

Ao iniciar a aplicação, o seguinte erro ocorria:

```
org.springframework.beans.factory.UnsatisfiedDependencyException:
Error creating bean with name 'documentController':
Unsatisfied dependency expressed through constructor parameter 0:
Error creating bean with name 'documentoService':
Unsatisfied dependency expressed through constructor parameter 4:
No qualifying bean of type 'bor.tools.utils.DocumentConverter' available:
expected at least 1 bean which qualifies as autowire candidate.
```

---

## 🔍 Causa Raiz

### Estrutura de Pacotes

```
bor.tools.simplerag              ← Application class (@SpringBootApplication)
├── controller
├── service
│   └── DocumentoService.java   ← Requer DocumentConverter
├── repository
└── dto

bor.tools.utils                  ← Fora do escopo de escaneamento!
└── TikaDocumentConverter.java  ← @Component não detectado

bor.tools.splitter               ← Fora do escopo de escaneamento!
├── AsyncSplitterService.java   ← @Service
├── DocumentRouter.java         ← @Component
└── ...
```

### Explicação

1. **Spring Boot Component Scan padrão**:
   - A anotação `@SpringBootApplication` escaneia apenas o pacote da classe principal e seus subpacotes
   - Pacote escaneado: `bor.tools.simplerag.*`

2. **Componentes fora do escopo**:
   - `TikaDocumentConverter` está em `bor.tools.utils` ❌
   - `AsyncSplitterService`, `DocumentRouter` estão em `bor.tools.splitter` ❌

3. **Resultado**:
   - Spring não encontra `TikaDocumentConverter` como bean
   - `DocumentoService` falha ao injetar dependência

---

## ✅ Solução

Adicionar `@ComponentScan` explícito para incluir os pacotes necessários.

### Mudança em JSimpleRagApplication.java:

```java
// Antes (❌ INCORRETO - escopo limitado)
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class JSimpleRagApplication {
    // ...
}

// Depois (✅ CORRETO - escopo expandido)
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
    "bor.tools.simplerag",  // Main application package
    "bor.tools.utils",       // Utility classes (DocumentConverter, RAGConverter, etc.)
    "bor.tools.splitter"     // Document splitter services
})
public class JSimpleRagApplication {
    // ...
}
```

---

## 🔧 Por que isso aconteceu?

### Arquitetura Multi-Módulo Lógica

O projeto JSimpleRag tem uma estrutura que simula módulos separados:

1. **Módulo Core** (`bor.tools.simplerag`):
   - Entidades, DTOs, Repositories, Services, Controllers
   - Configurações Spring

2. **Módulo Utils** (`bor.tools.utils`):
   - Utilitários genéricos reutilizáveis
   - Conversores de documentos
   - Parsers

3. **Módulo Splitter** (`bor.tools.splitter`):
   - Processamento de documentos
   - Divisão em chunks
   - Geração de embeddings

### Problema de Descoberta de Beans

Quando componentes Spring estão em pacotes "irmãos" (não subpacotes), o Spring não os detecta automaticamente.

```
bor.tools
├── simplerag     ← @SpringBootApplication aqui
├── utils         ← Precisa de @ComponentScan
└── splitter      ← Precisa de @ComponentScan
```

---

## 🎯 Alternativas de Solução

### Opção 1: @ComponentScan (Implementada) ✅

**Pros**:
- ✅ Solução simples e direta
- ✅ Mantém estrutura de pacotes organizada
- ✅ Componentes descobertos automaticamente

**Contras**:
- ⚠️ Escaneia todos os pacotes especificados (pode ser lento em grandes projetos)

**Quando usar**: Projetos de pequeno/médio porte com poucos pacotes externos

---

### Opção 2: @Configuration com @Bean explícitos

Criar uma classe de configuração:

```java
@Configuration
public class UtilsConfig {

    @Bean
    public DocumentConverter documentConverter() {
        return new TikaDocumentConverter();
    }
}
```

**Pros**:
- ✅ Controle fino sobre criação de beans
- ✅ Permite configuração customizada
- ✅ Não escaneia pacotes inteiros

**Contras**:
- ⚠️ Mais verboso
- ⚠️ Precisa declarar cada bean manualmente
- ⚠️ Dificulta adição de novos componentes

**Quando usar**: Quando você quer controle explícito sobre configuração de beans

---

### Opção 3: Mover classes para dentro do pacote principal

Reorganizar estrutura:

```
bor.tools.simplerag
├── controller
├── service
├── utils               ← Mover aqui
│   └── TikaDocumentConverter.java
└── splitter            ← Mover aqui
    └── AsyncSplitterService.java
```

**Pros**:
- ✅ Descoberta automática
- ✅ Sem necessidade de @ComponentScan

**Contras**:
- ❌ Perde organização lógica de módulos
- ❌ Mistura responsabilidades
- ❌ Dificulta reutilização de código

**Quando usar**: Monólitos simples sem intenção de separar módulos

---

## 🧪 Como Verificar a Solução

### 1. Compilar projeto

```bash
mvn clean compile
```

**✅ Deve compilar sem erros**

### 2. Verificar beans registrados

Adicionar log no application.properties (temporário):

```properties
logging.level.org.springframework.context.annotation=DEBUG
```

Iniciar aplicação e buscar no log:

```
Identified candidate component class:
  [bor.tools.utils.TikaDocumentConverter]
Identified candidate component class:
  [bor.tools.splitter.AsyncSplitterService]
Identified candidate component class:
  [bor.tools.splitter.DocumentRouter]
```

### 3. Testar injeção

```bash
mvn spring-boot:run
```

**✅ Sucesso - Você deve ver**:

```
Started JSimpleRagApplication in X.XXX seconds
```

**❌ Se ainda houver erro**:
- Verifique se `@Component` está na classe
- Verifique se o pacote está em `@ComponentScan`
- Veja [Troubleshooting](#troubleshooting) abaixo

---

## 🔧 Troubleshooting

### Erro: "Bean definition not found" mesmo com @ComponentScan

**Causa**: Classe não tem anotação Spring

**Verificar**:

```bash
# Buscar anotações Spring em utils
grep -r "@Component\|@Service\|@Repository" src/main/java/bor/tools/utils/
```

**Solução**: Adicionar `@Component` na classe

```java
@Component
public class TikaDocumentConverter implements DocumentConverter {
    // ...
}
```

---

### Erro: "Circular dependency" após adicionar @ComponentScan

**Causa**: Beans se injetam mutuamente

**Diagnóstico**:

```
The dependencies of some of the beans in the application context form a cycle:
  serviceA -> serviceB -> serviceA
```

**Solução**: Usar `@Lazy` em uma das dependências

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;

    public ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}
```

---

### Erro: "Multiple beans of type DocumentConverter found"

**Causa**: Múltiplas implementações de DocumentConverter

**Diagnóstico**:

```bash
# Buscar implementações
grep -r "implements DocumentConverter" src/main/java/
```

**Solução**: Usar `@Primary` na implementação principal

```java
@Component
@Primary  // ← Esta será a implementação padrão
public class TikaDocumentConverter implements DocumentConverter {
    // ...
}
```

---

## 📊 Componentes Descobertos

Após a correção, os seguintes beans são descobertos:

### Pacote bor.tools.utils:

| Classe | Tipo | Função |
|--------|------|--------|
| `TikaDocumentConverter` | @Component | Conversão de documentos para Markdown |

### Pacote bor.tools.splitter:

| Classe | Tipo | Função |
|--------|------|--------|
| `AsyncSplitterService` | @Service | Processamento assíncrono de documentos |
| `DocumentRouter` | @Component | Roteamento de documentos por tipo |
| `DocumentSummarizerImpl` | @Service | Sumarização de documentos |
| `EmbeddingProcessorImpl` | @Service | Processamento de embeddings |
| `SplitterConfig` | @Configuration | Configuração de splitters |
| `SplitterFactory` | @Component | Factory para criação de splitters |

---

## 🎯 Boas Práticas

### Para Desenvolvimento

1. **Organize por módulos lógicos**:
   - `bor.tools.simplerag.*` - Core da aplicação
   - `bor.tools.utils.*` - Utilitários reutilizáveis
   - `bor.tools.splitter.*` - Processamento de documentos

2. **Use @ComponentScan explícito** quando tiver múltiplos pacotes raiz

3. **Documente** quais pacotes são escaneados e por quê

4. **Verifique beans** após mudanças estruturais:
   ```bash
   mvn spring-boot:run | grep "Creating shared instance of"
   ```

### Para Produção

1. **Minimize escopo** do @ComponentScan:
   - Apenas pacotes necessários
   - Evita escaneamento de classes desnecessárias

2. **Use @Configuration** para beans complexos:
   - Configuração explícita
   - Melhor controle de lifecycle

3. **Considere módulos Maven** reais:
   - Se os pacotes crescerem muito
   - Para melhor separação de dependências

---

## 📝 Checklist de Verificação

Após aplicar a correção:

- [x] `@ComponentScan` adicionado em `JSimpleRagApplication`
- [x] Pacotes incluídos: `bor.tools.simplerag`, `bor.tools.utils`, `bor.tools.splitter`
- [x] Compilação sem erros: `mvn clean compile`
- [x] Aplicação inicia sem erros: `mvn spring-boot:run`
- [x] Bean `TikaDocumentConverter` registrado
- [x] Bean `AsyncSplitterService` registrado
- [x] Bean `DocumentRouter` registrado
- [x] `DocumentoService` inicializa com sucesso

---

## 🔗 Referências

- [Spring Boot Component Scanning](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html#using.auto-configuration.packages)
- [Spring @ComponentScan](https://docs.spring.io/spring-framework/reference/core/beans/java/configuration-annotation.html#beans-java-configuration-annotation-componentscan)
- [Spring Boot Auto-Configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)

---

## 📚 Arquivos Modificados

### JSimpleRagApplication.java (src/main/java/bor/tools/simplerag/JSimpleRagApplication.java:26)

```java
@ComponentScan(basePackages = {
    "bor.tools.simplerag",  // Main application package
    "bor.tools.utils",       // Utility classes (DocumentConverter, RAGConverter, etc.)
    "bor.tools.splitter"     // Document splitter services
})
```

---

**Resolvido por**: Claude Code
**Data**: 2025-10-15
**Versão**: 1.0
