package bor.tools.splitter.normsplitter;

import static bor.tools.splitter.normsplitter.NormUtil.isInciso_Item;
import static bor.tools.splitter.normsplitter.NormUtil.isMultitem;
import static bor.tools.splitter.normsplitter.NormUtil.isParagrafo;
import static bor.tools.splitter.normsplitter.NormUtil.isVetadoOuRevogado;
import static bor.tools.splitter.normsplitter.NormUtil.trimLo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Artigo inteiro, com relação de embeddings de trechos internos
 */
@Data
public class Artigo implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 8485331859627666803L;

	/**
	 * Nome do Normativo proprietário deste artigo
	 */
	String nomeNormativo;

	/**
	 * Identificação deste Artigo
	 */
	String id = "";
	/**
	 * Usando se este artigo faz parte de algum anexo
	 */
	String anexo = "";
	/**
	 * Livro usado em elguns normativos
	 */
	String livro = "";

	/**
	 * Título
	 */
	String titulo = "";

	/**
	 * Capitulo a qual este artigo faz parte
	 */
	String capitulo = "";

	/**
	 * Sessao a qual este artigo faz parte
	 */
	String sessao = "";

	/**
	 * Subsessao a qual este artigo faz parte
	 */
	String subsessao = "";

	/**
	 * Conteúdo do Artigo
	 */
	String conteudo = "";

	/**
	 * Opcional: Contexto a ser adicionado ao artigo.
	 */
	String contextoAdicional = "";

	/**
	 * EmbeddingsResponse e respectivos subtextos
	 */
	Map<Object, String> embeddings;

	/**
	 * Lista de subtextos do artigo
     *
	 */
	List<String> listaSubtexto = new ArrayList<>();

	/**
	 * Append texto no artigo
	 * @param complemento - trecho adicional
	 * @return
	 */
	public Artigo append2Artigo(String complemento) {
		if(conteudo==null)
			conteudo = "";
		conteudo += conteudo.length()==0 ? complemento : "\n" + complemento;
		return this;
	}


	public void addContextoAdicional(String contexto) {
		if (contextoAdicional == null || contextoAdicional.isBlank())
			contextoAdicional = contexto;
		else
			contextoAdicional += "\n" + contexto;
	}

	/**
	 * Executa particionamento do artigo em partes menores, se for necessário
     *
	 */
	public void smartSplit() {
		if (conteudo != null && conteudo.length() > 0 && listaSubtexto.isEmpty()) {
			stripArtigo();
		}
	}

	/**
	 * Particiona o artigo em várias partes, no caso de múltiplos parágrafos,
	 * incisos, enumerações, etc.
	 *
	 * @return lista de partes do Artigo.
	 * @throws IllegalAccessException - in case of error
	 */
	public List<String> stripArtigo() {
		listaSubtexto.clear();

		if (NormUtil.isEmpty(conteudo))
			return this.listaSubtexto;

		String contexto = nomeNormativo + " " + contextoAdicional + " " + anexo + " " + livro + " " + titulo + " "
				+ capitulo + " " + sessao + " " + subsessao;

		contexto = contexto.trim();
		/*
		 * contexto = contexto.replaceAll(" I "," ") .replaceAll(" II "," ")
		 * .replaceAll(" III "," ") .replaceAll(" IV "," ");
		 * 
		 * contexto = NormPatterns.removerPrimeiro(contexto, "título", "gis"); contexto
		 * = NormPatterns.removerPrimeiro(contexto, "capítulo", "gis"); contexto =
		 * NormPatterns.removerPrimeiro(contexto, "seção", "gis"); contexto =
		 * NormPatterns.removerPrimeiro(contexto, "subseção", "gis");
		 */

		contexto = contexto.replaceAll("  ", " ");
		contexto = NormPatterns.removerTodos(contexto, "null", " ");

		List<String> resultados = new ArrayList<>();
		String[] linhas = conteudo.split("\n");

		// adicionar Artigo full e artigo com contexto:
		{
			String artigoFull = conteudo.replace("\n", " ");
			resultados.add(artigoFull);
			resultados.add(contexto + " " + artigoFull);
		}

		String prefixArt = "";
		String prefixParagrafo = "";
		String prefixInciso = "";

		// linha do artigo
		String caput = removeComents(linhas[0]);

		// Este CAPUT tem enumeração
		if (isMultitem(caput)) {
			prefixArt = caput;
		} else {
			// Esse CAPUT não tem enumeração
			// ??
		}
		// salva o Caput, em qualquer situação
		{
			String res = contexto + " " + caput;
			res = trimLo(res);
			resultados.add(res);
		}

		// pegando o resultado
		for (int i = 1; i < linhas.length; i++) {
			String lin = removeComents(linhas[i]);

			if (isVetadoOuRevogado(lin))
				continue;

			if (isParagrafo(lin)) {
				prefixArt = "";
				prefixParagrafo = "";
				prefixInciso = "";

				if (isMultitem(lin)) {
					prefixParagrafo = lin;
					// compor com proximo item da proxima linha
					continue;
				} else {
					// sem enumeração
					String res = contexto + " " + lin;
					res = trimLo(res);
					resultados.add(res);
					continue;
				}
			}
			if (isInciso_Item(lin)) {
				if (isMultitem(lin)) {
					prefixInciso = lin;
					// compor com proximo item da proxima linha
					continue;
				}
			}

			String res = contexto + " " + prefixArt + " " + prefixParagrafo + " " + prefixInciso + " " + lin;
			// res = trimLo(res);
			resultados.add(res);
		}

		listaSubtexto.addAll(resultados);
		resultados.clear();
		return listaSubtexto;
	}



	/**
	 * Remove comentários de fim de linha, entre parentesis
	 * @param src
	 * @return
	 */
	private static String removeComents(String src) {
		// backwards
		String s = src.trim();
		while (s.endsWith(")")) {
			int pos = s.lastIndexOf("(");
			if (pos < 0)
				break;
			s = s.substring(0, pos).trim();
		}
		return s;
	}

	private boolean notEmpty(String v) {
		return v!=null && v.length()>0;
	}

	@Override
	public String toString() {
		return "Artigo ["
	            + (notEmpty(id)         ? "id=" + id + ", " : "")
	            + (notEmpty(anexo)      ? "\n anexo= " + anexo + ", " : "")
				+ (notEmpty(livro)      ? "\n livro= " + livro + ", " : "")
				+ (notEmpty(titulo)     ? "\n titulo= " + titulo + ", " : "")
				+ (notEmpty(capitulo)   ? "\n capitulo= " + capitulo + ", " : "")
				+ (notEmpty(sessao)     ? "\n sessao= " + sessao + ", " : "")
				+ (notEmpty(subsessao)  ? "\n subsessao= " + subsessao + ", " : "")
				+ (notEmpty(id)         ? "id= " + id + ", " : "")
				+ (notEmpty(conteudo)   ? "\n conteudo= " + conteudo + ", " : "")
				+ ((embeddings != null) ? "\n embeddings= " + embeddings : "") + "]";
	}




} // Class Artigo

//***********************************************************************
