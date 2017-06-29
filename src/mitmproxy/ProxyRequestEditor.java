package mitmproxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyRequestEditor {
	static String paramValueRegex = "=[\\S]*(?=[\\s&;,]|$)";
	private ArrayList<Pattern> parseParamPatterns = new ArrayList<Pattern>();
	private ArrayList<Pattern> stripParamPatterns = new ArrayList<Pattern>();
	private HashMap<String, String> strippedParams = new HashMap<String, String>();
	private HashMap<String, String> parsedParams = new HashMap<String, String>();
	private String header;
	private String body;
	private String path;
	private String method;
	private String[] headers;
	public InputStream inputStream;
	
	public ProxyRequestEditor(InputStream inputStream) throws IOException {
		this.inputStream = inputStream;
	}
	
	public boolean hasParams(String... params) {
		for (String p : params) {
			if (strippedParams.containsKey(p) || parsedParams.containsKey(p)) {
				;
			} else {
				return false;
			}
		}
		return true;
	}
	public String getParamValue(String p) {
		if (strippedParams.containsKey(p)) {
			return strippedParams.get(p);
		} else if (parsedParams.containsKey(p)) {
			return parsedParams.get(p);
		}
		return "";
	}
	public String getProcessedHeader() {
		return header;
	}
	public String[] getProcessedHeaders() {
		return headers;
	}
	public String getProcessedBody( ) {
		return body;
	}
	public String getProcessedPath() {
		return path;
	}
	public String getProcessedMethod() {
		return method;
	}
	public void addParseParam(String regex) {
		parseParamPatterns.add(Pattern.compile(regex+paramValueRegex));
	}
	public void addStripParam(String regex) {
		stripParamPatterns.add(Pattern.compile(regex+paramValueRegex));
	}
	public HashMap<String, String> getParsedParams() {
		return parsedParams;
	}
	public HashMap<String, String> getStrippedParams() {
		return strippedParams;
	}
	public void process() throws IOException {
		
		byte[] bytes = new byte[ 20*((int)Math.pow(10, 6))];
		int count = inputStream.read(bytes);
		if (count <= 0) return;
		
		String request = new String(bytes, 0, count, StandardCharsets.UTF_8);
		String header = request.split("\r?\n\r?\n")[0];
		String body = "";
		try {
			body = request.split("\r?\n\r?\n")[1];
		} catch (Throwable t) {
			;
		}
		processHeaders(header);
		processBody(body);
	}
	private void processHeaders(String header) {
		String firstLine = header.split("\r?\n")[0];
		String path = firstLine.split(" ")[1];
		// Strip
		for (Pattern p : stripParamPatterns) {
			try {
				Matcher mh = p.matcher(header);
				Matcher mp = p.matcher(path);
				mh.find();
				mp.find();
				String paramString = mp.group();
				String key = paramString.split("=")[0];
				String value = paramString.split("=")[1];
				header = mh.replaceAll("");
				header = header.replace("? ", " ");
				strippedParams.put(key, value);
			} catch (Throwable t){
				;
			}
		}
		// Parse
		for (Pattern p : parseParamPatterns) {
			try {
				Matcher mh = p.matcher(header);
				Matcher mp = p.matcher(path);
				mh.find();
				mp.find();
				String paramString = mp.group();
				String key = paramString.split("=")[0];
				String value = paramString.split("=")[1];
				parsedParams.put(key, value);
			} catch (Throwable t){
				;
			}
		}
		this.header = header;
		firstLine = header.split("\r?\n")[0];
		this.path = firstLine.split(" ")[1];
		this.method = firstLine.split(" ")[0];
		ArrayList<String> headers = new ArrayList<String>();
		String[] headerStrings = header.split("\r?\n");
		for (int i = 1; i < headerStrings.length; i++) {
			String h = headerStrings[i].trim();
			if (h.length() > 0) {
				headers.add(h);
			}
		}
		this.headers = new String[headers.size()];
		int i = 0;
		for (String h : headers) {
			this.headers[i] = h;
			i++;
		}
	}
	private void processBody(String body) {
		
	}
}
