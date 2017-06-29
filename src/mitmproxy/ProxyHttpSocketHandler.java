package mitmproxy;

import java.io.IOException;
import java.net.Socket;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class ProxyHttpSocketHandler extends Thread {
	public static String urlParam = "imgproxyhost";
	public static int times = 0;
	public Socket clientSocket;
	public ProxyHttpSocketHandler(Socket socket) {
		this.clientSocket = socket;
		this.start();
	}
	
	public void run() {
		try {
			ProxyRequestEditor requestEditor = new ProxyRequestEditor(clientSocket.getInputStream());
			requestEditor.addStripParam(urlParam);
			requestEditor.process();
			if (requestEditor.hasParams(ProxyHttpSocketHandler.urlParam)) {
				forward(requestEditor);
			} else {
				MITMProxy.staticWeb.handle(clientSocket, requestEditor);
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				clientSocket.close();
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
			ProxyResponseEditor responseEditor = new ProxyResponseEditor(response);
			responseEditor.addToRelativePaths(urlParam+"="+proxyHost);
			responseEditor.process();
			byte[] filteredResponse = responseEditor.getProcessedResponseBytes();
			clientSocket.getOutputStream().write(filteredResponse);
	}
	
}
