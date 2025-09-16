package bor.tools.splitter;

/**
 * Interface para verificar se um artefato existe.
 * @param <T>
 */
public interface ExistsArtefato<T> {
	/**
	 * Verifica se um artefato existe na base de dados.
	 *
	 * @param artefato a ser verificado
	 * @return
	 */
	public boolean exists(T artefato);
}
