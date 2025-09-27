package bor.tools.splitter.normsplitter;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import bor.tools.splitter.WebLinks;

public class NormativosLoader {

	protected static final String REGEX_PREFIXO_DATA = "\\, de ";

	protected static final String ISO_8859_1 = "ISO-8859-1";

	protected static final String WINDOWS_1252 = "Windows-1252";

	protected static final String DOIS_PONTOS = ":";

	protected static final String NUMERO = "Nº";

	protected static final String VIRGULA = ",";

	protected static final String TAG_PARAGRAFO = "p";

	static final String UTF8 = "UTF-8";

	static final String L14133P = "https://www.planalto.gov.br/ccivil_03/_Ato2019-2022/2021/Lei/L14133.htm";
	static final String L14133C = "https://www.planalto.gov.br/ccivil_03/_Ato2019-2022/2021/Lei/L14133.htm";

	static final String CF88P = "https://www.planalto.gov.br/ccivil_03/constituicao/constituicaocompilado.htm";
	static final String CF88C = "https://www2.camara.leg.br/atividade-legislativa/legislacao/constituicao1988/arquivos/ConstituicaoTextoAtualizado_EC%20131.html";

	static final String CF88 = "https://www2.camara.leg.br/atividade-legislativa/legislacao/constituicao1988/arquivos/ConstituicaoTextoAtualizado_EC%20131.html";
	static final String L14133 = "https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2021/lei/l14133.htm";
	static final String IN5 = "https://www.gov.br/compras/pt-br/acesso-a-informacao/legislacao/instrucoes-normativas/instrucao-normativa-no-5-de-26-de-maio-de-2017-atualizada";

	public static final String regexRomanos = "^(CCC|CC|C)?(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$\s-"; // gmis
	public static final String regexItens = "^[a-zA-Z]\\)\\s?"; // gmis

	protected static final String REGEX_YYYY = "\\d\\d\\d\\d";
	protected static final String REGEX_dd = "\\d\\d"; //
	protected static final String REGEX_DD_de_MMNN_YYYY = "\\d(\\d?)(º*)( +)de( +)(?:([A-Za-z]+))( +)de( +)\\d\\d\\d\\d"; // gmis

	public static String regexItensNum = "^[\\d]+\\)\\s?";// "^[\d]+\)\s?"gm

	/**
	 * Expressões regex para artigos
	 * Suporta números e letras
	 */
	public static String[] TAGS_ARTIGO = { "^(?i)Art\\. (\\d{1,4}(?:\\º)?(?:-[A-Z])?)(?:\\.)?",
			"^(?i)Artigo (\\d{1,4}(?:\\º)?(?:-[A-Z])?)(?:\\.)?"
	};

	public static String[] TAGS_PARAGRAF = { "§ ", "§", "parágrafo", "par. " };
	public static String[] TAGS_INCISO_ITEM = { regexRomanos, regexItens };

	public static String TAG_FIM = "Este texto nao substitui o publicado";

	/**
	 * Tipos de Anexos
	 */
	public static String[] TAGS_HEADER_ANEXO = { "anexo",
			"regulamento",
			"regimento",
			"estatuto",
			"estrutura",
			"natureza",
			"organiza",
			"assisten",
			"orgaos",
			"unidad",
			"executi",
			"comiss",
			"quadro",
			"Resumo",
			"ato",
	};

	public static String[] TAGS_HEADER = { "anexo",
			"regulamento", "regimen", "estatuto", "estrutura",
			"ato",
			"livro",
			"preâmbulo",
			"título", "titulo",
			"capítulo", "capitulo",
			"seção",
			"subseção", "sub-seção", "disposições", "gerais",
			"transitórias",
	};

	public static String[] TAGS_HEADER_prefixo = { "Do ", "Da ", "Dos ", "Das " };

	public static List<String> list_TAGS_HEADER = Arrays.asList(TAGS_HEADER);
	public static List<String> list_TAGS_HEADER_ANEXO = Arrays.asList(TAGS_HEADER_ANEXO);

	protected static String[] TAGS_NORMA_INICIO_NORMA = { "mensagem de veto", "subchefia para assuntos jur" };
	protected static String[] TAGS_MODO_CAPUT_INI = { "\"", "“" };
	protected static String[] TAGS_MODO_CAPUT_FIM = { "(NR)", "”" };
	protected static String TAG_MODO_MULTI = ":";

