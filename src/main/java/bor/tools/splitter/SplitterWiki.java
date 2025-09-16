package bor.tools.splitter;

import static bor.tools.splitter.wiki.WikiProcessor.SUMARIO;
import static bor.tools.splitter.wiki.WikiProcessor.TEXTO_MD_LIMPO;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bor.tools.splitter.wiki.WikiLoader;
import bor.tools.splitter.wiki.WikiPage;
import bor.tools.splitter.wiki.WikiProcessor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import superag.provedorIA.Provedor;

import superag.retriever.model.Biblioteca;
import superag.retriever.model.DocEmbeddings;
import superag.retriever.model.DocParte;
import superag.retriever.model.Documento;
import superag.retriever.model.MapMeta;

/**
 * Splitter para documentos Wiki.
 */
@Data
@EqualsAndHashCode(callSuper=true)
public class SplitterWiki extends AbstractSplitter {

	/** Logger */
	private static Logger logger = LoggerFactory.getLogger(SplitterWiki.class);

    protected Set<String> loadedEntries = new LinkedHashSet<>();
    protected WikiLoader wikiLoader;
    protected int maxArtigosRelacionados = 4 ;
    protected int maxDepth = 2;
    protected int maxVerbetes = 5;
    protected int maxQAporSessão = -1;

    protected Provedor provedor;
    protected WikiProcessor wikiProcessor;

    /**
	 * Construtor
	 *
     * @param biblioteca
     * @param validator
     * @param maxArtigosRelacionados
     * @param maxDepth
     */
    public SplitterWiki(Biblioteca biblioteca,
                       ExistsArtefato<Documento> validator,
                       int maxArtigosRelacionados,
                       int maxDepth) {
        super(biblioteca, validator);
        this.wikiLoader = new WikiLoader();
        this.maxArtigosRelacionados = maxArtigosRelacionados;
        this.maxDepth = maxDepth;
    }



    /**
	 * Construtor
	 *
     * @param biblioteca
     * @param validator
     * @param maxArtigosRelacionados
     * @param maxDepth
     */
    public SplitterWiki(Provedor provedor,
    				    Biblioteca biblioteca
                       ) {
        super(biblioteca, null);
        this.provedor = provedor;
        this.wikiLoader = new WikiLoader();      
    }

    
    /**
     * Faz o split do documento em partes.<br>
     * 
     * Nota: As partes criadas estão incluidas na lista de partes do documento.
     * A List Documento#getPartes() é atualizado com as partes criadas.
     *  <br>
     * 
     * @param documento documento a ser dividido
     * @return lista de partes do documento
     * 
     * @see Documento
     * @see DocParte
     */
    @Override
    public List<DocParte> splitDocumento(Documento documento) {
        List<DocParte> parts = new ArrayList<>();
        int partCounter = 1;
        // Add summary as first part
        if (documento.getSumario() != null) {
            DocParte summaryPart = new DocParte();
            summaryPart.setTitulo(documento.getTitulo());
            summaryPart.setParte(partCounter++);
            summaryPart.setCapitulo("Sumário");
            summaryPart.setTexto(documento.getSumario());
            summaryPart.setDataAlteracao(getDataNormalizada());
            summaryPart.setNivelAcesso(documento.getNivelAcesso());
            parts.add(summaryPart);
        }

        // Process main text
        String[] sections = splitIntoSections(documento.getTexto());
       

        for (String section : sections) {
            if (section.trim().isEmpty()) continue;

            HeaderInfo headerInfo = extractHeader(section);
            if (headerInfo != null) {
                DocParte part = new DocParte();
                part.setTitulo(documento.getTitulo());
                part.setParte(partCounter++);
                part.setCapitulo(headerInfo.title());

                part.getMetadados().setDescricao(headerInfo.metadata());
                part.setTexto(headerInfo.content());
                part.setDataAlteracao(getDataNormalizada());
                part.setNivelAcesso(documento.getNivelAcesso());
                parts.add(part);
            }
        }
        
        if(parts.isEmpty()) {
			DocParte part = new DocParte();
			part.setTitulo(documento.getTitulo());
			part.setParte(partCounter++);
			part.setCapitulo("Texto");
			part.setTexto(documento.getTexto());
			part.setDataAlteracao(getDataNormalizada());
			part.setNivelAcesso(documento.getNivelAcesso());
			parts.add(part);
		}

        documento.addPartes(parts);
        return parts;
    }

    /**
     * Split text into sections based on headings
     * @param text text to split
     * @return array of sections
     */
    private String[] splitIntoSections(String text) {
    	return text.split("(?<=\\n)(?=(={1,6}\\s[^=]+={1,6}))");
    }

    private record HeaderInfo(String title, String metadata, String content) {}

    /**
     * Extract header information from a section.<br>
     * This method is responsible for extracting header information from a
     * given section of text. It returns an instance of the HeaderInfo record,
     * which contains the title, metadata, and content of the header.
     *
     * @param section
     * @return
     */
    private HeaderInfo extractHeader(String section) {
        String[] lines = section.split("\n", 2);
        if (lines.length < 2) return null;

        String headerLine = lines[0].trim();
        if (!headerLine.startsWith("=")) return null;

        // Remove = symbols and trim
        String title = headerLine.replaceAll("=+$", "").replaceAll("^=+", "").trim();
        String content = lines[1].trim();
        String metadata = extractMetadata(title, content);

        return new HeaderInfo(title, metadata, content);
    }

