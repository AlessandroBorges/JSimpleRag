package bor.tools.splitter.normsplitter;


import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Classe com utilitários estáticos
 */
public class NormUtil {

	public static final String regexRomanos = "\"^(ccc|cc|c)?(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})( *)-"; // gmis
	//public static final String regexItens = "^[a-zA-Z]\\)\\s?"; // gmis
	//public static final String regexItens2 = "^[\\D](\\)|(\\s?\\-))"; // gmis
	public static final String regexItens ="^.(\\)|(\\s?\\-))";
	public static final String regexItens2 ="^..(\\)|(\\s?\\-))"; //regex = "^.(\\)|(\\s?\\-))";

	/**
	 * tags para item vetado ou revogado
	 */
	public static final String[] VETO_REVOGADO = {"(vetad",
			                                      "(revog",
			                                      "(declarado inconstitucional",
			                                      "(execução suspensa pelo senado federal"};

	public static String[] TAGS_PARAGRAF = { "§ ", "§", "parágrafo", "par. " };
	public static String[] TAGS_INCISO_ITEM = { regexItens, regexItens2, regexRomanos};

	private static String TAG_MODO_MULTI = ":";

	private NormUtil() {
	}

	/**
	 * Flag para uso da API flexmark
	 */
	public static boolean USE_FLEXMARK = true;

	/**
	 * Flag para injeção de CSS
	 */
	public static boolean injectaCSS = true;

	/**
	 * Trim String
	 * @param s
	 * @return
	 */
	public static String trim(String s) {
		return s==null? null : s.trim();
	}

	/**
	 * Trim String e converte para lowerCase
	 * @param s
	 * @return
	 */
	public static String trimLo(String s) {
		return s==null? null : s.trim().toLowerCase();
	}

	/**
	 * Verifica se a linha é Artigo (Caput)
	 * @param line
	 * @return
	 */
	public static boolean isInciso_Item(String line) {
		line = trimLo(line);
		for (String s : TAGS_INCISO_ITEM) {
			if(line.startsWith(s) | NormPatterns.matches(line, s, "gmi") )
				return true;
		}
		return false;
	}

	/**
	 * Verifica se a linha é Artigo (Caput)
	 * @param line
	 * @return
	 */
	public static boolean isParagrafo(String line) {
		line = trimLo(line);
		for (String s : TAGS_PARAGRAF) {
			if(line.startsWith(s) || line.matches(s))
				return true;
		}
		return false;
	}


	/**
	 * Verifica se o artigo/paragrafo ou inciso tem uma enumeração
	 * na sequencia.
	 * @param line
	 * @return
	 */
	public static boolean isMultitem(String line) {
		return line.trim().endsWith(TAG_MODO_MULTI);
	}

	public static boolean isVetadoOuRevogado(String line) {
		for(String s : VETO_REVOGADO)
			if(NormPatterns.containsFlex(line, s))
				return true;
		return false;
	}

	/**
	 * Detecta Charset
	 * @param data - string em modo binário
	 * @return
	 */
	public static Charset detectaCharset(byte[] data) {
		// Detecta charset
		CharsetDetector detector = new CharsetDetector();
		detector.setText(data);
		CharsetMatch match = detector.detect();
		if (match != null) {
			String encoding = match.getName();
			return Charset.availableCharsets().get(encoding);
		}
			return null;
	}

	/**
	 * Carrega String com Charset desconhecido
	 * @param data - string formato binário
	 * @return String com charset compatível
	 */
	public static String newStringCharset(byte[] data) {
		Charset cs = detectaCharset(data);
		return new String(data,cs);
	}


	/**
	 * Converte MarkDown para HTML
	 * @param html entrada
	 * @return
	 */
	public static String markDown2Html(String markdown) {
		if (USE_FLEXMARK) {
			MutableDataSet options = new MutableDataSet();
			options.set(com.vladsch.flexmark.parser.Parser.EXTENSIONS,
			        Arrays.asList(TablesExtension.create(),
			            AutolinkExtension.create(),
			            StrikethroughExtension.create()));
			com.vladsch.flexmark.parser.Parser parser = com.vladsch.flexmark.parser.Parser.builder(options).build();
			com.vladsch.flexmark.html.HtmlRenderer renderer = com.vladsch.flexmark.html.HtmlRenderer.builder(options).build();
			com.vladsch.flexmark.util.ast.Node document = parser.parse(markdown);
			String outputHtml = renderer.render(document);
			return outputHtml;
		} else {
			// Usar outro método
			return markdown;
		}
	}

