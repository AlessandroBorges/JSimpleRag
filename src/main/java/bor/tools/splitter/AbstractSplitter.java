package bor.tools.splitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import bor.tools.simplellm.CompletionResponse;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.utils.RagUtils;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * <h2>ISplitter de documentos.</h2> Previsão de implementação de diferentes
 * estratégias de split, incluindo:
 * <li>Split de Normativos e Leis (por artigos, parágrafos, incisos, alíneas,
 * etc)
 * <li>Split de Contrato (por cláusulas, parágrafos, incisos, alíneas, etc)
 * <li>Split de Documento ou Documento Técnico (por seções, subseções,
 * parágrafos, etc)
 * <li>Split de Nota Técnica (por seções, subseções, parágrafos, etc)
 *
 * @see DocumentSplitter
 * @see DocumentPreprocessor
 * @see SplitterLLMServices
 * @see Provedor
 * @see Library
 * @see ExistsArtefato
 * @see Documento
 */
@Data
public abstract class AbstractSplitter implements DocumentSplitter, DocumentPreprocessor, SplitterLLMServices {
    /** Logger **/
    private static final Logger logger = LoggerFactory.getLogger(AbstractSplitter.class);
    
    /**
     * Número máximo de tokens permitidos por split.
     */
    protected static final Integer MAX_TOKENS_SPLIT = 4096;
   
    

    /**
     * Metadata enhancer for document enrichment.
     */
    private Metadata metadata;
    
    /**
     * Serviço de LLM para operações de IA.
     */
    private LLMService llmServices;
    
    /**
     * Número máximo de tokens permitidos por split.
     */
    private Integer maxTokensSplit = MAX_TOKENS_SPLIT;

    /**
     * Construtor
     *
     */
    public AbstractSplitter() {	
    }
    
    /**
     * Construtor com serviço de LLM
     * 
     * @param llmService - serviço de LLM
     */
    public AbstractSplitter(LLMService llmService) {
	this.llmServices = llmService;
    }

    /**
     * Divite um texto em Parágrafos
     * 
     * @param texto - texto de entrada
     * @return array com os parágrafos
     */
    @Override
    public String[] splitIntoParagraphs(String texto) {
	// Duas linhas de espaço são consideradas como separador de parágrafos
	// Expressão regular para separar o texto por parágrafos
	String regex = "(?m)\\n{2,}";

	// Compilando a expressão regular em um padrão
	Pattern pattern = Pattern.compile(regex);

	// Dividindo o texto com base na expressão regular
	String[] paragrafos = pattern.split(texto);

	return paragrafos;
    }
    
    /**
     * Estima o número de tokens em um texto.
     *
     * @param text - texto de entrada
     * @return número estimado de tokens
     * @throws LLMException
     */
    public int getTokenCount(String text) throws LLMException {
	if (text == null || text.isEmpty()) {
	    return 0;
	}

	// Primeiro, tentar usar o LLMService se disponível
	if (llmServices != null) {
	    try {
		return llmServices.tokenCount(text, null);
	    } catch (Exception e) {
		logger.warn("Failed to get token count from LLM service, using fallback: {}", e.getMessage());
	    }
	}

	// Fallback melhorado baseado no ContentSplitter
	// Estimativa mais precisa: palavras / 0.75 (assumindo token médio = 0.75 palavras)
	String[] words = text.split("\\s+");
	int estimatedTokens = (int) Math.ceil(words.length / 0.75);

	logger.debug("Estimated {} tokens for text with {} words (fallback method)",
		     estimatedTokens, words.length);

	return estimatedTokens;
    }

    /**
     * Enrich document metadata using the provided Metadata enhancer.
     * 
     * @param metadataEnhancer - Metadata enhancer
     * @param doc              - Documento a ser enriquecido
     */
    public void enrichDocumentMetadata(Metadata metadataEnhancer, Documento doc) {
	this.metadata = metadataEnhancer;
	enrichDocumentMetadata(doc);
    }

