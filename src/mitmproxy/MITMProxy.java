package mitmproxy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Pattern;

public class MITMProxy {
	public static boolean isListening = true;
	
	public static ProxyStaticWebHandler staticWeb;
	public static DirectoryCache etcCache;
	
	public static void main(String[] args) {
		// Etc Cache
		etcCache = new DirectoryCache("etc/");
		// Start Static Server
		staticWeb = new ProxyStaticWebHandler("web/");
		// PreLoad Rules
		ProxyRule.getAllRules();
		// Listen On Port
		new ProxyServer(80);
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
					new ProxyHttpSocketHandler(socket);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}




