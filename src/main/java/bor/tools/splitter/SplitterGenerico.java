package bor.tools.splitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.xml.sax.SAXException;

import bor.tools.splitter.normsplitter.HtmlToMarkdown;
import bor.tools.utils.RAGUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import superag.provedorIA.Provedor;
import superag.retriever.model.Biblioteca;
import superag.retriever.model.DocParte;
import superag.retriever.model.Documento;

/**
 * Splitter genérico para documentos em texto plano e Markdown.
 *
 * Assume que o formato de origem é um documento texto plano simples ou markdown
 * eventualmente composta por títulos, subtítulos e parágrafos.
 *
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class SplitterGenerico extends AbstractSplitter {

	private static final Logger logger = LoggerFactory.getLogger(SplitterNorma.class);

	/**
	 * Número máximo de palavras em uma parte.
	 * Default é 6KB
	 */
	protected int maxWords = 1024*6;

	protected boolean removerTachado = true;

	protected Provedor provedor;

	/**
	 * Construtor
	 * @param biblioteca biblioteca de documentos, para sua
	 */
	public SplitterGenerico(Provedor provedor, Biblioteca biblioteca)  {
		super(biblioteca, null);
		this.provedor = provedor;
	}

	/**
	 * Construtor
	 * @param biblioteca biblioteca de documentos, para sua
	 */
	public SplitterGenerico(Biblioteca biblioteca, ExistsArtefato<Documento> validator)  {
		super(biblioteca, validator);
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public List<DocParte> splitDocumento(@NonNull Documento documento) {
		String text = documento.getTexto();
		String[] lines = text.split("\n");
		Map<Integer,String> titles = detectTitles(lines);
		if (titles.isEmpty()) {
			// Se não houver títulos, dividir o texto em partes de tamanho fixo
			return splitBySize(documento, this.maxWords);
		} else {
			return splitByTitles(documento, lines, titles);
		}

	}

	/**
	 * Divide documento por titulos.
	 * @param documento - documento a ser dividido
	 * @param lines - linhas do documento
	 * @param titles - titulos do documento
	 * @return lista de partes do documento
	*/
	public List<DocParte> splitByTitles(Documento documento, String[] lines,  Map<Integer, String> titles) {

		if (titles.isEmpty()) {
			return splitBySize(documento, maxWords);
		}else{
			int n_titles = titles.size();
			if (lines == null)
				lines = documento.getTexto().split("\n");

			List<DocParte> lista = new ArrayList<>(n_titles + 1);
			{

				String[] sections = new String[n_titles+1];
				Integer[] keys = titles.keySet().toArray(new Integer[0]);
				List<String> linesList = new ArrayList<>(lines.length);

				for (int i = 0; i < n_titles; i++) {
					//magic java
					int start = i == 0  ? 0 : keys[i - 1]; // inclui inicio
					int end   = i == (n_titles-1) ? n_titles : keys[i]; // inclui parte final
					linesList.clear();
					for (int j = start; j < end; j++) {
						linesList.add(lines[j]);
					}
					sections[i] = String.join("\n", linesList.toArray(new String[1]));
				}

				for (String parag : sections) {
					if (parag == null || parag.isEmpty())
						continue;
					DocParte parte = new DocParte();
					parte.setDocumento(documento);
					documento.addParte(parte);
					parte.setTexto(parag);
					lista.add(parte);
				}
			}
			return lista;
		}
	}

	public List<DocParte> splitBySize(Documento documento, int maxWords2) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Detecta títulos no texto do documento.
	 *
	 * @param text texto do documento
	 * @return Map com numero da linha e títulos
	 **/
	public static Map<Integer, String> detectTitles(String[] lines) {
		Map<Integer, String> titles = new LinkedHashMap<>();
		// Define regex patterns for different title formats
		Pattern uppercasePattern = Pattern.compile("^[A-Z\\s]+$");
		Pattern markdownPattern  = Pattern.compile("^#{1,6}\\s+.*$");
		Pattern numberingPattern = Pattern.compile("^(\\d+\\.\\s+|\\d+\\)\\s+).*");

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();

			// Check for uppercase titles with line breaks before and after
			if (i > 0 && i < lines.length - 1 && uppercasePattern.matcher(line).matches()
					&& lines[i - 1].trim().isEmpty() && lines[i + 1].trim().isEmpty()) {
				titles.put(i, line);
			}
			// Check for markdown titles
			else if (markdownPattern.matcher(line).matches()) {
				titles.put(i, line);
			}
			// Check for numbered titles
			else if (numberingPattern.matcher(line).matches()) {
				titles.put(i, line);
			}
		}

		// check if there are consecutive titles, like title and subtitle in a sequence
		// if so, combine them into a single title
		{
			List<Integer> keys = new ArrayList<>(titles.keySet());
			for (int i = 0; i < keys.size() - 1; i++) {
				int k1 = keys.get(i);
				int k2 = keys.get(i + 1);
				if (k2 - k1 <= 3) {
					String title1 = titles.get(k1);
					String title2 = titles.get(k2);
					String combinedTitle = title1 + "\n" + title2;
					titles.put(k1, combinedTitle);
					titles.remove(k2);
					// adjust the index of the next title
					if (i > 0) i--;
					}
			}
		}
		return titles;
	}



	/**
	 * @inheritDoc
	 */
	@Override
	public Documento carregaDocumento(@NonNull URL urlDocumento, Documento docStub) throws Exception {
		Documento doc = docStub==null ? new Documento() : docStub;
		doc.setUrl(urlDocumento.toString());
		OkHttpClient client = RAGUtil.getUnsafeOkHttpClient();
		Request request = new Request.Builder()
								.url(urlDocumento)
								.get()
								.header("User-Agent", "Mozilla/5.0 (Windows NT 10; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36")
								.header("Accept", "text/html,application/xhtml+xml,application/xml,text/json;")
								.build();
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

				// Fazendo o parsing do documento
				parser.parse(input, handler, metadata, context);

				{
					String contentType = metadata.get("Content-Type");
					// conteudo HTML e XHTML
					if (contentType != null
							&& (contentType.contains("text/html") || contentType.contains("application/xhtml+xml") ))
					{
						String content = handler.toString();
						String md = HtmlToMarkdown.convertHtml_2_Markdown(content);
						doc.setTexto(md);
					} else {
						doc.setTexto(handler.toString());
					}
				}
				doc.setTitulo(urlDocumento.getFile());

				StringBuffer sb = new StringBuffer(512);
				for (String name : metadata.names()) {
					sb.append(name).append("=").append(metadata.get(name)).append(",\n");
				}
				String res = sb.toString();
				logger.debug("Metadados: "+res);
				doc.getMetadados().addMetadados(res);
			}
        } catch (IOException | SAXException | TikaException e) {
            e.printStackTrace();
        }
		return doc;
	}




    /**
	 * Carrega documento a partir de um arquivo local ou em rede.
	 * @param path caminho do arquivo
	 */
	@Override
	public Documento carregaDocumento(String path, Documento docStub) throws Exception {
		if (path.matches("^(http|.*\\.(html|xhtml|htm|xml))$")) {
			return carregaDocumento(new URI(path).toURL(), docStub);
		} else {
			Documento doc =  docStub == null? new Documento() : docStub;
			doc.setUrl(path);
			if (doc.getTitulo() == null) {
				String titulo = AbstractSplitter.detectaNome(path);
				doc.setTitulo(titulo);
			}
			byte[] data = RAGUtil.lerArquivoBinario(path);
			String textoMD = RAGUtil.convertToMarkdown(data);
			String[] lines = textoMD.split("\n");
			Map<Integer, String> titles = detectTitles(lines);
			List<DocParte> partes = splitByTitles(doc, lines, titles);
			for (DocParte parte : partes) {
				doc.addParte(parte);
			}
			doc.setTexto(textoMD);
			doc.setTitulo(path);
			return doc;
		}

	}

	@Override
	public String removeRepetitions(String text) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Realiza a divisão do texto em parágrafos.
	 * É considerado um paragrafo a sequência de linhas de texto:
	 * 1) com uma linha em branco antes e depois;
	 * 2) com uma linha em branco antes e sem linha em branco depois - fim de arquivo;
	 * 3) com uma linha em branco depois e sem linha em branco antes - início de arquivo.
	 * 4) um título seguido de uma linha em branco e um parágrafo.
	 * 5) uma tabela, em formato markdown, html, csv, etc., seguido por uma linha em branco ou um parágrafo.
	 */
	@Override
	public String[] splitIntoParagraphs(String text) {
		String[] lines = text.split("\n");
		Map<Integer, String> titles = detectTitles(lines);
		return splitIntoParagraphs(lines, titles);
	}

	/**
	 * Realiza a divisão do texto em parágrafos.<br>
	 * É considerado um paragrafo a sequência de linhas de texto:
	 * <pre>
	 * 1) com uma linha em branco antes e depois;
	 * 2) com uma linha em branco antes e sem linha em branco depois - fim de arquivo;
	 * 3) com uma linha em branco depois e sem linha em branco antes - início de arquivo.
	 * 4) um título seguido de uma linha em branco e um parágrafo.
	 * 5) uma tabela, em formato markdown, html, csv, etc., seguido por uma linha em branco ou um parágrafo.
	 * </pre>
	 */
	public String[] splitIntoParagraphs(String[] lines, Map<Integer, String> titles) {
		List<String> paragraphs = new ArrayList<>();
		StringBuilder paragraph = new StringBuilder();
		Map<String, Boolean> titled_paragraphs = new HashMap<>();

        int n = lines.length;

		for (int i = 0; i < n; i++) {
			String line = lines[i].trim();

			// Check for blank line
			if (line.isEmpty()) {
				if (paragraph.length() > 0) {
					var p = paragraph.toString().trim();
					paragraphs.add(p);
					paragraph.setLength(0);
				}
			} else {
				// Check for title
				if (titles.containsKey(i)) {
					if (paragraph.length() > 0) {
						var p = paragraph.toString().trim();
						paragraphs.add(p);
						paragraph.setLength(0);
					}
					paragraphs.add(line);
					titled_paragraphs.put(line, true);
				} else {
					// Append line to current paragraph
					if (paragraph.length() > 0) {
						paragraph.append(" ");
					}
					paragraph.append(line);
				}
			}
		}
		// Add the last paragraph if any
		if (paragraph.length() > 0) {
			paragraphs.add(paragraph.toString().trim());
		}

		int maxWords = this.maxWords; // Define your maximum words per paragraph limit
		// verifique se a quantidade de palavras em um paragrafo excede o limite maximo maxWords
		// caso exceda, realizar a divisão do paragrafo em 2 paragrafos menores, considerando sentenças
		// como ponto de divisão.
		{
			List<String> splitParagraphs = new ArrayList<>();
			for (String parag : paragraphs) {
				int wordCount = countWords(parag);
				if (wordCount > maxWords) {
					String[] sentences = splitIntoSentences(parag, maxWords*80/100);
					for (String sentence : sentences) {
						splitParagraphs.add(sentence);
					}
				} else {
					splitParagraphs.add(parag);
				}
			}
			paragraphs.clear();
			paragraphs.addAll(splitParagraphs);
		}

		// verifique se a soma de palavras em parágrafos consecutivos
		// é inferior a limite máximo de palavras por parágrafo.
		// Se for, combine os parágrafos.

		List<String> combinedParagraphs = new ArrayList<>();
		StringBuilder currentParagraph = new StringBuilder();
		int currentWordCount = 0;

		for (String parag : paragraphs) {
			int wordCount = countWords(parag);
			if (currentWordCount + wordCount <= maxWords) {
				if (currentParagraph.length() > 0) {
					currentParagraph.append("\n");
				}
				currentParagraph.append(parag);
				currentWordCount += wordCount;
			} else {
				combinedParagraphs.add(currentParagraph.toString().trim());
				currentParagraph.setLength(0);
				currentParagraph.append(parag);
				currentWordCount = wordCount;
			}
		}
		// Add the last paragraph if any
		if (currentParagraph.length() > 0) {
			combinedParagraphs.add(currentParagraph.toString().trim());
		}
		return combinedParagraphs.toArray(new String[0]);
	}




}
