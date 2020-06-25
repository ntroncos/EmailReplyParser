package com.edlio.emailreplyparser;

import com.edlio.emailreplyparser.EmailParser.EmailParserBuilder;

public class EmailReplyParser {

	public static Email read(String emailText) {
		if (emailText == null){
			emailText = "";
		}

		EmailParser parser = new EmailParserBuilder().build();
		return parser.parse(emailText);
	}

	public static String parseReply(String emailText) {
		return read(emailText).getVisibleText();
	}

}
