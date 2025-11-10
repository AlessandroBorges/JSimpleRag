package bor.tools.splitter;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import bor.tools.simplellm.LLMProvider;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocChunkDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.splitter.normsplitter.Artigo;
import bor.tools.splitter.normsplitter.Normativo;
import bor.tools.splitter.normsplitter.NormativosLoader;

/**
 * Classe abstrata para splitting de documentos.
 *
 *
 */
public class SplitterNorma  extends AbstractSplitter{
	private static final Logger logger = LoggerFactory.getLogger(SplitterNorma.class);

	/**
	 * Nível máximo de aninhamento de partes
	 */
	protected static int NIVEL_MAXIMO = 2;
	protected static int MAX_TOKENS = 512;

	/**
	 * Loader de normativos
	 */
	protected NormativosLoader loader;

	protected LLMProvider provedor;

	
	/**
	 * Construtor
	 * @param provedorIA - Provedor de  serviços de IA
	 * @param biblioteca - Library para gestão de documentos
	 *
	 */
	public SplitterNorma(LLMProvider provedor)  {
		super(	provedor	);
		
		this.loader = new NormativosLoader();
	}


	/**
	 * Carrega DocumentoWithAssociationDTO a partir de uma URL.<br>
	 *
	 * @param urlDocumentoDTO - endereço do documento
	 * @return DocumentoWithAssociationDTO carregado
	 *
	 * @throws Exception
	 */
	public DocumentoWithAssociationDTO carregaNorma(@NonNull URL urlDocumento, DocumentoWithAssociationDTO docStub) throws Exception {
		Normativo normativo = loader.load(urlDocumento.toString());
		int[] nivel  = {0};
		return carregaNorma(normativo, nivel, docStub);
	}

	/**
	 * Carrega DocumentoWithAssociationDTO a partir de uma URL.<br>
	 *
	 * @param urlDocumentoDTO - endereço do documento
	 * @param nivel - nivel atual de profundidade de aninhamento
	 *
	 * @return null se o nível de aninhamento for maior que o permitido, ou DocumentoWithAssociationDTO carregado
	 *
	 * @throws Exception
	 */
	public DocumentoWithAssociationDTO carregaNorma(@NonNull URL urlDocumento, int[] nivel, 
		DocumentoWithAssociationDTO docStub) throws Exception {
		if (nivel[0] >= NIVEL_MAXIMO) {
			return null;
		}
		Normativo normativo = loader.load(urlDocumento.toString());
		return carregaNorma(normativo, nivel, docStub);
	}