	/**
	 * Ignorar linhas iniciadas com estes nomes/palavras
	 */
	public static String[] ignoreLineList = { "ulysses", // Desculpe, Mestre!
			"fernan", "wald", "vald", "and",
			"lui", "dilma", "jair",
			"mich", "itam", "jos", "joão", "pedr",
			"paul", "marcel", "wagn", "tarc", "mari", "cels", "glei",
			"helen", "jorg", "anton", "vini",
			"ander", "manu", "adema", "robert", "esth", "jarbas",
			"partipantes:",
			"...",
			"o presidente da rep",
			"decreta:",
			"promulga",
			"brasilia,",
			"mensagem de veto",
			"o presidente do",
			"a presid",
			"in memo",
			"partes vetadas" };

	/**
	 * Marcadores típicos de fim de Normativo
	 */
	public static String[] TAGS_FIM_NORMATIVO = {
			"este texto não substitui o publicado",
			"Serviço Nacional",
	};

	static {
		System.out.println("file.encoding: " + System.getProperty("file.encoding"));
		System.out.println("stdout.encoding: " + System.getProperty("stdout.encoding"));
	}

	/**
	 * Processa a Tabela.<br>
	 * Dados são colocados em {@link Normativo#listConteudoTabela}
	 *
	 * @param norma  - normativo proprietário da tabela
	 * @param tabela - tabela a ser lida
	 *
	 * @return URL para texto compilado, se houver
	 */
	protected String processaTabela(Normativo norma, Element tabela) {
		Elements links = tabela.select("a");
		Set<String> setLinksTxt = new HashSet<>();
		if (!isEmpty(links)) {
			for (Element link : links) {
				String url = trim(link.absUrl("href"));
				String texto = trim(link.text());
				setLinksTxt.add(texto);
				WebLinks ct = new WebLinks();
				ct.setTexto(texto);
				ct.setUrl(url);
				norma.getListConteudoTabela().add(ct);
			}
		}

		Elements textos = tabela.select(TAG_PARAGRAFO);
		String plainText = "";
		if (!isEmpty(textos)) {
			for (Element txt : textos) {
				String texto = txt.text();
				if (isEmpty(texto) || setLinksTxt.contains(texto))
					continue;

				if (isEmpty(plainText)) {
					plainText = texto;
					norma.setEmenta(plainText);
				}
				WebLinks ct = new WebLinks();
				ct.setTexto(texto);
				ct.setUrl(null);
				norma.getListConteudoTabela().add(ct);
			}
		}
		setLinksTxt.clear();
		return plainText;
	}

	/**
	 * Procura Links
	 *
	 * @param doc
	 * @return
	 */
	public LinkedHashSet<WebLinks> extracaoLinks(Document doc) {
		LinkedHashSet<WebLinks> docLinks = new LinkedHashSet<>();
		Elements links = doc.select("a");
		if (!isEmpty(links)) {
			for (Element link : links) {
				String url = link.absUrl("href");
				if (isEmpty(url))
					url = trim(link.attr("abs:href"));
				String texto = trim(link.text());

				if (isEmpty(url) && isEmpty(texto))
					continue;

				WebLinks ct = new WebLinks();
				ct.setTexto(texto);
				ct.setUrl(url);
				docLinks.add(ct);
			}
		}
		return docLinks;
	}



	/**
	 * Procura por URL, de acordo com critérios
	 *
	 * @param lista  - lista de links
	 * @param partes - substrings para teste
	 * @return
	 */
	public static List<WebLinks> procuraURL(Collection<WebLinks> lista, String... partes) {
		if (isEmpty(lista) || isEmpty(partes)) {
			return Collections.emptyList();
		}
		List<WebLinks> res = new ArrayList<>();
		for (WebLinks cs : lista) {
			String txt = trim(cs.getTexto());
			boolean match = true;
			if (txt != null) {
				match = NormUtil.contains(txt, partes);
				if (match) {
					res.add(cs);
				}
			}
		}
		return res;
	}

	/**
	 * Debug FLAG
	 */
	protected static boolean DEBUG = true;

	public static void debug(Object ob) {
		if (DEBUG)
			System.err.println(ob);
	}

	public static void print(Object ob) {
		System.out.println(ob);
	}