	/**
	 * Retorna true se objeto não for nulo e nem vazio
	 * @param o - objeto a ser avaliado
	 * @return
	 */
	public static boolean notEmpty(Object o) {
	     if(o==null)
	    	 return false;
	     if(o instanceof String)
	    	 return ((String) o).length()>0;
	     if(o instanceof Collection<?>)
	    	 return ((Collection<?>) o).size()>0;
	     if(o instanceof Map<?,?>)
	    	 return ((Map<?,?>) o).size()>0;
	     if(o.getClass().isArray())
	    	 return ((Object[])o).length > 0;

	     return true;
	}

	 /**
	 * Checa se um nome contêm pelo menos um dos valores listados.<br>
	 * Case insensitive
	 * @param name - nome a ser verificado
	 * @param valores - lista de valores possivelmente contidos em name
	 * @return true, se contêm valor
	 */
	public static boolean contains(String name, String... valores) {
		String nameLo = deaccent(name);
		for (String s : valores) {
			if(nameLo.contains(deaccent(s)))
				return true;
		}
		return false;
	}

	/**
	 * retira acentos e converte para lowerCase
	 * @param s
	 * @return
	 */
	public static String deaccent(String s) {
		return NormPatterns.deAccent(s).toLowerCase();
	}

	/**
	 * Verifica se uma coleção é nula ou vazia
	 * @param c
	 * @return
	 */
	public static boolean isEmpty(Collection<?> c) {
		return c==null || c.size()==0;
	}

	/**
	 * Verifica se uma coleção é nula ou vazia
	 * @param m - mapa para
	 * @return
	 */
	public static boolean isEmpty(Map<?,?> m) {
		return m==null || m.size()==0;
	}

	/**
	 * Verifica se uma string é nula ou vazia
	 * @param c
	 * @return
	 */
	public static boolean isEmpty(String c) {
		return c==null || c.length()==0;
	}

	/**
	 * Verifica se uma string é nula ou vazia
	 * @param c
	 * @return
	 */
	public static boolean isEmpty(Object c) {
		if(c==null)
			return true;
		if(c.getClass().isArray()) {
			return ((Object[]) c).length==0;
		}
		return false;
	}


	/**
	 * Converte Tabela HTML em Markdown
	 * @param titulo - título da tabela
	 * @param  - tabela em formato HTML
	 * @return
	 */
	public static String tabelasHtml2MD(String titulo, String htmlTable) {
		if(!isEmpty(htmlTable) && htmlTable.matches("(?i)<table")) {
			String md = isEmpty(titulo) ? "" : "## " + titulo + "\n";
			md += html2MarkDown(htmlTable);
			return md;
		}
		return "";
	}

	/**
	 * Converte html em Markdown
	 * @param html
	 * @return
	 */
	public static String html2MarkDown(String html) {
		String md = HtmlToMarkdown.convertHtml_2_Markdown(html);
		return md;
	}

	/**
	 * Injeta CSS em HTML, se habilitado. <B>
	 * Ver {@link #CSS}
	 * @see #
	 * @param src - source HTML
	 * @return src com CSS
	 */
	public static String injetaCSS(String src) {
		if (src.length() == 0)
			return src;
		if (injectaCSS) {
			if (src.contains("<head>") || src.contains("<HEAD>")) {
				src = src.replace("(?i)<head>", "<head>\n" + CSS + "\n");
			} else if (src.contains("<html>") || src.contains("<HTML>") ) {
				src = src.replace("(?i)<html>", "<html>\n<head>\n" + CSS + "\n</head>");
			} else {
				src = "\n" + CSS + "\n\n" + src + "\n";
			}
		}
		if (src.contains("<del>")) {
			src = src.replaceAll("(?i)<del>", "<strike>");
			src = src.replaceAll("(?i)</del>", "</strike>");
		}
		return src;
	}


	/**
	 * CSS básico
	 */
	public static final String CSS = """
<style>
table {
  font-family: arial, sans-serif;
  border-collapse: collapse;
  width: 100%;
}

 blockquote {
  background: #f9f9f9;
  border-left: 10px solid #ccc;
  margin: 1.5em 10px;
  padding: 0.5em 10px;
  quotes: "\201C""\201D""\2018""\2019";
}
blockquote:before {
  color: #ccc;
  content: open-quote;
  font-size: 4em;
  line-height: 0.1em;
  margin-right: 0.25em;
  vertical-align: -0.4em;
}
blockquote p {
  display: inline;
}
td, th {
  border: 1px solid #888888;
  text-align: left;
  padding: 6px;
}

tr:nth-child(even) {
  background-color: #dddddd;
}
pre{
  border: 1px solid #888888;
  background-color: #dddddd;
  padding: 6px;
}
</style>
			""";


}
