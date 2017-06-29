package mitmproxy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class ProxyRule {
	private static HashMap<String, ArrayList<ProxyRule>> fileRules = new HashMap<String, ArrayList<ProxyRule>>();
	public String domainRegex;
	public String pathRegex;
	public String contentTypeRegex;
	public String action;
	public String locationRegex;
	public String replacementString;
	
	public static ArrayList<ProxyRule> getAllRules() {
		System.out.println("RELOADING RULES:");
		for (Entry<String, ByteBuffer> entry : MITMProxy.etcCache) {
			String path = entry.getKey();
			byte[] bytes = entry.getValue().array();
			ArrayList<ProxyRule> rules = new ArrayList<ProxyRule>();
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
						System.out.println("ADDED PROXY RULE<"+path+">:\t\t"+rule.toString());
					}
					fileRules.put(path, rules);
				}
			}
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
