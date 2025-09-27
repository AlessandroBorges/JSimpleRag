
package bor.tools.splitter.normsplitter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import lombok.Data;
import bor.tools.splitter.WebLinks;


/**
 * Classe para armazenar dados de normativos
 */
@Data
public class Normativo implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = -3062848226824491301L;

	/**
	 * Identificação. Exemplo:
	 * <li>Lei nº 14.133/2022
	 * <li>Constituição Federal 1988
	 * <LI> CF/88
	 */
	String id = "";

	/**
	 * Nome descritivo do normativo.
	 * <li>CONSTITUIÇÃO DA REPÚBLICA FEDERATIVA DO BRASIL DE 1988
	 * <li>Lei de Licitações e Contratos Administrativos.
	 */
	String alias = "";

	/**
	 * Preambulo
	 */
	String preambulo = "";

	/**
	 * Ementa da Lei
	 */
	String ementa;

	/**
	 * Data da publicacao
	 */
	Date data_publicacao;

	/**
	 * Tipo de Normativo
	 */
	String tipo;

	/**
	 * URL Download
	 */
	String url;

	/**
	 * URL Metadados - O link original está bugado
	 * É necessario desenvolver scrapper adequado
	 */
	String urlMetadados;

	/**
	 * texto do normativo
	 */
	String texto;

	/**
	 * Metadados advindos de {@link #urlMetadados}
	 */
	String metadados;
	/**
	 * Conteudo das tabelas. Inicialmente vamos pegar apenas a segunda tabela, onde
	 * eestão as informações mais relevantes sobre o normativo
	 */
	List<WebLinks> listConteudoTabela = new ArrayList<>();

	/**
	 * Todos os links contidos no normativo
	 */
	Collection<WebLinks> listLinks = new LinkedHashSet<>();

	/**
	 * Tabelas em formato MarkDown
	 */
	List<String> listTabelas = new ArrayList<>();

	/**
	 * Relação de Artigos no Normativo
	 */
	List<Artigo> artigos = new ArrayList<>();

	/**
	 * Documento revogado
	 */
	boolean revogado = false;

	/**
	 * Normativo que revogou
	 */
	String revogadoPor = "";

	/**
	 * Vigência do Normativo
	 */
	String vigencia = "";

	/**
	 * Normativo Anexo - Caso de: <br>
	 * <li> Ato de Disposições Transitórias
	 * <li> Regulamentos / Estatutos
	 * <li> Leis Alteradas
	 */
	Collection<Normativo> listNormativosAnexos = new HashSet<>();

	/**
	 * Regulamentações conhecidas
	 */
	Collection<Normativo> listRegulamentos = new HashSet<>();

	/**
	 * Outros normativos associados
	 */
	Collection<Normativo> listNormativosAssociado = new HashSet<>();

	Normativo revogacao = null;

	/**
	 * Construtor padrão
     **/
	public Normativo() {

	}

    public void addAnexo(Normativo anexo) {
		listNormativosAnexos.add(anexo);
	}


    public void addWebLinks(WebLinks webLink) {
    	this.listLinks.add(webLink);
    	checaReferencias();
    }

    public void addWebLinks(Collection<WebLinks> webLinks) {
    	this.listLinks.addAll(webLinks);
    	checaReferencias();
    }

    /**
     * Procura por:  <br>
     * <li> Regulamento
     * <li> Referências à outras Leis/Decretos
     * <li> Revogações
     * <li> Vigência
     */
	public void checaReferencias() {
		// Regulamento
		List<WebLinks> regulamentos   = NormativosLoader.procuraURL(this.listLinks, "regulamento", "regulamentação" );
		if (regulamentos.size()>0) {
			for(WebLinks linkReg : regulamentos)
				criarNormativoRegulamentador(linkReg.getUrl());
		}

		// outros regulamentos
		regulamentos = NormativosLoader.procuraURL(this.listLinks, "vide decreto");
		if (regulamentos.size()>0) {
			for(WebLinks linkReg : regulamentos)
				criarNormativoRegulamentador(linkReg.getUrl());
		}

		// Outras Lei e Decretos
		regulamentos = NormativosLoader.procuraURL(this.listLinks, "lei", "decreto", "resolução", "portaria", "instrução normativa", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo", "ato normativo", "ato declaratório", "ato conjunto", "ato administrativo");
		if (regulamentos.size()>0) {
			for(WebLinks linkReg : regulamentos)
				criarNormativoRegulamentador(linkReg.getUrl());
		}
	}


	/**
	 * Adiciona um artigo na tabela
	 *
	 * @param art
	 * @return
	 */
	public Normativo addArtigo(Artigo art) {
		artigos.add(art);
		return this;
	}



    /**
     * Cria um Nomativo Anexo à este
     * @return
     */
	public Normativo criarNormativoAnexo() {
		Normativo anexo = new Normativo();
		addAnexo(anexo);
		anexo.data_publicacao = this.data_publicacao;
		anexo.id = "ANEXO DE " + this.id;
		return anexo;
	}

	/**
     * Cria um Normativo Anexo à este
     * @return
     */
	public Normativo criarNormativoRegulamentador(String url) {
		Normativo regulamento = new Normativo();
		regulamento.setUrl(url);
		this.listRegulamentos.add(regulamento);
		return regulamento;
	}


	/**
	 * Cria normativo Revogador
	 * @param url
	 * @return
	 */
	public Normativo criarNormativoRevogacao(String url) {
		Normativo revogador = new Normativo();
		revogador.setUrl(url);
		this.revogacao = revogador;
		return revogador;
	}

	/**
	 * Cria normativo associado
	 * @param url
	 * @return
	 */
	public Normativo criarNormativoAssociado(String url) {
		Normativo associado = new Normativo();
		associado.setUrl(url);
		this.listNormativosAssociado.add(associado);
		return associado;
	}

}// Normativo



