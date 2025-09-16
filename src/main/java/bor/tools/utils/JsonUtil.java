package bor.tools.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Classe Singleton com ObjectMapper.
 * 
 */
public class JsonUtil {

	private static JsonUtil singleton;

	private JsonUtil() {
		 mapper = new ObjectMapper();
		 mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		 mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		 mapper.addMixIn(Object.class, HibernatePropertiesFilter.class);
	}
	
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private abstract class HibernatePropertiesFilter {
        // Empty mixin class
    }

	/**
	 * Mapper para serialização Json.<br>
	 * <b> É estático!<b>
	 */
	private ObjectMapper mapper;

	/**
	 * Recupera um ObjectMapper singleton.<br>
	 * @return
	 */
	public static synchronized ObjectMapper getMapper() {
		if(singleton==null) {
			singleton = new JsonUtil();
		}
		return singleton.mapper;
	}

	/**
	 * Alias para {@link #converte(String, Class)}
	 * @param <T>
	 * @param json
	 * @param tipo
	 * @return
	 */
	public static <T> T fromJson(String json, Class<T> tipo) {
		json = json
	            .replace("```json"," ")
	            .replace("```", " ")
	            .replace("```", " ").trim();		
		int start = Math.max(json.indexOf("{"), json.indexOf("["));
		int end = Math.max(json.lastIndexOf("}"), json.lastIndexOf("]"));
		if (start < 0 || end < 0) {
			System.err.println("Erro na conversão primária de JSON. json: " + json);
			return null;
		}
		if (start > 0) {
			json = json.substring(start, end + 1);
		}
		return converte(json, tipo);
	}

	/**
	 * Converte um objeto em JSON
	 * @param obj - objeto POJO a ser convertido
	 * @return representacao string JSON
	 */
	public static String toJson(Object obj) {
		try {
			return getMapper().writeValueAsString(obj);
		} catch (Exception e) {
			System.err.println("Erro na conversão de JSON." + "\n tipo: " + obj.getClass() + "\nobj: " + obj);
			//e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * Converte um objeto em JSON
	 * @param obj - objeto POJO a ser convertido
	 * @return representacao string JSON
	 */
	public static String toJsonPrint(Object obj) {
		try {
			return getMapper()
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(obj);
		} catch (Exception e) {
			System.err.println("Erro na conversão de JSON." + "\n tipo: " + obj.getClass() + "\nobj: " + obj);
			//e.printStackTrace();
		}
		return null;
	}

	/**
	 * Converte String json em instancia de tipo
	 * @param <T>
	 * @param json - string JSON
	 * @param tipo - Classe do tipo
	 * @return null, se falhar ou uma instância de tipo
	 */
	public static <T> T converte(String json, Class<T> tipo) {
		try {
			T e = getMapper().readValue(json, tipo);
			return e;
		} catch (Exception e) {
			System.err.println("Erro na conversão primária de JSON."
					+ "\n tipo: " + tipo.getName()
					+ "\n json:\n" + json==null? " null json" : json.substring(0, Math.min(200,json.length()))
					+ "\n ## Message: " + e.getMessage()
					);
			//e.printStackTrace();
		}

		return null;
	}

}
