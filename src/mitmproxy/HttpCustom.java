package mitmproxy;

import java.net.URI;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

public class HttpCustom extends HttpEntityEnclosingRequestBase {
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
