package com.edlio.emailreplyparser;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;



public class EmailParser {

	private static final Pattern SIG_PATTERN = Pattern.compile( "((^Sent from my (\\s*\\w+){1,3}$)|(^-\\w|^\\s?__|^\\s?--|^\u2013|^\u2014))", Pattern.DOTALL);
	private static final Pattern QUOTE_PATTERN = Pattern.compile("(^>+)", Pattern.DOTALL);
	private static final List<Pattern> DEFAULT_COMPILED_QUOTE_HEADER_PATERNS = List.of(
			Pattern.compile("^(On\\s(.{1,500})wrote:)", Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("From:[^\\n]+\\n?([^\\n]+\\n?){0,2}To:[^\\n]+\\n?([^\\n]+\\n?){0,2}Subject:[^\\n]+", Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("To:[^\\n]+\\n?([^\\n]+\\n?){0,2}From:[^\\n]+\\n?([^\\n]+\\n?){0,2}Subject:[^\\n]+", Pattern.MULTILINE | Pattern.DOTALL)
			);
	private static final int DEFAULT_maxParagraphLines = 6;
	private static final int DEFAULT_maxNumCharsEachLine = 200;

	public static class EmailParserBuilder{

		private List<String> quoteHeadersRegex = new ArrayList<>();
		private int maxParagraphLines = DEFAULT_maxParagraphLines;
		private int maxNumCharsEachLine = DEFAULT_maxNumCharsEachLine;

		public EmailParserBuilder withQuoteHeadersRegex(String regex){
			quoteHeadersRegex.add(regex);
			return this;
		}

		public EmailParserBuilder withMaxParagraphLines(int maxParagraphLines){
			this.maxNumCharsEachLine = maxParagraphLines;
			return this;
		}

		public EmailParserBuilder withMaxNumCharsEachLine(int maxNumCharsEachLine){
			this.maxNumCharsEachLine = maxNumCharsEachLine;
			return this;
		}

		public EmailParser build(){
			List<Pattern> compiledQuoteHeaderPatterns = new ArrayList<>(DEFAULT_COMPILED_QUOTE_HEADER_PATERNS);
			quoteHeadersRegex.stream()
					.map(regex -> Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL))
					.forEach(compiledQuoteHeaderPatterns::add);
			return new EmailParser(compiledQuoteHeaderPatterns, maxParagraphLines, maxNumCharsEachLine);
		}

	}

	private final List<Pattern> compiledQuoteHeaderPatterns;
	private final int maxParagraphLines;
	private final int maxNumCharsEachLine;

	private List<FragmentDTO> fragments;


	private EmailParser(List<Pattern> compiledQuoteHeaderPatterns, int maxParagraphLines, int maxNumCharsEachLine){
		this.compiledQuoteHeaderPatterns = compiledQuoteHeaderPatterns;
		this.maxParagraphLines = maxParagraphLines;
		this.maxNumCharsEachLine = maxNumCharsEachLine;
	}

	/**
	 * Splits the given email text into a list of {@link Fragment} and returns the {@link Email} object.
	 *
	 * @param emailText
	 * @return
	 */
	public Email parse(String emailText) {
		fragments = new LinkedList<>();

		// Normalize line endings
		emailText = emailText.replace("\r\n", "\n");

		FragmentDTO fragment = null;

		// Split body to multiple lines.
		String[] lines = new StringBuilder(emailText).toString().split("\n");
		/* Reverse the array.
		 *
		 * Reversing the array makes us to parse from the bottom to the top.
		 * This way we can check for quote headers lines above quoted blocks
		 */
		ArrayUtils.reverse(lines);

		/* Paragraph for multi-line quote headers.
		 * Some clients break up the quote headers into multiple lines.
		 */
		List<String> paragraph = new LinkedList<>();

		// Scans the given email line by line and figures out which fragment it belong to.
		for (String line : lines){
			// Strip new line at the end of the string
			line = StringUtils.stripEnd(line, "\n");
			// Strip empty spaces at the end of the string
			line = StringUtils.stripEnd(line, null);

			/* If the fragment is not null and we hit the empty line,
			 * we get the last line from the fragment and check if the last line is either
			 * signature and quote headers.
			 * If it is, add fragment to the list of fragments and delete the current fragment.
			 * Also, delete the paragraph.
			 */
			if (fragment != null && line.isEmpty()) {
				String last = fragment.lines.get(fragment.lines.size()-1);

				if (isSignature(last)) {
					fragment.isSignature = true;
					addFragment(fragment);

					fragment = null;
				}
				else if (isQuoteHeader(paragraph)) {
					fragment.isQuoted = true;
					addFragment(fragment);

					fragment = null;
				}
				paragraph.clear();
			}

			// Check if the line is a quoted line.
			boolean isQuoted = isQuote(line);

			/*
			 * If fragment is empty or if the line does not matches the current fragment,
			 * create new fragment.
			 */
			if (fragment == null || !isFragmentLine(fragment, line, isQuoted)) {
				if (fragment != null){
					addFragment(fragment);
				}

				fragment = new FragmentDTO();
				fragment.isQuoted = isQuoted;
				fragment.lines = new LinkedList<>();
			}

			// Add line to fragment and paragraph
			fragment.lines.add(line);
			if (!line.isEmpty()) {
				paragraph.add(line);
			}
		}

		if (fragment != null){
			addFragment(fragment);
		}

		return createEmail(fragments);
	}

	/**
	 * Gets max number of lines allowed for each paragraph when checking quote headers.
	 * @return
	 */
	public int getMaxParagraphLines() {
		return this.maxParagraphLines;
	}

	/**
	 * Gets max number of characters allowed for each line when checking quote headers.
	 *
	 * @return
	 */
	public int getMaxNumCharsEachLine() {
		return maxNumCharsEachLine;
	}

	/**
	 * Creates {@link Email} object from List of fragments.
	 * @param fragmentDTOs
	 * @return
	 */
	protected Email createEmail(List<FragmentDTO> fragmentDTOs) {
		List <Fragment> fs = new LinkedList<>();
		Collections.reverse(fragmentDTOs);
		for (FragmentDTO f : fragmentDTOs) {
			Collections.reverse(f.lines);
			String content = new StringBuilder(StringUtils.join(f.lines,"\n")).toString();
			Fragment fr = new Fragment(content, f.isHidden, f.isSignature, f.isQuoted);
			fs.add(fr);
		}
		return new Email(fs);
	}

	/**
	 * Check if the line is a signature.
	 * @param line
	 * @return
	 */
	private boolean isSignature(String line) {
		boolean find = SIG_PATTERN.matcher(line).find();
		return find;
	}

	/**
	 * Checks if the line is quoted line.
	 * @param line
	 * @return
	 */
	private boolean isQuote(String line) {
		return QUOTE_PATTERN.matcher(line).find();
	}

	/**
	 * Checks if lines in the fragment are empty.
	 * @param fragment
	 * @return
	 */
	private boolean isEmpty(FragmentDTO fragment) {
		return StringUtils.join(fragment.lines,"").isEmpty();
	}

	/**
	 * If the line matches the current fragment, return true.
	 * Note that a common reply header also counts as part of the quoted Fragment,
	 * even though it doesn't start with `>`.
	 *
	 * @param fragment
	 * @param line
	 * @param isQuoted
	 * @return
	 */
	private boolean isFragmentLine(FragmentDTO fragment, String line, boolean isQuoted) {
		return fragment.isQuoted == isQuoted || (fragment.isQuoted && (isQuoteHeader(Arrays.asList(line)) || line.isEmpty()));
	}

	/**
	 * Add fragment to fragments list.
	 * @param fragment
	 */
	private void addFragment(FragmentDTO fragment) {
		if (fragment.isQuoted || fragment.isSignature || isEmpty(fragment)){
			fragment.isHidden = true;
		}

		fragments.add(fragment);
	}

	/**
	 * Checks if the given multiple-lines paragraph has one of the quote headers.
	 * Returns false if it doesn't contain any of the quote headers,
	 * if paragraph lines are greater than maxParagraphLines, or line has more than maxNumberCharsEachLine characters.
	 *
	 * @param paragraph
	 * @return
	 */
	private boolean isQuoteHeader(List<String> paragraph) {
		if (paragraph.size() > maxParagraphLines){
			return false;
		}
		for (String line : paragraph) {
			if (line.length() > maxNumCharsEachLine){
				return false;
			}
		}
		Collections.reverse(paragraph);
		String content = new StringBuilder(StringUtils.join(paragraph,"\n")).toString();
		for(Pattern p : compiledQuoteHeaderPatterns) {
			if (p.matcher(content).find()) {
				return true;
			}
		}

		return false;

	}
}
