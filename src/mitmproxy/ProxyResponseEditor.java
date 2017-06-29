package mitmproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

public class ProxyResponseEditor {
	enum TYPE {HTML, CSS, JS};
	byte[] processedResponseBytes;
	private ArrayList<String> relativePathAdditions = new ArrayList<String>();
	private HttpResponse response;
	
	public ProxyResponseEditor(HttpResponse response) throws IOException {
		this.response = response;
	}
	
	public void addToRelativePaths(String param) {
		relativePathAdditions.add(param);
	}
	
	public byte[] getProcessedResponseBytes() {
		return processedResponseBytes;
	}
	
	public void process() throws IOException {
			String outResponseHeaders = response.getStatusLine().toString() + "\r\n";
			Header[] responseHeadersArray = response.getAllHeaders();
			for (Header h : responseHeadersArray) {
				String key = h.getName();
				String value = h.getValue();
				String line = key + ": " + value + "\r\n";
				if (key == null) {
					outResponseHeaders = value + "\r\n" + outResponseHeaders;
				} else {
					outResponseHeaders = outResponseHeaders + line;	
				}
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    response.getEntity().writeTo(baos);
		    byte[] bodyBytes = baos.toByteArray();
		    String headers = outResponseHeaders;
		
			String contentType = headers.split("Content-Type:\\s*")[1].split("\r?\n")[0];
			// FORWARD RESPONSE
			// STRIP HEADERS ALWAYS
			headers = headers.replaceAll("Content-Length:.*?\r?\n", "");
			headers = headers.replaceAll("Transfer-Encoding:.*?\r?\n", "");
			if (contentType.contains("text/html")) {
				String body = new String(bodyBytes);
				// PARSE HTML START
				ArrayList<ProxyRule> rules = ProxyRule.getAllRules();
				for (ProxyRule rule : rules) {
					body = body.replaceAll(rule.locationRegex, rule.replacementString);
				}
				// PARSE HTML END
				// HANDLE FLAVICON
				if (!body.contains("rel=\"shortcut icon\"")) {
					body = body.replaceAll("</[hH][eE][aA][dD]\\s*?>", "\t\t<link rel=\"shortcut icon\" href=\"/favicon.ico\">\n\t</head>");
				}
				// PARSE RELATIVE PATHS
				Matcher m = ProxyServer.htmlURIPattern.matcher(body);
				StringBuilder builder = new StringBuilder("");
				int i = 0;
				int savedIndex = 0;
				while (m.find()) {
					// Build complete string each iteration
					int start = m.start(1);
					int end = m.end(1);
					if (i == 0) {
						String section = body.substring(0, start);
						builder.append(section);
						savedIndex = end;
					} else {
						String section = body.substring(savedIndex, start);
						builder.append(section);
						savedIndex = end;
					}
					i++;
					// Replace relative paths
					String uri = m.group(1);
					uri = addParamsToRelativePath(uri, relativePathAdditions);
					builder.append(uri);
				}
				builder.append(body.substring(savedIndex, body.length()));
				body = builder.toString();
				// PARSE END
				// PARSE END
				bodyBytes = body.getBytes(StandardCharsets.UTF_8);
			} if (contentType.contains("text/css")) {
				String body = new String(bodyBytes);
				// PARSE CSS START
				// PARSE CSS END
				// PARSE RELATIVE PATHS
				Matcher m = ProxyServer.cssURIPattern.matcher(body);
				StringBuilder builder = new StringBuilder("");
				int i = 0;
				int savedIndex = 0;
				while (m.find()) {
					// Build complete string each iteration
					int start = m.start(1);
					int end = m.end(1);
					if (i == 0) {
						String section = body.substring(0, start);
						builder.append(section);
						savedIndex = end;
					} else {
						String section = body.substring(savedIndex, start);
						builder.append(section);
						savedIndex = end;
					}
					i++;
					// Replace relative paths
					String uri = m.group(1);
					uri = addParamsToRelativePath(uri, relativePathAdditions);
					builder.append(uri);
				}
				builder.append(body.substring(savedIndex, body.length()));
				body = builder.toString();
				// Replace Source Maps for CSS SASS
				body = body.replaceAll("\\/\\*#\\s*sourceMappingURL\\s*=.*?\\*\\/", "");
				// PARSE END
				// PARSE END
				bodyBytes = body.getBytes(StandardCharsets.UTF_8);
			}
			
			String responseHeaders = headers + String.format("Content-Length: %d\r\n\r\n", bodyBytes.length);
			byte[] headerBytes = responseHeaders.getBytes(StandardCharsets.US_ASCII);
			this.processedResponseBytes = new byte[headerBytes.length + bodyBytes.length]; 
			System.arraycopy(headerBytes, 0, processedResponseBytes, 0, headerBytes.length);
			System.arraycopy(bodyBytes, 0, processedResponseBytes, headerBytes.length, bodyBytes.length);
	}
	
	private String addParamsToRelativePath(String path, ArrayList<String> params) {
		Matcher m = ProxyServer.isNotRelativePattern.matcher(path);
		if (!m.matches()) {
			// is relative path
			String relPath = path;
			String fragment = "";
			// has fragment
			Matcher m2 = ProxyServer.fragmentPattern.matcher(path);
			if (m2.find()) {
				relPath = m2.group(1);
				fragment = m2.group(2);
			}
			for (String param : params) {
				if (relPath.contains("?")) {
					relPath = relPath + "&"+param;
				} else {
					relPath = relPath + "?"+param;
				}
			}
			path = relPath + fragment;
		}
		return path;
	}
}
