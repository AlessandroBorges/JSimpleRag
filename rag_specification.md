# Especificação Técnica - Sistema RAG Hierárquico

## 1. Visão Geral

Sistema de Retrieval-Augmented Generation (RAG) com arquitetura hierárquica usando PostgreSQL 18 + PGVector, pesquisa híbrida (semântica + textual) e estrutura Biblioteca → Documento → Capítulo → DocEmbedding.

### Tecnologias
- **Backend**: Java + Spring Boot + Lombok + OpenAPI
- **Banco**: PostgreSQL 18 + PGVector
- **Busca**: Híbrida (embedding semântico + full-text search)

## 2. Modelo de Dados

### 2.1 Enums

```sql
CREATE TYPE tipo_embedding AS ENUM ('documento', 'capitulo', 'trecho');
CREATE TYPE tipo_conteudo AS ENUM ('livro', 'normativo', 'artigo', 'manual', 'outros');
CREATE TYPE tipo_secao AS ENUM ('introducao', 'metodologia', 'desenvolvimento', 'conclusao', 'anexo', 'outros');
```

### 2.2 Estrutura das Tabelas

```sql
-- Biblioteca: Agregador de documentos por área de conhecimento
CREATE TABLE biblioteca (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    area_conhecimento VARCHAR(255) NOT NULL,
    peso_semantico DECIMAL(3,2) DEFAULT 0.60 CHECK (peso_semantico BETWEEN 0 AND 1),
    peso_textual DECIMAL(3,2) DEFAULT 0.40 CHECK (peso_textual BETWEEN 0 AND 1),
    metadados JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT peso_total_check CHECK (peso_semantico + peso_textual = 1.0)
);

-- Documento: Conteúdo completo (livro, normativo, artigo, etc.)
CREATE TABLE documento (
    id BIGSERIAL PRIMARY KEY,
    biblioteca_id BIGINT NOT NULL REFERENCES biblioteca(id),
    titulo VARCHAR(500) NOT NULL,
    conteudo_markdown TEXT NOT NULL,
    flag_vigente BOOLEAN DEFAULT TRUE,
    data_publicacao DATE NOT NULL,
    tokens_total INTEGER,
    metadados JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Capítulo: Seção do documento (~8k tokens padrão)
CREATE TABLE capitulo (
    id BIGSERIAL PRIMARY KEY,
    documento_id BIGINT NOT NULL REFERENCES documento(id),
    titulo VARCHAR(500) NOT NULL,
    conteudo TEXT NOT NULL,
    ordem_doc INTEGER NOT NULL,
    token_inicio INTEGER,
    token_fim INTEGER,
    tokens_total INTEGER,
    metadados JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_ordem_por_doc UNIQUE (documento_id, ordem_doc)
);

-- DocEmbedding: Embeddings para pesquisa (documento, capítulo ou trecho)
CREATE TABLE doc_embedding (
    id BIGSERIAL PRIMARY KEY,
    biblioteca_id BIGINT NOT NULL REFERENCES biblioteca(id),
    documento_id BIGINT NOT NULL REFERENCES documento(id),
    capitulo_id BIGINT REFERENCES capitulo(id),
    tipo_embedding tipo_embedding NOT NULL,
    trecho_texto TEXT,
    ordem_cap INTEGER,
    embedding_vector vector(1536), -- Assumindo OpenAI ada-002 (ajustar conforme modelo)
    texto_indexado tsvector,
    metadados JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_consistency CHECK (
        (tipo_embedding = 'documento' AND capitulo_id IS NULL AND ordem_cap IS NULL) OR
        (tipo_embedding = 'capitulo' AND capitulo_id IS NOT NULL AND ordem_cap IS NULL) OR
        (tipo_embedding = 'trecho' AND capitulo_id IS NOT NULL AND ordem_cap IS NOT NULL)
    )
);

-- Índices para performance
CREATE INDEX idx_biblioteca_area ON biblioteca(area_conhecimento);
CREATE INDEX idx_documento_vigente ON documento(flag_vigente, data_publicacao DESC);
CREATE INDEX idx_documento_biblioteca ON documento(biblioteca_id);
CREATE INDEX idx_capitulo_documento_ordem ON capitulo(documento_id, ordem_doc);
CREATE INDEX idx_embedding_biblioteca ON doc_embedding(biblioteca_id);
CREATE INDEX idx_embedding_documento ON doc_embedding(documento_id);
CREATE INDEX idx_embedding_capitulo ON doc_embedding(capitulo_id);
CREATE INDEX idx_embedding_tipo ON doc_embedding(tipo_embedding);
CREATE INDEX idx_embedding_vector ON doc_embedding USING ivfflat (embedding_vector vector_cosine_ops);
CREATE INDEX idx_embedding_texto ON doc_embedding USING gin(texto_indexado);
```

## 3. Modelo Java

### 3.1 Entidades JPA

```java
// Biblioteca.java
@Entity
@Table(name = "biblioteca")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Biblioteca {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nome;
    
    @Column(name = "area_conhecimento", nullable = false)
    private String areaConhecimento;
    
    @Column(name = "peso_semantico", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal pesoSemantico = new BigDecimal("0.60");
    
    @Column(name = "peso_textual", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal pesoTextual = new BigDecimal("0.40");
    
    @Type(JsonType.class)
    private Map<String, String> metadados;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "biblioteca", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Documento> documentos;
}

// Documento.java
@Entity
@Table(name = "documento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biblioteca_id", nullable = false)
    private Biblioteca biblioteca;
    
    @Column(nullable = false, length = 500)
    private String titulo;
    
    @Column(name = "conteudo_markdown", nullable = false, columnDefinition = "TEXT")
    private String conteudoMarkdown;
    
    @Column(name = "flag_vigente")
    @Builder.Default
    private Boolean flagVigente = true;
    
    @Column(name = "data_publicacao", nullable = false)
    private LocalDate dataPublicacao;
    
    @Column(name = "tokens_total")
    private Integer tokensTotal;
    
    @Type(JsonType.class)
    private Map<String, String> metadados;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("ordemDoc ASC")
    private List<Capitulo> capitulos;
}

// Capitulo.java
@Entity
@Table(name = "capitulo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capitulo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;
    
    @Column(nullable = false, length = 500)
    private String titulo;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;
    
    @Column(name = "ordem_doc", nullable = false)
    private Integer ordemDoc;
    
    @Column(name = "token_inicio")
    private Integer tokenInicio;
    
    @Column(name = "token_fim")
    private Integer tokenFim;
    
    @Column(name = "tokens_total")
    private Integer tokensTotal;
    
    @Type(JsonType.class)
    private Map<String, String> metadados;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "capitulo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("ordemCap ASC")
    private List<DocEmbedding> embeddings;
}

// DocEmbedding.java
@Entity
@Table(name = "doc_embedding")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biblioteca_id", nullable = false)
    private Biblioteca biblioteca;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capitulo_id")
    private Capitulo capitulo;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_embedding", nullable = false)
    private TipoEmbedding tipoEmbedding;
    
    @Column(name = "trecho_texto", columnDefinition = "TEXT")
    private String trechoTexto;
    
    @Column(name = "ordem_cap")
    private Integer ordemCap;
    
    @Column(name = "embedding_vector", columnDefinition = "vector(1536)")
    private float[] embeddingVector;
    
    @Column(name = "texto_indexado", columnDefinition = "tsvector")
    private String textoIndexado;
    
    @Type(JsonType.class)
    private Map<String, String> metadados;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

// Enums
public enum TipoEmbedding {
    DOCUMENTO, CAPITULO, TRECHO
}

public enum TipoConteudo {
    LIVRO(1), NORMATIVO(2), ARTIGO(3), MANUAL(4), OUTROS(5);
    
    private final int codigo;
    TipoConteudo(int codigo) { this.codigo = codigo; }
    public int getCodigo() { return codigo; }
}

public enum TipoSecao {
    INTRODUCAO(1), METODOLOGIA(2), DESENVOLVIMENTO(3), CONCLUSAO(4), ANEXO(5), OUTROS(6);
    
    private final int codigo;
    TipoSecao(int codigo) { this.codigo = codigo; }
    public int getCodigo() { return codigo; }
}
```