	/**
	 * Conecta e descarrega pagina HTML como Jsoup Document
	 *
	 * @param url_ - url para carregar. Pode ser file ou Web
	 *
	 * @return null se não possivel detectar o tipo de encoding
	 *
	 * @throws Exception - em caso de alguma falha de IO
	 *
	 */
	public static Document carregaHTML(String url_) throws Exception {

		// Download como byte array
		byte[] webData = null;
		// verifica se é web
		if (url_.toLowerCase().startsWith("http")) {
			URI uri = new URI(url_);
			HttpClient client = HttpClient.newBuilder()
					//.version(Version.HTTP_1_1)
					.followRedirects(Redirect.NORMAL)
					.connectTimeout(Duration.ofSeconds(30))
					.build();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(uri)
					.header("user-agent", "Mozilla/5.0 (Linux; Android 6.0") // "Mozilla/5.0 (Windows NT 10.0; Win64;
																				// x64)" )
					.GET()
					.build();

			HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());
			// binario
			webData = response.body();
		} else {
			try (FileInputStream fis = new FileInputStream(new File(url_))) {
				webData = fis.readAllBytes();
			}
		}

		// Detecta charset
		CharsetDetector detector = new CharsetDetector();
		byte[] contentBytes = webData; // Obtenha o conteúdo em bytes da página web
		detector.setText(contentBytes);
		CharsetMatch match = detector.detect();

		if (match != null) {
			String encoding = match.getName();
			System.err.println("Charset: " + encoding);

			String page = new String(contentBytes, encoding);
			// insere encoding à força, como META
			if (url_.toLowerCase().contains(".htm") && !page.contains(encoding)) {
				String meta = " <meta http-equiv=\"Content-Type\"  "
						+ " content=\"text/html; charset=" + encoding
						+ "\" > ";

				if (page.contains("</head>") || page.contains("</HEAD>")) {
					page = page.replace(" </head>\n ", meta + "\n </head> ");
					page = page.replace(" </HEAD>\n ", meta + "\n </HEAD> ");
				}
			}

			{ // alguns testes
				if (page.contains("não") || page.contains("Não") || page.contains("República")
						|| page.contains("Independência")) {
					debug("Passou no teste de caracter acentuado!");
				}
			}

			// debug(page);
			Document doc = Jsoup.parse(page);
			if (url_.toLowerCase().startsWith("http")) {
				doc.setBaseUri(url_);
			}
			return doc;
		}

