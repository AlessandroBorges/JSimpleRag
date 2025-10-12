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

import bor.tools.simplellm.LLMService;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
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

	protected LLMService provedor;

	
	/**
	 * Construtor
	 * @param provedorIA - Provedor de  serviços de IA
	 * @param biblioteca - Library para gestão de documentos
	 *
	 */
	public SplitterNorma(LLMService provedor)  {
		super(	provedor	);
		
		this.loader = new NormativosLoader();
	}


	/**
	 * Carrega DocumentoDTO a partir de uma URL.<br>
	 *
	 * @param urlDocumentoDTO - endereço do documento
	 * @return DocumentoDTO carregado
	 *
	 * @throws Exception
	 */
	public DocumentoDTO carregaNorma(@NonNull URL urlDocumento, DocumentoDTO docStub) throws Exception {
		Normativo normativo = loader.load(urlDocumento.toString());
		int[] nivel  = {0};
		return carregaNorma(normativo, nivel, docStub);
	}

	/**
	 * Carrega DocumentoDTO a partir de uma URL.<br>
	 *
	 * @param urlDocumentoDTO - endereço do documento
	 * @param nivel - nivel atual de profundidade de aninhamento
	 *
	 * @return null se o nível de aninhamento for maior que o permitido, ou DocumentoDTO carregado
	 *
	 * @throws Exception
	 */
	public DocumentoDTO carregaNorma(@NonNull URL urlDocumento, int[] nivel, 
		DocumentoDTO docStub) throws Exception {
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
	 * @param docStub - DocumentoDTO base. Opcional. Pode ser nulo.
	 * @return DocumentoDTO com o normativo carregado, incluindo partes e embeddings
	 * @throws Exception
	 */
	public DocumentoDTO carregaNorma(@NonNull Normativo normativo, int[] nivel,
		DocumentoDTO docStub) throws Exception {
		if (nivel[0] >= NIVEL_MAXIMO) {
			return null;
		}

		logger.info("Carregando normativo: " + normativo.getUrl());

		// incrementa nível de aninhamento
		nivel[0] = nivel[0] + 1;

		DocumentoDTO doc = docStub == null? new DocumentoDTO():docStub;
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
			DocumentEmbeddingDTO emb = new DocumentEmbeddingDTO();
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
	 * @param doc - DocumentoDTO a ser populado
	 * @param nivel - nivel atual de aninhamento
     *
	 */
	
	protected void carregaNormasAssociadas(Normativo normativo, DocumentoDTO doc, int nivel) {
		// carregamento recursivo das normas relacionadas
		List<Normativo> nAssociados = new ArrayList<>();
		nAssociados.addAll(normativo.getListRegulamentos());
		nAssociados.addAll(normativo.getListNormativosAssociado());
		nAssociados.addAll(normativo.getListNormativosAnexos());

		for (Normativo normativoAssociado : nAssociados) {
			try {
				URL url = new URL(normativoAssociado.getUrl());
				int[] nivelAssociado = {nivel};
				DocumentoDTO docAssociado = carregaNorma(url, nivelAssociado, doc);
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
		DocumentEmbeddingDTO emb = new DocumentEmbeddingDTO();
		//emb.set (titulo);
		emb.setTrechoTexto(titulo + "\n" + texto);
		capDTO.addEmbedding(emb);

		List<String> extras = artigo.getListaSubtexto();
		if (extras != null) {
			for (String extra : extras) {
				DocumentEmbeddingDTO embExtra = new DocumentEmbeddingDTO();
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


    @Override
    public List<ChapterDTO > splitDocumento(@NonNull DocumentoDTO documento) {
        return documento.getCapitulos();
    }

    /**
     * Carrega DocumentoDTO por URL
     *
     * @param urlDocumentoDTO - URL do documento
     */
    @Override
    public DocumentoDTO carregaDocumento(@NonNull URL urlDocumento, DocumentoDTO docStub) throws Exception {
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
	public DocumentoDTO carregaDocumento(String path, DocumentoDTO docStub) throws Exception {      
		URL url = new URL(path);
		return carregaNorma(url, docStub);
	}


	@Override
	protected List<ChapterDTO> splitByTitles(DocumentoDTO doc, String[] lines, List<TitleTag> titles) {
	    logger.debug("Splitting legal document by titles. Found {} titles", titles.size());

	    if (titles.isEmpty()) {
	        // Se não há títulos, criar um único capítulo com todo o conteúdo
	        ChapterDTO capitulo = new ChapterDTO();
	        capitulo.setTitulo(doc.getTitulo());
	        capitulo.setConteudo(doc.getTexto());
	        capitulo.setOrdemDoc(1);
	        return List.of(capitulo);
	    }

	    List<ChapterDTO> capitulos = new ArrayList<>();
	    String currentSection = null;
	    StringBuilder currentContent = new StringBuilder();
	    int currentChapterNumber = 1;

	    // Agrupar por Seções (nível 4) que serão mapeadas para ChapterDTO
	    for (int i = 0; i < titles.size(); i++) {
	        TitleTag title = titles.get(i);

	        // Se encontramos uma seção, ela se torna um novo ChapterDTO
	        if ("secao".equals(title.getTag()) ||
	            ("capitulo".equals(title.getTag()) && currentSection == null)) {

	            // Finalizar seção anterior
	            if (currentSection != null && currentContent.length() > 0) {
	                ChapterDTO capitulo = createCapituloFromSection(currentSection,
	                                                               currentContent.toString().trim(),
	                                                               currentChapterNumber++);
	                capitulos.add(capitulo);
	            }

	            // Iniciar nova seção
	            currentSection = title.getTitle();
	            currentContent = new StringBuilder();

	            // Adicionar conteúdo da linha do título
	            if (title.getPosition() < lines.length) {
	                currentContent.append(lines[title.getPosition()]).append("\n");
	            }
	        } else {
	            // Para outros tipos de título (artigo, subseção), adicionar ao conteúdo atual
	            if (title.getPosition() < lines.length) {
	                currentContent.append(lines[title.getPosition()]).append("\n");
	            }
	        }

	        // Adicionar conteúdo entre títulos
	        int startPos = title.getPosition() + 1;
	        int endPos = (i + 1 < titles.size()) ? titles.get(i + 1).getPosition() : lines.length;

	        for (int j = startPos; j < endPos; j++) {
	            String line = lines[j];
	            if (!line.trim().isEmpty()) {
	                currentContent.append(line).append("\n");
	            }
	        }
	    }

	    // Finalizar última seção
	    if (currentSection != null && currentContent.length() > 0) {
	        ChapterDTO capitulo = createCapituloFromSection(currentSection,
	                                                       currentContent.toString().trim(),
	                                                       currentChapterNumber);
	        capitulos.add(capitulo);
	    }

	    // Se não foi possível criar capítulos por seções, criar por conteúdo completo
	    if (capitulos.isEmpty()) {
	        ChapterDTO capitulo = new ChapterDTO();
	        capitulo.setTitulo(doc.getTitulo());
	        capitulo.setConteudo(doc.getTexto());
	        capitulo.setOrdemDoc(1);
	        capitulos.add(capitulo);
	    }

	    logger.debug("Created {} chapters from legal document", capitulos.size());
	    return capitulos;
	}

	/**
	 * Cria um ChapterDTO a partir de uma seção de normativo
	 */
	private ChapterDTO createCapituloFromSection(String sectionTitle, String content, int ordem) {
	    ChapterDTO capitulo = new ChapterDTO();
	    capitulo.setTitulo(sectionTitle);
	    capitulo.setConteudo(content);
	    capitulo.setOrdemDoc(ordem);

	    // Adicionar metadados específicos de normativos
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
	public List<ChapterDTO> splitBySize(DocumentoDTO documento, int effectiveChunkSize) {
	   throw new UnsupportedOperationException("Split by size not supported for legal documents");
	}

}


