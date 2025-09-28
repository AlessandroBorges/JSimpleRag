# Tasks Splitter - Refatoração do Pacote bor.tools.splitter

## Visão Geral
Refatoração e completude das funcionalidades do pacote `bor.tools.splitter` para melhor integração com o JSimpleRag.

## Decisões Arquiteturais

### Questões Resolvidas
1. **normsplitter**: Manter como subpacote
2. **ContentSplitter**: Aguardar definição de classe utilitária para roteamento de documentos
3. **Performance**: Sem limites específicos impostos no momento

### Considerações Importantes

#### LLMServices Integration
- Criar interface `SplitterLLMServices` na Fase 2
- Funcionalidades LLM:
  - Sumarização
  - Tradução
  - Contagem de tokens
  - Identificação de tipo de documento
  - Criação de mapa mental de documentos
- AbstractSplitter implementará funcionalidades padrão

#### Estrutura de Normativos
- **Estrutura Real**: Normativo → Capítulos → Seções → Artigos
- **Modelo JSimpleRag**: Documento → Capítulo → Chunks (DocEmbeddings)
- **Mapeamento**: Seção → CapituloDTO
- Novas classes: `bor.tools.splitter.normsplitter.Capitulo` e `bor.tools.splitter.normsplitter.Secao`

#### SplitterWiki Especializado
- Recriar SplitterWiki para conteúdo tipo Wikipedia
- `splitIntoParagraphs()` como modo default para split secundário (Capítulos → DocEmbeddings)

---

## FASE 1: Consolidação e Limpeza (CONCLUÍDA ✅)

### ✅ Concluído

#### 1.1 Funcionalidades Críticas Implementadas

- ✅ **SplitterGenerico.splitBySize()**
  - Implementado usando ContentSplitter como base
  - Mantém compatibilidade com maxWords parameter
  - Divisão inteligente de capítulos grandes

- ✅ **SplitterGenerico.removeRepetitions()**
  - Implementado delegando para AbstractSplitter
  - Funcionalidade consistente e logging adequado

- ✅ **SplitterNorma - Métodos Abstratos**
  - ✅ `detectTitles()` - Detecta estrutura hierárquica de normativos
  - ✅ `splitByTitles()` - Split baseado em hierarquia legal (Seção → CapituloDTO)
  - Integração com estrutura de normativos

#### 1.2 Ajustes na Arquitetura

- ✅ **Mapear Seção → CapituloDTO**
  - SplitterNorma ajustado para mapear Seções para CapituloDTO
  - Metadados específicos para normativos adicionados
  - Suporte a estrutura: Normativo → Capítulos → Seções → Artigos

- ✅ **Criar SplitterWiki**
  - Novo splitter especializado para conteúdo Wikipedia-like
  - splitIntoParagraphs() como split secundário padrão
  - Limpeza automática de markup Wiki
  - Detecção de infoboxes e referências

#### 1.3 Padronização de Token Count

- ✅ **getTokenCount() Centralizado**
  - Implementação padronizada no AbstractSplitter
  - Usa LLMService quando disponível com fallback robusto
  - Estimativa melhorada: palavras / 0.75 (mais precisa que caracteres / 4)
  - Tratamento de erros e logging adequado

### 🔴 Problemas Resolvidos
- ✅ SplitterNorma agora integra com estrutura de normativos
- ✅ Métodos abstratos implementados com lógica específica
- ✅ SplitterWiki criado para casos de uso específicos
- ✅ Token counting padronizado e melhorado

---

## FASE 2: Modularização e Especialização (CONCLUÍDA ✅)

### ✅ Funcionalidades Implementadas

#### 2.1 Interface SplitterLLMServices ✅
```java
public interface SplitterLLMServices {
    String sumarizeText(String text, String instructions, int maxLength);
    String translateText(String text, String sourceLang, String targetLang);
    int getTokenCount(String text);
    String identifyDocumentType(String content);
    String createMindMap(String content);
    String clarifyText(String text, String instructions);
    List<QuestionAnswer> generateQA(String text, int numQuestions);
    Map<String, String> extractMetadata(String text);
    String categorizeContent(String content, List<String> categories);
    boolean isAvailable();
    List<String> getAvailableModels();
}
```

**Implementação Completa no AbstractSplitter:**
- ✅ Todas as funcionalidades LLM centralizadas
- ✅ Fallback heurístico quando LLM não disponível
- ✅ Logging adequado e tratamento de erros
- ✅ Reutilização de métodos existentes (clarifiqueTexto)

