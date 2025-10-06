package bor.tools.splitter.normsplitter;

import java.util.ArrayList;
import java.util.List;

import bor.tools.simplerag.entity.Metadata;
import lombok.Data;

/**
 * Seção de um Capítulo de um normativo. Uma seção pode conter vários Artigos.
 * Na integração com AbstractSplitter, uma seção é um "subcapítulo" de capitulo e poderá ser 
 * entendido, na maioria dos casos, como um candidato à objeto bor.tools.simplerag.CapituloDTO.
 * 
 * @see ChapterDTO
 */
@Data
class Secao {
    private String numero;
    private String nome;
    private List<Artigo> artigos;
    
    /**
     * Metadados do Capítulo. <br>
     * 
     * Deve incluir informações como:
     * <li>Nome da lei completo
     * <li>Número da lei
     * <li>Apelido da lei (LAI, CF/88, CLT, LGPD, etc)
     * <li>Data da Publicação
     * <li> Nome do capitulo
     * <li> Nome da seção
     * <li> Síntese da Sessão	 
     * 
     */
    private Metadata metadados = new Metadata();

    public Secao() {
	this.artigos = new ArrayList<>();
    }
    
    public Secao(String numero, String nome) {
	this.numero = numero;
	this.nome = nome;
	this.artigos = new ArrayList<>();
    }

    public void addArtigo(Artigo artigo) {
	this.artigos.add(artigo);
    }

}