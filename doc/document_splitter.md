# Document Splitter - Guia Completo

## Visão Geral

O **Document Splitter** é um sistema modular e inteligente para particionamento e processamento de documentos de diversos formatos. Integrado ao JSimpleRag, oferece funcionalidades avançadas de splitting, processamento de embeddings e operações LLM.

## 📋 Índice

1. [Arquitetura e Componentes](#arquitetura-e-componentes)
2. [Splitters Especializados](#splitters-especializados)
3. [Roteamento Automático](#roteamento-automático)
4. [Funcionalidades LLM](#funcionalidades-llm)
5. [Processamento de Embeddings](#processamento-de-embeddings)
6. [Sumarização e Q&A](#sumarização-e-qa)
7. [Exemplos de Uso](#exemplos-de-uso)
8. [Configuração e Integração](#configuração-e-integração)
9. [Troubleshooting](#troubleshooting)

---

## Arquitetura e Componentes

### Estrutura do Pacote
```
bor.tools.splitter/
├── AbstractSplitter.java          # Classe base com funcionalidades LLM
├── DocumentRouter.java            # Roteamento automático
├── SplitterLLMServices.java       # Interface LLM centralizada
├── DocumentSummarizerImpl.java    # Sumarização e Q&A
├── EmbeddingProcessorImpl.java    # Processamento de embeddings
├── SplitterGenerico.java          # Documentos gerais
├── SplitterNorma.java            # Normativos legais
├── SplitterWiki.java             # Conteúdo Wikipedia-like
├── ContentSplitter.java          # Utilitário de splitting
└── normsplitter/                 # Subpacote para normativos
```

### Hierarquia de Classes
```
AbstractSplitter (base)
├── SplitterGenerico (documentos gerais)
├── SplitterNorma (normativos legais)
└── SplitterWiki (Wikipedia-like)
```

### Interfaces Principais
- **`DocumentSplitter`**: Operações básicas de splitting
- **`DocumentPreprocessor`**: Pré-processamento de texto
- **`SplitterLLMServices`**: Funcionalidades LLM centralizadas
- **`DocumentSummarizer`**: Sumarização e geração Q&A
- **`EmbeddingProcessorInterface`**: Processamento de embeddings

---

## Splitters Especializados

### 1. SplitterGenerico
**Uso**: Documentos gerais, manuais, livros, artigos

**Características**:
- Detecção automática de títulos (Markdown, numerados, maiúsculas)
- Divisão por tamanho configurável
- Otimização de chunks para embeddings
- Remoção automática de repetições

**Exemplo**:
```java
SplitterGenerico splitter = new SplitterGenerico(llmService);
List<CapituloDTO> capitulos = splitter.splitDocumento(documento);
```

### 2. SplitterNorma
**Uso**: Documentos normativos, leis, decretos, resoluções

**Características**:
- Detecção hierárquica de estrutura legal (Livro → Título → Capítulo → Seção → Artigo)
- Mapeamento automático Seção → CapituloDTO
- Integração com subpacote `normsplitter`
- Metadados específicos para normativos

**Estrutura Hierárquica**:
```
Normativo
├── Livro (nível 1)
├── Título (nível 2)
├── Capítulo (nível 3)
├── Seção (nível 4) → mapeada para CapituloDTO
├── Subseção (nível 5)
└── Artigo (nível 6)
```

**Exemplo**:
```java
SplitterNorma splitter = new SplitterNorma(llmService);
DocumentoDTO normativo = splitter.carregaNorma(urlNormativo, null);
```

### 3. SplitterWiki
**Uso**: Conteúdo tipo Wikipedia, artigos enciclopédicos

**Características**:
- Limpeza automática de markup Wiki (links, referências, templates)
- Detecção de seções Wiki (`==Seção==`, `===Subseção===`)
- `splitIntoParagraphs()` como modo padrão para embeddings secundários
- Processamento de infoboxes

**Exemplo**:
```java
SplitterWiki splitter = new SplitterWiki(llmService);
splitter.setCleanWikiMarkup(true);
splitter.setProcessInfoboxes(true);
List<CapituloDTO> capitulos = splitter.splitDocumento(documento);
```

---

## Roteamento Automático

### DocumentRouter
O `DocumentRouter` identifica automaticamente o tipo de documento e roteia para o splitter apropriado.

#### Tipos Suportados
- `normativo` → SplitterNorma
- `wikipedia` → SplitterWiki
- `artigo` → SplitterWiki
- `manual` → SplitterGenerico
- `livro` → SplitterGenerico
- `contrato` → SplitterGenerico
- `generico` → SplitterGenerico

#### Uso Básico
```java
DocumentRouter router = new DocumentRouter(llmService);

// Roteamento automático por conteúdo
AbstractSplitter splitter = router.routeDocument(conteudo);

// Roteamento com hints (URL, extensão)
AbstractSplitter splitter = router.routeDocument(conteudo,
    "https://planalto.gov.br/lei123", "arquivo.pdf");

// Roteamento por tipo predefinido
AbstractSplitter splitter = router.routeDocument(TipoConteudo.NORMATIVO);
```

#### Estratégias de Identificação
1. **Hints**: URL, extensão de arquivo
2. **LLM**: Análise inteligente do conteúdo
3. **Heurística**: Padrões textuais específicos

**Exemplo de Hints**:
```java
// URLs específicas
"planalto.gov.br" → normativo
"wikipedia.org" → wikipedia

// Extensões
".wiki" → wikipedia
".md" → artigo

// Conteúdo heurístico
"Art. 1º" + "Lei" → normativo
"{{" + "[[" → wikipedia
```

---

## Funcionalidades LLM

### Interface SplitterLLMServices
Todas as funcionalidades LLM estão centralizadas na interface `SplitterLLMServices`, implementada no `AbstractSplitter`.

#### Funcionalidades Disponíveis

##### 1. Sumarização
```java
// Sumarização básica
String resumo = splitter.sumarizeText(texto, 500);

// Sumarização com instruções específicas
String resumo = splitter.sumarizeText(texto,
    "Resuma focando nos aspectos técnicos", 300);
```

##### 2. Tradução
```java
String textoTraduzido = splitter.translateText(texto, "pt", "en");
```

##### 3. Identificação de Tipo
```java
String tipo = splitter.identifyDocumentType(conteudo);
// Retorna: "normativo", "wikipedia", "artigo", etc.
```

##### 4. Mapa Mental
```java
String mapaMental = splitter.createMindMap(documento);
```

##### 5. Clarificação de Texto
```java
// Clarificação padrão
String textoClarificado = splitter.clarifyText(texto);

// Com instruções específicas
String textoClarificado = splitter.clarifyText(texto,
    "Simplifique para linguagem técnica");
```

##### 6. Geração de Q&A
```java
List<QuestionAnswer> qaList = splitter.generateQA(texto, 5);
for (QuestionAnswer qa : qaList) {
    System.out.println("Q: " + qa.getQuestion());
    System.out.println("A: " + qa.getAnswer());
}
```

##### 7. Extração de Metadados
```java
Map<String, String> metadados = splitter.extractMetadata(texto);
// Retorna: titulo, autor, data, palavras_chave, resumo
```

##### 8. Categorização
```java
List<String> categorias = Arrays.asList("técnico", "legal", "científico");
String categoria = splitter.categorizeContent(conteudo, categorias);
```

#### Fallbacks Heurísticos
Todas as funcionalidades LLM possuem implementações de fallback que funcionam sem LLM:

```java
// Verifica disponibilidade
if (splitter.isAvailable()) {
    // Usa LLM
    String tipo = splitter.identifyDocumentType(conteudo);
} else {
    // Usa heurística automática
    String tipo = splitter.identifyDocumentType(conteudo);
}
```

---

## Processamento de Embeddings

### EmbeddingProcessorImpl
Classe responsável pela criação e processamento de embeddings integrada ao JSimpleRag.

#### Modos de Geração (FLAGS)

##### 1. FLAG_FULL_TEXT_METADATA
Cria embedding com texto completo + metadados
```java
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_FULL_TEXT_METADATA);
```

##### 2. FLAG_ONLY_TEXT
Cria embedding apenas com o texto
```java
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_ONLY_TEXT);
```

##### 3. FLAG_ONLY_METADATA
Cria embedding apenas com metadados
```java
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_ONLY_METADATA);
```

##### 4. FLAG_SPLIT_TEXT_METADATA
Divide texto em chunks e cria múltiplos embeddings
```java
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_SPLIT_TEXT_METADATA);
```

##### 5. FLAG_AUTO (Recomendado)
Escolhe automaticamente baseado no tamanho do conteúdo
```java
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_AUTO);
```

**Lógica do FLAG_AUTO**:
- Conteúdo < 100 tokens → FLAG_FULL_TEXT_METADATA
- Conteúdo 100-2000 tokens → FLAG_ONLY_TEXT
- Conteúdo > 2000 tokens → FLAG_SPLIT_TEXT_METADATA

#### Embeddings para Busca
```java
// Embedding otimizado para queries
float[] queryEmbedding = processor.createSearchEmbeddings(
    "texto da consulta", biblioteca);

// Embedding com operação específica
float[] embedding = processor.createEmbeddings(
    Emb_Operation.DOCUMENT, texto, biblioteca);
```

#### Embeddings Q&A
Cria embeddings especializados para perguntas e respostas:
```java
List<DocEmbeddingDTO> qaEmbeddings = processor.createQAEmbeddings(
    capitulo, biblioteca);
```

**Estrutura dos Embeddings Q&A**:
- Embeddings de perguntas (TipoEmbedding.PERGUNTA, Emb_Operation.QUERY)
- Embeddings de respostas (TipoEmbedding.RESPOSTA, Emb_Operation.DOCUMENT)
- Metadados com relacionamento pergunta-resposta

---

## Sumarização e Q&A

### DocumentSummarizerImpl
Serviço especializado em sumarização e geração de pares pergunta-resposta.

#### Sumarização
```java
DocumentSummarizerImpl summarizer = new DocumentSummarizerImpl(llmService);

// Sumarização básica
String resumo = summarizer.summarize(texto, 500);

// Sumarização com instruções específicas
String resumo = summarizer.summarize(texto,
    "Foque nos aspectos legais e principais impactos", 300);
```

#### Geração de Q&A
```java
List<QuestionAnswer> qaList = summarizer.generateQA(texto, 5);

for (QuestionAnswer qa : qaList) {
    System.out.println("Pergunta: " + qa.getQuestion());
    System.out.println("Resposta: " + qa.getAnswer());
    System.out.println("---");
}
```

#### Fallbacks sem LLM
O serviço possui estratégias de fallback que funcionam sem LLM:
- **Sumarização**: Extrai primeiras frases até limite de palavras
- **Q&A**: Gera perguntas heurísticas baseadas em padrões textuais

---

## Exemplos de Uso

### Exemplo 1: Processamento Completo de Documento
```java
// 1. Configurar serviços
LLMService llmService = // configurar conforme JSimpleLLM
DocumentRouter router = new DocumentRouter(llmService);
EmbeddingProcessorImpl embeddingProcessor = new EmbeddingProcessorImpl(
    llmService, new DocumentSummarizerImpl(llmService));

// 2. Carregar documento
DocumentoDTO documento = // carregar documento
BibliotecaDTO biblioteca = // configurar biblioteca

// 3. Roteamento automático
AbstractSplitter splitter = router.routeDocument(documento.getTexto());

// 4. Split em capítulos
List<CapituloDTO> capitulos = splitter.splitDocumento(documento);

// 5. Processar cada capítulo
for (CapituloDTO capitulo : capitulos) {
    // Criar embeddings automaticamente
    List<DocEmbeddingDTO> embeddings = embeddingProcessor.createSimpleEmbeddings(
        capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_AUTO);

    // Criar embeddings Q&A
    List<DocEmbeddingDTO> qaEmbeddings = embeddingProcessor.createQAEmbeddings(
        capitulo, biblioteca);

    // Adicionar ao capítulo
    embeddings.forEach(capitulo::addEmbedding);
    qaEmbeddings.forEach(capitulo::addEmbedding);
}
```

### Exemplo 2: Processamento de Normativo
```java
// 1. Splitter específico para normativos
SplitterNorma splitterNorma = new SplitterNorma(llmService);

// 2. Carregar normativo de URL
URL urlNormativo = new URL("https://planalto.gov.br/lei-exemplo");
DocumentoDTO normativo = splitterNorma.carregaNorma(urlNormativo, null);

// 3. Os capítulos já são criados automaticamente baseados em seções
List<CapituloDTO> secoes = normativo.getCapitulos();

// 4. Processar embeddings para cada seção
for (CapituloDTO secao : secoes) {
    List<DocEmbeddingDTO> embeddings = embeddingProcessor.createSimpleEmbeddings(
        secao, biblioteca, EmbeddingProcessorInterface.FLAG_AUTO);
}
```

### Exemplo 3: Processamento Wikipedia
```java
// 1. Splitter específico para Wikipedia
SplitterWiki splitterWiki = new SplitterWiki(llmService);
splitterWiki.setCleanWikiMarkup(true);
splitterWiki.setMaxWordsPerChapter(1500);

// 2. Processar conteúdo Wikipedia
DocumentoDTO wikiDoc = // carregar conteúdo Wikipedia
List<CapituloDTO> secoes = splitterWiki.splitDocumento(wikiDoc);

// 3. Para cada seção, usar split em parágrafos para embeddings secundários
for (CapituloDTO secao : secoes) {
    String[] paragrafos = splitterWiki.splitIntoParagraphs(secao.getConteudo());

    // Criar embeddings para cada parágrafo
    for (String paragrafo : paragrafos) {
        if (paragrafo.trim().length() > 100) { // Mínimo de caracteres
            DocEmbeddingDTO embedding = // criar embedding do parágrafo
            secao.addEmbedding(embedding);
        }
    }
}
```

### Exemplo 4: Usar Funcionalidades LLM Diretamente
```java
// 1. Qualquer splitter tem acesso às funcionalidades LLM
AbstractSplitter splitter = new SplitterGenerico(llmService);

// 2. Identificar tipo de documento
String tipo = splitter.identifyDocumentType(conteudo);
System.out.println("Tipo identificado: " + tipo);

// 3. Criar resumo
String resumo = splitter.sumarizeText(conteudo, 300);
System.out.println("Resumo: " + resumo);

// 4. Gerar Q&A
List<QuestionAnswer> qa = splitter.generateQA(conteudo, 3);
qa.forEach(q -> System.out.println("Q: " + q.getQuestion() + " A: " + q.getAnswer()));

// 5. Extrair metadados
Map<String, String> metadados = splitter.extractMetadata(conteudo);
System.out.println("Metadados: " + metadados);

// 6. Criar mapa mental
String mapaMental = splitter.createMindMap(conteudo);
System.out.println("Mapa Mental:\n" + mapaMental);
```

---

## Configuração e Integração

### Dependências Necessárias
```xml
<!-- JSimpleLLM (obrigatório) -->
<dependency>
    <groupId>bor.tools</groupId>
    <artifactId>simplellm</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot (para @Service, @Component) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
```

### Configuração Spring Boot
```java
@Configuration
public class SplitterConfig {

    @Bean
    public DocumentRouter documentRouter(LLMService llmService) {
        return new DocumentRouter(llmService);
    }

    @Bean
    public EmbeddingProcessorImpl embeddingProcessor(
            LLMService llmService,
            DocumentSummarizerImpl summarizer) {
        return new EmbeddingProcessorImpl(llmService, summarizer);
    }
}
```

### Configuração de Propriedades
```properties
# Configurações do splitter
splitter.default-chunk-size=2000
splitter.min-chunk-size=100
splitter.max-chunk-size=16000

# Configurações específicas por tipo
splitter.wiki.max-words-per-chapter=1500
splitter.wiki.clean-markup=true
splitter.normativo.nivel-maximo=2
```

### Integração com ProcessamentoAssincrono
```java
@Service
public class DocumentProcessingService {

    @Autowired
    private DocumentRouter documentRouter;

    @Autowired
    private EmbeddingProcessorImpl embeddingProcessor;

    @Async
    public CompletableFuture<DocumentoDTO> processDocumentAsync(
            DocumentoDTO documento, BibliotecaDTO biblioteca) {

        // Roteamento automático
        AbstractSplitter splitter = documentRouter.routeDocument(
            documento.getTexto(), documento.getUrl());

        // Splitting
        List<CapituloDTO> capitulos = splitter.splitDocumento(documento);

        // Processamento de embeddings
        for (CapituloDTO capitulo : capitulos) {
            List<DocEmbeddingDTO> embeddings = embeddingProcessor.createSimpleEmbeddings(
                capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_AUTO);
            embeddings.forEach(capitulo::addEmbedding);
        }

        return CompletableFuture.completedFuture(documento);
    }
}
```

---

## Troubleshooting

### Problemas Comuns

#### 1. LLMService não disponível
**Sintoma**: Funcionalidades LLM retornam fallbacks heurísticos
```java
// Verificar disponibilidade
if (!splitter.isAvailable()) {
    System.out.println("LLM Service não disponível, usando fallbacks");
}

// Verificar modelos disponíveis
List<String> models = splitter.getAvailableModels();
System.out.println("Modelos disponíveis: " + models);
```

#### 2. Embeddings vazios
**Sintoma**: `createEmbeddings()` retorna array vazio
```java
// Verificar se há conteúdo
if (texto == null || texto.trim().isEmpty()) {
    System.out.println("Texto vazio para embedding");
}

// Verificar configuração LLM
EmbeddingProcessorImpl processor = // ...
if (!processor.isLLMServiceAvailable()) {
    System.out.println("LLM Service não configurado para embeddings");
}
```

#### 3. Splitter incorreto selecionado
**Sintoma**: DocumentRouter seleciona splitter inadequado
```java
// Usar hints específicos
AbstractSplitter splitter = router.routeDocument(conteudo,
    "wikipedia.org/wiki/artigo", "arquivo.wiki");

// Ou especificar tipo diretamente
AbstractSplitter splitter = router.routeDocument(TipoConteudo.WIKIPEDIA);

// Verificar tipos suportados
String[] types = router.getSupportedTypes();
System.out.println("Tipos suportados: " + Arrays.toString(types));
```

#### 4. Performance em documentos grandes
**Sintoma**: Processamento lento para documentos > 50MB
```java
// Usar FLAG_SPLIT_TEXT_METADATA para documentos grandes
List<DocEmbeddingDTO> embeddings = processor.createSimpleEmbeddings(
    capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_SPLIT_TEXT_METADATA);

// Ajustar chunk size
SplitterGenerico splitter = new SplitterGenerico(llmService);
splitter.setMaxWords(1000); // Reduzir tamanho dos chunks
```

### Logs e Debugging
```java
// Habilitar logs debug no application.properties
logging.level.bor.tools.splitter=DEBUG

// Obter estatísticas
Map<String, Object> stats = router.getRouterStats();
System.out.println("Router stats: " + stats);

Map<String, Object> embStats = processor.getProcessorStats();
System.out.println("Embedding stats: " + embStats);
```

### Limitações Conhecidas
1. **Tamanho máximo**: Documentos > 100MB podem causar OutOfMemory
2. **Contexto LLM**: Textos > 8K tokens são truncados para sumarização
3. **Normativos**: Apenas estruturas hierárquicas padronizadas são suportadas
4. **Wikipedia**: Markup muito complexo pode não ser totalmente limpo

---

## Performance e Escalabilidade

### Métricas de Performance
- **Splitting**: ~1MB/segundo para documentos markdown
- **Embeddings**: Limitado pela API do LLM (~10 requests/segundo)
- **Roteamento**: < 100ms para identificação de tipo

### Otimizações Recomendadas
1. **Cache de embeddings** para conteúdo repetido
2. **Processamento assíncrono** para múltiplos documentos
3. **Batch processing** de embeddings quando suportado pelo LLM
4. **Lazy loading** de normsplitter para reduzir uso de memória

### Monitoramento
```java
// Métricas disponíveis via JMX ou logs
processor.getProcessorStats();
router.getRouterStats();
summarizer.getSummarizerStats();
```

---

**Última atualização**: 2025-01-28
**Versão**: 2.0 (Fase 2 Completa)
**Compatibilidade**: JSimpleRag 1.x, JSimpleLLM 1.x