package bor.tools.splitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import bor.tools.simplellm.LLMService;
import bor.tools.simplerag.dto.BibliotecaDTO;
import bor.tools.simplerag.entity.enums.TipoConteudo;

/**
 * Factory Pattern aprimorada para criação e reutilização de instâncias de Splitters.
 *
 * Esta factory implementa cache de instâncias, configuração por biblioteca,
 * e integração completa com o sistema JSimpleRag.
 */
@Component
public class SplitterFactory {

    private static final Logger logger = LoggerFactory.getLogger(SplitterFactory.class);

    private final DocumentRouter documentRouter;
    private final LLMService llmService;
    private final SplitterConfig splitterConfig;

    /**
     * Cache de instâncias de splitters por tipo
     */
    private final Map<String, AbstractSplitter> splitterCache = new ConcurrentHashMap<>();

    /**
     * Construtor com injeção de dependências
     */
    public SplitterFactory(DocumentRouter documentRouter,
                          LLMService llmService,
                          SplitterConfig splitterConfig) {
        this.documentRouter = documentRouter;
        this.llmService = llmService;
        this.splitterConfig = splitterConfig;
        logger.debug("SplitterFactory initialized with config and dependencies");
    }

    /**
     * Cria ou reutiliza splitter baseado no tipo de conteúdo
     *
     * @param tipoConteudo - tipo de conteúdo do enum
     * @param biblioteca - biblioteca para configuração específica
     * @return instância configurada do splitter
     */
    public AbstractSplitter createSplitter(@NonNull TipoConteudo tipoConteudo,
                                          @NonNull BibliotecaDTO biblioteca) {
        try {
            AbstractSplitter splitter = documentRouter.routeDocument(tipoConteudo);
            configureSplitter(splitter, biblioteca);
            return splitter;
        } catch (Exception e) {
            logger.error("Failed to create splitter for type {}: {}", tipoConteudo, e.getMessage());
            // Fallback para SplitterGenerico
            return createGenericSplitter(biblioteca);
        }
    }

    /**
     * Cria ou reutiliza splitter baseado no conteúdo do documento
     *
     * @param content - conteúdo do documento
     * @param biblioteca - biblioteca para configuração específica
     * @param hints - dicas opcionais (URL, extensão, etc.)
     * @return instância configurada do splitter
     */
    public AbstractSplitter createSplitter(@NonNull String content,
                                          @NonNull BibliotecaDTO biblioteca,
                                          String... hints) {
        try {
            AbstractSplitter splitter = documentRouter.routeDocument(content, hints);
            configureSplitter(splitter, biblioteca);
            return splitter;
        } catch (Exception e) {
            logger.error("Failed to create splitter for content: {}", e.getMessage());
            // Fallback para SplitterGenerico
            return createGenericSplitter(biblioteca);
        }
    }

    /**
     * Cria splitter específico por classe (cached)
     *
     * @param splitterClass - classe do splitter desejado
     * @param biblioteca - biblioteca para configuração específica
     * @return instância configurada do splitter
     */
    public <T extends AbstractSplitter> T createSplitter(@NonNull Class<T> splitterClass,
                                                         @NonNull BibliotecaDTO biblioteca) {
        String cacheKey = splitterClass.getSimpleName();

        @SuppressWarnings("unchecked")
        T splitter = (T) splitterCache.computeIfAbsent(cacheKey, k -> {
            try {
                return createSplitterInstance(splitterClass);
            } catch (Exception e) {
                logger.error("Failed to create splitter instance {}: {}", splitterClass.getSimpleName(), e.getMessage());
                return null;
            }
        });

        if (splitter != null) {
            configureSplitter(splitter, biblioteca);
        }

        return splitter;
    }

    /**
     * Cria splitter genérico configurado
     */
    public SplitterGenerico createGenericSplitter(@NonNull BibliotecaDTO biblioteca) {
        SplitterGenerico splitter = new SplitterGenerico(llmService);
        configureSplitter(splitter, biblioteca);
        return splitter;
    }