#### 2.2 DocumentRouter ✅
**Classe utilitária para roteamento automático de documentos:**
- ✅ Identificação automática de tipo de documento (LLM + heurística)
- ✅ Roteamento para splitter apropriado
- ✅ Suporte a hints (URL, extensão de arquivo)
- ✅ Integração com TipoConteudo enum
- ✅ Registry extensível de splitters
- ✅ Factory pattern para criação de instâncias

**Tipos Suportados:**
- `normativo` → SplitterNorma
- `wikipedia` → SplitterWiki
- `artigo` → SplitterWiki
- `manual/livro/contrato` → SplitterGenerico

#### 2.3 DocumentSummarizerImpl ✅
**Implementação completa da interface DocumentSummarizer:**
- ✅ Sumarização com LLM + fallback heurístico
- ✅ Geração de Q&A automatizada
- ✅ Parse inteligente de respostas LLM
- ✅ Controle de tamanho e contexto
- ✅ Estratégias de fallback sem LLM
- ✅ Estatísticas e monitoramento

#### 2.4 EmbeddingProcessorImpl ✅
**Implementação completa da interface EmbeddingProcessorInterface:**
- ✅ 5 modos de geração de embeddings (FLAG_*)
- ✅ Integração com bor.tools.simplellm (Emb_Operation)
- ✅ Criação de embeddings Q&A
- ✅ Split automático baseado em tamanho
- ✅ Embeddings para busca otimizados
- ✅ Metadados enriquecidos
- ✅ Chunk size automático (MIN: 100, IDEAL: 2000 tokens)

### 🔄 Reorganização Arquitetural (Preparada)
**Estrutura atual vs. planejada:**
```
Atual:
bor.tools.splitter/
├── SplitterLLMServices.java ✅
├── DocumentRouter.java ✅
├── DocumentSummarizerImpl.java ✅
├── EmbeddingProcessorImpl.java ✅
├── AbstractSplitter.java (com SplitterLLMServices) ✅
├── SplitterGenerico.java ✅
├── SplitterNorma.java ✅
├── SplitterWiki.java ✅
└── normsplitter/ (mantido) ✅

Planejada para Fase 3:
├── core/ (TextSplitter, TokenCounter, MetadataEnricher)
├── specialized/ (refatoração das classes atuais)
├── processors/ (mover *Impl)
└── llm/ (mover SplitterLLMServices)
```

---

## FASE 3: Integração com JSimpleRag (PLANEJADA)

### 3.1 Alinhamento com Arquitetura Principal
- Factory Pattern para Splitters
- Integração com ProcessamentoAssincrono
- Uso de enums do core (TipoConteudo, TipoEmbedding, etc.)

### 3.2 Configurabilidade
- SplitterConfig para parâmetros por biblioteca
- Configuração de chunks sizes por tipo de documento

---

## FASE 4: Deprecação e Cleanup (CONTÍNUA)

### 4.1 Timeline de Deprecação
- **v1.1**: Métodos duplicados de divisão de texto
- **v1.2**: AbstractSplitter como classe base
- **v2.0**: Cleanup completo

---

## Métricas de Sucesso

### Fase 1
- ✅ Todos os métodos críticos implementados (0 retornos null)
- ✅ SplitterNorma integrado com normsplitter.Secao
- ✅ SplitterWiki criado e funcional
- ✅ Estimativa de tokens padronizada

### Fase 2
- ✅ Interface SplitterLLMServices implementada
- ✅ DocumentRouter funcional
- ✅ DocumentSummarizerImpl completo
- ✅ EmbeddingProcessorImpl completo
- 🔄 Reorganização arquitetural (preparada para Fase 3)

### Fase 3
- [ ] Factory Pattern implementado
- [ ] Integração com ProcessamentoAssincrono
- [ ] Configurabilidade por biblioteca

---

## Notas de Implementação

### Prioridades Atuais (Fase 2 - Próxima)
1. Criar interface SplitterLLMServices
2. Implementar DocumentRouter para roteamento automático
3. Criar DocumentSummarizerImpl
4. Implementar EmbeddingProcessor completo

### Considerações Técnicas
- Manter compatibilidade backward durante transição
- Testes de regressão para funcionalidades existentes
- Documentação das migrações necessárias

### Dependencies
- bor.tools.simplellm (LLMService)
- normsplitter classes (Capitulo, Secao)
- Core JSimpleRag entities e DTOs

---

**Última atualização**: 2025-01-28
**Status**: Fase 1 em progresso
**Responsável**: Claude Code