## 4. DTOs (Data Transfer Objects)

```java
// BibliotecaDTO.java
@Data
@Builder
@Schema(description = "Dados da Biblioteca")
public class BibliotecaDTO {
    @Schema(description = "ID da biblioteca", example = "1")
    private Long id;
    
    @NotBlank
    @Schema(description = "Nome da biblioteca", example = "Engenharia de Software")
    private String nome;
    
    @NotBlank
    @Schema(description = "Área de conhecimento", example = "Tecnologia")
    private String areaConhecimento;
    
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Schema(description = "Peso da busca semântica", example = "0.60")
    private BigDecimal pesoSemantico;
    
    @DecimalMin("0.0") @DecimalMax("1.0")
    @Schema(description = "Peso da busca textual", example = "0.40")
    private BigDecimal pesoTextual;
    
    @Schema(description = "Metadados adicionais")
    private Map<String, String> metadados;
}

// DocumentoDTO.java
@Data
@Builder
@Schema(description = "Dados do Documento")
public class DocumentoDTO {
    @Schema(description = "ID do documento", example = "1")
    private Long id;
    
    @NotNull
    @Schema(description = "ID da biblioteca", example = "1")
    private Long bibliotecaId;
    
    @NotBlank
    @Size(max = 500)
    @Schema(description = "Título do documento", example = "Clean Code")
    private String titulo;
    
    @NotBlank
    @Schema(description = "Conteúdo em Markdown")
    private String conteudoMarkdown;
    
    @Schema(description = "Se é a versão vigente", example = "true")
    private Boolean flagVigente;
    
    @NotNull
    @Schema(description = "Data de publicação", example = "2023-01-15")
    private LocalDate dataPublicacao;
    
    @Schema(description = "Total de tokens", example = "15000")
    private Integer tokensTotal;
    
    @Schema(description = "Metadados adicionais")
    private Map<String, String> metadados;
}

// CapituloDTO.java
@Data
@Builder
@Schema(description = "Dados do Capítulo")
public class CapituloDTO {
    @Schema(description = "ID do capítulo", example = "1")
    private Long id;
    
    @NotNull
    @Schema(description = "ID do documento", example = "1")
    private Long documentoId;
    
    @NotBlank
    @Size(max = 500)
    @Schema(description = "Título do capítulo", example = "Introdução")
    private String titulo;
    
    @NotBlank
    @Schema(description = "Conteúdo do capítulo")
    private String conteudo;
    
    @NotNull
    @Schema(description = "Ordem no documento", example = "1")
    private Integer ordemDoc;
    
    @Schema(description = "Token de início", example = "0")
    private Integer tokenInicio;
    
    @Schema(description = "Token de fim", example = "8000")
    private Integer tokenFim;
    
    @Schema(description = "Total de tokens", example = "8000")
    private Integer tokensTotal;
    
    @Schema(description = "Metadados adicionais")
    private Map<String, String> metadados;
}

// DocEmbeddingDTO.java
@Data
@Builder
@Schema(description = "Dados do DocEmbedding")
public class DocEmbeddingDTO {
    @Schema(description = "ID do embedding", example = "1")
    private Long id;
    
    @NotNull
    @Schema(description = "ID da biblioteca", example = "1")
    private Long bibliotecaId;
    
    @NotNull
    @Schema(description = "ID do documento", example = "1")
    private Long documentoId;
    
    @Schema(description = "ID do capítulo (opcional)", example = "1")
    private Long capituloId;
    
    @NotNull
    @Schema(description = "Tipo do embedding", example = "TRECHO")
    private TipoEmbedding tipoEmbedding;
    
    @Schema(description = "Texto do trecho")
    private String trechoTexto;
    
    @Schema(description = "Ordem no capítulo", example = "1")
    private Integer ordemCap;
    
    @Schema(description = "Vetor de embedding")
    private float[] embeddingVector;
    
    @Schema(description = "Metadados adicionais")
    private Map<String, String> metadados;
}

// PesquisaDTO.java
@Data
@Builder
@Schema(description = "Parâmetros de pesquisa")
public class PesquisaDTO {
    @NotBlank
    @Schema(description = "Termo de busca", example = "clean architecture")
    private String query;
    
    @Schema(description = "ID da biblioteca (opcional)", example = "1")
    private Long bibliotecaId;
    
    @Schema(description = "Apenas documentos vigentes", example = "true")
    @Builder.Default
    private Boolean apenasVigentes = true;
    
    @Schema(description = "Limite de resultados", example = "10")
    @Builder.Default
    private Integer limite = 10;
    
    @Schema(description = "Peso customizado para busca semântica", example = "0.7")
    private BigDecimal pesoSemanticoCustom;
}

// ResultadoPesquisaDTO.java
@Data
@Builder
@Schema(description = "Resultado da pesquisa")
public class ResultadoPesquisaDTO {
    @Schema(description = "ID do embedding")
    private Long embeddingId;
    
    @Schema(description = "Score da pesquisa", example = "0.85")
    private Double score;
    
    @Schema(description = "Nome da biblioteca")
    private String biblioteca;
    
    @Schema(description = "Título do documento")
    private String documento;
    
    @Schema(description = "Título do capítulo")
    private String capitulo;
    
    @Schema(description = "Tipo do embedding")
    private TipoEmbedding tipoEmbedding;
    
    @Schema(description = "Texto do resultado")
    private String texto;
    
    @Schema(description = "Metadados relevantes")
    private Map<String, String> metadados;
}
```

## 5. APIs REST (Controllers)

```java
// BibliotecaController.java
@RestController
@RequestMapping("/api/v1/bibliotecas")
@RequiredArgsConstructor
@Tag(name = "Biblioteca", description = "Operações com bibliotecas")
public class BibliotecaController {
    
    private final BibliotecaService bibliotecaService;
    
    @PostMapping
    @Operation(summary = "Criar biblioteca")
    public ResponseEntity<BibliotecaDTO> criar(@Valid @RequestBody BibliotecaDTO dto) {
        BibliotecaDTO criada = bibliotecaService.criar(dto);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Erro de validação: {}", ex.getMessage());
        
        Map<String, String> erros = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            erros.put(error.getField(), error.getDefaultMessage())
        );
        
        ErrorResponse error = ErrorResponse.builder()
            .codigo("VALIDATION_ERROR")
            .mensagem("Dados inválidos")
            .detalhes(erros)
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ProcessamentoException.class)
    public ResponseEntity<ErrorResponse> handleProcessamento(ProcessamentoException ex) {
        log.error("Erro no processamento: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .codigo("PROCESSING_ERROR")
            .mensagem("Erro no processamento: " + ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Erro interno: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .codigo("INTERNAL_ERROR")
            .mensagem("Erro interno do servidor")
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// ErrorResponse.java
@Data
@Builder
@Schema(description = "Resposta de erro")
public class ErrorResponse {
    @Schema(description = "Código do erro", example = "ENTITY_NOT_FOUND")
    private String codigo;
    
    @Schema(description = "Mensagem do erro", example = "Biblioteca não encontrada")
    private String mensagem;
    
    @Schema(description = "Timestamp do erro")
    private LocalDateTime timestamp;
    
    @Schema(description = "Detalhes adicionais do erro")
    private Map<String, String> detalhes;
}

// ProcessamentoException.java
public class ProcessamentoException extends RuntimeException {
    public ProcessamentoException(String message) {
        super(message);
    }
    
    public ProcessamentoException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## 12. Testes

### 12.1 Testes de Unidade

```java
// BibliotecaServiceTest.java
@ExtendWith(MockitoExtension.class)
class BibliotecaServiceTest {
    