    /**
     * Cria splitter para normativos
     */
    public SplitterNorma createNormSplitter(@NonNull BibliotecaDTO biblioteca) {
        SplitterNorma splitter = new SplitterNorma(llmService);
        configureSplitter(splitter, biblioteca);
        return splitter;
    }

    /**
     * Cria splitter para conteúdo Wiki
     */
    public SplitterWiki createWikiSplitter(@NonNull BibliotecaDTO biblioteca) {
        SplitterWiki splitter = new SplitterWiki(llmService);
        configureSplitter(splitter, biblioteca);
        return splitter;
    }

    /**
     * Configura splitter com parâmetros específicos da biblioteca
     */
    private void configureSplitter(AbstractSplitter splitter, BibliotecaDTO biblioteca) {
        if (splitter == null || biblioteca == null) {
            logger.warn("Cannot configure null splitter or biblioteca");
            return;
        }

        // Configurar LLMService se não configurado
        if (splitter.getLlmServices() == null && llmService != null) {
            splitter.setLlmServices(llmService);
        }

        // Aplicar configurações específicas da biblioteca
        SplitterConfig.BibliotecaConfig bibliotecaConfig =
            splitterConfig.getConfigForBiblioteca(biblioteca.getUuid());

        if (bibliotecaConfig != null) {
            applySplitterConfig(splitter, bibliotecaConfig);
        }

        logger.debug("Configured splitter {} for biblioteca {}",
                    splitter.getClass().getSimpleName(), biblioteca.getNome());
    }

    /**
     * Aplica configurações específicas ao splitter
     */
    private void applySplitterConfig(AbstractSplitter splitter, SplitterConfig.BibliotecaConfig config) {
        // As configurações específicas serão implementadas quando SplitterConfig estiver pronto
        logger.debug("Applied config to splitter: maxChunkSize={}, model={}",
                    config.getMaxChunkSize(), config.getPreferredModel());
    }

    /**
     * Cria instância do splitter usando reflexão
     */
    private <T extends AbstractSplitter> T createSplitterInstance(Class<T> splitterClass) throws Exception {
        try {
            // Tentar construtor com LLMService primeiro
            if (llmService != null) {
                try {
                    return splitterClass.getConstructor(LLMService.class).newInstance(llmService);
                } catch (NoSuchMethodException e) {
                    logger.debug("No LLMService constructor found for {}, using default",
                               splitterClass.getSimpleName());
                }
            }

            // Usar construtor padrão
            T splitter = splitterClass.getDeclaredConstructor().newInstance();

            // Configurar LLMService se disponível
            if (llmService != null) {
                splitter.setLlmServices(llmService);
            }

            return splitter;

        } catch (Exception e) {
            logger.error("Failed to create splitter instance of type {}: {}",
                        splitterClass.getSimpleName(), e.getMessage());
            throw new Exception("Could not create splitter instance", e);
        }
    }

    /**
     * Limpa cache de splitters
     */
    public void clearCache() {
        splitterCache.clear();
        logger.debug("Splitter cache cleared");
    }

    /**
     * Obtém estatísticas da factory
     */
    public Map<String, Object> getFactoryStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("cached_splitters", splitterCache.size());
        stats.put("llm_service_available", llmService != null);
        stats.put("config_available", splitterConfig != null);
        stats.put("router_available", documentRouter != null);
        stats.put("cached_types", splitterCache.keySet());
        return stats;
    }

    /**
     * Verifica se a factory está completamente configurada
     */
    public boolean isFullyConfigured() {
        return documentRouter != null && splitterConfig != null;
    }

    /**
     * Obtém instância do DocumentRouter interno
     */
    public DocumentRouter getDocumentRouter() {
        return documentRouter;
    }

    /**
     * Obtém configuração atual
     */
    public SplitterConfig getSplitterConfig() {
        return splitterConfig;
    }
}