package bor.tools.splitter;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.entity.enums.TipoConteudo;

/**
 * Classe utilitária responsável por rotear documentos para o splitter apropriado
 * baseado no tipo de conteúdo identificado automaticamente.
 *
 * Esta classe resolve a necessidade mencionada de uma classe utilitária capaz de
 * identificar o tipo de documento e fazer o roteamento correto para qual splitter
 * utilizar, permitindo melhor integração com o ProcessamentoAssincrono do core RAG.
 */
@Component
public class DocumentRouter {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRouter.class);

    private final Map<String, Class<? extends AbstractSplitter>> splitterRegistry;
    private final LLMService llmService;

    /**
     * Construtor com injeção de dependência do LLMService
     */
    public DocumentRouter(LLMService llmService) {
        this.llmService = llmService;
        this.splitterRegistry = new HashMap<>();
        initializeSplitterRegistry();
    }

    /**
     * Construtor padrão sem LLMService (fallback para detecção heurística)
     */
    public DocumentRouter() {
        this.llmService = null;
        this.splitterRegistry = new HashMap<>();
        initializeSplitterRegistry();
    }

    /**
     * Inicializa o registro de splitters disponíveis
     */
    private void initializeSplitterRegistry() {
        splitterRegistry.put("normativo", SplitterNorma.class);
        splitterRegistry.put("wikipedia", SplitterWiki.class);
        splitterRegistry.put("artigo", SplitterWiki.class);  // Wikipedia-like
        splitterRegistry.put("manual", SplitterGenerico.class);
        splitterRegistry.put("livro", SplitterGenerico.class);
        splitterRegistry.put("contrato", SplitterGenerico.class);
        splitterRegistry.put("nota_tecnica", SplitterGenerico.class);
        splitterRegistry.put("generico", SplitterGenerico.class);

        logger.debug("Initialized splitter registry with {} types", splitterRegistry.size());
    }

    /**
     * Roteia um documento para o splitter apropriado baseado no conteúdo.
     *
     * @param content - conteúdo do documento
     * @param hints - dicas opcionais sobre o tipo (ex: extensão do arquivo, URL)
     * @return instância do splitter apropriado
     * @throws Exception se não for possível criar o splitter
     */
    public AbstractSplitter routeDocument(@NonNull String content, String... hints) throws Exception {
        logger.debug("Routing document with {} characters", content.length());

        // 1. Tentar identificar tipo automaticamente
        String documentType = identifyDocumentType(content, hints);
        logger.debug("Identified document type: {}", documentType);

        // 2. Obter classe do splitter apropriado
        Class<? extends AbstractSplitter> splitterClass = splitterRegistry.get(documentType);
        if (splitterClass == null) {
            logger.warn("No specific splitter found for type {}, using generic", documentType);
            splitterClass = SplitterGenerico.class;
        }

        // 3. Criar instância do splitter
        AbstractSplitter splitter = createSplitterInstance(splitterClass);
        logger.debug("Created splitter: {}", splitter.getClass().getSimpleName());

        return splitter;
    }

    /**
     * Roteia um documento para o splitter apropriado baseado no TipoConteudo do enum.
     *
     * @param tipoConteudo - tipo predefinido do conteúdo
     * @return instância do splitter apropriado
     * @throws Exception se não for possível criar o splitter
     */
    public AbstractSplitter routeDocument(@NonNull TipoConteudo tipoConteudo) throws Exception {
        logger.debug("Routing document by TipoConteudo: {}", tipoConteudo);

        String documentType = mapTipoConteudoToSplitterType(tipoConteudo);
        Class<? extends AbstractSplitter> splitterClass = splitterRegistry.get(documentType);

        if (splitterClass == null) {
            logger.warn("No specific splitter found for TipoConteudo {}, using generic", tipoConteudo);
            splitterClass = SplitterGenerico.class;
        }

        return createSplitterInstance(splitterClass);
    }

    /**
     * Identifica o tipo de documento usando LLM ou heurística
     */
    private String identifyDocumentType(String content, String... hints) {
        // 1. Verificar hints primeiro (extensão, URL, etc.)
        for (String hint : hints) {
            if (hint != null) {
                String typeFromHint = identifyFromHint(hint);
                if (typeFromHint != null) {
                    logger.debug("Document type identified from hint '{}': {}", hint, typeFromHint);
                    return typeFromHint;
                }
            }
        }

        // 2. Tentar usar LLM se disponível
        if (llmService != null) {
            try {
                AbstractSplitter tempSplitter = new SplitterGenerico(llmService);
                return tempSplitter.identifyDocumentType(content);
            } catch (LLMException e) {
                logger.warn("Failed to identify document type using LLM: {}", e.getMessage());
            }
        }

        // 3. Fallback para identificação heurística
        return identifyDocumentTypeHeuristic(content);
    }

    /**
     * Identifica tipo baseado em hints (URL, extensão, etc.)
     */
    private String identifyFromHint(String hint) {
        String lowerHint = hint.toLowerCase();

        // URLs de normativos
        if (lowerHint.contains("planalto.gov.br") ||
            lowerHint.contains("in.gov.br") ||
            lowerHint.contains("legislacao")) {
            return "normativo";
        }

        // URLs do Wikipedia
        if (lowerHint.contains("wikipedia.org") ||
            lowerHint.contains("wikimedia.org")) {
            return "wikipedia";
        }

        // Extensões de arquivo
        if (lowerHint.endsWith(".wiki") || lowerHint.endsWith(".wikitext")) {
            return "wikipedia";
        }

        if (lowerHint.endsWith(".md") || lowerHint.endsWith(".markdown")) {
            return "artigo";
        }

        return null;
    }

    /**
     * Identificação heurística de tipo de documento
     */
    private String identifyDocumentTypeHeuristic(String content) {
        String lowerContent = content.toLowerCase();

        // Verificar normativos
        if (lowerContent.contains("art.") && lowerContent.contains("lei") ||
            lowerContent.contains("decreto") || lowerContent.contains("resolução") ||
            lowerContent.contains("portaria") || lowerContent.contains("normativa")) {
            return "normativo";
        }

        // Verificar Wikipedia
        if (lowerContent.contains("categoria:") || lowerContent.contains("{{") ||
            lowerContent.contains("[[") || lowerContent.contains("]]") ||
            (lowerContent.contains("==") && lowerContent.contains("==="))) {
            return "wikipedia";
        }

        // Verificar manual/procedimento
        if (lowerContent.contains("manual") || lowerContent.contains("instruções") ||
            lowerContent.contains("procedimento") || lowerContent.contains("tutorial")) {
            return "manual";
        }

        // Verificar contrato
        if (lowerContent.contains("contrato") || lowerContent.contains("contratante") ||
            lowerContent.contains("cláusula") || lowerContent.contains("partes")) {
            return "contrato";
        }

        return "generico";
    }

    /**
     * Mapeia TipoConteudo para tipo de splitter
     */
    private String mapTipoConteudoToSplitterType(TipoConteudo tipoConteudo) {
        return switch (tipoConteudo) {
            case NORMATIVO -> "normativo";
            case LIVRO, ARTIGO -> "artigo";
            case MANUAL -> "manual";
            default -> "generico";
        };
    }

    /**
     * Cria instância do splitter usando reflexão
     */
    private AbstractSplitter createSplitterInstance(Class<? extends AbstractSplitter> splitterClass) throws Exception {
        try {
            // Tentar construtor com LLMService primeiro
            if (llmService != null) {
                try {
                    return splitterClass.getConstructor(LLMService.class).newInstance(llmService);
                } catch (NoSuchMethodException e) {
                    // Se não há construtor com LLMService, usar construtor padrão
                    logger.debug("No LLMService constructor found for {}, using default", splitterClass.getSimpleName());
                }
            }

            // Usar construtor padrão
            AbstractSplitter splitter = splitterClass.getDeclaredConstructor().newInstance();

            // Configurar LLMService se disponível
            if (llmService != null && splitter.getLlmServices() == null) {
                splitter.setLlmServices(llmService);
            }

            return splitter;

        } catch (Exception e) {
            logger.error("Failed to create splitter instance of type {}: {}", splitterClass.getSimpleName(), e.getMessage());
            throw new Exception("Could not create splitter instance", e);
        }
    }

    /**
     * Registra um novo tipo de splitter
     *
     * @param documentType - tipo do documento
     * @param splitterClass - classe do splitter
     */
    public void registerSplitter(String documentType, Class<? extends AbstractSplitter> splitterClass) {
        splitterRegistry.put(documentType, splitterClass);
        logger.debug("Registered splitter {} for document type {}", splitterClass.getSimpleName(), documentType);
    }

    /**
     * Obtém os tipos de documento suportados
     *
     * @return array com tipos suportados
     */
    public String[] getSupportedTypes() {
        return splitterRegistry.keySet().toArray(new String[0]);
    }

    /**
     * Verifica se um tipo de documento é suportado
     *
     * @param documentType - tipo do documento
     * @return true se suportado
     */
    public boolean isTypeSupported(String documentType) {
        return splitterRegistry.containsKey(documentType);
    }

    /**
     * Obtém estatísticas do roteador
     *
     * @return mapa com estatísticas
     */
    public Map<String, Object> getRouterStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("supported_types", splitterRegistry.keySet());
        stats.put("total_splitters", splitterRegistry.size());
        stats.put("llm_service_available", llmService != null);

        return stats;
    }

    public TipoConteudo detectContentType(String conteudoMarkdown) {
	LLMService llm = this.llmService;
	if (llm != null) {
	    try {
		String resposta = llm.classifyContent(conteudoMarkdown,
			TipoConteudo.getAllNames(),
			TipoConteudo.getAllNamesAndDescriptions());
		return TipoConteudo.fromName(resposta);
	    } catch (LLMException e) {
		logger.warn("Failed to classify content type using LLM: {}", e.getMessage());
	    }
	}
	return TipoConteudo.OUTROS;
    }
}