    /**
     * Extrai título e conteúdo inicial do documento.<br>
     * @param title
     * @param content
     * @return
     */
    public String extractMetadata(String title, String content) {
        // Extract first paragraph as metadata
        int endOfFirstPara = content.indexOf("\n\n");
        if (endOfFirstPara > 512) {
        	int firstDot = content.indexOf(".");
        	endOfFirstPara = firstDot > 512 ? 512 : content.indexOf(".", firstDot);
        }
        String meta =  endOfFirstPara > 0 ?
               content.substring(0, endOfFirstPara).trim() :
               title;
        meta  = "titulo: " + meta + "\n";
        return meta;
    }

    /**
     * Carrega um documento a partir de um caminho fornecido.<br>
     * Atualiza wikiProcessor
     * <p>
     * Este método carrega um documento a partir de um URL ou de um caminho de arquivo local.
     * Se o caminho começar com "http", ele será tratado como um URL e o documento será carregado
     * a partir da web. Caso contrário, o caminho será tratado como um caminho de arquivo local.
     * </p>
     *
     * @param url - URL do documento a ser carregado.
     * @param docStub Um objeto Documento opcional que pode ser usado como base para o documento carregado.
     * @return O documento carregado.
     * @throws Exception Se ocorrer um erro ao carregar o documento, como um arquivo não encontrado.
     */
    @Override
    public Documento carregaDocumento(URL url, Documento docStub) throws Exception {
        if (documentoPresenteNaBase(url.toString())) {
            return null;
        }

        WikiPage wikiPage = wikiLoader.loadPage(url);
        if (wikiPage == null) {
            return null;
        }
        Documento doc = parseWikiPage(wikiPage);
        return doc;
    }

	/**
	 * Parse a WikiPage into a Documento object.
	 *
	 * @param wikiPage - WikiPage to parse
	 * @return - Documento object
	 */
    protected Documento parseWikiPage(WikiPage wikiPage) {
    	Map<String,Object> wikiMap = wikiProcessor.process();
    	if (wikiMap == null) {
			logger.error("Erro ao processar a página wiki: {}", wikiPage.getTitle());
			return null;
		}
    	
    	Documento doc = new Documento();
    	doc.setUrl(wikiPage.getURL());
    	doc.setTitulo(wikiPage.getTitle());
    	doc.setTexto(wikiMap.get(TEXTO_MD_LIMPO).toString());
    	doc.setSumario(wikiMap.get(SUMARIO).toString());
    	doc.setDataAlteracao(getDataNormalizada());
    	doc.setBiblioteca(biblioteca);
    	return doc;
	}



    /**
     * Carrega um documento a partir de um caminho fornecido.
     * <p>
     * Este método carrega um documento a partir de um URL ou de um caminho de arquivo local.
     * Se o caminho começar com "http", ele será tratado como um URL e o documento será carregado
     * a partir da web. Caso contrário, o caminho será tratado como um caminho de arquivo local.
     * </p>
     *
     * @param path O caminho do documento a ser carregado. Pode ser um URL ou um caminho de arquivo local.
     * @param docStub Um objeto Documento opcional que pode ser usado como base para o documento carregado.
     * @return O documento carregado.
     * @throws Exception Se ocorrer um erro ao carregar o documento, como um arquivo não encontrado.
     */
	@SuppressWarnings("deprecation")
	@Override
	public Documento carregaDocumento(String path, Documento docStub) throws Exception {
		if (path.startsWith("http")) {
			return carregaDocumento(new URL(path), docStub);
		} else {
			File file = new File(path);
			if (!file.exists()) {
				logger.error("Arquivo não encontrado: {}", path);
				throw new FileNotFoundException(path);
			}
			return carregaDocumento(file.toURI().toURL(), docStub);
		}
	}

	/**
	 * Carrega um documento a partir de nome de artigo ou verbete.
	 *
	 * @param artigo - nome do artigo ou verbete
	 * @param lang - linguagem de origem
	 * @param pt - versão
	 * @return
	 */
	public Documento carregaDocumento(String artigo, Lang langOrigem, Lang langTraduzido) {
		if (documentoPresenteNaBase(artigo)) {
			return null;
		}

		WikiPage wikiPage = wikiLoader.loadPage(artigo, langOrigem);
		if (wikiPage == null) {
			return null;
		}
		Documento doc = parseWikiPage(wikiPage);
		logger.info("Carregado documento: {}", doc.getTitulo());
		if(langOrigem != langTraduzido) {
            doc = traduzDocumento(doc, langTraduzido);
        }
		return doc;
	}

	/**
	 *
	 * @param doc
	 * @param langTraduzido
	 * @return
	 */
	private Documento traduzDocumento(Documento doc, Lang langTraduzido) {
		if (provedor != null) {
			var either = provedor.traduzirDocumento(doc, langTraduzido, langTraduzido);
			if (either.isRight()) {
				doc = either.getRight();
			} else {
				logger.error("Erro ao traduzir documento: {}", either.getLeft());
			}
		}
		return doc;
	}

	/**
	 * Cria os embeddings para o documento.
	 * @param doc - documento
	 * @param i
	 * @param n
	 */
	public void createDocEmbeddings(Documento doc) {		
		List<DocParte> partes = doc.getPartes();
		for (DocParte parte : partes) {
			createDocEmbeddings(parte);
		}
	}

	/**
	 * Cria os embeddings para o documento.
	 * @param parte
	 * @return
	 */
	public List<DocEmbeddings>  createDocEmbeddings(DocParte parte) {
		MapMeta metadados = parte.getMetadados();
		{
			String titulo = parte.getTitulo();
			String capitulo = parte.getCapitulo();
			metadados.setNomeDocumento(titulo);
			metadados.setCapitulo(capitulo);
		}
		
		List<DocEmbeddings> chunks = parte.prepareDocEmbeddings();
		return chunks;
	}

}





