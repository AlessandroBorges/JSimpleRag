package bor.tools.splitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import bor.tools.simplerag.entity.enums.TipoConteudo;
import lombok.Data;

/**
 * Configuração centralizada para parâmetros de Splitters por biblioteca.
 *
 * Permite configuração específica de chunk sizes, modelos preferenciais,
 * e outros parâmetros por biblioteca ou tipo de conteúdo.
 */
@Component
@ConfigurationProperties(prefix = "rag.splitter")
public class SplitterConfig {

    private static final Logger logger = LoggerFactory.getLogger(SplitterConfig.class);

    /**
     * Configurações padrão do sistema
     */
    private DefaultConfig defaults = new DefaultConfig();

    /**
     * Configurações específicas por biblioteca (ID da biblioteca → configuração)
     */
    private final Map<String, BibliotecaConfig> bibliotecas = new ConcurrentHashMap<>();

    /**
     * Configurações específicas por tipo de conteúdo
     */
    private final Map<TipoConteudo, ContentTypeConfig> contentTypes = new ConcurrentHashMap<>();

    /**
     * Configurações padrão do sistema
     */
    @Data
    public static class DefaultConfig {
        private int maxChunkSize = 2000;
        private int minChunkSize = 100;
        private String preferredModel = "default";
        private boolean enableLlmFallback = true;
        private int maxSummaryLength = 500;
        private int defaultQaCount = 3;
        private double tokenEstimationRatio = 0.75;
    }

    /**
     * Configuração específica por biblioteca
     */
    @Data
    public static class BibliotecaConfig {
        private String bibliotecaId;
        private UUID   bibliotecaUUID;
        private String bibliotecaNome;
        private Integer maxChunkSize;
        private Integer minChunkSize;
        private String preferredModel;
        private Boolean enableLlmFallback;
        private Integer maxSummaryLength;
        private Integer defaultQaCount;
        private Double tokenEstimationRatio;
        private TipoConteudo defaultContentType;
        private Map<String, String> customSettings = new ConcurrentHashMap<>();
    }

    /**
     * Configuração específica por tipo de conteúdo
     */
    @Data
    public static class ContentTypeConfig {
        private TipoConteudo tipoConteudo;
        private Integer preferredChunkSize;
        private String splitterClass;
        private Boolean enableQaGeneration;
        private Boolean enableSummarization;
        private Map<String, Object> typeSpecificSettings = new ConcurrentHashMap<>();
    }

    /**
     * Construtor padrão com configurações iniciais
     */
    public SplitterConfig() {
        initializeDefaultContentTypeConfigs();
        logger.debug("SplitterConfig initialized with default settings");
    }

    /**
     * Inicializa configurações padrão por tipo de conteúdo
     */
    private void initializeDefaultContentTypeConfigs() {
        // Configuração para normativos
        ContentTypeConfig normativoConfig = new ContentTypeConfig();
        normativoConfig.setTipoConteudo(TipoConteudo.NORMATIVO);
        normativoConfig.setPreferredChunkSize(1500); // Menor para capturar artigos completos
        normativoConfig.setSplitterClass("SplitterNorma");
        normativoConfig.setEnableQaGeneration(true);
        normativoConfig.setEnableSummarization(true);
        contentTypes.put(TipoConteudo.NORMATIVO, normativoConfig);

        // Configuração para livros
        ContentTypeConfig livroConfig = new ContentTypeConfig();
        livroConfig.setTipoConteudo(TipoConteudo.LIVRO);
        livroConfig.setPreferredChunkSize(2500); // Maior para narrativa contínua
        livroConfig.setSplitterClass("SplitterGenerico");
        livroConfig.setEnableQaGeneration(true);
        livroConfig.setEnableSummarization(true);
        contentTypes.put(TipoConteudo.LIVRO, livroConfig);

        // Configuração para artigos
        ContentTypeConfig artigoConfig = new ContentTypeConfig();
        artigoConfig.setTipoConteudo(TipoConteudo.ARTIGO);
        artigoConfig.setPreferredChunkSize(2000); // Padrão
        artigoConfig.setSplitterClass("SplitterWiki");
        artigoConfig.setEnableQaGeneration(true);
        artigoConfig.setEnableSummarization(true);
        contentTypes.put(TipoConteudo.ARTIGO, artigoConfig);

        // Configuração para manuais
        ContentTypeConfig manualConfig = new ContentTypeConfig();
        manualConfig.setTipoConteudo(TipoConteudo.MANUAL);
        manualConfig.setPreferredChunkSize(1800); // Menor para procedimentos
        manualConfig.setSplitterClass("SplitterGenerico");
        manualConfig.setEnableQaGeneration(true);
        manualConfig.setEnableSummarization(false); // Manuais são mais procedimentais
        contentTypes.put(TipoConteudo.MANUAL, manualConfig);

        logger.debug("Initialized {} default content type configurations", contentTypes.size());
    }