    @Mock
    private BibliotecaRepository bibliotecaRepository;
    
    @Mock
    private BibliotecaMapper bibliotecaMapper;
    
    @InjectMocks
    private BibliotecaService bibliotecaService;
    
    @Test
    void deveCriarBiblioteca() {
        // Given
        BibliotecaDTO dto = BibliotecaDTO.builder()
            .nome("Engenharia")
            .areaConhecimento("Tecnologia")
            .pesoSemantico(new BigDecimal("0.60"))
            .pesoTextual(new BigDecimal("0.40"))
            .build();
        
        Biblioteca entity = new Biblioteca();
        entity.setId(1L);
        
        when(bibliotecaMapper.toEntity(dto)).thenReturn(entity);
        when(bibliotecaRepository.save(entity)).thenReturn(entity);
        when(bibliotecaMapper.toDto(entity)).thenReturn(dto);
        
        // When
        BibliotecaDTO resultado = bibliotecaService.criar(dto);
        
        // Then
        assertThat(resultado).isNotNull();
        verify(bibliotecaRepository).save(entity);
    }
    
    @Test
    void deveRejeitarPesosInvalidos() {
        // Given
        BibliotecaDTO dto = BibliotecaDTO.builder()
            .nome("Engenharia")
            .areaConhecimento("Tecnologia")
            .pesoSemantico(new BigDecimal("0.70"))
            .pesoTextual(new BigDecimal("0.40"))
            .build();
        
        // When/Then
        assertThatThrownBy(() -> bibliotecaService.criar(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("A soma dos pesos deve ser igual a 1.0");
    }
}

// DocumentoServiceTest.java
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {
    
    @Mock
    private DocumentoRepository documentoRepository;
    
    @Mock
    private BibliotecaRepository bibliotecaRepository;
    
    @Mock
    private DocumentoMapper documentoMapper;
    
    @Mock
    private ProcessamentoAssincrono processamentoAssincrono;
    
    @InjectMocks
    private DocumentoService documentoService;
    
    @Test
    void deveCriarDocumento() {
        // Given
        DocumentoDTO dto = DocumentoDTO.builder()
            .bibliotecaId(1L)
            .titulo("Clean Code")
            .conteudoMarkdown("# Clean Code\n\nContent...")
            .flagVigente(true)
            .dataPublicacao(LocalDate.now())
            .build();
        
        Biblioteca biblioteca = new Biblioteca();
        biblioteca.setId(1L);
        
        Documento entity = new Documento();
        entity.setId(1L);
        
        when(bibliotecaRepository.findById(1L)).thenReturn(Optional.of(biblioteca));
        when(documentoMapper.toEntity(dto)).thenReturn(entity);
        when(documentoRepository.save(entity)).thenReturn(entity);
        when(documentoMapper.toDto(entity)).thenReturn(dto);
        
        // When
        DocumentoDTO resultado = documentoService.criar(dto);
        
        // Then
        assertThat(resultado).isNotNull();
        verify(documentoRepository).marcarTodosComoNaoVigentes(1L, "Clean Code");
        verify(documentoRepository).save(entity);
    }
}
```

### 12.2 Testes de Integração

```java
// BibliotecaIntegrationTest.java
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BibliotecaIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private BibliotecaRepository bibliotecaRepository;
    
    @Test
    void devePermitirCrudCompleto() {
        // Criar
        BibliotecaDTO novaDto = BibliotecaDTO.builder()
            .nome("Engenharia de Software")
            .areaConhecimento("Tecnologia")
            .pesoSemantico(new BigDecimal("0.60"))
            .pesoTextual(new BigDecimal("0.40"))
            .build();
        
        ResponseEntity<BibliotecaDTO> createResponse = restTemplate.postForEntity(
            "/api/v1/bibliotecas", novaDto, BibliotecaDTO.class);
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BibliotecaDTO criada = createResponse.getBody();
        assertThat(criada.getId()).isNotNull();
        
        // Buscar
        ResponseEntity<BibliotecaDTO> getResponse = restTemplate.getForEntity(
            "/api/v1/bibliotecas/" + criada.getId(), BibliotecaDTO.class);
        
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getNome()).isEqualTo("Engenharia de Software");
        
        // Atualizar
        criada.setNome("Engenharia de Software Atualizada");
        
        restTemplate.put("/api/v1/bibliotecas/" + criada.getId(), criada);
        
        ResponseEntity<BibliotecaDTO> updatedResponse = restTemplate.getForEntity(
            "/api/v1/bibliotecas/" + criada.getId(), BibliotecaDTO.class);
        
        assertThat(updatedResponse.getBody().getNome()).isEqualTo("Engenharia de Software Atualizada");
        
        // Excluir
        restTemplate.delete("/api/v1/bibliotecas/" + criada.getId());
        
        ResponseEntity<BibliotecaDTO> deletedResponse = restTemplate.getForEntity(
            "/api/v1/bibliotecas/" + criada.getId(), BibliotecaDTO.class);
        
        assertThat(deletedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

## 13. Scripts de Migração (Liquibase)

### 13.1 db.changelog-master.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <include file="db/changelog/001-create-extensions.xml"/>
    <include file="db/changelog/002-create-enums.xml"/>
    <include file="db/changelog/003-create-tables.xml"/>
    <include file="db/changelog/004-create-indexes.xml"/>
    <include file="db/changelog/005-insert-seed-data.xml"/>

</databaseChangeLog>
```

### 13.2 001-create-extensions.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet id="001-create-pgvector-extension" author="sistema">
        <sql>CREATE EXTENSION IF NOT EXISTS vector;</sql>
        <rollback>DROP EXTENSION IF EXISTS vector;</rollback>
    </changeSet>

</databaseChangeLog>
```

### 13.3 003-create-tables.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet id="003-001-create-biblioteca-table" author="sistema">
        <createTable tableName="biblioteca">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="nome" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="area_conhecimento" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="peso_semantico" type="DECIMAL(3,2)" defaultValue="0.60">
                <constraints nullable="false"/>
            </column>
            <column name="peso_textual" type="DECIMAL(3,2)" defaultValue="0.40">
                <constraints nullable="false"/>
            </column>
            <column name="metadados" type="JSONB"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        </createTable>
        
        <addCheckConstraint tableName="biblioteca" constraintName="peso_total_check">
            <checkConstraint>peso_semantico + peso_textual = 1.0</checkConstraint>
        </addCheckConstraint>
    </changeSet>

    <changeSet id="003-002-create-documento-table" author="sistema">
        <createTable tableName="documento">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="biblioteca_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_documento_biblioteca" 
                           references="biblioteca(id)"/>
            </column>
            <column name="titulo" type="VARCHAR(500)">
                <constraints nullable="false"/>
            </column>
            <column name="conteudo_markdown" type="TEXT">
                <constraints nullable="false"/>
            </column>
            <column name="flag_vigente" type="BOOLEAN" defaultValue="true"/>
            <column name="data_publicacao" type="DATE">
                <constraints nullable="false"/>
            </column>
            <column name="tokens_total" type="INTEGER"/>
            <column name="metadados" type="JSONB"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        </createTable>
    </changeSet>

    <changeSet id="003-003-create-capitulo-table" author="sistema">
        <createTable tableName="capitulo">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="documento_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_capitulo_documento" 
                           references="documento(id)"/>
            </column>
            <column name="titulo" type="VARCHAR(500)">
                <constraints nullable="false"/>
            </column>
            <column name="conteudo" type="TEXT">
                <constraints nullable="false"/>
            </column>
            <column name="ordem_doc" type="INTEGER">
                <constraints nullable="false"/>
            </column>
            <column name="token_inicio" type="INTEGER"/>
            <column name="token_fim" type="INTEGER"/>
            <column name="tokens_total" type="INTEGER"/>
            <column name="metadados" type="JSONB"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        </createTable>
        
        <addUniqueConstraint tableName="capitulo" 
                           columnNames="documento_id, ordem_doc" 
                           constraintName="unique_ordem_por_doc"/>
    </changeSet>

    <changeSet id="003-004-create-doc-embedding-table" author="sistema">
        <createTable tableName="doc_embedding">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="biblioteca_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_embedding_biblioteca" 
                           references="biblioteca(id)"/>
            </column>
            <column name="documento_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_embedding_documento" 
                           references="documento(id)"/>
            </column>
            <column name="capitulo_id" type="BIGINT">
                <constraints foreignKeyName="fk_embedding_capitulo" 
                           references="capitulo(id)"/>
            </column>
            <column name="tipo_embedding" type="tipo_embedding">
                <constraints nullable="false"/>
            </column>
            <column name="trecho_texto" type="TEXT"/>
            <column name="ordem_cap" type="INTEGER"/>
            <column name="embedding_vector" type="vector(1536)"/>
            <column name="texto_indexado" type="tsvector"/>
            <column name="metadados" type="JSONB"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

## 14. Documentação da API (Exemplos de Uso)

### 14.1 Exemplos de Requests

```json
// POST /api/v1/bibliotecas
{
  "nome": "Engenharia de Software",
  "areaConhecimento": "Tecnologia",
  "pesoSemantico": 0.60,
  "pesoTextual": 0.40,
  "metadados": {
    "curador": "João Silva",
    "instituicao": "UFMG",
    "descricao": "Biblioteca focada em engenharia de software"
  }
}

// POST /api/v1/documentos
{
  "bibliotecaId": 1,
  "titulo": "Clean Code: A Handbook of Agile Software Craftsmanship",
  "conteudoMarkdown": "# Clean Code\n\n## Introdução\n\nEste livro ensina...",
  "flagVigente": true,
  "dataPublicacao": "2023-01-15",
  "metadados": {
    "tipo_conteudo": "1",
    "autor": "Robert C. Martin",
    "isbn": "978-0132350884",
    "palavras_chave": "clean code,software craftsmanship,agile"
  }
}

// POST /api/v1/pesquisa
{
  "query": "princípios de clean code",
  "bibliotecaId": 1,
  "apenasVigentes": true,
  "limite": 10,
  "pesoSemanticoCustom": 0.7
}
```

### 14.2 Exemplos de Responses

```json
// Resposta de pesquisa
{
  "resultados": [
    {
      "embeddingId": 123,
      "score": 0.85,
      "biblioteca": "Engenharia de Software",
      "documento": "Clean Code: A Handbook of Agile Software Craftsmanship",
      "capitulo": "Capítulo 2: Nomes Significativos",
      "tipoEmbedding": "TRECHO",
      "texto": "Escolher bons nomes leva tempo, mas economiza mais tempo ainda...",
      "metadados": {
        "tipo_conteudo": "1",
        "autor": "Robert C. Martin",
        "tokens": "350"
      }
    }
  ]
}

// Resposta de erro
{
  "codigo": "ENTITY_NOT_FOUND",
  "mensagem": "Biblioteca não encontrada: 999",
  "timestamp": "2023-12-15T10:30:00",
  "detalhes": null
}
```

## 15. Deploy e Monitoramento

### 15.1 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg15
    environment:
      POSTGRES_DB: rag_db
      POSTGRES_USER: rag_user
      POSTGRES_PASSWORD: rag_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init.sql:/docker-entrypoint-initdb.d/init.sql

  rag-api:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: rag_db
      DB_USERNAME: rag_user
      DB_PASSWORD: rag_pass
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on:
      - postgres
    volumes:
      - ./logs:/app/logs

volumes:
  postgres_data:
```

### 15.2 Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/rag-hierarquico-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 15.3 Métricas e Health Check

```java
// HealthController.java
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class HealthController {
    
    private final BibliotecaRepository bibliotecaRepository;
    private final DataSource dataSource;
    
    @GetMapping("/health/database")
    public ResponseEntity<Map<String, String>> healthDatabase() {
        try {
            bibliotecaRepository.count();
            return ResponseEntity.ok(Map.of("status", "UP", "database", "connected"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "database", "disconnected", "error", e.getMessage()));
        }
    }
    
    @GetMapping("/metrics/sistema")
    public ResponseEntity<Map<String, Object>> metricas() {
        Map<String, Object> metricas = new HashMap<>();
        
        metricas.put("total_bibliotecas", bibliotecaRepository.count());
        metricas.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(metricas);
    }
}
```

## 16. Considerações de Performance

### 16.1 Índices Adicionais

```sql
-- Índices para otimização de consultas específicas
CREATE INDEX CONCURRENTLY idx_embedding_vector_gin ON doc_embedding USING gin(embedding_vector);
CREATE INDEX CONCURRENTLY idx_documento_vigente_biblioteca ON documento(biblioteca_id, flag_vigente, data_publicacao DESC);
CREATE INDEX CONCURRENTLY idx_embedding_tipo_biblioteca ON doc_embedding(biblioteca_id, tipo_embedding);

-- Índice parcial para documentos vigentes
CREATE INDEX CONCURRENTLY idx_documento_vigente_only ON documento(biblioteca_id, data_publicacao DESC) 
WHERE flag_vigente = true;
```

### 16.2 Configurações de Pool de Conexão

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      pool-name: RagHikariPool
```

## 17. Próximos Passos

### 17.1 Funcionalidades Futuras
- Cache Redis para resultados de pesquisa
- Reranking de resultados com modelos específicos
- Interface web para administração
- Bulk upload de documentos
- Versionamento automático via Git
- Métricas detalhadas com Prometheus
- Suporte a múltiplos idiomas

### 17.2 Melhorias de Performance
- Implementação de cache L1/L2
- Otimização de queries com EXPLAIN ANALYZE
- Implementação de read replicas
- Compressão de embeddings
- Lazy loading otimizado

---

**Fim da Especificação Técnica**

Esta especificação fornece uma base sólida para implementação do sistema RAG hierárquico, seguindo as melhores práticas de Spring Boot e design patterns adequados para escalabilidade e manutenibilidade.Status.CREATED).body(criada);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Buscar biblioteca por ID")
    public ResponseEntity<BibliotecaDTO> buscarPorId(@PathVariable Long id) {
        BibliotecaDTO biblioteca = bibliotecaService.buscarPorId(id);
        return ResponseEntity.ok(biblioteca);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar biblioteca")
    public ResponseEntity<BibliotecaDTO> atualizar(@PathVariable Long id, @Valid @RequestBody BibliotecaDTO dto) {
        BibliotecaDTO atualizada = bibliotecaService.atualizar(id, dto);
        return ResponseEntity.ok(atualizada);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir biblioteca")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        bibliotecaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping
    @Operation(summary = "Listar todas as bibliotecas")
    public ResponseEntity<List<BibliotecaDTO>> listarTodas() {
        List<BibliotecaDTO> bibliotecas = bibliotecaService.listarTodas();
        return ResponseEntity.ok(bibliotecas);
    }
}

// DocumentoController.java  
@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
@Tag(name = "Documento", description = "Operações com documentos")
public class DocumentoController {
    
    private final DocumentoService documentoService;
    
    @PostMapping
    @Operation(summary = "Criar documento")
    public ResponseEntity<DocumentoDTO> criar(@Valid @RequestBody DocumentoDTO dto) {
        DocumentoDTO criado = documentoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Buscar documento por ID")
    public ResponseEntity<DocumentoDTO> buscarPorId(@PathVariable Long id) {
        DocumentoDTO documento = documentoService.buscarPorId(id);
        return ResponseEntity.ok(documento);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar documento")
    public ResponseEntity<DocumentoDTO> atualizar(@PathVariable Long id, @Valid @RequestBody DocumentoDTO dto) {
        DocumentoDTO atualizado = documentoService.atualizar(id, dto);
        return ResponseEntity.ok(atualizado);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir documento")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        documentoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/processar")
    @Operation(summary = "Processar documento para gerar embeddings")
    public ResponseEntity<Void> processar(@PathVariable Long id) {
        documentoService.processarAssincrono(id);
        return ResponseEntity.accepted().build();
    }
}

// CapituloController.java
@RestController
@RequestMapping("/api/v1/capitulos")
@RequiredArgsConstructor
@Tag(name = "Capítulo", description = "Operações com capítulos")
public class CapituloController {
    
    private final CapituloService capituloService;
    
    @PostMapping
    @Operation(summary = "Criar capítulo")
    public ResponseEntity<CapituloDTO> criar(@Valid @RequestBody CapituloDTO dto) {
        CapituloDTO criado = capituloService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Buscar capítulo por ID")
    public ResponseEntity<CapituloDTO> buscarPorId(@PathVariable Long id) {
        CapituloDTO capitulo = capituloService.buscarPorId(id);
        return ResponseEntity.ok(capitulo);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar capítulo")
    public ResponseEntity<CapituloDTO> atualizar(@PathVariable Long id, @Valid @RequestBody CapituloDTO dto) {
        CapituloDTO atualizado = capituloService.atualizar(id, dto);
        return ResponseEntity.ok(atualizado);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir capítulo")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        capituloService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}

// DocEmbeddingController.java
@RestController
@RequestMapping("/api/v1/embeddings")
@RequiredArgsConstructor
@Tag(name = "DocEmbedding", description = "Operações com embeddings")
public class DocEmbeddingController {
    
    private final DocEmbeddingService embeddingService;
    
    @PostMapping
    @Operation(summary = "Criar embedding")
    public ResponseEntity<DocEmbeddingDTO> criar(@Valid @RequestBody DocEmbeddingDTO dto) {
        DocEmbeddingDTO criado = embeddingService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Buscar embedding por ID")
    public ResponseEntity<DocEmbeddingDTO> buscarPorId(@PathVariable Long id) {
        DocEmbeddingDTO embedding = embeddingService.buscarPorId(id);
        return ResponseEntity.ok(embedding);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar embedding")
    public ResponseEntity<DocEmbeddingDTO> atualizar(@PathVariable Long id, @Valid @RequestBody DocEmbeddingDTO dto) {
        DocEmbeddingDTO atualizado = embeddingService.atualizar(id, dto);
        return ResponseEntity.ok(atualizado);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir embedding")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        embeddingService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}

// PesquisaController.java
@RestController
@RequestMapping("/api/v1/pesquisa")
@RequiredArgsConstructor
@Tag(name = "Pesquisa", description = "Operações de pesquisa híbrida")
public class PesquisaController {
    
    private final PesquisaService pesquisaService;
    
    @PostMapping
    @Operation(summary = "Realizar pesquisa híbrida")
    public ResponseEntity<List<ResultadoPesquisaDTO>> pesquisar(@Valid @RequestBody PesquisaDTO dto) {
        List<ResultadoPesquisaDTO> resultados = pesquisaService.pesquisarHibrida(dto);
        return ResponseEntity.ok(resultados);
    }
    
    @PostMapping("/semantica")
    @Operation(summary = "Pesquisa apenas semântica")
    public ResponseEntity<List<ResultadoPesquisaDTO>> pesquisaSemantica(@Valid @RequestBody PesquisaDTO dto) {
        List<ResultadoPesquisaDTO> resultados = pesquisaService.pesquisaSemantica(dto);
        return ResponseEntity.ok(resultados);
    }
    
    @PostMapping("/textual")
    @Operation(summary = "Pesquisa apenas textual")
    public ResponseEntity<List<ResultadoPesquisaDTO>> pesquisaTextual(@Valid @RequestBody PesquisaDTO dto) {
        List<ResultadoPesquisaDTO> resultados = pesquisaService.pesquisaTextual(dto);
        return ResponseEntity.ok(resultados);
    }
}
```

## 6. Configuração do Projeto

### 6.1 application.yml

```yaml
spring:
  application:
    name: rag-hierarquico
  
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: ${DB_USERNAME:rag_user}
    password: ${DB_PASSWORD:rag_pass}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

# Configurações do RAG
rag:
  embedding:
    model: "text-embedding-ada-002"  # ou outro modelo
    dimensoes: 1536
    batch-size: 100
  
  processamento:
    chunk-size-maximo: 2000
    capitulo-size-padrao: 8000
    async:
      core-pool-size: 2
      max-pool-size: 5
      queue-capacity: 100

# OpenAPI/Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method

# Logging
logging:
  level:
    com.rag.hierarquico: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
```

### 6.2 pom.xml (dependências principais)

```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- OpenAPI/Swagger -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.2.0</version>
    </dependency>
    
    <!-- JSON Support -->
    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-62</artifactId>
        <version>3.5.1</version>
    </dependency>
    
    <!-- Liquibase -->
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>
    
    <!-- MapStruct -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>1.5.5.Final</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Test Dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## 7. Services (Camada de Negócio)

### 7.1 BibliotecaService

```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BibliotecaService {
    
    private final BibliotecaRepository bibliotecaRepository;
    private final BibliotecaMapper bibliotecaMapper;
    
    public BibliotecaDTO criar(BibliotecaDTO dto) {
        log.info("Criando nova biblioteca: {}", dto.getNome());
        
        validarPesos(dto.getPesoSemantico(), dto.getPesoTextual());
        
        Biblioteca biblioteca = bibliotecaMapper.toEntity(dto);
        Biblioteca salva = bibliotecaRepository.save(biblioteca);
        
        log.info("Biblioteca criada com ID: {}", salva.getId());
        return bibliotecaMapper.toDto(salva);
    }
    
    @Transactional(readOnly = true)
    public BibliotecaDTO buscarPorId(Long id) {
        Biblioteca biblioteca = bibliotecaRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + id));
        
        return bibliotecaMapper.toDto(biblioteca);
    }
    
    public BibliotecaDTO atualizar(Long id, BibliotecaDTO dto) {
        log.info("Atualizando biblioteca ID: {}", id);
        
        Biblioteca biblioteca = bibliotecaRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + id));
        
        validarPesos(dto.getPesoSemantico(), dto.getPesoTextual());
        
        bibliotecaMapper.updateEntityFromDto(dto, biblioteca);
        Biblioteca atualizada = bibliotecaRepository.save(biblioteca);
        
        return bibliotecaMapper.toDto(atualizada);
    }
    
    public void excluir(Long id) {
        log.info("Excluindo biblioteca ID: {}", id);
        
        if (!bibliotecaRepository.existsById(id)) {
            throw new EntityNotFoundException("Biblioteca não encontrada: " + id);
        }
        
        bibliotecaRepository.deleteById(id);
    }
    
    @Transactional(readOnly = true)
    public List<BibliotecaDTO> listarTodas() {
        List<Biblioteca> bibliotecas = bibliotecaRepository.findAllByOrderByNome();
        return bibliotecaMapper.toDtoList(bibliotecas);
    }
    
    private void validarPesos(BigDecimal pesoSemantico, BigDecimal pesoTextual) {
        if (pesoSemantico != null && pesoTextual != null) {
            BigDecimal soma = pesoSemantico.add(pesoTextual);
            if (soma.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("A soma dos pesos deve ser igual a 1.0");
            }
        }
    }
}

// DocumentoService.java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DocumentoService {
    
    private final DocumentoRepository documentoRepository;
    private final BibliotecaRepository bibliotecaRepository;
    private final DocumentoMapper documentoMapper;
    private final ProcessamentoAssincrono processamentoAssincrono;
    
    public DocumentoDTO criar(DocumentoDTO dto) {
        log.info("Criando novo documento: {}", dto.getTitulo());
        
        Biblioteca biblioteca = bibliotecaRepository.findById(dto.getBibliotecaId())
            .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + dto.getBibliotecaId()));
        
        // Marcar outros documentos como não vigentes se este for vigente
        if (Boolean.TRUE.equals(dto.getFlagVigente())) {
            documentoRepository.marcarTodosComoNaoVigentes(dto.getBibliotecaId(), dto.getTitulo());
        }
        
        Documento documento = documentoMapper.toEntity(dto);
        documento.setBiblioteca(biblioteca);
        
        // Calcular tokens (implementar conforme tokenizer escolhido)
        documento.setTokensTotal(calcularTokens(dto.getConteudoMarkdown()));
        
        Documento salvo = documentoRepository.save(documento);
        
        log.info("Documento criado com ID: {}", salvo.getId());
        return documentoMapper.toDto(salvo);
    }
    
    @Transactional(readOnly = true)
    public DocumentoDTO buscarPorId(Long id) {
        Documento documento = documentoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado: " + id));
        
        return documentoMapper.toDto(documento);
    }
    
    public DocumentoDTO atualizar(Long id, DocumentoDTO dto) {
        log.info("Atualizando documento ID: {}", id);
        
        Documento documento = documentoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado: " + id));
        
        // Verificar se biblioteca existe
        if (!documento.getBiblioteca().getId().equals(dto.getBibliotecaId())) {
            Biblioteca biblioteca = bibliotecaRepository.findById(dto.getBibliotecaId())
                .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + dto.getBibliotecaId()));
            documento.setBiblioteca(biblioteca);
        }
        
        documentoMapper.updateEntityFromDto(dto, documento);
        documento.setTokensTotal(calcularTokens(dto.getConteudoMarkdown()));
        
        Documento atualizado = documentoRepository.save(documento);
        
        return documentoMapper.toDto(atualizado);
    }
    
    public void excluir(Long id) {
        log.info("Excluindo documento ID: {}", id);
        
        if (!documentoRepository.existsById(id)) {
            throw new EntityNotFoundException("Documento não encontrado: " + id);
        }
        
        documentoRepository.deleteById(id);
    }
    
    @Async
    public void processarAssincrono(Long documentoId) {
        log.info("Iniciando processamento assíncrono do documento ID: {}", documentoId);
        processamentoAssincrono.processarDocumento(documentoId);
    }
    
    private Integer calcularTokens(String texto) {
        // Implementar tokenização conforme modelo escolhido
        // Aproximação simples: 1 token ≈ 4 caracteres
        return texto.length() / 4;
    }
}
```

### 7.2 Serviços de Processamento

```java
// ProcessamentoAssincrono.java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessamentoAssincrono {
    
    private final DocumentoRepository documentoRepository;
    private final CapituloService capituloService;
    private final DocEmbeddingService embeddingService;
    private final EmbeddingGenerator embeddingGenerator;
    private final TextSplitter textSplitter;
    
    @Async
    @Transactional
    public void processarDocumento(Long documentoId) {
        try {
            log.info("Processando documento ID: {}", documentoId);
            
            Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado: " + documentoId));
            
            // 1. Criar embedding do documento completo
            criarEmbeddingDocumento(documento);
            
            // 2. Dividir em capítulos se necessário
            List<Capitulo> capitulos = criarCapitulos(documento);
            
            // 3. Criar embeddings dos capítulos
            for (Capitulo capitulo : capitulos) {
                criarEmbeddingCapitulo(capitulo);
                
                // 4. Dividir capítulo em trechos e criar embeddings
                criarEmbeddingsTrechos(capitulo);
            }
            
            log.info("Processamento concluído para documento ID: {}", documentoId);
            
        } catch (Exception e) {
            log.error("Erro no processamento do documento ID: {}", documentoId, e);
            throw new ProcessamentoException("Falha no processamento do documento: " + documentoId, e);
        }
    }
    
    private void criarEmbeddingDocumento(Documento documento) {
        log.debug("Criando embedding do documento completo");
        
        float[] embedding = embeddingGenerator.gerarEmbedding(documento.getConteudoMarkdown());
        
        DocEmbedding docEmbedding = DocEmbedding.builder()
            .biblioteca(documento.getBiblioteca())
            .documento(documento)
            .tipoEmbedding(TipoEmbedding.DOCUMENTO)
            .trechoTexto(documento.getConteudoMarkdown())
            .embeddingVector(embedding)
            .textoIndexado(gerarTsVector(documento.getConteudoMarkdown()))
            .metadados(documento.getMetadados())
            .build();
        
        embeddingService.salvarEmbedding(docEmbedding);
    }
    
    private List<Capitulo> criarCapitulos(Documento documento) {
        log.debug("Criando capítulos para documento ID: {}", documento.getId());
        
        List<TextChunk> chunks = textSplitter.dividirEmCapitulos(
            documento.getConteudoMarkdown(), 
            8000 // Tamanho padrão
        );
        
        List<Capitulo> capitulos = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            
            Capitulo capitulo = Capitulo.builder()
                .documento(documento)
                .titulo(gerarTituloCapitulo(chunk, i + 1))
                .conteudo(chunk.getTexto())
                .ordemDoc(i + 1)
                .tokenInicio(chunk.getTokenInicio())
                .tokenFim(chunk.getTokenFim())
                .tokensTotal(chunk.getTokenFim() - chunk.getTokenInicio())
                .metadados(Map.of("auto_gerado", "true"))
                .build();
            
            capitulos.add(capituloService.salvarCapitulo(capitulo));
        }
        
        return capitulos;
    }
    
    private void criarEmbeddingCapitulo(Capitulo capitulo) {
        log.debug("Criando embedding do capítulo ID: {}", capitulo.getId());
        
        float[] embedding = embeddingGenerator.gerarEmbedding(capitulo.getConteudo());
        
        DocEmbedding capituloEmbedding = DocEmbedding.builder()
            .biblioteca(capitulo.getDocumento().getBiblioteca())
            .documento(capitulo.getDocumento())
            .capitulo(capitulo)
            .tipoEmbedding(TipoEmbedding.CAPITULO)
            .trechoTexto(capitulo.getConteudo())
            .embeddingVector(embedding)
            .textoIndexado(gerarTsVector(capitulo.getConteudo()))
            .metadados(capitulo.getMetadados())
            .build();
        
        embeddingService.salvarEmbedding(capituloEmbedding);
    }
    
    private void criarEmbeddingsTrechos(Capitulo capitulo) {
        log.debug("Criando embeddings dos trechos para capítulo ID: {}", capitulo.getId());
        
        List<TextChunk> trechos = textSplitter.dividirEmTrechos(
            capitulo.getConteudo(), 
            2000 // Tamanho máximo do trecho
        );
        
        for (int i = 0; i < trechos.size(); i++) {
            TextChunk trecho = trechos.get(i);
            
            float[] embedding = embeddingGenerator.gerarEmbedding(trecho.getTexto());
            
            DocEmbedding trechoEmbedding = DocEmbedding.builder()
                .biblioteca(capitulo.getDocumento().getBiblioteca())
                .documento(capitulo.getDocumento())
                .capitulo(capitulo)
                .tipoEmbedding(TipoEmbedding.TRECHO)
                .trechoTexto(trecho.getTexto())
                .ordemCap(i + 1)
                .embeddingVector(embedding)
                .textoIndexado(gerarTsVector(trecho.getTexto()))
                .metadados(Map.of("tokens", String.valueOf(trecho.getTokenFim() - trecho.getTokenInicio())))
                .build();
            
            embeddingService.salvarEmbedding(trechoEmbedding);
        }
    }
    
    private String gerarTituloCapitulo(TextChunk chunk, int numero) {
        // Implementar lógica para extrair título do chunk ou gerar automaticamente
        String primeiraLinha = chunk.getTexto().split("\n")[0];
        if (primeiraLinha.length() > 100) {
            primeiraLinha = primeiraLinha.substring(0, 100) + "...";
        }
        return "Capítulo " + numero + ": " + primeiraLinha;
    }
    
    private String gerarTsVector(String texto) {
        // Implementar geração de tsvector para busca textual
        return texto.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", " ");
    }
}

// PesquisaService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class PesquisaService {
    
    private final DocEmbeddingRepository embeddingRepository;
    private final BibliotecaRepository bibliotecaRepository;
    private final EmbeddingGenerator embeddingGenerator;
    private final ResultadoPesquisaMapper resultadoMapper;
    
    @Transactional(readOnly = true)
    public List<ResultadoPesquisaDTO> pesquisarHibrida(PesquisaDTO pesquisaDto) {
        log.info("Realizando pesquisa híbrida para: {}", pesquisaDto.getQuery());
        
        // Gerar embedding da query
        float[] queryEmbedding = embeddingGenerator.gerarEmbedding(pesquisaDto.getQuery());
        
        // Buscar biblioteca e pesos
        Biblioteca biblioteca = null;
        BigDecimal pesoSemantico = pesquisaDto.getPesoSemanticoCustom();
        BigDecimal pesoTextual = null;
        
        if (pesquisaDto.getBibliotecaId() != null) {
            biblioteca = bibliotecaRepository.findById(pesquisaDto.getBibliotecaId())
                .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada"));
            
            if (pesoSemantico == null) {
                pesoSemantico = biblioteca.getPesoSemantico();
                pesoTextual = biblioteca.getPesoTextual();
            } else {
                pesoTextual = BigDecimal.ONE.subtract(pesoSemantico);
            }
        } else {
            // Usar pesos padrão se não especificado
            pesoSemantico = pesoSemantico != null ? pesoSemantico : new BigDecimal("0.60");
            pesoTextual = BigDecimal.ONE.subtract(pesoSemantico);
        }
        
        // Executar pesquisa híbrida
        List<DocEmbedding> resultados = embeddingRepository.pesquisaHibrida(
            queryEmbedding,
            pesquisaDto.getQuery(),
            pesquisaDto.getBibliotecaId(),
            pesquisaDto.getApenasVigentes(),
            pesoSemantico.doubleValue(),
            pesoTextual.doubleValue(),
            pesquisaDto.getLimite()
        );
        
        return resultadoMapper.toDtoList(resultados);
    }
    
    @Transactional(readOnly = true)
    public List<ResultadoPesquisaDTO> pesquisaSemantica(PesquisaDTO pesquisaDto) {
        log.info("Realizando pesquisa semântica para: {}", pesquisaDto.getQuery());
        
        float[] queryEmbedding = embeddingGenerator.gerarEmbedding(pesquisaDto.getQuery());
        
        List<DocEmbedding> resultados = embeddingRepository.pesquisaSemantica(
            queryEmbedding,
            pesquisaDto.getBibliotecaId(),
            pesquisaDto.getApenasVigentes(),
            pesquisaDto.getLimite()
        );
        
        return resultadoMapper.toDtoList(resultados);
    }
    
    @Transactional(readOnly = true)
    public List<ResultadoPesquisaDTO> pesquisaTextual(PesquisaDTO pesquisaDto) {
        log.info("Realizando pesquisa textual para: {}", pesquisaDto.getQuery());
        
        List<DocEmbedding> resultados = embeddingRepository.pesquisaTextual(
            pesquisaDto.getQuery(),
            pesquisaDto.getBibliotecaId(),
            pesquisaDto.getApenasVigentes(),
            pesquisaDto.getLimite()
        );
        
        return resultadoMapper.toDtoList(resultados);
    }
}
```

## 8. Repositories (Acesso a Dados)

```java
// BibliotecaRepository.java
@Repository
public interface BibliotecaRepository extends JpaRepository<Biblioteca, Long> {
    
    List<Biblioteca> findAllByOrderByNome();
    
    List<Biblioteca> findByAreaConhecimentoContainingIgnoreCase(String areaConhecimento);
    
    Optional<Biblioteca> findByNomeIgnoreCase(String nome);
}

// DocumentoRepository.java
@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    
    List<Documento> findByBibliotecaIdOrderByDataPublicacaoDesc(Long bibliotecaId);
    
    List<Documento> findByFlagVigenteTrue();
    
    @Query("SELECT d FROM Documento d WHERE d.biblioteca.id = :bibliotecaId AND d.titulo ILIKE %:titulo%")
    List<Documento> findByBibliotecaAndTituloContaining(@Param("bibliotecaId") Long bibliotecaId, 
                                                        @Param("titulo") String titulo);
    
    @Modifying
    @Query("UPDATE Documento SET flagVigente = false WHERE biblioteca.id = :bibliotecaId AND titulo = :titulo")
    void marcarTodosComoNaoVigentes(@Param("bibliotecaId") Long bibliotecaId, @Param("titulo") String titulo);
}

// CapituloRepository.java
@Repository
public interface CapituloRepository extends JpaRepository<Capitulo, Long> {
    
    List<Capitulo> findByDocumentoIdOrderByOrdemDoc(Long documentoId);
    
    @Query("SELECT c FROM Capitulo c WHERE c.documento.id = :documentoId AND c.ordemDoc BETWEEN :ordemInicio AND :ordemFim")
    List<Capitulo> findByDocumentoAndOrdemRange(@Param("documentoId") Long documentoId,
                                               @Param("ordemInicio") Integer ordemInicio,
                                               @Param("ordemFim") Integer ordemFim);
}

// DocEmbeddingRepository.java
@Repository
public interface DocEmbeddingRepository extends JpaRepository<DocEmbedding, Long> {
    
    List<DocEmbedding> findByBibliotecaIdAndTipoEmbedding(Long bibliotecaId, TipoEmbedding tipo);
    
    List<DocEmbedding> findByDocumentoIdOrderByTipoEmbeddingAscOrdemCapAsc(Long documentoId);
    
    // Pesquisa híbrida usando query nativa para PGVector
    @Query(value = """
        SELECT e.*, 
               ((:pesoSemantico * (1 - (e.embedding_vector <=> CAST(:queryEmbedding AS vector))) + 
                 :pesoTextual * ts_rank_cd(e.texto_indexado, plainto_tsquery(:queryText)))) as score
        FROM doc_embedding e 
        JOIN documento d ON e.documento_id = d.id 
        WHERE (:bibliotecaId IS NULL OR e.biblioteca_id = :bibliotecaId)
          AND (:apenasVigentes = false OR d.flag_vigente = true)
        ORDER BY score DESC 
        LIMIT :limite
        """, nativeQuery = true)
    List<DocEmbedding> pesquisaHibrida(@Param("queryEmbedding") float[] queryEmbedding,
                                      @Param("queryText") String queryText,
                                      @Param("bibliotecaId") Long bibliotecaId,
                                      @Param("apenasVigentes") Boolean apenasVigentes,
                                      @Param("pesoSemantico") Double pesoSemantico,
                                      @Param("pesoTextual") Double pesoTextual,
                                      @Param("limite") Integer limite);
    
    // Pesquisa apenas semântica
    @Query(value = """
        SELECT e.*, (1 - (e.embedding_vector <=> CAST(:queryEmbedding AS vector))) as score
        FROM doc_embedding e 
        JOIN documento d ON e.documento_id = d.id 
        WHERE (:bibliotecaId IS NULL OR e.biblioteca_id = :bibliotecaId)
          AND (:apenasVigentes = false OR d.flag_vigente = true)
        ORDER BY e.embedding_vector <=> CAST(:queryEmbedding AS vector)
        LIMIT :limite
        """, nativeQuery = true)
    List<DocEmbedding> pesquisaSemantica(@Param("queryEmbedding") float[] queryEmbedding,
                                        @Param("bibliotecaId") Long bibliotecaId,
                                        @Param("apenasVigentes") Boolean apenasVigentes,
                                        @Param("limite") Integer limite);
    
    // Pesquisa apenas textual
    @Query(value = """
        SELECT e.*, ts_rank_cd(e.texto_indexado, plainto_tsquery(:queryText)) as score
        FROM doc_embedding e 
        JOIN documento d ON e.documento_id = d.id 
        WHERE (:bibliotecaId IS NULL OR e.biblioteca_id = :bibliotecaId)
          AND (:apenasVigentes = false OR d.flag_vigente = true)
          AND e.texto_indexado @@ plainto_tsquery(:queryText)
        ORDER BY score DESC 
        LIMIT :limite
        """, nativeQuery = true)
    List<DocEmbedding> pesquisaTextual(@Param("queryText") String queryText,
                                      @Param("bibliotecaId") Long bibliotecaId,
                                      @Param("apenasVigentes") Boolean apenasVigentes,
                                      @Param("limite") Integer limite);
}
```

## 9. Mappers (MapStruct)

```java
// BibliotecaMapper.java
@Mapper(componentModel = "spring")
public interface BibliotecaMapper {
    
    BibliotecaDTO toDto(Biblioteca entity);
    
    Biblioteca toEntity(BibliotecaDTO dto);
    
    List<BibliotecaDTO> toDtoList(List<Biblioteca> entities);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "documentos", ignore = true)
    void updateEntityFromDto(BibliotecaDTO dto, @MappingTarget Biblioteca entity);
}

// DocumentoMapper.java
@Mapper(componentModel = "spring")
public interface DocumentoMapper {
    
    @Mapping(source = "biblioteca.id", target = "bibliotecaId")
    DocumentoDTO toDto(Documento entity);
    
    @Mapping(target = "biblioteca", ignore = true)
    @Mapping(target = "capitulos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Documento toEntity(DocumentoDTO dto);
    
    List<DocumentoDTO> toDtoList(List<Documento> entities);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "biblioteca", ignore = true)
    @Mapping(target = "capitulos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(DocumentoDTO dto, @MappingTarget Documento entity);
}
```

## 10. Configurações e Utilitários

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "ragTaskExecutor")
    public TaskExecutor ragTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("RAG-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

// TextSplitter.java
@Component
@Slf4j
public class TextSplitter {
    
    public List<TextChunk> dividirEmCapitulos(String texto, int tamanhoMaximo) {
        // Implementar lógica de divisão inteligente
        // Considerando quebras naturais (parágrafos, seções, etc.)
        return dividirTexto(texto, tamanhoMaximo, true);
    }
    
    public List<TextChunk> dividirEmTrechos(String texto, int tamanhoMaximo) {
        return dividirTexto(texto, tamanhoMaximo, false);
    }
    
    private List<TextChunk> dividirTexto(String texto, int tamanhoMaximo, boolean manterSeções) {
        List<TextChunk> chunks = new ArrayList<>();
        
        // Implementação simplificada - dividir por tokens
        String[] palavras = texto.split("\\s+");
        
        StringBuilder chunkAtual = new StringBuilder();
        int tokenInicio = 0;
        int tokenAtual = 0;
        
        for (String palavra : palavras) {
            if (chunkAtual.length() + palavra.length() + 1 > tamanhoMaximo * 4) { // Aproximação: 1 token = 4 chars
                // Criar chunk
                chunks.add(new TextChunk(chunkAtual.toString().trim(), tokenInicio, tokenAtual));
                
                // Reiniciar
                chunkAtual = new StringBuilder();
                tokenInicio = tokenAtual;
            }
            
            chunkAtual.append(palavra).append(" ");
            tokenAtual++;
        }
        
        // Adicionar último chunk
        if (chunkAtual.length() > 0) {
            chunks.add(new TextChunk(chunkAtual.toString().trim(), tokenInicio, tokenAtual));
        }
        
        return chunks;
    }
}

// TextChunk.java
@Data
@AllArgsConstructor
public class TextChunk {
    private String texto;
    private int tokenInicio;
    private int tokenFim;
}

// EmbeddingGenerator.java (Interface para geração de embeddings)
@Component
public interface EmbeddingGenerator {
    float[] gerarEmbedding(String texto);
    List<float[]> gerarEmbeddings(List<String> textos);
}

// Implementação exemplo com OpenAI
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIEmbeddingGenerator implements EmbeddingGenerator {
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.embedding.model:text-embedding-ada-002}")
    private String model;
    
    @Override
    public float[] gerarEmbedding(String texto) {
        // Implementar chamada para API OpenAI
        // Por enquanto, retorna array dummy
        log.debug("Gerando embedding para texto de {} caracteres", texto.length());
        
        // TODO: Implementar integração real com OpenAI
        return new float[1536]; // Dimensão do ada-002
    }
    
    @Override
    public List<float[]> gerarEmbeddings(List<String> textos) {
        return textos.stream()
            .map(this::gerarEmbedding)
            .collect(Collectors.toList());
    }
}
```

## 11. Tratamento de Exceções

```java
// GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entidade não encontrada: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .codigo("ENTITY_NOT_FOUND")
            .mensagem(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .codigo("INVALID_ARGUMENT")
            .mensagem(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(Http