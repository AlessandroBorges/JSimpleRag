package bor.tools.splitter;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bor.tools.utils.RAGUtil;
import lombok.Data;

import bor.tools.simplerag.entity.*;
import bor.tools.simplellm.*;
import bor.tools.simplellm.exceptions.LLMException;

/**
 * <h2>ISplitter de documentos.</h2>
 * Previsão de implementação de diferentes estratégias de split, incluindo:
 * <li> Split de Normativos e Leis (por artigos, parágrafos, incisos, alíneas, etc)
 * <li> Split de Contrato (por cláusulas, parágrafos, incisos, alíneas, etc)
 * <li> Split de Documento ou  Documento Técnico (por seções, subseções, parágrafos, etc)
 * <li> Split de Nota Técnica (por seções, subseções, parágrafos, etc)
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

    protected final Biblioteca biblioteca;
    /**
     * Validador de existência de documento na base de conhecimento
     */
    protected ExistsArtefato<Documento> existsDocValidator;

    private  Date dataNormalizada;
    private  Integer nivelAcesso;
    //private final Sumarizador sumarizador = new Sumarizador();


    protected MetadataEnhancer metadataEnhancer;
    protected MetadataValidator metadataValidator;

	/**
	 * Construtor
	 * @param provedor - Provedor de IA
	 * @param biblioteca - Biblioteca de documentos
	 * @param valitador - validador de existência de documento
	 *
	 * @see Provedor
	 * @see Biblioteca
	 * @see ExistsArtefato
	 *
	 */
    public AbstractSplitter(Biblioteca biblioteca, ExistsArtefato<Documento> validator) {
        this.biblioteca = biblioteca;
        this.existsDocValidator = validator;
        this.dataNormalizada = new Date();
        this.nivelAcesso = 1;
    }

    /**
     * Divite um texto em Parágrafos
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

    public void enrichDocumentMetadata(MetadataEnhancer metadataEnhancer, Documento doc) {
    	this.metadataEnhancer = metadataEnhancer;
    	enrichDocumentMetadata(doc);
    }

    public void enrichDocumentMetadata(Documento doc) {
        // Document-level metadata
	/*
    	var either = metadataEnhancer.enhanceDocumentMetadata(doc);

    	if (either.isRight()) {
    		EnhancedMetadata doc_metadata = either.getRight();
    		doc.updateMetadata(doc_metadata);


            // Enrich parts with contextual metadata
            for (DocParte parte : doc.getPartes()) {
            	//parte.updateMetadata(doc_metadata);
                enrichPartMetadata(parte, doc_metadata);
            }

    	} else {
    		logger.error("Erro ao enriquecer metadados do documento: " + either.getLeft().toString());
    	}
*/

    }

    protected void enrichPartMetadata(Capitulo parte, EnhancedMetadata contextMetadata) {
	/*
        // Combine context with part-specific metadata
    	EnhancedMetadata part_metadata = parte.updateMetadata(contextMetadata);
        // Validate and potentially improve metadata quality
        for (DocEmbeddings emb : parte.getDocEmbeddings()) {
        	emb.updateMetadata(part_metadata);
            metadataValidator.validateAndEnrich(emb);
        }
        */
    }



    /**
     * Realiza Split de texto em sentenças.
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
	 * This method counts the number of words in a given text.
	 * It iterates through each character of the input string and checks for whitespace characters.
	 * When it encounters a whitespace character, it increments the word count if it was previously inside a word.
	 * The method uses a boolean flag 'inWord' to track whether the current character is part of a word.
	 * Finally, if the last character in the text is not a whitespace, it increments the word count one last time.
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
     * @param src - texto de entrada
     * @return texto sem tachados
     */
	public String removerTachado(String src) {
		return RAGUtil.removeStrikeThrough(src) ;
	}

	/**
	 * Retorna a data normalizada, com as horas arredondada para multiplos de 2 minutos
	 * @return
	 */

	@SuppressWarnings("deprecation")
	public Date getDataNormalizada() {
		long old = dataNormalizada.getTime();
		long now = System.currentTimeMillis();

		if (now - old > 120000) {

			// Restore the use of Date class without deprecated methods
			dataNormalizada.setTime(now);
			int minute = (dataNormalizada.getMinutes()) % 60; // Calculate total
																								// minutes
			int newMinute = (minute % 2 == 0) ? minute : minute + 1;
			dataNormalizada.setMinutes(newMinute);
			dataNormalizada.setSeconds(0);

		}
		return dataNormalizada;
	}

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
		//author
		Element author = doc.select("meta[name=author]").first();
		if (author != null) {
			props.put("autor", author.attr("content"));
		}
		//charset
		Element charset = doc.select("meta[charset]").first();
		if (charset != null) {
			props.put("charset", charset.attr("charset"));
		}
		return props;
	}

	/**
	 * Verifica se um documento existe na base de dados
	 * @param url
	 * @return
	 */
    protected boolean documentoPresenteNaBase(String url) {
        if (existsDocValidator == null) {
            return false;
        }
        Documento doc = new Documento();
        doc.setUrl(url);
        return existsDocValidator.exists(doc);
    }

	/**
	 * Carrega um documento a partir de uma URL,
	 * sem um Documento stub, isto é, parcialmente inicializado.
	 *
	 *
	 * @param url - URL do documento
	 * @return Documento
	 * @throws Exception
	 */
    public Documento carregaDocumento(URL url) throws Exception {
    	return carregaDocumento(url, new Documento());
    }


    /**
     * Carrega um documento a partir de uma URL, usando um Documento stub,
     *  isto é, um documento já inicializado.
     *
     *  <pre>
     *  Documento docStub = new Documento();
     *  docStub.setUrl(url);
     *  docStub.setTitulo("Título");
     *  docStub.setBibloteca(biblioteca);
     *  docStub.setNivelAcesso(1);
     *
     *  // carregando o documento
     *  Documento doc = carregaDocumento(url, docStub);
     *
     *
     *  </pre>
     */
    @Override
public abstract Documento carregaDocumento(URL url, Documento docStub) throws Exception;

	 /**
     * Clarifica um texto
     * @param texto - texto de entrada
     * @return
     */
	public String clarifiqueTexto(LLMService provedorIA, String texto) {
		return clarifiqueTexto(provedorIA, texto, null);
	}

    /**
     *
     * @param texto - texto a ser clarificado     *
     * @param prompt - texto de prompt
     * @return texto ajustado, clarificado
     * @throws LLMException 
     */
	public String clarifiqueTexto(LLMService provedorIA, String texto, String prompt) throws LLMException {
		if(prompt==null || prompt.isBlank()) {
			prompt = "Rescreva o texto a ser apresentado na lingua Português do Brasil, "
					+ "de forma clara, informativa e concisa, "
					+ "mantendo o sentido original e os seus principais elementos. "
					+ "Evite repetições e redundâncias, não adicione informações que não "
					+ "estão presentes no texto original.";

		}
		
		MapParam params = new MapParam();
		CompletionResponse response  =  provedorIA.completion(prompt, texto, params );		
			return response.getText();
	}//

	/**
	 * Remove repetições de palavras em um texto.
	 * <p>
	 * Este método remove sequências de palavras repetidas (de uma até três palavras)
	 * que aparecem consecutivamente em cada linha do texto fornecido.
	 * Ele preserva as quebras de linha do texto original.
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



	public static String detectaNome(String pathName) {
		return RAGUtil.detectaNome(pathName);
	}

}// fim classe
