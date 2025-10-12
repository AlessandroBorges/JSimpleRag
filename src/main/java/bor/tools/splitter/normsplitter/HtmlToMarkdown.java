package bor.tools.splitter.normsplitter;
//package com.vladsch.flexmark.java.samples;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolver;
import com.vladsch.flexmark.html2md.converter.HtmlLinkResolverFactory;
import com.vladsch.flexmark.html2md.converter.HtmlMarkdownWriter;
import com.vladsch.flexmark.html2md.converter.HtmlNodeConverterContext;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRenderer;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRendererFactory;
import com.vladsch.flexmark.html2md.converter.HtmlNodeRendererHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Classe modeloLLM de conversão entre HTML e MarkDown
 */
public class HtmlToMarkdown {

	private static final boolean debug = false;

    /**
	 * CTOR oculto
	 */
	private HtmlToMarkdown() {}

	/**
	 * Classe interna
	 */
    static class CustomLinkResolver implements HtmlLinkResolver {
	
	@SuppressWarnings("unused")
	private HtmlNodeConverterContext context;
	
        public CustomLinkResolver(@SuppressWarnings("unused") HtmlNodeConverterContext context) {
            this.context = context;
        }

        @Override
        public ResolvedLink resolveLink(Node node, HtmlNodeConverterContext context, ResolvedLink link) {
            // convert all links from http:// to https://
            if (link.getUrl().startsWith("http:")) {
                return link.withUrl("https:" + link.getUrl().substring("http:".length()));
            }
            return link;
        }

        static class Factory implements HtmlLinkResolverFactory {
            @Nullable
            @Override
            public Set<Class<?>> getAfterDependents() {
                return null;
            }

            @Nullable
            @Override
            public Set<Class<?>> getBeforeDependents() {
                return null;
            }

            @Override
            public boolean affectsGlobalScope() {
                return false;
            }

            @Override
            public HtmlLinkResolver apply(HtmlNodeConverterContext context) {
                return new CustomLinkResolver(context);
            }
        }
    }

    static class HtmlConverterTextExtension implements FlexmarkHtmlConverter.HtmlConverterExtension {
        public static HtmlConverterTextExtension create() {
            return new HtmlConverterTextExtension();
        }

        @Override
        public void rendererOptions(@NotNull MutableDataHolder options) {

        }

        @Override
        public void extend(FlexmarkHtmlConverter.@NotNull Builder builder) {
            builder.linkResolverFactory(new CustomLinkResolver.Factory());
            builder.htmlNodeRendererFactory(new CustomHtmlNodeConverter.Factory());
        }
    }

    static class CustomHtmlNodeConverter implements HtmlNodeRenderer {
	@SuppressWarnings("unused")
	private DataHolder options;
	
        public CustomHtmlNodeConverter(DataHolder options) {
            this.options = options;
        }

        @Override
        public Set<HtmlNodeRendererHandler<?>> getHtmlNodeRendererHandlers() {
            return new HashSet<>(Collections.singletonList(
                    new HtmlNodeRendererHandler<>("kbd", Element.class, this::processKbd)
            ));
        }

        private void processKbd(Element node, HtmlNodeConverterContext context, HtmlMarkdownWriter out) {
            out.append("<<");
            context.renderChildren(node, false, null);
            out.append(">>");
        }

        static class Factory implements HtmlNodeRendererFactory {
            @Override
            public HtmlNodeRenderer apply(DataHolder options) {
                return new CustomHtmlNodeConverter(options);
            }
        }
    }

    /**
     * Converte String HTML em Markdown
     * @param html - fonte em HTML
     * @return resultado em MarkDown
     */
	public static String convertHtml_2_Markdown(String html) {
		MutableDataSet options = new MutableDataSet();
		options.set(Parser.EXTENSIONS,
		            Arrays.asList(TablesExtension.create(),
		                          AutolinkExtension.create(),
		                          StrikethroughExtension.create()));
				/*
				  new MutableDataSet().set( Parser.EXTENSIONS,
				                           Collections.singletonList(HtmlConverterTextExtension.create())
				                                           );
				                            */
		String markdown = FlexmarkHtmlConverter.builder(options).build().convert(html);
		return markdown;
	}


    /**
     * Le um arquivo e devolve uma string
     * @param fileLocation
     * @return
     * @throws IOException
     */
    public String lerArquivo(String fileLocation) throws IOException {

    	FileReader fileReader = new FileReader(fileLocation);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        int index = 1;
        StringBuilder sb = new StringBuilder();
        while ((line = bufferedReader.readLine()) != null) {
           if (debug) System.out.println("line " + index + " : " + line);
        	sb.append(line).append("\n");
            index++;
        }
        String res  = sb.toString();
        bufferedReader.close();
        fileReader.close();
        return res;
    }

    public static void main(String[] args) {
        String html = "";
    	MutableDataSet options = new MutableDataSet()
                                 .set(Parser.EXTENSIONS,
                                	   Collections.singletonList(HtmlConverterTextExtension.create()));
        String markdown = FlexmarkHtmlConverter.builder(options).build().convert(html);

       // System.out.println("HTML:");
      //  System.out.println(html);
        System.out.println("************************");
        System.out.println("\nMarkdown:");
        System.out.println(markdown);
    }
}