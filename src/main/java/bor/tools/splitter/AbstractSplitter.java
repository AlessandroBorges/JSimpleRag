package bor.tools.splitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import bor.tools.utils.RAGUtil;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import bor.tools.simplerag.dto.CapituloDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.entity.*;
import bor.tools.simplellm.*;
import bor.tools.simplellm.exceptions.LLMException;

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
 * @see Provedor
 * @see Biblioteca
 * @see ExistsArtefato
 * @see Documento
 */
@Data
public abstract class AbstractSplitter implements DocumentSplitter, DocumentPreprocessor {
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
	if (llmServices != null) {
	    return llmServices.tokenCount(text, null);
	}
	return text.length() / 4; // Estimativa simples: 1 token ~ 4 caracteres	
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
    protected void enrichPartMetadata(CapituloDTO parte, Metadata contextMetadata) {
	
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
	return RAGUtil.removerRepeticoes(src);
    }

    /**
     * Remove texto tachado
     * 
     * @param src - texto de entrada
     * @return texto sem tachados
     */
    public String removerTachado(String src) {
	return RAGUtil.removeStrikeThrough(src);
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
	if (!RAGUtil.isHtml(html)) {
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
	return RAGUtil.detectaNome(pathName);
    }

    /**
     * Split de um documento em capítulos.
     * 
     */
    public abstract List<CapituloDTO> splitDocumento(DocumentoDTO DocumentoDTO);
    
    
    /**
     * Carrega DocumentoDTO a partir de uma URL.
     * @param urlDocumento URL do DocumentoDTO
     * @param docStub DocumentoDTO para preencher, se null, cria um novo
     * 
     */
    public DocumentoDTO carregaDocumento(@NonNull URL urlDocumento, DocumentoDTO docStub) throws Exception {
	DocumentoDTO doc = docStub == null ? new DocumentoDTO() : docStub;
	doc.setUrl(urlDocumento.toString());
	OkHttpClient client = RAGUtil.getUnsafeOkHttpClient();
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

		// Fazendo o parsing do DocumentoDTO
		parser.parse(input, handler, metadata, context);

		{
		    String contentType = metadata.get("Content-Type");
		    // conteudo HTML e XHTML
		    if (contentType != null
			    && (contentType.contains("text/html") || contentType.contains("application/xhtml+xml"))) {
			String content = handler.toString();
			String md = RAGUtil.convertToMarkdown(content);
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
     * Carrega DocumentoDTO a partir de um arquivo local ou em rede.
     * 
     * @param path caminho do arquivo
     * @param docStub DocumentoDTO para preencher, se null, cria um novo
     * @return DocumentoDTO carregado
     */
    public DocumentoDTO carregaDocumento(String path, DocumentoDTO docStub) throws Exception {
	if (path.matches("^(http|.*\\.(html|xhtml|htm|xml))$")) {
	    return carregaDocumento(new URI(path).toURL(), docStub);
	} else {
	    DocumentoDTO doc = docStub == null ? new DocumentoDTO() : docStub;
	    doc.setUrl(path);
	    if (doc.getTitulo() == null) {
		String titulo = AbstractSplitter.detectaNome(path);
		doc.setTitulo(titulo);
	    }
	    byte[] data = RAGUtil.lerArquivoBinario(path);
	    String textoMD = RAGUtil.convertToMarkdown(data);
	    String[] lines = textoMD.split("\n");
	    List<TitleTag> titles = detectTitles(lines);
	    List<CapituloDTO> partes = splitByTitles(doc, lines, titles);
	    for (CapituloDTO parte : partes) {
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
     * @param doc    - DocumentoDTO
     * @param lines  - lines of text
     * @param titles - detected titles
     * @return list of CapituloDTO
     */    
    protected abstract List<CapituloDTO> splitByTitles(DocumentoDTO doc, String[] lines, List<TitleTag> titles);

    /**
     * Detect titles in text lines.
     * 
     * @param lines - lines of text
     * @return list of detected TitleTag
     */
    protected abstract List<TitleTag> detectTitles(String[] lines);
    

}// fim classe
