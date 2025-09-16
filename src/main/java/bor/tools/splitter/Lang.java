package bor.tools.splitter;

/**
 * Enumeracao de algumas linguagens suportadas pela Wikipedia. <br>
 * São suportadas as seguintes linguagens:<br>
 *
 * <ul> PT - português
 * <ul> PT_BR - português brasileiro
 * <ul> EN - inglês
 * <ul> ES - espanhol
 * <ul> FR - francês
 * <ul> DE - alemão
 * <ul> IT - italiano
 * <ul> RU - russo
 * <ul> ZH - chinês
 * <ul> JA - japonês
 * <ul> AR - árabe
 * <ul> EO - esperanto
 * <ul> HI - hindi
 *

 *
 */
public enum Lang {
	PT("pt"), PT_BR("pt_br"), EN("en"), ES("es"), FR("fr"),
	DE("de"),
	IT("it"),
	RU("ru"),
	ZH("zh"),
	JA("ja"),
	AR("ar"),
	EO("eo"),
	HI("hi"),;


	private String lang;

	Lang(String lang) {
		this.lang = lang;
	}

	/**
	 * Retorna o código da linguagem
	 */
	public String getLang() {
		return lang;
	}

	/**
	 * Retorna o nome da linguagem em inglês
     *
	 */
	public String getLangName() {
		switch (this) {
		case PT:
			return "Portuguese";
		case PT_BR:
			return "Brazilian Portuguese";
		case EN:
			return "English";
		case ES:
			return "Spanish";
		case FR:
			return "French";
		case DE:
			return "German";
		case IT:
			return "Italian";
		case RU:
			return "Russian";
		case ZH:
			return "Chinese";
		case JA:
			return "Japanese";
		case AR:
			return "Arabic";
		case EO:
			return "Esperanto";
		case HI:
            return "Hindi";
		}
		return "Brazilian Portuguese";
	}

	/**
	 * Retorna a linguagem correspondente ao código informado.
	 * São suportados os seguintes códigos:
	 * <ul> pt - português
	 * <ul> pt_br - português brasileiro
	 * <ul> en - inglês
	 * <ul> es - espanhol
	 * <ul> fr - francês
	 * <ul> de - alemão
	 * <ul> it - italiano
	 * <ul> ru - russo
	 * <ul> zh - chinês
	 * <ul> ja - japonês
	 * <ul> ar - árabe
	 * <ul> eo - esperanto
	 * <ul> hi - hindi
	 *
	 * @param langCode - código da linguagem , em dois digitos.
	 * @return
	 */
	public static Lang getLang(String langCode) {
		switch (langCode) {
		case "pt":	return PT;
		case "pt_br": return PT_BR;
		case "en":	return EN;
		case "es":	return ES;
		case "fr":	return FR;
		case "de":	return DE;
		case "it":	return IT;
		case "ru":	return RU;
		case "zh":	return ZH;
		case "ja":	return JA;
		case "ar":	return AR;
		case "eo":	return EO;
		case "hi":	return HI;

		}
		return PT;
	}
}