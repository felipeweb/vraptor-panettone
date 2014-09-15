package br.com.caelum.vraptor.panettone.parser;

import java.util.HashMap;

import java.util.Map;

import br.com.caelum.vraptor.panettone.parser.rule.Rules;
import static br.com.caelum.vraptor.panettone.parser.Tokens.RULECHUNK_END;
import static br.com.caelum.vraptor.panettone.parser.Tokens.RULECHUNK_START;
import static br.com.caelum.vraptor.panettone.parser.Regexes.RULECHUNK_START_REGEX;
import static br.com.caelum.vraptor.panettone.parser.Tokens.SCRIPTLET_END;
import static br.com.caelum.vraptor.panettone.parser.Tokens.SCRIPTLET_START;

public class SourceCode {

	private String source;
	private Map<Integer, TextChunk> extractedChunks;
	private int counter = 0;
	private ChunkLocalizer localizer;

	public SourceCode(String source, ChunkLocalizer l) {
		this.source = source;
		extractedChunks = new HashMap<Integer, TextChunk>();
		localizer = l;
	}
	public SourceCode(String source) {
		this(source, new DefaultChunkLocalizer());
	}
	
	public String getSource() {
		return source;
	}

	public void transform(TextChunk chunk, Rules aRule) {
		addChunk(chunk);
		source = source.replace(chunk.getText(), 
				RULECHUNK_START + " " + aRule.name() + " " + counter + " " + RULECHUNK_END);
	}
	
	public TextChunk getTextChunk(int number) {
		return extractedChunks.get(number);
	}

	
	public void transformHtmlAndScriptlet() {
		StringBuilder newSourceCode = new StringBuilder();
		
		String[] lines = source.split(RULECHUNK_START_REGEX);
		
		for(String line : lines) {
			if(line.trim().isEmpty()) continue;
			
			// line.trim().endsWith(RULECHUNK_END)
			if(localizer.lineEndsWithChunkEnd(line)) {
				newSourceCode.append(RULECHUNK_START + " " + line.trim());
			} 
			// line.trim().contains(RULECHUNK_END)
			else if(localizer.lineContainsEndingChunk(line)) {
				String firstPart = line.substring(0, line.indexOf(RULECHUNK_END)).trim();
				newSourceCode.append(RULECHUNK_START + " " + firstPart + " " + RULECHUNK_END);

				String secondPart = line.substring(line.indexOf(RULECHUNK_END)+4);
				htmlOrScriptlet(newSourceCode, secondPart);

			}
			else {
				htmlOrScriptlet(newSourceCode, line);
			}
		}

		source = newSourceCode.toString();
	}

	private void htmlOrScriptlet(StringBuilder newSourceCode, String chunk) {
		
		if(chunk.trim().startsWith(SCRIPTLET_START)) {
			
			String justScriptlet = chunk.trim().substring(2);
			int endOfTheScriptlet = justScriptlet.indexOf(SCRIPTLET_END); // be careful, an "%>" would break it
			justScriptlet = justScriptlet.substring(0, endOfTheScriptlet);

			addChunk(new TextChunk(justScriptlet));
			newSourceCode.append(RULECHUNK_START + " " + Rules.scriptletRuleName() + " " + counter + " " + RULECHUNK_END);
			
			if(chunk.length() > chunk.indexOf(SCRIPTLET_END) +2) {
				String theRestOfTheChunk = chunk.substring(chunk.indexOf(SCRIPTLET_END)+2);
				htmlOrScriptlet(newSourceCode, theRestOfTheChunk);
			}
		} else {

			String justHTML = chunk;
			int startOfScriptlet = justHTML.indexOf(SCRIPTLET_START);
			justHTML = justHTML.substring(0, startOfScriptlet == -1 ? justHTML.length() : startOfScriptlet);
			
			addChunk(new TextChunk(justHTML));
			newSourceCode.append(RULECHUNK_START + " " + Rules.htmlRuleName() + " " + counter + " " + RULECHUNK_END);
			
			if(startOfScriptlet > -1) {
				String theRestOfTheChunk = chunk.substring(chunk.indexOf(SCRIPTLET_START));
				htmlOrScriptlet(newSourceCode, theRestOfTheChunk);
			}
			
		}
	}
	
	private void addChunk(TextChunk chunk) {
		counter++;
		extractedChunks.put(counter, chunk);
	}
}