	/**
	 * Carrega normativo
	 *
	 * @param normativo Instancia de Normativo
	 * @param nivel - nível de aninhamento
	 * @param docStub - DocumentoWithAssociationDTO base. Opcional. Pode ser nulo.
	 * @return DocumentoWithAssociationDTO com o normativo carregado, incluindo partes e embeddings
	 * @throws Exception
	 */
	public DocumentoWithAssociationDTO carregaNorma(@NonNull Normativo normativo, int[] nivel,
		DocumentoWithAssociationDTO docStub) throws Exception {
		if (nivel[0] >= NIVEL_MAXIMO) {
			return null;
		}

		logger.info("Carregando normativo: " + normativo.getUrl());

		// incrementa nível de aninhamento
		nivel[0] = nivel[0] + 1;

		DocumentoWithAssociationDTO doc = docStub == null? new DocumentoWithAssociationDTO():docStub;
		doc.setUrl(normativo.getUrl());
		doc.setTitulo(normativo.getAlias());
		doc.setTexto(normativo.getTexto());

		Date dataStr = normativo.getData_publicacao();
		if (dataStr != null) {
			doc.setDataPublicacao(dataStr);
		}

		List<Artigo> listaArtigos =  normativo.getArtigos();

		LinkedList<String> partes = new LinkedList<>();
		Map<String, ChapterDTO> mapPartes = new LinkedHashMap<>();

		String identificacao = normativo.getId();

		for (Artigo artigo : listaArtigos) {
			artigo.smartSplit();
			String titulo_id = montaTitulo(artigo, identificacao);
			ChapterDTO parte = mapPartes.get(titulo_id);

			if (parte == null) {
				parte = new ChapterDTO();
				doc.addParte(parte);

				parte.setTitulo(titulo_id);
				parte.getMetadados().setNomeDocumento(identificacao + " " + titulo_id);

				String texto = titulo_id + "\n" +  artigo.getConteudo() ;
				parte.setConteudo(texto);

				mapPartes.put(titulo_id, parte);
				partes.add(titulo_id);
			}else {
				String texto = parte.getConteudo() + "\n" + artigo.getConteudo();
				parte.setConteudo(texto);
			}
			criaEmbeddingsBasicos(artigo, parte, titulo_id);
		}
		// cria embeddings para as ChapterDTO s
		for (ChapterDTO capDTO  : doc.getCapitulos()) {
			DocChunkDTO emb = new DocChunkDTO();
			capDTO.addEmbedding(emb);

			String texto = capDTO .getConteudo();
			texto = texto.trim();
			int maxLen = MAX_TOKENS * 4;
			if (texto.length() > maxLen) {
				texto = texto.substring(0, maxLen);
			}
			emb.setTrechoTexto(texto);
			emb.setMetadados(capDTO .getMetadados());
			// identificador é o título da parte
			emb.getMetadados().put("identificador", capDTO .getTitulo());

		}
		// carrega normas associadas
		if (nivel[0] < NIVEL_MAXIMO) {
			carregaNormasAssociadas(normativo, doc, nivel[0]);
			nivel[0] = nivel[0] + 1;
		}
		return doc;
	}

	/**
	 * Carregamento recursivo das normas associadas:
	 * <li>Regulamentos</li>
	 * <li>Normativos Associados</li>
	 * <li>Normativos Anexos</li>
	 *
	 * @param normativo  - normativo de origem
	 * @param doc - DocumentoWithAssociationDTO a ser populado
	 * @param nivel - nivel atual de aninhamento
     *
	 */
	
