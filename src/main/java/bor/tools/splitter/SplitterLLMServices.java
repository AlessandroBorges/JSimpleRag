package bor.tools.splitter;

import java.util.List;

import bor.tools.simplellm.exceptions.LLMException;

/**
 * Interface que centraliza todas as funcionalidades relacionadas aos recursos LLM
 * para operações de splitting e processamento de documentos.
 *
 * Esta interface abstrai as operações de IA necessárias para o processamento
 * inteligente de documentos, permitindo diferentes implementações baseadas
 * em diferentes provedores de LLM.
 */
public interface SplitterLLMServices {

    /**
     * Sumariza um texto mantendo as informações mais importantes.
     *
     * @param text - texto a ser sumarizado
     * @param instructions - instruções específicas para a sumarização
     * @param maxLength - tamanho máximo do resumo (em tokens)
     * @return texto sumarizado
     * @throws LLMException em caso de erro na operação
     */
    String sumarizeText(String text, String instructions, int maxLength) throws LLMException;

    /**
     * Sumariza um texto com instruções padrão.
     *
     * @param text - texto a ser sumarizado
     * @param maxLength - tamanho máximo do resumo (em tokens)
     * @return texto sumarizado
     * @throws LLMException em caso de erro na operação
     */
    default String sumarizeText(String text, int maxLength) throws LLMException {
        return sumarizeText(text,
            "Resuma o texto de forma concisa mantendo as informações mais importantes",
            maxLength);
    }

    /**
     * Traduz um texto de um idioma para outro.
     *
     * @param text - texto a ser traduzido
     * @param sourceLang - idioma de origem (ex: "en", "pt", "es")
     * @param targetLang - idioma de destino (ex: "en", "pt", "es")
     * @return texto traduzido
     * @throws LLMException em caso de erro na operação
     */
    String translateText(String text, String sourceLang, String targetLang) throws LLMException;

    /**
     * Conta o número de tokens em um texto.
     *
     * @param text - texto para contagem
     * @return número de tokens
     * @throws LLMException em caso de erro na operação
     */
    int getTokenCount(String text) throws LLMException;

    /**
     * Identifica o tipo de documento baseado no conteúdo.
     *
     * @param content - conteúdo do documento
     * @return tipo identificado (ex: "normativo", "wikipedia", "artigo", "manual", "livro")
     * @throws LLMException em caso de erro na operação
     */
    String identifyDocumentType(String content) throws LLMException;

    /**
     * Cria um mapa mental/estrutural do documento.
     *
     * @param content - conteúdo do documento
     * @return representação estrutural do documento em formato legível
     * @throws LLMException em caso de erro na operação
     */
    String createMindMap(String content) throws LLMException;

    /**
     * Clarifica e melhora a qualidade de um texto.
     *
     * @param text - texto a ser clarificado
     * @param instructions - instruções específicas para clarificação
     * @return texto clarificado
     * @throws LLMException em caso de erro na operação
     */
    String clarifyText(String text, String instructions) throws LLMException;

    /**
     * Clarifica um texto com instruções padrão.
     *
     * @param text - texto a ser clarificado
     * @return texto clarificado
     * @throws LLMException em caso de erro na operação
     */
    default String clarifyText(String text) throws LLMException {
        return clarifyText(text,
            "Reescreva o texto de forma clara, informativa e concisa, " +
            "mantendo o sentido original e os principais elementos. " +
            "Evite repetições e redundâncias.");
    }

    /**
     * Gera perguntas e respostas a partir de um texto.
     *
     * @param text - texto fonte
     * @param numQuestions - número de perguntas a gerar
     * @return lista de pares pergunta-resposta
     * @throws LLMException em caso de erro na operação
     */
    List<QuestionAnswer> generateQA(String text, int numQuestions) throws LLMException;

    /**
     * Extrai metadados relevantes de um texto.
     *
     * @param text - texto para análise
     * @return mapa com metadados extraídos (título, autor, data, palavras-chave, etc.)
     * @throws LLMException em caso de erro na operação
     */
    java.util.Map<String, String> extractMetadata(String text) throws LLMException;

    /**
     * Categoriza o conteúdo do documento.
     *
     * @param content - conteúdo a ser categorizado
     * @param categories - lista de categorias possíveis
     * @return categoria mais provável
     * @throws LLMException em caso de erro na operação
     */
    String categorizeContent(String content, List<String> categories) throws LLMException;

    /**
     * Verifica se o LLM service está disponível e funcionando.
     *
     * @return true se o serviço estiver disponível
     */
    boolean isAvailable();

    /**
     * Obtém informações sobre os modelos disponíveis.
     *
     * @return lista com nomes dos modelos disponíveis
     * @throws LLMException em caso de erro na operação
     */
    List<String> getAvailableModels() throws LLMException;
}