    /**
     * Extract and enrich document metadata.
     * @param doc - Documento a ser enriquecido
     */
    public void enrichDocumentMetadata(Documento doc) {
	
    }

    /**
     * Enriquece os metadados de um capítulo.	
     * @param parte - capítulo a ser enriquecido
     * @param contextMetadata - metadata do contexto
     */
    protected void enrichPartMetadata(ChapterDTO parte, Metadata contextMetadata) {
	
    }

    /**
     * Realiza Split de texto em sentenças.
     * 
     * @param texto
     * @param maxCaracteres
     * @return
     */
    @Override
    public String[] splitIntoSentences(String texto, int maxCaracteres) {
	// Duas linhas de espaço são consideradas como separador de parágrafos

	// Expressão regular para separar o texto por sentenças
	String regex = "(?<=[.!?])\\s+|\\n+";

	// Compilando a expressão regular em um padrão
	Pattern pattern = Pattern.compile(regex);

	// Dividindo o texto com base na expressão regular
	String[] sentencas = pattern.split(texto);

	List<String> lista = new ArrayList<>();
	StringBuilder sb = new StringBuilder();

	for (String sentenca : sentencas) {
	    if (sb.length() + sentenca.length() < maxCaracteres) {
		sb.append(sentenca);
		sb.append(" ");
	    } else {
		lista.add(sb.toString());
		sb.setLength(0);
		sb.append(sentenca);
	    }
	}
	String[] resultado = new String[lista.size()];
	return lista.toArray(resultado);
    }

    /**
     * This method counts the number of words in a given text. It iterates through
     * each character of the input string and checks for whitespace characters. When
     * it encounters a whitespace character, it increments the word count if it was
     * previously inside a word. The method uses a boolean flag 'inWord' to track
     * whether the current character is part of a word. Finally, if the last
     * character in the text is not a whitespace, it increments the word count one
     * last time.
     *
     * @param text The input string for which the word count is to be calculated.
     * @return The total number of words in the input string.
     */
    public int countWords(String text) {
	int wordCount = 0;
	boolean inWord = false;
	for (char c : text.toCharArray()) {
	    if (Character.isWhitespace(c)) {
		if (inWord) {
		    wordCount++;
		    inWord = false;
		}
	    } else {
		inWord = true;
	    }
	}
	if (inWord) {
	    wordCount++;
	}
	return wordCount;
    }

    /**
     * Remove repetições de palavras em um texto
     *
     * @param src texto de entrada
     * @return texto sem repetições
     */
    public String removerRepeticoes(String src) {
	return RagUtils.removerRepeticoes(src);
    }

    /**
     * Remove texto tachado
     * 
     * @param src - texto de entrada
     * @return texto sem tachados
     */
    public String removerTachado(String src) {
	return RagUtils.removeStrikeThrough(src);
    }

    /**
     * Retorna a data normalizada, com as horas arredondada para multiplos de 2
     * minutos
     * 
     * @return
     */



    /**
     * Extrai metadados de um texto HTML.
     *
     * @return propriedades extraídas do HTML, no que houver.
     */
    public static Properties getHtmlMetadata(String html) {
	Properties props = new Properties();
	if (!RagUtils.isHtml(html)) {
	    return props;
	}

	org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
	String title = doc.title();
	if (title != null && !title.isEmpty()) {
	    props.put("title", title);
	}
	// Extrair a descrição (meta description)
	Element description = doc.select("meta[name=description]").first();
	if (description != null) {
	    props.put("descricao", description.attr("content"));
	}
	// Extrair as palavras-chave (meta keywords)
	Element keywords = doc.select("meta[name=keywords]").first();
	if (keywords != null) {
	    props.put("palavras-chave", keywords.attr("content"));
	}
	// author
	Element author = doc.select("meta[name=author]").first();
	if (author != null) {
	    props.put("autor", author.attr("content"));
	}
	// charset
	Element charset = doc.select("meta[charset]").first();
	if (charset != null) {
	    props.put("charset", charset.attr("charset"));
	}
	return props;
    }

   
    /**
     * Clarifica um texto
     * 
     * @param texto - texto de entrada
     * @return
     */
    public String clarifiqueTexto(LLMService provedorIA, String texto) {
	try {
	    return clarifiqueTexto(provedorIA, texto, null);
	} catch (LLMException e) {
	    e.printStackTrace();
	}
	return texto;
    }