    /**
     * Obtém configuração para uma biblioteca específica
     */
    @Nullable
    public BibliotecaConfig getConfigForBiblioteca(@NonNull UUID bibliotecaId) {
        return bibliotecas.get(bibliotecaId.toString());
    }

    /**
     * Obtém configuração para um tipo de conteúdo
     */
    @Nullable
    public ContentTypeConfig getConfigForContentType(@NonNull TipoConteudo tipoConteudo) {
        return contentTypes.get(tipoConteudo);
    }

    /**
     * Registra configuração para uma biblioteca
     * 
     * 
     */
    public void registerBibliotecaConfig(@NonNull UUID bibliotecaUuId, @NonNull BibliotecaConfig config) {
        config.setBibliotecaUUID(bibliotecaUuId);
        bibliotecas.put(bibliotecaUuId.toString(), config);
        logger.debug("Registered config for biblioteca: {}", bibliotecaUuId);
    }

    /**
     * Registra configuração para um tipo de conteúdo
     */
    public void registerContentTypeConfig(@NonNull TipoConteudo tipoConteudo, @NonNull ContentTypeConfig config) {
        config.setTipoConteudo(tipoConteudo);
        contentTypes.put(tipoConteudo, config);
        logger.debug("Registered config for content type: {}", tipoConteudo);
    }

    /**
     * Obtém chunk size efetivo para uma biblioteca e tipo de conteúdo
     */
    public int getEffectiveChunkSize(@Nullable String bibliotecaId, @Nullable TipoConteudo tipoConteudo) {
        // Prioridade: biblioteca específica > tipo de conteúdo > padrão
        if (bibliotecaId != null) {
            BibliotecaConfig bibliotecaConfig = bibliotecas.get(bibliotecaId);
            if (bibliotecaConfig != null && bibliotecaConfig.getMaxChunkSize() != null) {
                return bibliotecaConfig.getMaxChunkSize();
            }
        }

        if (tipoConteudo != null) {
            ContentTypeConfig contentConfig = contentTypes.get(tipoConteudo);
            if (contentConfig != null && contentConfig.getPreferredChunkSize() != null) {
                return contentConfig.getPreferredChunkSize();
            }
        }

        return defaults.getMaxChunkSize();
    }

    /**
     * Obtém modelo preferencial efetivo
     */
    @NonNull
    public String getEffectivePreferredModel(@Nullable String bibliotecaId) {
        if (bibliotecaId != null) {
            BibliotecaConfig bibliotecaConfig = bibliotecas.get(bibliotecaId);
            if (bibliotecaConfig != null && bibliotecaConfig.getPreferredModel() != null) {
                return bibliotecaConfig.getPreferredModel();
            }
        }

        return defaults.getPreferredModel();
    }

    /**
     * Verifica se fallback LLM está habilitado
     */
    public boolean isLlmFallbackEnabled(@Nullable String bibliotecaId) {
        if (bibliotecaId != null) {
            BibliotecaConfig bibliotecaConfig = bibliotecas.get(bibliotecaId);
            if (bibliotecaConfig != null && bibliotecaConfig.getEnableLlmFallback() != null) {
                return bibliotecaConfig.getEnableLlmFallback();
            }
        }

        return defaults.isEnableLlmFallback();
    }

    /**
     * Obtém estatísticas de configuração
     */
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("bibliotecas_configuradas", bibliotecas.size());
        stats.put("tipos_conteudo_configurados", contentTypes.size());
        stats.put("chunk_size_padrao", defaults.getMaxChunkSize());
        stats.put("modelo_preferencial_padrao", defaults.getPreferredModel());
        stats.put("llm_fallback_habilitado", defaults.isEnableLlmFallback());

        return stats;
    }

    // Getters e Setters principais
    public DefaultConfig getDefaults() { return defaults; }
    public void setDefaults(DefaultConfig defaults) { this.defaults = defaults; }

    public Map<String, BibliotecaConfig> getBibliotecas() { return bibliotecas; }

    public Map<TipoConteudo, ContentTypeConfig> getContentTypes() { return contentTypes; }
}