		return null;
	}

	/**
	 * Carrega Normativo
	 *
	 * @param url - endereço web do normativo.
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws URISyntaxException
	 */
	public Normativo load(String url_) throws Exception {
		Document doc = carregaHTML(url_);
		if (doc == null) {
			throw new Exception("Endereço não encontrado: " + url_);
		}
		// log("Charset do doc0: " + doc.charset());
		return processa(doc, url_);
	}

	/**
	 *
	 * Carrega Normativo
	 *
	 * @param doc - Jsoup Document
	 * @return Normativo
	 */
	public Normativo processa(Document doc, String url) {
		Normativo norm = new Normativo();
		norm.setUrl(url);
		try {
			log(doc.title());
			// remover texto tachado
			Elements strikes = doc.select("strike");
			for (Element strk : strikes) {
				strk.remove();
			}

			// remover texto tachado
			strikes = doc.select("span[style*=text-decoration:line-through]");
			for (Element strk : strikes) {
				strk.remove();
			}

			// Salva todos os links
			LinkedHashSet<WebLinks> listaLinks = extracaoLinks(doc);
			norm.addWebLinks(listaLinks);

			// recarrega a versão compilada
			{
				String[] texto_compilado = { "texto", "compilado" };
				List<WebLinks> listUrlTextoCompilado = procuraURL(listaLinks, texto_compilado);
				if (!isEmpty(listUrlTextoCompilado)) {
					WebLinks csComp = listUrlTextoCompilado.get(0);
					String url_nova = csComp.getUrl();
					try {
						if (!isEmpty(url_nova) && url_nova.startsWith("http")) {
							log("Carregando versão compilada: " + url_nova);
							return load(url_nova);
						}
					} catch (Exception e) {
						log("Erro ao carregar versão compilada: " + url_nova);
						e.printStackTrace();
					}
				}
			}

			// extrai texto completo da página web
			{
				String html =  doc.outerHtml();
				String md = HtmlToMarkdown.convertHtml_2_Markdown(html);
				norm.setTexto(md);
			}


			// Processa Conteúdo das tabelas
			Elements tables = doc.select("table");
			if (!isEmpty(tables)) {
				/*
				 * int count = 0;
				 * for (Element tabela00 : tables) {
				 * processaTabela(norm, tabela00);
				 * count++;
				 * if(count>2)
				 * break;
				 * }
				 */
				if (tables.size() >= 2) {
					Element tabela00 = tables.get(1);
					processaTabela(norm, tabela00);
				}
			}

			// Procura Nome do normativo
			{
				// No site https://www.planalto.gov.br , o nome do normativo está no primeiro link

				Iterator<WebLinks> iterator = listaLinks.iterator();
				WebLinks cs = iterator.next();
				if (!isEmpty(cs.getUrl())) {
					String urlMetadados = trim(cs.getUrl());
					String s = trim(cs.getTexto());
					// procura nome e data do normativo

					String mark = NUMERO;
					if (s.startsWith("CONSTIT")) {
						s += ", de 5 de Outubro de 1988";
						mark = " ";
					}

					int virgPos = s.indexOf(VIRGULA);
					if (virgPos < 0) {
						System.err.println("Não foi possivel processar cabeçalho: " + s);
					}

					if (s.substring(0, virgPos).contains(mark)) {
						int indexNu = s.indexOf(mark);
						String tipo = trim(s.substring(0, indexNu));
						String data = NormPatterns.extractFirst(s, REGEX_DD_de_MMNN_YYYY, "gmis"); // trim(s.substring(virgPos));
						if (data != null) {
							data = NormPatterns.replaceFirst(data, "", REGEX_PREFIXO_DATA, "gmis");
							data = data.replace("º", "").replace("ª", ""); // LEI Nº 14.133, DE 1º DE ABRIL DE 2021
						}

						String nome = null;
						String ano = trim(NormPatterns.extractFirst(s, REGEX_YYYY, "gmis")); // trim(s.substring(s.length()
																							// - 4));
						if (ano != null) {
							nome = trim(s.substring(0, virgPos) + "/" + ano);
						}



						try {
							SimpleDateFormat sdf = new SimpleDateFormat("dd de MMMM de yyyy");
							Date dt = sdf.parse(data);
							norm.setData_publicacao(dt);
						} catch (Exception e) {
							System.err.println("Data inválida: " + data);
							data = null;
						}

						norm.setId(nome);
						norm.setAlias(s);
						norm.setTipo(tipo);
						norm.setUrlMetadados(urlMetadados);
					}
				}
			}

			// removendo partes que não serão processadas
			{
				// remover tabelas
				for (Element tabela : tables) {
					tabela.remove();
				}
				// remover ancoras STF
				Elements stfLinks = doc.select("a[href*=stf.jus.br]");
				for (Element strk : stfLinks) {
					strk.remove();
				}
			}

			// Processa preambulo, títulos, sessões, sub-sessões e artigos
			Elements paragrafos = doc.select(TAG_PARAGRAFO);
			List<String> blocos = new ArrayList<>();

			if (!isEmpty(paragrafos)) {
				for (Element p : paragrafos) {
					String text = p.text();
					blocos.add(text);
				}
				processaBlocos(norm, blocos);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return norm;
	}

	/**
	 * processa texto plano
	 *
	 * @param doc - Jsoup Document
	 * @return Normativo
	 */
	public Normativo processa(String textoPlano, String url, String metadados, String urlMetadados) {
		Normativo norm = new Normativo();
		norm.setUrl(url);

		int headerLen = detectaInicioCorpo(textoPlano);
		String header = "";
		if (headerLen > 0) {
			header = textoPlano.substring(0, headerLen);
		}
		// Procura Nome do normativo
		{

			if (header.length() > 0) {
				String s = procuraNomeNormativo(header);
				// procura nome e data do normativo
				String mark = NUMERO;
				if (s.startsWith("CONSTIT")) {
					s += ", de 5 de Outubro de 1988";
					mark = " ";
				}

				int virgPos = s.indexOf(VIRGULA);
				if (virgPos < 0) {
					System.err.println("Não foi possivel processar cabeçalho: " + s);
				}
				// Processa data
				if (s.substring(0, virgPos).contains(mark)) {
					int indexNu = s.indexOf(mark);
					String tipo = trim(s.substring(0, indexNu));
					String data = NormPatterns.extractFirst(s, REGEX_DD_de_MMNN_YYYY, "gmis"); // trim(s.substring(virgPos));
					if (data != null) {
						data = NormPatterns.replaceFirst(data, "", REGEX_PREFIXO_DATA, "gmis");
						data = data.replace("º", "").replace("ª", ""); // LEI Nº 14.133, DE 1º DE ABRIL DE 2021
					}

					String nome = null;
					String ano = trim(NormPatterns.extractFirst(s, REGEX_YYYY, "gmis")); // trim(s.substring(s.length() -
																						// 4));
					if (ano != null) {
						nome = trim(s.substring(0, virgPos) + "/" + ano);
					}

					try {
						SimpleDateFormat sdf = new SimpleDateFormat("dd de MMMMMMMMM de yyyy");
						Date dt = sdf.parse(data);
						norm.setData_publicacao(dt);
					} catch (Exception e) {
						System.err.println("Data inválida: " + data);
						data = null;
					}

					norm.setId(nome);
					norm.setAlias(s);
					norm.setTipo(tipo);
					norm.setUrlMetadados(urlMetadados);
					norm.setMetadados(metadados);
				}

				// processa preambulo

			}
		}

		int inicioCorpo = detectaInicioCorpo(textoPlano);
		String corpo = textoPlano.substring(inicioCorpo);
		List<String> blocos = new ArrayList<>(1024);

		String[] linhas = corpo.split("\n");

		for (String l : linhas) {
			l = l.trim();
			if (l.length() > 0)
				blocos.add(l);
		}
		processaBlocos(norm, blocos);
		return norm;
	}

	/**
	 * Extrai preambulo do normativo
	 *
	 * @param header - texto cabeçalho
	 * @return
	 */
	public static String extraiPreambulo(Normativo norm, String header) {
		String[] stopwords = { "brastra", "brasão", "texto", "mensagem", "(vide", "vide", "(rev", "o presid",
				"a presis", };
		String[] linhas = header.split("\n");

		List<String> lista = new ArrayList<>();

		for (String lin : linhas) {
			String lo = lin.trim().toLowerCase();
			if (lo.length() > 0) {
				int i = 0;
				while (i < stopwords.length && !lo.startsWith(stopwords[i])) {
					i++;
				}
				if (i < stopwords.length && lo.startsWith(stopwords[i])) {
					lista.add(lin);
				}
			}
		}
		String preambulo = "";
		if (lista.size() > 0) {
			for (String s : lista) {
				preambulo += " " + s;
			}
		}

		norm.setPreambulo(preambulo);
		return preambulo;
	}

	/**
	 * Checa Revogação
	 *
	 * @param norm
	 * @param header
	 * @return
	 */
	public static boolean checaRevogacao(Normativo norm, String header) {
		String[] tagRevogados = { "revogado pela", "revogado",};
		String[] linhas = header.split("\n");
		boolean found = false;

		int i = 0;
		String achado = "";
		while (!found && i < linhas.length) {
			String lin = linhas[i];
			for (String tag : tagRevogados) {
				if (lin.toLowerCase().contains(tag)) {
					found = true;
					achado = lin;
					break;
				}
			}
		}
		if (found) {
			norm.setRevogado(true);
			// pegar só que está entre parenteses
			int begin = max(achado.indexOf("("), 0);
			int end = max(abs(achado.indexOf(")")), achado.length());
			achado = achado.substring(begin, end).trim();
			norm.setRevogadoPor(achado);
		}
		return found;
	}

	/**
	 * Detecta Início do Normativo
	 *
	 * @param textoPlano
	 * @return
	 */
	public static int detectaInicioCorpo(String textoPlano) {
		String[] tags = { "título", "dispo", "capí", "art. 1" };

		int max = Math.max(textoPlano.indexOf("Art. 1"), 1500);
		max = Math.min(max, textoPlano.length());
		String header = textoPlano.substring(0, max).toLowerCase();

		int pos = -1;
		int i = 0;
		while (i < tags.length && (pos = header.indexOf(tags[i])) < 0) {
			i++;
		}
		return pos > 0 ? pos : 0;
	}

	/**
	 * Procura o nome do Normativo
	 *
	 * @param header
	 * @return nome do normativo ou string vazia
	 */
	public static String procuraNomeNormativo(String header) {
		String[] tokens = { "lei", "decreto", "medida", "instr", "port", "ato", "regu", "regimento", "resolu", "ordem",
				"ofício", "edital", "aviso" };
		int i = 0;
		int pos = -1;
		String headerLo = header.toLowerCase();
		while (i < tokens.length && (pos = headerLo.indexOf(tokens[i])) < 0) {
			i++;
		}
		if (pos >= 0) {
			int fim = headerLo.indexOf("\n", pos);
			String nome = header.substring(pos, fim);
			return nome;
		} else
			return "";
	}

	/**
	 * Processa blocos de texto, possivelmente composto por Artigos.
	 * Caso seja encontrado um marcador de novo Normativo anexo, este será inserido
	 * no Normativo norm, via {@link Normativo#setNormativoAnexo(Normativo)}
	 *
	 * @param norm   - Normativo mestre
	 * @param blocos - Blocos de texto a serem processados.
	 */
	protected void processaBlocos(Normativo norm, List<String> blocos) {

		ParserNormativo state = new ParserNormativo(norm);
		List<String> linhas = new ArrayList<>();

		for (String blk : blocos) {
			if (isEmpty(blk)) {
				continue;
			}
			if (blk.contains("\n")) {
				String[] mlinhas = blk.split("\n");
				if (!isEmpty(mlinhas)) {
					for (String lin : mlinhas) {
						if (!isEmpty(lin))
							linhas.add(lin);
					}
				}
			} else {
				linhas.add(blk);
			}
		}

		int curLine = 0;
		int max = linhas.size();

		while (curLine < max) {
			curLine++;
			if (curLine >= linhas.size())
				break;
			String line = linhas.get(curLine);

			if (isEmpty(line))
				continue;

			line = trim(line);

			if (isIgnore(line)) {
				continue;
			}

			// Fim Normativo / Inicio Anexo
			if (isFimNormativo(line)) {
				// ainda tem muitas linhas
				if ((max - curLine) > 10) {
					// transfere processamento para capturar Normativo Anexo
					Normativo anexo = norm.criarNormativoAnexo();
					List<String> linhasAnexo = new ArrayList<>(max + 5 - curLine);
					for (int i = curLine; i < max; i++) {
						linhasAnexo.add(linhas.get(i));
					}
					processaBlocos(anexo, linhasAnexo);
					curLine = max;
					break;
				} else {
					break;
				}
			}

			// ==============================================//
			// processa cabeçalhos de artigos
			String headerType = null;
			if ((headerType = headerLineType(line)) != null) {
				curLine = processaHeader(state, linhas, curLine, headerType);
			}

			// ==============================================//
			// Aqui o parse será simples, sem fazer Strip
			// O Strip será implementado na Classe Artigo
			// meta: ler tudo até achar proximo Artigo
			if (isArtigo(line)) {
				int nextLineNum = curLine + 1;
				String artigo = extrairIdArtigo(line);
				if (state.isNovoArtigoOK(artigo)) {
					state.setCurArtigo(artigo);
					state.appendTextoArtigo(line);
					while (nextLineNum < max) {
						String nextLin = linhas.get(nextLineNum);

						if (isFimArtigo(nextLin) || headerLineType(line) != null) { // hora de dar tchau
							curLine = nextLineNum - 1;
							debug(state.getArtigo());
							debug("===========================");
							break;
						} else { // append até achar o proximo Artigo
							if (!isIgnore(nextLin) || !nextLin.toLowerCase().contains("(vetado)")) {
								state.appendTextoArtigo(nextLin);
							}
						} // else
						if (nextLin.toLowerCase().contains("ficam revogad")|| nextLin.contains("fica revogad")) {
							debug("Revogação detectada: " + nextLin);
							excluiRevogados(norm, nextLin);
						}
						nextLineNum++;
					} // while
				}
			} // if artigo
		}

		// remover links de revogados
	}

	/**
	 * Exclui links de revogados
	 * @param norm
	 * @param nextLin
	 */
	private void excluiRevogados(Normativo norm, String nextLin) {
		Collection<WebLinks> lista = norm.getListLinks();
		List<WebLinks> remove = new ArrayList<>();
		for (WebLinks wl : lista) {
			if (nextLin.contains(wl.getTexto())) {
				remove.add(wl);
			}
		}
		for (WebLinks wl : remove) {
			lista.remove(wl);
		}
	}

	/**
	 * Processo os vários Headers - Títulos, Capitulos,
	 *
	 * @param state      - state do parse
	 * @param linhas     - linhas em List
	 * @param curLine    - atual linha para realizar parse
	 * @param headerType - um dos valores de {@link #TAGS_HEADER
	 *
	 * @return novo valor do índice de linhas
	 */
	protected int processaHeader(ParserNormativo state,
			List<String> linhas,
			int curLine,
			String headerType) {
		// Anexo já existe - esquivar dos outros headers de anexo
		if (!isEmpty(state.getAnexo()) && list_TAGS_HEADER_ANEXO.contains(headerType)) {
			return curLine + 1;
		}

		String line = linhas.get(curLine);
		String lineLC = line.toLowerCase();

		if (lineLC.startsWith(headerType)) {
			String header = line;
			// O título pode estar na mesma linha ou na linha seguinte
			// Bora testar a proxima linha:
			int nextLine = curLine + 1;
			if (nextLine < linhas.size()) {
				String next = linhas.get(nextLine);
				// BUG
				if (!isIgnore(lineLC) && !isArtigo(next)) {
					if (headerLineType(next) == null || headerType.equalsIgnoreCase("anexo")) {
						// grande chance de ser o header complementar
						header += " " + next;
						curLine = nextLine;
					}
				}
			}

			if (headerType.startsWith("pre"))
				state.setPreambulo(header);
			else if (headerType.startsWith("anexo") || headerType.startsWith("regulamento"))
				state.setAnexo(header);

			else if (headerType.startsWith("livr"))
				state.setLivro(header);

			else if (headerType.startsWith("tít"))
				state.setCurTitulo(header);
			else if (headerType.startsWith("cap"))
				state.setCurCapitulo(header);
			else if (headerType.startsWith("seç"))
				state.setCurSecao(header);

			else if (headerType.startsWith("sub"))
				state.setCurSubsecao(header);
		}

		return curLine++;
	}

	/**
	 * Verifica se a linha atual é fim do Artigo
	 *
	 * @param line
	 * @return
	 */
	protected boolean isFimArtigo(String line) {
		if (isArtigo(line) || (headerLineType(line) != null))
			return true;
		return false;
	}

	/**
	 * Remove acentos das palavras
	 *
	 * @param str
	 * @return
	 */
	public static String deAccent(String str) {
		String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
		Pattern pattern = NormPatterns.compile("\\p{InCombiningDiacriticalMarks}+");
		return pattern.matcher(nfdNormalizedString).replaceAll("");
	}

	/**
	 * Verifica se a String src contem a substring cont, ignorando acentos e CaSes.
	 *
	 * @param src  - string a ser testada
	 * @param cont - substring contida
	 * @return true src se contem
	 */
	public static boolean containsFlex(String src, String cont) {
		if (src == null || cont == null)
			return false;
		String a = deAccent(src).toLowerCase();
		String b = deAccent(cont).toLowerCase();
		return a.contains(b);
	}

	/**
	 * Verifica se a String src se inicia a substring cont, ignorando acentos e
	 * CaSes.
	 *
	 * @param src    - string a ser testada
	 * @param prefix - substring prefixo
	 * @return true src se contem
	 */
	public static boolean startWithFlex(String src, String prefix) {
		if (src == null || prefix == null)
			return false;
		String a = deAccent(src).toLowerCase();
		String b = deAccent(prefix).toLowerCase();
		return a.startsWith(b);
	}

	protected boolean isHeader(String line) {
		line = line.toLowerCase();
		for (String s : TAGS_HEADER) {
			if (containsFlex(line, s))
				return true;
		}

		for (String s : TAGS_HEADER_ANEXO) {
			if (containsFlex(line, s))
				return true;
		}

		for (String s : TAGS_HEADER_prefixo) {
			if (startWithFlex(line, s))
				return true;
		}

		return false;
	}

	/**
	 *
	 * @param line
	 * @return
	 */
	protected boolean isFimNormativo(String line) {
		for (String s : TAGS_FIM_NORMATIVO) {
			if (line.startsWith(s))
				return true;
		}
		return false;
	}

	/**
	 * Verifica se não é Capitulo / Titulo / Seção / subseção
	 *
	 * @param line
	 * @return
	 */
	protected String headerLineType(String line) {
		String lineLo = line.trim().toLowerCase();
		for (String s : TAGS_HEADER) {
			if (lineLo.startsWith(s)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Verifica se a linha é Artigo (Caput)
	 *
	 * @param line
	 * @return
	 */
	public static boolean isInciso_Item(String line) {
		line = trim(line.toLowerCase());
		for (String s : TAGS_INCISO_ITEM) {
			if (line.matches(s) || line.startsWith(line))
				return true;
		}
		return false;
	}

	/**
	 * Verifica se a linha é Artigo (Caput)
	 *
	 * @param line
	 * @return
	 */
	public static boolean isParagrafo(String line) {
		line = trim(line).toLowerCase();
		for (String s : TAGS_PARAGRAF) {
			if (line.startsWith(line) || line.matches(s))
				return true;
		}
		return false;
	}

	/**
	 * Verifica se o artigo/paragrafo ou inciso tem uma enumeração
	 * na sequencia.
	 *
	 * @param line
	 * @return
	 */
	public static boolean isMultitem(String line) {
		return line.trim().endsWith(DOIS_PONTOS);
	}

	/**
	 * Verifica se o artigo/paragrafo ou inciso tem uma enumeração
	 * na sequencia.
	 *
	 * @param line
	 * @return
	 */
	protected boolean isMultiCaput(String line) {
		for (String s : TAGS_MODO_CAPUT_INI) {
			if (line.startsWith(s))
				return true;
		}
		return false;
	}

	/**
	 * Verifica se o artigo/paragrafo ou inciso tem uma enumeração
	 * na sequencia.
	 *
	 * @param line
	 * @return
	 */
	protected boolean isMultiCaputFim(String line) {
		for (String s : TAGS_MODO_CAPUT_INI) {
			if (line.startsWith(s))
				return true;
		}
		return false;
	}

	/**
	 * Verifica se a linha é Artigo (Caput)
	 *
	 * @param line
	 * @return
	 */
	protected boolean isArtigo(String line) {
		for (String s : TAGS_ARTIGO) {
			Pattern p = NormPatterns.compile(s);
			Matcher m = p.matcher(line);
			if (m.find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Linhas para ignorar
	 *
	 * @param line linha para testar
	 * @return
	 */
	protected boolean isIgnore(String line) {
		if (isEmpty(line))
			return true;
		line = line.toLowerCase();
		for (String s : ignoreLineList) {
			if (startWithFlex(line, s))
				return true;
		}
		// pode ser artigo
		
		// pode ser inciso ou item
		if (startWithFlex(line, "art") || isParagrafo(line) || isInciso_Item(line))
			return false;

		// verificar HEADER

		// pular nomes
		if (line.length() <= 30) {
			return true;
		}

		return false;
	}

	/***
	 *
	 * @param line
	 */
	protected String extrairIdArtigo(String line) {
		int i = 0;
		while (i < TAGS_ARTIGO.length) {
			String regex = TAGS_ARTIGO[i];
			Pattern pattern = NormPatterns.compile(regex);
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				return (matcher.group());
			}
		}
		return "";
	}

	/**
	 * Main
	 *
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			NormativosLoader loader = new NormativosLoader();
			Normativo norm = loader.load(CF88P);
			print(norm);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected static void log(Object ob) {
		System.out.println(ob);
	}

	/**
	 * Trima strings
	 *
	 * @param str
	 * @return
	 */
	protected static String trim(String str) {
		return str == null ? null : str.trim();
	}

	/**
	 * Verifica se objeto é nulo ou vazio
	 *
	 * @param obj
	 * @return
	 */
	protected static boolean isEmpty(Object obj) {
		if (obj == null)
			return true;

		if (obj instanceof String) {
			return ((String) obj).trim().length() == 0;
		}

		if (obj instanceof Collection<?>) {
			return ((Collection<?>) obj).size() == 0;
		}

		if (obj.getClass().isArray()) {
			return ((Object[]) obj).length == 0;
		}
		return false;
	}
} // NormativoLoader