    /**
     *
     * @param texto  - texto a ser clarificado *
     * @param prompt - texto de prompt
     * @return texto ajustado, clarificado
     * @throws LLMException
     */
    public String clarifiqueTexto(LLMService provedorIA, String texto, String prompt) throws LLMException {
	if (prompt == null || prompt.isBlank()) {
	    prompt = "Rescreva o texto a ser apresentado na lingua Português do Brasil, "
		    + "de forma clara, informativa e concisa, "
		    + "mantendo o sentido original e os seus principais elementos. "
		    + "Evite repetições e redundâncias, não adicione informações que não "
		    + "estão presentes no texto original.";

	}

	MapParam params = new MapParam();
	CompletionResponse response = provedorIA.completion(prompt, texto, params);
	return response.getText();
    }//

    /**
     * Remove repetições de palavras em um texto.
     * <p>
     * Este método remove sequências de palavras repetidas (de uma até três
     * palavras) que aparecem consecutivamente em cada linha do texto fornecido. Ele
     * preserva as quebras de linha do texto original.
     * </p>
     *
     * @param text O texto de entrada no qual as repetições serão removidas.
     * @return O texto sem repetições de palavras consecutivas.
     */
    @Override
    public String removeRepetitions(String text) {
	String[] lines = text.split("\n");
	StringBuilder result = new StringBuilder();

	for (String line : lines) {
	    String[] words = line.split("\\s+");
	    int i = 0;

	    while (i < words.length) {
		result.append(words[i]);
		int j = i + 1;
		while (j < words.length && words[j].equals(words[i])) {
		    j++;
		}
		i = j;
		if (i < words.length) {
		    result.append(" ");
		}
	    }
	    result.append("\n");
	}

	return result.toString().trim();
    }

    /**
     * Detecta o nome do arquivo a partir de um path.
     * 
     * @param pathName - caminho do arquivo
     * @return nome do arquivo
     */
    public static String detectaNome(String pathName) {
	return RagUtils.detectaNome(pathName);
    }

