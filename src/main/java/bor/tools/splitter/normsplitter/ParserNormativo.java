package bor.tools.splitter.normsplitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;

/**
 * Controle de esatdos
 */
@Data
public class ParserNormativo {

	private Normativo norm;
	private String livro = "";
	private String curTitulo = "";
	private String curCapitulo = "";
	private String curSecao = "";
	private String curSubsecao = "";
	private String preambulo = "";
	private String anexo;

	boolean isArtigo  = false;
	//boolean isPreambulo = false;
	boolean isEmenta = false;

	private Artigo artigo;
	/**
	 * O modoCaput será TRUE quando o artigo atual
	 * incluir trechos de outro normativo, o qual devera ser incluído como
	 * caput.
	 *
	 * O principal marcador será o ínicio da linha com haspas "
	 *
	 */
	 private boolean isModoCaput = false;

	/**
	 * O modo multi indica que o paragrafo/inciso/alinea
	 * são enumerações do item anterior.
	 */
	 boolean isModoMulti = false;

	private 	int curArtigoNum = 0;
	private 	int nextArtigoNum = 0;
	private 	String curArtigoID = "";
	private 	String nextArtigoID = "";


	/**
	 * CTor
	 * @param norm
	 */
	public ParserNormativo(Normativo norm) {
		this.norm = norm;
	}

	/**
	 * Adiciona um artigo
	 * @param aa
	 * @return
	 */
	public  ParserNormativo add(Artigo aa) {
		norm.addArtigo(aa);
		return this;
	}

	/**
	 * Adiciona um artigo
	 * @param aa
	 * @return
	 */
	public  ParserNormativo appendTextoArtigo(String complemento) {
		artigo.append2Artigo(complemento);
		return this;
	}

	public ParserNormativo setAnexo(String anexo) {
		this.anexo = anexo;
		this.setLivro("");
	    return this;
	}

	public ParserNormativo setLivro(String livro) {
		this.livro = livro;
		this.setCurTitulo("");
		return this;
	}

	public ParserNormativo setCurTitulo(String titulo) {
		curTitulo = titulo;
		curCapitulo = "";
		curSecao = "";
		curSubsecao = "";
		isEmenta = false;
		return this;
	}

	public ParserNormativo setCurCapitulo(String capitulo) {
		curCapitulo = capitulo;
		curSecao = "";
		curSubsecao = "";
		isEmenta = false;
		return this;
	}

	public ParserNormativo setCurSecao(String secao) {
		curSecao = secao;
		curSubsecao = "";
		isEmenta = false;
		return this;
	}

	/**
	 * Verifica se o novo artigo segue sequencia do anterior.
	 * Pode ser número ou numero mais letras:<br>
	 * <li> Art. 1º.
	 * <li> Art. 76-A.
	 * @param artigoID
	 * @return
	 */
	public boolean isNovoArtigoOK(String artigoID) {
		int num = extraiNumeroArtigo(artigoID);
        String  curArtigoID = this.curArtigoID;

		// caminho feliz:
		if (num > this.curArtigoNum)
			return true;

		if (num < 1)
			throw new RuntimeException("Artigo id invalido: " + artigoID);

		// suporte para artigos com letras e números

		if (num < this.curArtigoNum) {
			//new RuntimeException("Artigo menor: " + artigoID).printStackTrace();;
			return false;
		}

		if (this.curArtigoNum == num) {

			 if(artigoID.trim().length()>curArtigoID.trim().length()) {
				 return true;
			 }
			 int compare = curArtigoID.compareToIgnoreCase(artigoID) ;
			 if(compare < 0)
				 return true;
			if(compare==0) {
				new RuntimeException("Artigo repetido: " + artigoID).printStackTrace();
			}
		}

		return false;
	}

	/**
	 * Extrai o numero do artigo
	 * @param artigoLabel
	 * @return número do artigo ou -1
	 */
	private int extraiNumeroArtigo(String artigoLabel) {
		String regex = "\\d+";
		Pattern pattern = NormPatterns.compile(regex);
		Matcher matcher = pattern.matcher(artigoLabel);
		if (matcher.find()) {
		    String numStr = matcher.group();
		    int numero = Integer.parseInt(numStr);
		    return numero;
		}
		return -1;
	}

	/**
	 * Inicializa nova instancia de Artigo e o ID do próximo,
	 *
	 * @param artigo
	 * @return
	 */
	public ParserNormativo setCurArtigo(String artigoLabel) {
		isEmenta = false;
		curArtigoID = artigoLabel;
		nextArtigoID = "";

		int numero = extraiNumeroArtigo(artigoLabel);
		if (numero>0) {
		    this.curArtigoNum = numero;
		    String numStr = "" + (numero +1) + (numero<10? "º":"");
		    nextArtigoID = artigoLabel.substring(0, artigoLabel.indexOf(" ")) + " "+ numStr ;
		}else {
			throw new RuntimeException("Artigo Inválido: " + artigoLabel);
		}


		// Novo artigo
		artigo = new Artigo();
		norm.addArtigo(artigo);

		// populate
		artigo.setTitulo(this.curTitulo);
		artigo.setCapitulo(this.curCapitulo);
		artigo.setSessao(this.curSecao);
		artigo.setSubsessao(this.curSubsecao);
		artigo.setId(artigoLabel);
		return this;
	}

}
