package bor.tools.simplerag.entity.enums;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the type of document content.
 * Maps to PostgreSQL enum tipo_conteudo ('livro', 'normativo', 'artigo', 'manual', 'outros')
 */
public enum TipoConteudo {
    CONSTITUICAO(1, "constituicao", "Texto da Constituição Federal ou Estadual"),
    LEI(2, "lei", "Lei ordinária ou complementar"),
    DECRETO(3, "decreto", "Decreto executivo ou legislativo"),
    SUMULA(4, "sumula", "Súmula de jurisprudência"),
    ACORDAO(5, "acordao", "Decisão colegiada de tribunal"),
    INSTRUCAO_NORMATIVA(6, "instrucao_normativa", "Norma administrativa interna"),
    PORTARIA(7, "portaria", "Ato administrativo de autoridade"),
    NORMATIVO(8, "normativo", "Documento com força normativa"),
    TESE(9, "tese", "Tese acadêmica ou jurídica"),
    ARTIGO(10, "artigo", "Artigo científico ou técnico"),
    LIVRO(11, "livro", "Livro técnico, científico ou jurídico"),
    RELATORIO(12, "relatorio", "Relatório técnico ou institucional"),
    NOTA_TECNICA(13, "nota_tecnica", "Nota técnica explicativa"),
    NOTA_AUDITORIA(14, "nota_auditoria", "Nota de auditoria interna ou externa"),
    MANUAL(15, "manual", "Manual de procedimentos ou instruções"),
    PAPEL_TRABALHO(16, "papel_trabalho", "Documento de apoio à auditoria"),
    RESUMO(17, "resumo", "Resumo de conteúdo ou documento"),
    WIKI(18, "wiki", "Conteúdo colaborativo tipo Wiki"),
    PROJETO(19, "projeto", "Documento de projeto ou planejamento"),
    DOCUMENTO_INTERNO(20, "documento_interno", "Documento interno da organização"),
    OUTROS(99, "outros", "Outro tipo de documento não listado");

    /**
     * The corresponding code in the application.
     */
    private final int codigo;
    /**
     * The corresponding value in the PostgreSQL database.
     */
    private final String dbValue;
    /**
     * A descrição amigável do tipo de conteúdo.
     */
    private final String description;

    TipoConteudo(int codigo, String dbValue, String description) {
        this.codigo = codigo;
        this.dbValue = dbValue;
        this.description = description;
    }

    public int getCodigo() {
        return codigo;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    public String getDescription() {
        return description;
    }

    
    /**
     * Get enum from code
     */
    public static TipoConteudo fromCodigo(int codigo) {
        for (TipoConteudo tipo : values()) {
            if (tipo.codigo == codigo)
                return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_conteudo code: " + codigo);
    }

    /**
     * get all names as array of strings
     * @return
     */
    public static String[] getAllNames() {
        String[] names = new String[values().length];
        for (int i = 0; i < values().length; i++) {
            names[i] = values()[i].name();
        }
        return names;
    }
    
    /**
     * get all names and descriptions as array of strings
     * @return
     */
    public static String[] getAllNamesAndDescriptions() {
	String[] names = new String[values().length];
	for (int i = 0; i < values().length; i++) {
	    names[i] = values()[i].name() + " - " + values()[i].getDescription();
	}
	return names;
    }

    /**
     * Get enum from name, case insensitive
     * @param name
     * @return
     */
    @JsonCreator
    public static TipoConteudo fromString(String name) {
	return fromName(name);
    }
    
    /**
     * Get enum from name, case insensitive
     */    
    public static TipoConteudo fromName(String name) {
        for (TipoConteudo tipo : values()) {
            if (tipo.name().equalsIgnoreCase(name))
                return tipo;
        }
        throw new IllegalArgumentException("Unknown tipo_conteudo name: " + name);
    }
}