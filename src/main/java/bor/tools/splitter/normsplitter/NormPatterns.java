package bor.tools.splitter.normsplitter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for Patterns.
 *
 * Os flags dos regex são as seguintes letras:
 * <li> m - {@link Pattern#MULTILINE}
 * <li> i - {@link Pattern#CASE_INSENSITIVE}
 * <li> s - {@link Pattern#DOTALL}
 * <li> x - {@link Pattern#COMMENTS}
 * <li> u - {@link Pattern#UNICODE_CASE}
 * <li> U - {@link Pattern#UNICODE_CHARACTER_CLASS}
 *
 *
 */
class NormPatterns {
	static Map<String, Pattern> map = new HashMap<>();


	private static Pattern get(String nome) {
		return map.get(nome);
	}


	/**
	 * Compile a Regex
	 * @param regex
	 * @return
	 */
	public static Pattern compile(String regex) {
		Pattern p = map.get(regex);
		if(p==null) {
			p = Pattern.compile(regex);
			map.put(regex, p);
		}
		return p;
	}



	/**
	 * Compile e store a Pattern
	 * @param regex
	 * @param flags_
	 * @return
	 */
	public static Pattern compile(String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = get(regex);
		if(pattern==null) {
			pattern = createPattern(regex, flags_);
		}
		return pattern;
	}

	/**
	 * Create and store for later use
	 * @param regex  - regex expression
	 * @param flags _ - flags "misxuU"
 	 * @return pattern created
	 */
	private static Pattern createPattern(String regex, String flags_) {
		flags_ = flags_ == null ? "" : flags_;
		Pattern pattern = get(regex + "#" + flags_);
		if(pattern == null) {
		 int flags =  (flags_.contains("m") ? Pattern.MULTILINE : 0)
				    | (flags_.contains("i") ? Pattern.CASE_INSENSITIVE :0)
		            | (flags_.contains("s") ? Pattern.DOTALL :0)
		            | (flags_.contains("x") ? Pattern.COMMENTS :0)
		            | (flags_.contains("u") ? Pattern.UNICODE_CASE :0)
		            | (flags_.contains("U") ? Pattern.UNICODE_CHARACTER_CLASS :0);
		 pattern = Pattern.compile(regex, flags);
		 map.put(regex + "#" + flags_, pattern);
		}
		return pattern;
	}

	/**
	 * Verifica se deu Match.
	 * @param string - string to test
	 * @param regex - regex expression
	 * @param flags_ - Regex flags. veja {@link Pattern#compile(String, int)}
	 * @return true - if matches apply
	 */
	public static boolean matches(String string, String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = get(regex + "#" + flags_);
		if(pattern == null) {
		   pattern = createPattern(regex, flags_);
		}
	   final Matcher matcher = pattern.matcher(string);
	   return matcher.find();
	}

	/**
	 * Extrai primeira ocorrência
	 *
	 * @param string - texto fonte
	 * @param regex - expressão regex
	 * @param flags_ - flags. veja {@link Pattern#compile(String, int)}
	 *
	 * @return null, se não achou nada
	 */
	public static String extractFirst(String string, String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = get(regex + "#" + flags_);
		if(pattern == null) {
		   pattern = createPattern(regex, flags_);
		}
	   final Matcher matcher = pattern.matcher(string);

	   if (matcher.find()) {
            return matcher.group(0);
       }
	   return null;
	}


	/**
	 * Extrai lista de ocorrências
	 * @param string - fonte a pesquisar
	 * @param regex  - expressão regex
	 * @param flags_ - flags. veja {@link Pattern#compile(String, int)}
	 * @return List com ocorrencia. Pode ser vazia.
	 */
	public static List<String> extractAll(String string, String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = get(regex + "#" + flags_);
		if(pattern == null) {
		   pattern = createPattern(regex, flags_);
		}
	   final Matcher matcher = pattern.matcher(string);

	   List<String> list = new ArrayList<>();
	   if (matcher.find()) {
		   for (int i = 1; i <= matcher.groupCount(); i++) {
              list.add(matcher.group(i));
           }
       }
	   return list;
	}

	/**
	 * Troca todas as ocorrências dadas pela expressão.
	 * @param string - fonte
	 * @param subst - nova substring
	 * @param regex - expressãop
	 * @param flags_ flags, como "gmi"
	 *
	 * @return String modificada.
	 */
	public static String replaceAll(String string, String subst, String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = get(regex + "#" + flags_);
		if(pattern == null) {
		   pattern = createPattern(regex, flags_);
		}
	   final Matcher matcher = pattern.matcher(string);
	   final String result = matcher.replaceAll(subst);
	   return result;
	}

	/**
	 * Remove todas as ocorrências capturadas pela regex
	 * @param src - fonte
	 * @param regex - expressão regular
	 * @param flags_ - flags, como gmis
	 * @return string src atualizada
	 */
	public static String removerTodos(String src, String regex, String flags_) {
		return replaceAll(src, "", regex, flags_);
	}

	/**
	 * Remove a primeira ocorrência dada por regex
	 * @param src - fonte
	 * @param regex - expressão regular
	 * @param flags_ - flags, como gmis
	 * @return string src atualizada
	 */
	public static String removerPrimeiro(String src, String regex, String flags_) {
		return replaceFirst(src, "", regex, flags_);
	}

	/**
	 * Troca todas as ocorrencias
	 * @param string - fonte
	 * @param subst - nova substring
	 * @param regex - expressãop
	 * @param flags_ flags, como "gmi"
	 *
	 * @return String modificada.
	 */
	public static String replaceFirst(String string, String subst, String regex, String flags_) {
		flags_ = flags_ == null ? "0" : flags_;
		Pattern pattern = map.get(regex + "#" + flags_);
		if(pattern == null) {
		   pattern = createPattern(regex, flags_);
		}
	   final Matcher matcher = pattern.matcher(string);
	   final String result = matcher.replaceFirst(subst);
	   return result;
	}

	/**
	 * Remove acentos das palavras
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
	 * @param src - string a ser testada
	 * @param cont - substring contida
	 * @return true src se contem
	 */
	public static boolean containsFlex(String src, String cont) {
		if(src==null || cont==null)
			return false;
		String a = deAccent(src).toLowerCase();
		String b = deAccent(cont).toLowerCase();
		return a.contains(b);
	}

}
