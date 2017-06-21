package mitmproxy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.SocketHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;

// TODO: handle html base tag
// TODO: handle different content-types

public class MITMProxy {
	public static boolean isListening = true;
	private static long  indexTime = 0;
	private static byte[] index;	
	
	public static byte[] reloadIndex() {
		Path path = Paths.get("web/index.html");
		try {
			FileTime time = Files.getLastModifiedTime(path);
			long fileTime = time.toMillis();
			if (fileTime > indexTime) {
				indexTime = fileTime;
				// Reloadfile
				index = Files.readAllBytes(path);
			}
		} catch (Exception e) {
			;
		}
		return index;
	}
	
	public static void main(String[] args) {
		// PreLoad Rules
		ProxyRule.getAllRules();
		// Listen On Port
		new ProxyServer(80);
	}
}

class ProxyRule {
	private static HashMap<String, Long> fileTimes = new HashMap<String, Long>();
	private static HashMap<String, ArrayList<ProxyRule>> fileRules = new HashMap<String, ArrayList<ProxyRule>>();
	public String domainRegex;
	public String pathRegex;
	public String contentTypeRegex;
	public String action;
	public String locationRegex;
	public String replacementString;
	
	public static ArrayList<ProxyRule> getAllRules() {
		// REFRESH RULES
		Stream<Path> paths;
		try {
			paths = Files.walk(Paths.get("etc/"));
			paths.filter(Files::isRegularFile).forEach( (path) -> {
				try {
					boolean shouldReload = false;
					String pathString = path.toAbsolutePath().toString();
					long modTime = Files.getLastModifiedTime(path).toMillis();
					long lastTime = 0;
					if (!fileTimes.containsKey(pathString)) {
						fileTimes.put(pathString, new Long(modTime));
						fileRules.put(pathString, new ArrayList<ProxyRule>());
						shouldReload = true;
					} else {
						lastTime = fileTimes.get(pathString);
						if (modTime < lastTime) {
							shouldReload = true;
						}
					}
					if (shouldReload) {
						System.out.println("RELOADING RULES:");
						ArrayList<ProxyRule> rules = new ArrayList<ProxyRule>();
						byte[] bytes = Files.readAllBytes(path);
						String rulesString = new String(bytes, StandardCharsets.UTF_8);
						// Strip Comments
						rulesString = rulesString.replaceAll("\\/\\/.*?[\\n\\r]+", "");
						// Condense Tabs
						rulesString = rulesString.replaceAll("\\t+", "\t");
						String[] lines = rulesString.split("\r?\n");
						for (String line : lines) {
							String[] tokens = line.split("\t");
							if (tokens.length == 6) {
								ProxyRule rule = new ProxyRule();
								rule.domainRegex = tokens[0];
								rule.pathRegex = tokens[1];
								rule.contentTypeRegex = tokens[2];
								rule.action = tokens[3];
								rule.locationRegex = tokens[4];
								rule.replacementString = tokens[5];
								if (rule.replacementString.startsWith("inject-file:")) {
									try {
										rule.replacementString = rule.replacementString.replaceAll("inject-file:", "");
										rule.replacementString = "inject/"+rule.replacementString;
										Path injectFile = Paths.get(rule.replacementString);
										byte[] injectBytes = Files.readAllBytes(injectFile);
										rule.replacementString = new String(injectBytes, StandardCharsets.UTF_8);
									} catch (Exception e) {
										rule.replacementString = "MITMProxy-NO-INJECT-FILE-FOUND";
									}
									
								}
								if (rules != null) {
									rules.add(rule);
									System.out.println("ADDED PROXY RULE<"+pathString+">:\t\t"+rule.toString());
								}
								fileRules.put(pathString, rules);
							}
						}
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			});
			paths.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		// GET ALL RULES
		ArrayList<ProxyRule> allRules = new ArrayList<ProxyRule>();
		if (fileRules != null) {
			fileRules.forEach((path, rules) -> {
				if (rules != null) {
					rules.forEach((rule) -> {
						if (rule != null) {
							allRules.add(rule);
						}
					});
				}
			});
		}
		return allRules;
	}
	
	public String toString() {
		return domainRegex +"\t"+ pathRegex +"\t"+ contentTypeRegex +"\t"+ action +"\t"+ locationRegex +"\t"+ replacementString;
	}
}

class HttpCustom extends HttpEntityEnclosingRequestBase {
	String method;
	public HttpCustom() {
		super();
		method = "GET";
	}
	
	public HttpCustom(final URI uri) {
		super();
		setURI(uri);
		method = "GET";
	}
	
	public HttpCustom(final String uri, String method) {
		super();
		setURI(URI.create(uri));
		this.method = method.trim().toUpperCase();
	}
	
	@Override
	public String getMethod() {
		return method;
	}
}

class ProxyServer extends Thread {
	static Pattern htmlURIPattern = Pattern.compile("(?:(?:href)|(?:src))=\"(.*?(?<!\\\\))\"");
	static Pattern cssURIPattern = Pattern.compile("url\\(['\"]?(.*?)['\"]?\\)");
	static Pattern isNotRelativePattern = Pattern.compile("(?:(?:\\S*:(?:\\/\\/)?)|(?:^#)).*");
	static Pattern fragmentPattern = Pattern.compile("(.*)(#.*)");
	
	int port;
	public ProxyServer() {
		this(80);
	}
	public ProxyServer(int port) {
		this.port = port;
		this.start();
	}
	public void run() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(port);
			System.out.println("Started server listening on port " + port);
			while (MITMProxy.isListening) {
				try {
					Socket socket = null;
					socket = server.accept();
					System.out.println("Accepted connection on port " + port);
					new ProxySocketHandler(socket);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}

class ProxyRequestEditor {
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
	public String getProcessedBody() {
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
	public void process(byte[] bytes, int count) {
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

class ProxyResponseEditor {
	enum TYPE {HTML, CSS, JS};
	byte[] processedResponseBytes;
	private ArrayList<String> relativePathAdditions = new ArrayList<String>();
	
	public void addToRelativePaths(String param) {
		relativePathAdditions.add(param);
	}
	
	public byte[] getProcessedResponseBytes() {
		return processedResponseBytes;
	}
	
	public void process(String headers, byte[] bodyBytes) {
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


class ProxySocketHandler extends Thread {
	public static String urlParam = "imgproxyhost";
	public static int times = 0;
	public static int bufferSize = 1024*4*1000;
	public Socket inSocket;
	public Socket outSocket;
	public InputStream inRequest;
	public OutputStream inResponse;
	public ProxySocketHandler(Socket socket) {
		this.inSocket = socket;
		try {
			this.inRequest = inSocket.getInputStream();
			this.inResponse = inSocket.getOutputStream();
		this.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		try {
			// TERMINOLOGY
			// >>>>>>>>>>>>>>>>>>>>>>>>>>Request>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			// [Client]  ---> in ---> [Proxy] ---> out --> [Server]
			// >>>>>>>>>>>>>>>>>>>>>>>>>>Response>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			
			// READ REQUEST
			byte[] inRequestBytes = new byte[bufferSize];
			int inRequestSize = inRequest.read(inRequestBytes);
			if (inRequestSize <= 0) return;
			// PARSE START - modify request header and body here
			ProxyRequestEditor requestEditor = new ProxyRequestEditor();
			requestEditor.addStripParam(urlParam);
			requestEditor.process(inRequestBytes, inRequestSize);
			String path = requestEditor.getProcessedPath();
			if (requestEditor.hasParams(ProxySocketHandler.urlParam)) {
				forward(requestEditor);
			} else if (path.compareTo("/index.html") == 0) {
				byte[] responseBodyBytes = MITMProxy.reloadIndex();
				String responseHeaders = "HTTP/1.1 200 OK\r\n"
						+ "Server: MITMProxy Server\r\n"
						+ "Content-Type: text/html\r\n"
						+ "Conetne-Length: "+responseBodyBytes.length+"\r\n"
						+ "Connection: Closed\r\n\r\n";
				byte[] responseHeadersBytes = responseHeaders.getBytes(StandardCharsets.US_ASCII);
				byte[] both = new byte[responseHeadersBytes.length + responseBodyBytes.length]; 
				System.arraycopy(responseHeadersBytes, 0, both,                           0, responseHeadersBytes.length);
				System.arraycopy(responseBodyBytes,    0, both, responseHeadersBytes.length, responseBodyBytes.length);
				inResponse.write(both);
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				inSocket.close();
			} catch (IOException e) {
				;
			}
		}
	}
	
	public void forward(ProxyRequestEditor request) throws Exception {
			String[] headers = request.getProcessedHeaders();
			String path = request.getProcessedPath();
			String method = request.getProcessedMethod();
			String proxyHost = request.getParamValue(urlParam);

			// FORWARD REQUEST
			HttpClient client = HttpClientBuilder.create().build();
			HttpCustom http = new HttpCustom(proxyHost+path, method);
			String debugRequestHeaders = "\n"+method+" "+proxyHost+path+" "+http.getProtocolVersion()+"\r\n";
			for (String h : headers) {
				String[] keyValue = h.split(": ");
				if (keyValue.length >= 2) {
					String key = keyValue[0];
					String value = keyValue[1].trim();
					http.addHeader(key, value);
					debugRequestHeaders += key + ": " + value + "\r\n";
				}
			}
			HttpResponse response;
			try {
				response = client.execute(http);
			} catch (ClientProtocolException cpe) {
				throw new ClientProtocolException(debugRequestHeaders+cpe.getMessage(), cpe);
			}

			// READ RESPONSE
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
		    byte[] outResponseBodyBytes = baos.toByteArray();
			
			ProxyResponseEditor responseEditor = new ProxyResponseEditor();
			responseEditor.addToRelativePaths(urlParam+"="+proxyHost);
			responseEditor.process(outResponseHeaders, outResponseBodyBytes);
			byte[] filteredResponse = responseEditor.getProcessedResponseBytes();
			inResponse.write(filteredResponse);
	}
	
}



