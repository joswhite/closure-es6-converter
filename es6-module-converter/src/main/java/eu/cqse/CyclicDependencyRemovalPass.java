package eu.cqse;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class CyclicDependencyRemovalPass {

	private final File googDir;

	public CyclicDependencyRemovalPass(File closurePath) throws IOException {
		googDir = new File(closurePath, "closure/goog");

		if (!googDir.isDirectory()) {
			throw new IOException("Input dir not found");
		}
	}

	void process() throws IOException {
		merge("date/date.js", "date/date.js", "date/datelike.js");
		merge("events/eventhandler.js", "events/eventhandler.js", "events/events.js", "events/eventwrapper.js", "events/listenable.js", "events/eventtarget.js", "events/listener.js", "events/listenermap.js");
		merge("promise/promise.js", "promise/thenable.js", "promise/promise.js", "promise/resolver.js");
		merge("ui/container.js", "ui/container.js", "ui/containerrenderer.js");
		merge("ui/control.js", "ui/controlrenderer.js", "ui/registry.js", "ui/control.js");
		merge("ui/menu.js", "ui/menurenderer.js", "ui/menuitem.js", "ui/menu.js");
	}

	private void merge(String finalName, String... fileNames) throws IOException {
		List<File> files = Arrays.stream(fileNames).map(f -> new File(googDir, f)).collect(Collectors.toList());
		String content = files.stream().map(f -> {
			if (!f.exists()) {
				System.out.println("Cyclic dependency is not required and therefore skipped in the merge process: " + f.getName());
				return "";
			}
			return FileUtils.getFileContentSafe(f);
		}).collect(Collectors.joining("\n\n"));

		Matcher matcher = ReaderPass.PROVIDE_OR_MODULE_PATTERN.matcher(content);
		while (matcher.find()) {
			String ns = matcher.group(1);
			content = content.replaceAll("goog\\.(?:require|forwardDeclare)\\('" + ns + "'\\);", "");
		}

		for (File file : files) {
			file.delete();
		}
		FileUtils.writeFileContent(new File(googDir, finalName), content);
	}
}
