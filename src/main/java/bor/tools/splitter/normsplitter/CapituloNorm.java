package bor.tools.splitter.normsplitter;

import java.util.ArrayList;
import java.util.List;

import bor.tools.simplerag.dto.Metadata;
import lombok.Data;

/**
 * Classe para Capítulo Um Capitiulo pode conter várias Seções, que por sua vez
 * contêm Artigos.
 */
@Data
public class CapituloNorm {
    /**
     * Número do capítulo. Exemplo: 1, 2, I, II, III
     */	
    private String numero;
    /**
     * Nome descritivo do capítulo. Exemplo: Disposições Preliminares
     */
    private String nome;
    /**
     * Um Capitulo de normativo pode ter várias seções.<br>
     * Seções deste capítulo. Uma seção pode conter vários Artigos.
     */
    private List<Secao> secoes;

    /**
     * Metadados do Capítulo. <br>
     * 
     * Deve incluir informações como:
     * <li>Nome da lei completo
     * <li>Número da lei
     * <li>Apelido da lei (LAI, CF/88, CLT, LGPD, etc)
     * <li>Data da Publicação
     * <li> Nome do capitulo
     * <li> Síntese do capítulo	 
     * 
     */
    private Metadata metadados = new Metadata();
    
    public CapituloNorm() {
	this.secoes = new ArrayList<>();
    }
    
    public CapituloNorm(String numero, String nome) {
	this.numero = numero;
	this.nome = nome;
	this.secoes = new ArrayList<>();
    }

    public void addSecao(Secao secao) {
	this.secoes.add(secao);
    }

}
