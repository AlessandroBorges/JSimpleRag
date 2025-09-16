package bor.tools.splitter;

import java.io.Serializable;
import java.util.Objects;

import lombok.Data;

/**
 * Conteudo com texto e, opcionalmente, uma URL associada
 */
@Data
public class WebLinks implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = -883994976798082258L;

	/**
	 * Construtor padrão
     */
	public WebLinks() {}

	/**
	 * Ctor
	 * @param texto - texto
	 * @param url - url
	 */
	public WebLinks(String texto, String url) {
		this.texto = texto;
		this.url = url;
	}

	private String texto;
	private String url;



	@Override
	public String toString() {
		return "WebLinks [" + (texto != null ? "texto=" + texto + ", " : "") + (url != null ? "url=" + url : "") + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof WebLinks))
			return false;
		WebLinks other = (WebLinks) obj;
		return Objects.equals(texto, other.texto) && Objects.equals(url, other.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(texto, url);
	}



}


