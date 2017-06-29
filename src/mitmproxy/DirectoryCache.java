package mitmproxy;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DirectoryCache implements Iterable<Entry<String, ByteBuffer>> {
	public String root = "";
	private Pattern filenameCachePattern;
	private HashMap<String, Long> fileTimes = new HashMap<String, Long>();
	private HashMap<String, ByteBuffer> fileBytes = new HashMap<String, ByteBuffer>();
	public DirectoryCache(String root) {
		this.root = root;
		this.filenameCachePattern = Pattern.compile(".*");
		this.reload();
	}
	public DirectoryCache(String root, String filenameCacheRegex) {
		this.root = root;
		this.filenameCachePattern = Pattern.compile(filenameCacheRegex);
		this.reload();
	}
	public byte[] getFileBytes(String pathEndRegex) {
		pathEndRegex = ".*" + root + pathEndRegex + "";
		// reload already happens through iterator.
		for (Entry<String, ByteBuffer> e : this) {
			String path = e.getKey();
			// modify windows paths to web uri format.
			String replaceRegex = "\\\\|\\/";
			path = path.replaceAll(replaceRegex, "/");
			if (path.matches(pathEndRegex)) {
				return e.getValue().array();
			}
		}
		return null;
	}
	
	public void reload() {
		Stream<Path> paths;
		try {
			paths = Files.walk(Paths.get(root));
			paths.filter(Files::isRegularFile)
				.filter( (path) -> {
					if (filenameCachePattern.matcher(path.toString()).matches()) {
						return true;
					} else {
						return false;
					}
				})
				.forEach( (path) -> {
				try {
					boolean shouldReload = false;
					String pathString = path.toString();
					long modTime = Files.getLastModifiedTime(path).toMillis();
					long lastTime = 0;
					if (!fileTimes.containsKey(pathString)) {
						fileTimes.put(pathString, new Long(modTime));
						shouldReload = true;
					} else {
						lastTime = fileTimes.get(pathString);
						if (modTime > lastTime) {
							shouldReload = true;
						}
					}
					if (shouldReload) {
						byte[] bytes = Files.readAllBytes(path);
						ByteBuffer bb = ByteBuffer.wrap(bytes);
						fileBytes.put(pathString, bb);
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Iterator<Entry<String, ByteBuffer>> iterator() {
		reload();
		return fileBytes.entrySet().iterator();
	}
}