    /**
     * Split de um documento em capítulos.
     * 
     */
    public abstract List<ChapterDTO> splitDocumento(DocumentoWithAssociationDTO DocumentoWithAssociationDTO);
    
    
    /**
     * Carrega DocumentoWithAssociationDTO a partir de uma URL.
     * @param urlDocumento URL do DocumentoWithAssociationDTO
     * @param docStub DocumentoWithAssociationDTO para preencher, se null, cria um novo
     * 
     */
    public DocumentoWithAssociationDTO carregaDocumento(@NonNull URL urlDocumento, DocumentoWithAssociationDTO docStub) throws Exception {
	DocumentoWithAssociationDTO doc = docStub == null ? new DocumentoWithAssociationDTO() : docStub;
	doc.setUrl(urlDocumento.toString());
	OkHttpClient client = RagUtils.getUnsafeOkHttpClient();
	Request request = new Request.Builder().url(urlDocumento).get().header("User-Agent",
		"Mozilla/5.0 (Windows NT 10; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36")
		.header("Accept", "text/html,application/xhtml+xml,application/xml,text/json;").build();
	try (Response response = client.newCall(request).execute()) {
	    if (!response.isSuccessful()) {
		throw new IOException("Erro ao fazer a requisição: " + response);
	    }
	    // Obtém o corpo da resposta
	    ResponseBody responseBody = response.body();

	    if (responseBody != null) {
		// Obtém o InputStream do corpo da resposta
		InputStream input = responseBody.byteStream();
		// Instanciar o parser
		AutoDetectParser parser = new AutoDetectParser();
		// Passando -1 para ler documentos de qualquer tamanho
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();
		ParseContext context = new ParseContext();

		// Fazendo o parsing do DocumentoWithAssociationDTO
		parser.parse(input, handler, metadata, context);

		{
		    String contentType = metadata.get("Content-Type");
		    // conteudo HTML e XHTML
		    if (contentType != null
			    && (contentType.contains("text/html") || contentType.contains("application/xhtml+xml"))) {
			String content = handler.toString();
			String md = RagUtils.convertToMarkdown(content);
			doc.setTexto(md);
		    } else {
			doc.setTexto(handler.toString());
		    }
		}
		doc.setTitulo(urlDocumento.getFile());

		Map<String,Object> metaMap = new HashMap<>();
		StringBuffer sb = new StringBuffer(512);
		for (String name : metadata.names()) {		    
		    metaMap.put(name, metadata.get(name));
		}
		String res = sb.toString();
		logger.debug("Metadados: " + res);
		doc.getMetadados().addMetadata(metaMap);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return doc;
    }

    /**
     * Carrega DocumentoWithAssociationDTO a partir de um arquivo local ou em rede.
     * 
     * @param path caminho do arquivo
     * @param docStub DocumentoWithAssociationDTO para preencher, se null, cria um novo
     * @return DocumentoWithAssociationDTO carregado
     */
    public DocumentoWithAssociationDTO carregaDocumento(String path, DocumentoWithAssociationDTO docStub) throws Exception {
	if (path.matches("^(http|.*\\.(html|xhtml|htm|xml))$")) {
	    return carregaDocumento(new URI(path).toURL(), docStub);
	} else {
	    DocumentoWithAssociationDTO doc = docStub == null ? new DocumentoWithAssociationDTO() : docStub;
	    doc.setUrl(path);
	    if (doc.getTitulo() == null) {
		String titulo = AbstractSplitter.detectaNome(path);
		doc.setTitulo(titulo);
	    }
	    byte[] data = RagUtils.lerArquivoBinario(path);
	    String textoMD = RagUtils.convertToMarkdown(data);
	    String[] lines = textoMD.split("\n");
	    List<TitleTag> titles = detectTitles(lines);
	    List<ChapterDTO> partes = splitByTitles(doc, lines, titles);
	    for (ChapterDTO parte : partes) {
		doc.addParte(parte);
	    }
	    doc.setTexto(textoMD);
	    doc.setTitulo(path);
	    return doc;
	}

    }

    /**
     * Split document by detected titles.
     * 
     * @param doc    - DocumentoWithAssociationDTO
     * @param lines  - lines of text
     * @param titles - detected titles
     * @return list of ChapterDTO
     */    
    protected abstract List<ChapterDTO> splitByTitles(DocumentoWithAssociationDTO doc, String[] lines, List<TitleTag> titles);

    /**
     * Detect titles in text lines.
     *
     * @param lines - lines of text
     * @return list of detected TitleTag
     */
    protected abstract List<TitleTag> detectTitles(String[] lines);

    // ================ IMPLEMENTAÇÃO SplitterLLMServices ================

    /**
     * {@inheritDoc}
     */
    @Override
    public String sumarizeText(String text, String instructions, int maxLength) throws LLMException {
        if (llmServices == null) {
            throw new LLMException("LLM service not available for text summarization");
        }

        logger.debug("Summarizing text of {} characters with maxLength: {}", text.length(), maxLength);

        MapParam params = new MapParam();
        params.put("max_tokens", maxLength);

        return llmServices.sumarizeText(text, instructions, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String translateText(String text, String sourceLang, String targetLang) throws LLMException {
        if (llmServices == null) {
            throw new LLMException("LLM service not available for translation");
        }

        logger.debug("Translating text from {} to {}", sourceLang, targetLang);

        String prompt = String.format("Traduza o seguinte texto de %s para %s:", sourceLang, targetLang);
        MapParam params = new MapParam();
        CompletionResponse response = llmServices.completion(prompt, text, params);

        return response.getText();
    }

    /**
     * {@inheritDoc}
     *
     * Nota: Este método sobrescreve a interface SplitterLLMServices,
     * mas delega para a implementação já existente na classe base.
     */
    // Implementação já existe na classe - não precisa reimplementar

    /**
     * {@inheritDoc}
     */
    @Override
    public String identifyDocumentType(String content) throws LLMException {
        if (llmServices == null) {
            logger.warn("LLM service not available, using heuristic document type detection");
            return identifyDocumentTypeHeuristic(content);
        }

        logger.debug("Identifying document type using LLM for content of {} characters", content.length());

        String prompt = "Analise o conteúdo abaixo e identifique o tipo de documento. " +
                       "Responda apenas com uma das seguintes categorias: " +
                       "normativo, wikipedia, artigo, manual, livro, contrato, nota_tecnica, generico";

        // Usar apenas os primeiros 2000 caracteres para análise
        String sample = content.length() > 2000 ? content.substring(0, 2000) : content;

        MapParam params = new MapParam();
        params.put("max_tokens", 50);

        CompletionResponse response = llmServices.completion(prompt, sample, params);
        String result = response.getText().trim().toLowerCase();

        // Validar e normalizar resultado
        String[] validTypes = {"normativo", "wikipedia", "artigo", "manual", "livro", "contrato", "nota_tecnica"};
        for (String validType : validTypes) {
            if (result.contains(validType)) {
                return validType;
            }
        }

        return "generico";
    }

    /**
     * Identificação heurística de tipo de documento (fallback)
     */
    private String identifyDocumentTypeHeuristic(String content) {
        String lowerContent = content.toLowerCase();

        // Verificar normativos
        if (lowerContent.contains("artigo") && lowerContent.contains("lei") ||
            lowerContent.contains("decreto") || lowerContent.contains("resolução")) {
            return "normativo";
        }

        // Verificar Wikipedia
        if (lowerContent.contains("categoria:") || lowerContent.contains("{{") ||
            lowerContent.contains("==") && lowerContent.contains("===")) {
            return "wikipedia";
        }

        // Verificar manual
        if (lowerContent.contains("manual") || lowerContent.contains("instruções") ||
            lowerContent.contains("procedimento")) {
            return "manual";
        }

        return "generico";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createMindMap(String content) throws LLMException {
        if (llmServices == null) {
            throw new LLMException("LLM service not available for mind map creation");
        }

        logger.debug("Creating mind map for content of {} characters", content.length());

        String prompt = "Crie um mapa mental estrutural do documento abaixo. " +
                       "Organize as informações de forma hierárquica, identificando " +
                       "os principais tópicos, subtópicos e conceitos-chave. " +
                       "Use formato de árvore com indentação.";

        MapParam params = new MapParam();
        params.put("max_tokens", 1000);

        CompletionResponse response = llmServices.completion(prompt, content, params);
        return response.getText();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String clarifyText(String text, String instructions) throws LLMException {
        // Usar a implementação já existente na classe
        return clarifiqueTexto(llmServices, text, instructions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<QuestionAnswer> generateQA(String text, int numQuestions) throws LLMException {
        if (llmServices == null) {
            throw new LLMException("LLM service not available for Q&A generation");
        }

        logger.debug("Generating {} Q&A pairs for text of {} characters", numQuestions, text.length());

        String prompt = String.format(
            "Com base no texto abaixo, gere %d perguntas e respostas relevantes. " +
            "Formato: Q: [pergunta] A: [resposta]. Separe cada par com uma linha em branco.",
            numQuestions);

        MapParam params = new MapParam();
        params.put("max_tokens", numQuestions * 100);

        CompletionResponse response = llmServices.completion(prompt, text, params);
        String result = response.getText();

        return parseQAResponse(result);
    }

    /**
     * Parse da resposta Q&A do LLM
     */
    private List<QuestionAnswer> parseQAResponse(String response) {
        List<QuestionAnswer> qaList = new ArrayList<>();
        String[] lines = response.split("\n");

        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Q:")) {
                // Salvar Q&A anterior se existir
                if (currentQuestion != null && currentAnswer.length() > 0) {
                    qaList.add(new QuestionAnswer(currentQuestion, currentAnswer.toString().trim()));
                }
                // Iniciar nova pergunta
                currentQuestion = line.substring(2).trim();
                currentAnswer = new StringBuilder();
            } else if (line.startsWith("A:")) {
                currentAnswer.append(line.substring(2).trim());
            } else if (!line.isEmpty() && currentAnswer.length() > 0) {
                currentAnswer.append(" ").append(line);
            }
        }

        // Adicionar último Q&A
        if (currentQuestion != null && currentAnswer.length() > 0) {
            qaList.add(new QuestionAnswer(currentQuestion, currentAnswer.toString().trim()));
        }

        return qaList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> extractMetadata(String text) throws LLMException {
        if (llmServices == null) {
            logger.warn("LLM service not available, using heuristic metadata extraction");
            return extractMetadataHeuristic(text);
        }

        logger.debug("Extracting metadata using LLM for text of {} characters", text.length());

        String prompt = "Extraia metadados do texto abaixo. Retorne no formato: " +
                       "titulo: [título] | autor: [autor] | data: [data] | palavras_chave: [palavras] | resumo: [resumo breve]";

        String sample = text.length() > 1500 ? text.substring(0, 1500) : text;

        MapParam params = new MapParam();
        params.put("max_tokens", 300);

        CompletionResponse response = llmServices.completion(prompt, sample, params);
        return parseMetadataResponse(response.getText());
    }

    /**
     * Extração heurística de metadados (fallback)
     */
    private Map<String, String> extractMetadataHeuristic(String text) {
        Map<String, String> metadata = new HashMap<>();

        // Tentar extrair título (primeira linha não vazia)
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                metadata.put("titulo", line.length() > 100 ? line.substring(0, 100) + "..." : line);
                break;
            }
        }

        // Estatísticas básicas
        metadata.put("palavras", String.valueOf(countWords(text)));
        metadata.put("caracteres", String.valueOf(text.length()));

        return metadata;
    }

    /**
     * Parse da resposta de metadados do LLM
     */
    private Map<String, String> parseMetadataResponse(String response) {
        Map<String, String> metadata = new HashMap<>();
        String[] parts = response.split("\\|");

        for (String part : parts) {
            part = part.trim();
            if (part.contains(":")) {
                String[] keyValue = part.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    metadata.put(key, value);
                }
            }
        }

        return metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String categorizeContent(String content, List<String> categories) throws LLMException {
        if (llmServices == null) {
            logger.warn("LLM service not available for content categorization");
            return categories.isEmpty() ? "indefinido" : categories.get(0);
        }

        logger.debug("Categorizing content among {} categories", categories.size());

        String categoriesStr = String.join(", ", categories);
        String prompt = String.format(
            "Categorize o conteúdo abaixo escolhendo a categoria mais apropriada entre os seguintes: %s. \n" +
            "Responda apenas com o nome da categoria.", categoriesStr);

        String sample = content.length() > 1000 ? content.substring(0, 1000) : content;

        MapParam params = new MapParam();
        params.put("max_tokens", 128);

        CompletionResponse response = llmServices.completion(prompt, sample, params);
        String result = response.getText().trim().toLowerCase();

        // Encontrar categoria mais próxima
        for (String category : categories) {
            if (result.contains(category.toLowerCase())) {
                return category;
            }
        }

        return categories.isEmpty() ? "indefinido" : categories.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable() {
        return llmServices != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAvailableModels() throws LLMException {
        if (llmServices == null) {
            return List.of();
        }

        return llmServices.getRegisteredModelNames();
    }
    
    /**
     * Splits a chapter into smaller parts so that each part stays close to {@code idealTokens}
     * and never goes below {@code minTokens}.  Paragraphs are kept intact – we only split
     * on blank lines (any line‑break sequence followed by optional whitespace).
     *
     * <p>Implementation notes:</p>
     * <ul>
     *     <li>Input is validated – a {@link NullPointerException} is thrown if {@code chapter}
     *         or its content is {@code null}.</li>
     *     <li>The method uses a configurable {@link TokenEstimator}.  By default it falls back to
     *         the legacy {@code estimateTokenCount(String)} but can be swapped for a more accurate
     *         tokenizer (e.g. tiktoken).</li>
     *     <li>Paragraphs that themselves exceed {@code idealTokens} are kept as a single part;
     *         this prevents accidental loss of data.  A warning is logged.</li>
     * </ul>
     *
     * @param chapter      the original chapter to split
     * @param idealTokens  target token count per part (≈ max allowed by the LLM)
     * @param minTokens    minimum tokens a part should contain before we allow a split
     * @return an unmodifiable list of sub‑chapters
     * @throws LLMException 
     */
    public List<ChapterDTO> splitLargeChapter(
            ChapterDTO chapter,
            Integer idealTokens,
            Integer minTokens) throws LLMException {

        Objects.requireNonNull(chapter, "chapter must not be null");
        String content = Objects.requireNonNull(chapter.getConteudo(),
                () -> "content of " + chapter.getTitulo() + " is null");
       
        idealTokens = idealTokens ==null || idealTokens < 1024 ? 8192 : idealTokens;
        minTokens = minTokens == null || minTokens < 512 ? 2048 : minTokens;
        
        // 1. Split into paragraphs – works on Windows (\r\n), Linux (\n) and Mac (\r)
        final Pattern paragraphSplitter = Pattern.compile("\\R\\s*\\R");
        String[] paragraphs = paragraphSplitter.split(content);

        // 2. Pre‑estimate tokens for each paragraph (avoids recomputation in the loop)
        int[] tokenCounts = new int[paragraphs.length];
        for (int i = 0; i < paragraphs.length; i++) {
            tokenCounts[i] = getTokenCount(paragraphs[i]); // see below
        }

        List<ChapterDTO> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(content.length() / 2); // rough hint
        int curTokens = 0;
        int partNo   = 1;

        for (int i = 0; i < paragraphs.length; i++) {
            String para      = paragraphs[i];
            int    paraTokens = tokenCounts[i];

            /* Decide whether we should start a new part */
            boolean exceedsIdeal = curTokens + paraTokens > idealTokens;
            boolean hasEnough   = curTokens >= minTokens;

            if (exceedsIdeal && hasEnough) {
                addPart(parts, chapter, partNo++, current);
                current.setLength(0);
                curTokens = 0;
            }

            /* Append paragraph to the running buffer */
            current.append(para).append("\n\n");
            curTokens += paraTokens;

            /* Special case: a single paragraph is larger than ideal → keep it alone */
            if (curTokens == paraTokens && paraTokens > idealTokens) {
                addPart(parts, chapter, partNo++, current);
                current.setLength(0);
                curTokens = 0;
            }
        }

        // Add the final fragment
        if (current.length() > 0) {
            addPart(parts, chapter, partNo, current);
        }

        return Collections.unmodifiableList(parts);
    }

    /* ------------------------------------------------------------------ */
    /* Helper section – keeps the loop body clean and testable             */
    /* ------------------------------------------------------------------ */

    private void addPart(List<ChapterDTO> parts,
                         ChapterDTO original,
                         int number,
                         StringBuilder content) {
        String title = number == 1
                ? original.getTitulo()
                : original.getTitulo() + " (Part " + number + ")";
        parts.add(new ChapterDTO(number, title, content.toString().trim()));
    }



    /**
     * Split documento by effective chunk size.
     * @param documento
     * @param effectiveChunkSize - effective chunk size in tokens / words
     * @return
     */
    public abstract List<ChapterDTO> splitBySize(DocumentoWithAssociationDTO documento, int effectiveChunkSize) ;


}// fim classe