	protected void carregaNormasAssociadas(Normativo normativo, DocumentoWithAssociationDTO doc, int nivel) {
		// carregamento recursivo das normas relacionadas
		List<Normativo> nAssociados = new ArrayList<>();
		nAssociados.addAll(normativo.getListRegulamentos());
		nAssociados.addAll(normativo.getListNormativosAssociado());
		nAssociados.addAll(normativo.getListNormativosAnexos());

		for (Normativo normativoAssociado : nAssociados) {
			try {
				URL url = new URL(normativoAssociado.getUrl());
				int[] nivelAssociado = {nivel};
				DocumentoWithAssociationDTO docAssociado = carregaNorma(url, nivelAssociado, doc);
				if (docAssociado != null) {
					doc.addAnexo(doc);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Cria embeddings básicos, sem Q&A
	 * @param artigo - artigo a ser processado
	 * @param ChapterDTO  - ChapterDTO  a ser populada
	 *
	 * @param titulo - titulo ou metadados do artigo
	 */
	protected void criaEmbeddingsBasicos(Artigo artigo, ChapterDTO  capDTO , String titulo) {
		String texto = artigo.getConteudo();
		DocChunkDTO emb = new DocChunkDTO();
		//emb.set (titulo);
		emb.setTrechoTexto(titulo + "\n" + texto);
		capDTO.addEmbedding(emb);

		List<String> extras = artigo.getListaSubtexto();
		if (extras != null) {
			for (String extra : extras) {
				DocChunkDTO embExtra = new DocChunkDTO();
				embExtra.setTrechoTexto(titulo + "\n" + extra);
				capDTO .addEmbedding(embExtra);
			}
		}
	}

	/**
	 * Monta Título Hierarquizado de um artigo
	 * @param artigo - artigo a ser montado o título
	 * @param identificacao - nome do normativo
	 * @return
	 */
	private String montaTitulo(Artigo artigo, String identificacao) {
		String tituloArt = artigo.getTitulo();
		String capitulo = artigo.getCapitulo();
		String secao = artigo.getSessao();
		String subsecao = artigo.getSubsessao();

	    String titulo = "";
	    if (tituloArt != null && !tituloArt.isBlank()) {
        	titulo += "Título: " + tituloArt + "\n";
	    }
		if (capitulo != null && !capitulo.isBlank()) {
			titulo += "Capítulo: " + capitulo + "\n";
		}
		if (secao != null && !secao.isBlank()) {
			titulo += "Seção: " + secao + "\n";
		}
		if (subsecao != null && !subsecao.isBlank()) {
			titulo += "Subseção: " + subsecao + "\n";
		}

		if (titulo.isBlank()) {
			// caso seja um normativo sem partes internas
			titulo = identificacao;
		}else {
			titulo = identificacao + " - " + titulo;
		}

		return titulo;
	}


    /**
     * Split DocumentoWithAssociationDTO into ChapterDTO parts.
     *
     * @param documento - DocumentoWithAssociationDTO to be split
     * @return List of ChapterDTO parts
     */
    @Override
    public List<ChapterDTO > splitDocumento(@NonNull DocumentoWithAssociationDTO documento) {
        return documento.getCapitulos();
    }

    /**
     * Carrega DocumentoWithAssociationDTO por URL
     *
     * @param urlDocumentoDTO - URL do documento
     */
    @Override
    public DocumentoWithAssociationDTO carregaDocumento(@NonNull URL urlDocumento, DocumentoWithAssociationDTO docStub) throws Exception {
       return carregaNorma(urlDocumento, docStub);
    }



	/**
	 * Split into paragraphs.<br>
	 *
	 * Nesta implementação, não há separação de parágrafos, pois o texto é tratado
	 * como um único parágrafo.
	 *
	 * @param text     - input text
	 * @param maxChars - maximum characters per paragraph
	 * @return array of paragraphs with text
	 */
	@Override
	public String[] splitIntoSentences(String text, int maxChars) {
		return new String[] {text};
	}

	@Override
	public DocumentoWithAssociationDTO carregaDocumento(String path, DocumentoWithAssociationDTO docStub) throws Exception {      
		URL url = new URL(path);
		return carregaNorma(url, docStub);
	}

	/**
	 * Split by titles specific for legal documents
	 * @param doc    - DocumentoWithAssociationDTO to be split
	 * @param lines  - array of lines from the document
	 * @param titles - list of detected TitleTag
	 */
	@Override
	protected List<ChapterDTO> splitByTitles(DocumentoWithAssociationDTO doc, 
						 String[] lines, 
						 List<TitleTag> titles) 
	{
		logger.debug("Splitting legal document by titles. Found {} titles", titles.size());

		if (titles.isEmpty()) {
			return List.of(createSingleChapter(doc));
		}	
		
		List<TitleTag> secoes = extractSecoes(titles);
		if (secoes.isEmpty()) {
			return List.of(createSingleChapter(doc));
		}

		for (int i = 0; i < secoes.size(); i++) {
			TitleTag secao = secoes.get(i);
			int startPos = secao.getPosition();
			int endPos = (i + 1 < secoes.size()) ? secoes.get(i + 1).getPosition() : lines.length;
			
			String conteudo = extractContent(lines, startPos, endPos);
			ChapterDTO capitulo = createCapitulo(secao.getTitle(), conteudo, i + 1, doc);			
		}
		logger.debug("Created {} chapters from legal document", doc.getCapitulos().size());
		return doc.getCapitulos();
	}

	/**
	 * Cria um único capítulo com todo o conteúdo do documento
	 */
	private ChapterDTO createSingleChapter(DocumentoWithAssociationDTO doc) {			
		ChapterDTO capitulo = doc.createAndAddNewChapter(doc.getTitulo(),doc.getConteudoMarkdown());
		return capitulo;
	}

	/**
	 * Extrai seções relevantes da lista de títulos
	 */
	private List<TitleTag> extractSecoes(List<TitleTag> titles) {
		return titles.stream()
			.filter(t -> "secao".equals(t.getTag()) || 
						("capitulo".equals(t.getTag()) && isFirstCapitulo(t, titles)))
			.toList();
	}

	/**
	 * Verifica se um capítulo é o primeiro (não há seção antes dele)
	 */
	private boolean isFirstCapitulo(TitleTag capitulo, List<TitleTag> titles) {
		return titles.stream()
			.noneMatch(t -> "secao".equals(t.getTag()) && t.getPosition() < capitulo.getPosition());
	}

	/**
	 * Extrai o conteúdo entre duas posições do array de linhas
	 */
	private String extractContent(String[] lines, int start, int end) {
		StringBuilder content = new StringBuilder();
		for (int i = start; i < end; i++) {
			if (!lines[i].trim().isEmpty()) {
				content.append(lines[i]).append("\n");
			}
		}
		return content.toString().trim();
	}

	/**
	 * Cria um ChapterDTO com todos os metadados necessários
	 */
	private ChapterDTO createCapitulo(String titulo, String conteudo, Integer ordemDoc, DocumentoWithAssociationDTO doc) {
	
		ChapterDTO capitulo = doc.createAndAddNewChapter(titulo, conteudo);
		if (ordemDoc != null && ordemDoc > 0) {
			capitulo.setOrdemDoc(ordemDoc);
		}
		capitulo.getMetadados().put("tipo_secao", "normativo");
		capitulo.getMetadados().put("nivel_hierarquico", "secao");
		
		return capitulo;
	}

	@Override
	protected List<TitleTag> detectTitles(String[] lines) {
	    logger.debug("Detecting titles in legal document with {} lines", lines.length);

	    List<TitleTag> titles = new ArrayList<>();

	    // Padrões regex para elementos de normativos
	    Pattern artigoPattern = Pattern.compile("^\\s*Art\\.?\\s+\\d+", Pattern.CASE_INSENSITIVE);
	    Pattern capituloPattern = Pattern.compile("^\\s*CAP[ÍI]TULO\\s+[IVX\\d]+", Pattern.CASE_INSENSITIVE);
	    Pattern secaoPattern = Pattern.compile("^\\s*SE[ÇC][ÃA]O\\s+[IVX\\d]+", Pattern.CASE_INSENSITIVE);
	    Pattern subsecaoPattern = Pattern.compile("^\\s*SUBSE[ÇC][ÃA]O\\s+[IVX\\d]+", Pattern.CASE_INSENSITIVE);
	    Pattern tituloPattern = Pattern.compile("^\\s*T[ÍI]TULO\\s+[IVX\\d]+", Pattern.CASE_INSENSITIVE);
	    Pattern livroPattern = Pattern.compile("^\\s*LIVRO\\s+[IVX\\d]+", Pattern.CASE_INSENSITIVE);

	    for (int i = 0; i < lines.length; i++) {
	        String line = lines[i].trim();
	        if (line.isEmpty()) {
	            continue;
	        }

	        TitleTag titleTag = null;

	        // Verificar diferentes níveis hierárquicos de normativos
	        if (livroPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("livro");
	            titleTag.setLevel(1);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        } else if (tituloPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("titulo");
	            titleTag.setLevel(2);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        } else if (capituloPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("capitulo");
	            titleTag.setLevel(3);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        } else if (secaoPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("secao");
	            titleTag.setLevel(4);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        } else if (subsecaoPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("subsecao");
	            titleTag.setLevel(5);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        } else if (artigoPattern.matcher(line).find()) {
	            titleTag = new TitleTag();
	            titleTag.setTag("artigo");
	            titleTag.setLevel(6);
	            titleTag.setTitle(line);
	            titleTag.setPosition(i);
	        }

	        if (titleTag != null) {
	            titles.add(titleTag);
	            logger.debug("Detected title: {} at line {}", titleTag.getTitle(), i);
	        }
	    }

	    logger.debug("Total titles detected: {}", titles.size());
	    return titles;
	}


	@Override
	public List<ChapterDTO> splitBySize(DocumentoWithAssociationDTO documento, int effectiveChunkSize) {
	   throw new UnsupportedOperationException("Split by size not supported for legal documents");
	}

}
