package bor.tools.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import bor.tools.simplellm.LLMService;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Splitter especializado para conteúdo tipo Wikipedia.
 *
 * Este splitter é otimizado para processar artigos enciclopédicos que seguem
 * estruturas similares ao Wikipedia, onde splitIntoParagraphs() é o modo
 * default para split secundário (Capítulos → DocEmbeddings).
 *
 * Características:
 * - Detecção de seções típicas de Wikipedia
 * - Processamento de infoboxes
 * - Limpeza de referencias e links
 * - Split em parágrafos como modo padrão para embeddings
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class SplitterWiki extends AbstractSplitter {

    private static final Logger logger = LoggerFactory.getLogger(SplitterWiki.class);

    /**
     * Tamanho padrão de palavras por capítulo para conteúdo Wiki
     */
    private int maxWordsPerChapter = 2000;

    /**
     * Se deve limpar referencias e links do Wikipedia
     */
    private boolean cleanWikiMarkup = true;

    /**
     * Se deve processar infoboxes separadamente
     */
    private boolean processInfoboxes = true;

    public SplitterWiki() {
        super();
    }

    public SplitterWiki(LLMService llmService) {
        super(llmService);
    }

    /**
     * Split principal do documento em capítulos
     */
    @Override
    public List<ChapterDTO> splitDocumento(@NonNull DocumentoWithAssociationDTO documento) {
        logger.debug("Splitting Wikipedia-style document: {}", documento.getTitulo());

        String content = documento.getTexto();

        // Limpeza específica para conteúdo Wiki
        if (cleanWikiMarkup) {
            content = cleanWikiContent(content);
        }

        String[] lines = content.split("\n");
        List<TitleTag> titles = detectTitles(lines);

        if (titles.isEmpty()) {
            // Se não há títulos, usar split por parágrafos como padrão
            return splitIntoChaptersByParagraphs(documento);
        } else {
            return splitByTitles(documento, lines, titles);
        }
    }

    /**
     * Detecta títulos em formato Wikipedia/Markdown
     */
    @Override
    protected List<TitleTag> detectTitles(String[] lines) {
        List<TitleTag> titles = new ArrayList<>();

        // Padrões específicos para conteúdo Wiki
        Pattern headerPattern = Pattern.compile("^(#{1,6})\\s+(.+)$");
        Pattern sectionPattern = Pattern.compile("^==\\s*(.+)\\s*==$");
        Pattern subsectionPattern = Pattern.compile("^===\\s*(.+)\\s*===$");
        Pattern boldTitlePattern = Pattern.compile("^'''(.+)'''\\s*$");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            TitleTag titleTag = null;

            // Markdown headers
            Matcher markdownMatcher = headerPattern.matcher(line);
            if (markdownMatcher.matches()) {
                titleTag = new TitleTag();
                String hashes = markdownMatcher.group(1);
                titleTag.setTag(hashes);
                titleTag.setLevel(hashes.length());
                titleTag.setTitle(markdownMatcher.group(2).trim());
                titleTag.setPosition(i);
            }
            // Wiki-style sections
            else if (sectionPattern.matcher(line).matches()) {
                titleTag = new TitleTag();
                titleTag.setTag("wiki-section");
                titleTag.setLevel(2);
                titleTag.setTitle(line.replaceAll("=", "").trim());
                titleTag.setPosition(i);
            }
            else if (subsectionPattern.matcher(line).matches()) {
                titleTag = new TitleTag();
                titleTag.setTag("wiki-subsection");
                titleTag.setLevel(3);
                titleTag.setTitle(line.replaceAll("=", "").trim());
                titleTag.setPosition(i);
            }
            // Bold titles
            else if (boldTitlePattern.matcher(line).matches()) {
                titleTag = new TitleTag();
                titleTag.setTag("bold-title");
                titleTag.setLevel(4);
                titleTag.setTitle(line.replaceAll("'''", "").trim());
                titleTag.setPosition(i);
            }

            if (titleTag != null) {
                titles.add(titleTag);
                logger.debug("Detected Wiki title: {} (level {}) at line {}",
                           titleTag.getTitle(), titleTag.getLevel(), i);
            }
        }

        return titles;
    }

    /**
     * Split por títulos detectados
     */
    @Override
    protected List<ChapterDTO> splitByTitles(DocumentoWithAssociationDTO doc, 
	                                     String[] lines, 
	                                     List<TitleTag> titles) 
    {
        List<ChapterDTO> capitulos = new ArrayList<>();

        for (int i = 0; i < titles.size(); i++) {
            TitleTag currentTitle = titles.get(i);
            int startLine = currentTitle.getPosition();
            int endLine = (i + 1 < titles.size()) ? titles.get(i + 1).getPosition() : lines.length;

            // Extrair conteúdo da seção
            StringBuilder sectionContent = new StringBuilder();
            for (int j = startLine; j < endLine; j++) {
                sectionContent.append(lines[j]).append("\n");
            }

            String content = sectionContent.toString().trim();
            if (!content.isEmpty()) {
                ChapterDTO capitulo = new ChapterDTO();
	        capitulo.getMetadados().addMetadata(doc.getMetadados());
	        capitulo.setDocumentoId(doc.getId());
                capitulo.setTitulo(currentTitle.getTitle());
                capitulo.setConteudo(content);
                capitulo.setOrdemDoc(i + 1);

                // Metadados específicos para Wiki
                capitulo.getMetadados().put("tipo_conteudo", "wikipedia");
                capitulo.getMetadados().put("nivel_titulo", String.valueOf(currentTitle.getLevel()));
                capitulo.getMetadados().put("tag_titulo", currentTitle.getTag());

                capitulos.add(capitulo);
            }
        }

        // Se nenhum capítulo foi criado, usar conteúdo completo
        if (capitulos.isEmpty()) {
            return splitIntoChaptersByParagraphs(doc);
        }

        logger.debug("Created {} chapters from Wiki content", capitulos.size());
        return capitulos;
    }

    /**
     * Split em capítulos baseado em parágrafos (modo padrão para Wiki)
     */
    private List<ChapterDTO> splitIntoChaptersByParagraphs(DocumentoWithAssociationDTO doc) {
        logger.debug("Splitting Wiki content by paragraphs with chapterMaxTokens: {}", maxWordsPerChapter);

        String content = doc.getTexto();
        // Usar o splitIntoParagraphs() como base para separação
        String[] paragraphs = splitIntoParagraphs(content);

        List<ChapterDTO> capitulos = new ArrayList<>();
        StringBuilder currentChapter = new StringBuilder();
        int currentWords = 0;
        int chapterNumber = 1;

        for (String paragraph : paragraphs) {
            int paragraphWords = countWords(paragraph);

            // Se adicionar este parágrafo exceder o limite, criar novo capítulo
            if (currentWords + paragraphWords > maxWordsPerChapter && currentWords > 0) {
                ChapterDTO capitulo = new ChapterDTO();
	        
                capitulo.getMetadados().addMetadata(doc.getMetadados());
	        capitulo.setDocumentoId(doc.getId());
	        
                capitulo.setTitulo(doc.getTitulo() + " - Parte " + chapterNumber);
                capitulo.setConteudo(currentChapter.toString().trim());
                capitulo.setOrdemDoc(chapterNumber);

                // Metadados para capítulos criados por parágrafos
                capitulo.getMetadados().put("tipo_split", "paragrafos");
                capitulo.getMetadados().put("palavras_aproximadas", String.valueOf(currentWords));

                capitulos.add(capitulo);

                // Reiniciar para próximo capítulo
                currentChapter = new StringBuilder();
                currentWords = 0;
                chapterNumber++;
            }

            currentChapter.append(paragraph).append("\n\n");
            currentWords += paragraphWords;
        }

        // Adicionar último capítulo se houver conteúdo
        if (currentChapter.length() > 0) {
            
            ChapterDTO capitulo = new ChapterDTO();
	    capitulo.getMetadados().addMetadata(doc.getMetadados());
	    capitulo.setDocumentoId(doc.getId());
            capitulo.setTitulo(doc.getTitulo() + " - Parte " + chapterNumber);
            capitulo.setConteudo(currentChapter.toString().trim());
            capitulo.setOrdemDoc(chapterNumber);

            capitulo.getMetadados().put("tipo_split", "paragrafos");
            capitulo.getMetadados().put("palavras_aproximadas", String.valueOf(currentWords));

            capitulos.add(capitulo);
        }

        logger.debug("Created {} chapters by paragraphs", capitulos.size());
        return capitulos;
    }

    /**
     * Limpeza específica para conteúdo Wiki
     */
    private String cleanWikiContent(String content) {
        if (content == null) return "";

        // Remover templates e infoboxes
        content = content.replaceAll("\\{\\{[^}]+\\}\\}", "");

        // Remover links internos do Wikipedia, mantendo apenas o texto
        content = content.replaceAll("\\[\\[([^|\\]]+)\\|([^\\]]+)\\]\\]", "$2");
        content = content.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1");

        // Remover links externos
        content = content.replaceAll("\\[http[^\\s]+ ([^\\]]+)\\]", "$1");

        // Remover referências
        content = content.replaceAll("<ref[^>]*>[^<]*</ref>", "");
        content = content.replaceAll("<ref[^>]*/>", "");

        // Remover comentários HTML
        content = content.replaceAll("<!--[^>]*-->", "");

        // Limpar markup residual
        content = content.replaceAll("'''", "");  // Bold
        content = content.replaceAll("''", "");   // Italic

        // Normalizar espaços em branco
        content = content.replaceAll("\\n{3,}", "\n\n");
        content = content.trim();

        logger.debug("Cleaned Wiki content, reduced from {} to {} characters",
                    content.length() + (content.length() * 0.2), content.length());

        return content;
    }

    /**
     * Override para usar implementação otimizada do AbstractSplitter
     */
    @Override
    public String removeRepetitions(String text) {
        return super.removeRepetitions(text);
    }

    /**
     * Split em parágrafos otimizado para conteúdo Wiki
     *
     * Este método é o modo padrão para split secundário (Capítulos → DocEmbeddings)
     */
    @Override
    public String[] splitIntoParagraphs(String text) {
        // Usar a implementação sofisticada do SplitterGenerico como base
        SplitterGenerico genericSplitter = new SplitterGenerico();
        return genericSplitter.splitIntoParagraphs(text);
    }
    
    
    /**
     * Split por tamanho não é recomendado para Wiki.
     * Retorna split por parágrafos como fallback.
     */
    @Override
    public List<ChapterDTO> splitBySize(DocumentoWithAssociationDTO documento, int effectiveChunkSize) 
    {
	return splitIntoChaptersByParagraphs(documento);
    }
}