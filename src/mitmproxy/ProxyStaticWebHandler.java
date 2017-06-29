package mitmproxy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ProxyStaticWebHandler {
	private static String commonHeaders = 	"Server: MITMProxy Server\r\n"
											+ "Connection: Closed\r\n";	
	private String root = "";
	private DirectoryCache fileCache;
	public ProxyStaticWebHandler(String root) {
		this.root = root;
		fileCache = new DirectoryCache(this.root);
	}
		
	private static String getHeaders(int status, int contentLength, String contentType) {
		String headers = commonHeaders;
		if (status == 200) {
			headers = "HTTP/1.1 200 OK\r\n" + headers;
			headers = headers + "Content-Type: "+contentType+"\r\n";
			headers = headers + "Conetne-Length: "+contentLength+"\r\n\r\n";
		} else {
			headers = "HTTP/1.1 404 Not Found\r\n" + headers;
		}
		return headers;
	}
	
	public void handle(Socket socket, ProxyRequestEditor request) throws IOException {
		String processedPath = request.getProcessedPath();
		// Home
		if (processedPath == null || processedPath.compareTo("/") == 0) {
			processedPath = "/index.html";
		}
		// strip first "/" since file caching is based on system paths.
		processedPath = processedPath.replaceFirst("\\/", "");
		String contentType = "text/html";
		if (processedPath.matches(".*\\.html") || processedPath.matches(".*\\.html")) {
			contentType = "text/html";
		} else if (processedPath.matches(".*\\.css")) {
			contentType = "text/css";
		} else if (processedPath.matches(".*\\.js")) {
			contentType = "application/javascript";
		}
		byte[] responseBodyBytes = fileCache.getFileBytes(processedPath);
		if (responseBodyBytes == null) {
			String headers = getHeaders(404, 0, contentType);
			byte[] responseHeadersBytes = headers.getBytes(StandardCharsets.US_ASCII);
			OutputStream clientResponseStream = socket.getOutputStream(); 
			clientResponseStream.write(responseHeadersBytes);
		} else {
			String headers = getHeaders(200, responseBodyBytes.length, contentType);
			byte[] responseHeadersBytes = headers.getBytes(StandardCharsets.US_ASCII);
			byte[] both = new byte[responseHeadersBytes.length + responseBodyBytes.length]; 
			System.arraycopy(responseHeadersBytes, 0, both,                           0, responseHeadersBytes.length);
			System.arraycopy(responseBodyBytes,    0, both, responseHeadersBytes.length, responseBodyBytes.length);
			OutputStream clientResponseStream = socket.getOutputStream(); 
			clientResponseStream.write(both);
		}
	}
}
