package eu.cqse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

class ConvertingPass {

	private static final HashSet<String> RESERVED_KEYWORDS = new HashSet<>(Arrays.asList("document", "Array",
			"localStorage", "Map", "Set", "string", "number", "Object", "Notification", "Error", "Date", "Logger", "LogRecord"));

	private static final Map<String, String> DEFAULT_REPLACEMENTS = new HashMap<>();
	private static final Set<String> IMPORT_WHOLE_MODULE_EXCEPTIONS = ImmutableSet.of("GraphemeBreak", "CssSpecificity", "CssSanitizer", "ComponentUtil");

	static {
		DEFAULT_REPLACEMENTS.put("string", "strings");
		DEFAULT_REPLACEMENTS.put("number", "numbers");
	}

	private static final Set<String> RESERVERD_KEYWORDS = new HashSet<>(Arrays.asList("Error", "console", "File",
			"document", "window", "Array", "Set", "Map", "Notification", "ServiceWorker", "string", "array"));

	void process(ReaderPass readerPass) throws IOException {
		for (File file : readerPass.providesByFile.keySet()) {
			if (file.getName().equals(ReaderPass.GOOG_JS)) {
				continue;
			}
			String content = Files.asCharSource(file, Charsets.UTF_8).read();
			List<GoogProvideOrModule> provides = new ArrayList<>(readerPass.providesByFile.get(file));
			boolean isModule = provides.stream().anyMatch(provideOrModule -> provideOrModule.isModule);
			List<String> shortExports = new ArrayList<>();
			if (isModule) {
				content = convertGoogleModuleFile(provides.get(0), content);
				shortExports.addAll(provides.get(0).exports.stream().map(e -> e.exportName).collect(Collectors.toList()));
			} else {
				content = convertGoogProvideFile(provides, file, content, shortExports);
			}
			content = replaceRequires(file, content, new ArrayList<>(readerPass.requiresByFile.get(file)),
					readerPass.filesByNamespace, shortExports);
			content = replaceSupressedExtraRequires(content);
			content = removeUsageOfProvidedNamespaces(content, provides);
			content = content.replaceAll("(\\W)COMPILED(\\W)", "$1true$2");
			Files.asCharSink(file, Charsets.UTF_8).write(content);
		}
	}

	private String removeUsageOfProvidedNamespaces(String content, List<GoogProvideOrModule> provides) {
		for (GoogProvideOrModule provide : provides) {
			content = content.replaceAll("(?m)^" + Pattern.quote(provide.namespace) + "\\.(\\w+)\\s*=", "let $1 =");
			content = removePartialQualifiedCall(content, provide.namespace);
		}
		return content;
	}

	private static String removePartialQualifiedCall(String content, String partialQualifiedCall) {
		Matcher matcher = Pattern.compile("([^\\w])" + Pattern.quote(partialQualifiedCall) + "\\.(\\w+)([^\\w])").matcher(content);
		String[] invalidChars = {",", "'", "\""};
		while (matcher.find()) {
			String prefix = matcher.group(1);
			String privateFunction = matcher.group(2);
			String suffix = matcher.group(3);
			if (StringUtils.equalsOneOf(prefix, invalidChars) || StringUtils.equalsOneOf(suffix, invalidChars)) {
				continue;
			}
			content = content.replace(matcher.group(), prefix + privateFunction + suffix);
		}
		return content;
	}

	/**
	 * '@suppress{extraRequires}' will no longer work in ES6 and cause a compiler error
	 */
	private String replaceSupressedExtraRequires(String content) {
		return content.replaceAll("@suppress\\s*\\{extraRequire}", "");
	}

	/**
	 * let FOO = goog.define(...) is invalid an needs to use 'const' instead.
	 */
	@VisibleForTesting
	static String fixGoogDefineKeywords(String content, Collection<String> exportedNamespaces) {
		Matcher matcher = Pattern.compile("let\\s+([\\w\\d]+)\\s*=[\\s\\n]*goog\\s*\\.\\s*define\\s*\\(")
				.matcher(content);
		while (matcher.find()) {
			content = content.replace(matcher.group(), "const " + matcher.group(1) + " = goog.define(");
			exportedNamespaces.add(matcher.group(1));
		}
		matcher = Pattern.compile("(?m)^[\\s\\n]*goog\\s*\\.\\s*define\\s*\\('([^)]+\\.([^).]+))',").matcher(content);
		while (matcher.find()) {
			content = content.replace(matcher.group(), "const " + matcher.group(2) + " = " + matcher.group());
			content = replaceFullyQualifiedCallWith(content, matcher.group(1), matcher.group(2));
			exportedNamespaces.add(matcher.group(2));
		}
		return content;
	}

	private String replaceRequires(File file, String content, List<GoogRequireOrForwardDeclare> requires,
								   Map<String, File> filesByNamespace, List<String> shortExports) {
		Set<String> usedShortReferencesInFile = new HashSet<>(shortExports);
		requires.sort((require1, require2) -> require2.requiredNamespace.length() - require1.requiredNamespace.length());
		for (GoogRequireOrForwardDeclare require : requires) {
			File requiredFile = filesByNamespace.get(require.requiredNamespace);
			if (requiredFile == null || !requiredFile.isFile() || !requiredFile.canRead()) {
				throw new RuntimeException("Required namespace " + require.requiredNamespace + " could not be found "
						+ (requiredFile == null ? "" : requiredFile.getName()));
			}
			String relativePath = getRequirePathFor(file.getAbsolutePath(), requiredFile.getAbsolutePath());

			if (require.importedFunction != null) {
				content = replaceOrInsert(content, require.fullText, "import {" + require.importedFunction + "} from '" + relativePath + "';");
				usedShortReferencesInFile.add(require.importedFunction);
				continue;
			}

			if (require.shortReference == null) {
				require.shortReference = findSafeReferenceForGoogRequire(content, require.requiredNamespace,
						Sets.union(usedShortReferencesInFile, RESERVED_KEYWORDS));
				content = replaceFullyQualifiedCallWith(content, require.requiredNamespace, require.shortReference);
			}
			usedShortReferencesInFile.add(require.shortReference);

			String[] parts = require.requiredNamespace.split("\\.");
			String originalImportedElement = parts[parts.length - 1];
			String importedElement = originalImportedElement;
			if (RESERVERD_KEYWORDS.contains(importedElement)) {
				importedElement = parts[parts.length - 2] + "_" + importedElement;
			}

			if (!isClassName(originalImportedElement) || IMPORT_WHOLE_MODULE_EXCEPTIONS.contains(originalImportedElement)) {
				content = replaceOrInsert(content, require.fullText, "import * as " + require.shortReference + " from '" + relativePath + "';"
				);
			} else if (importedElement.equals(require.shortReference)) {
				content = replaceOrInsert(content, require.fullText, "import {" + require.shortReference + "} from '" + relativePath + "';"
				);
			} else {
				content = replaceOrInsert(content,
						require.fullText, "import {" + importedElement + " as " + require.shortReference + "} from '" + relativePath + "';");
			}
		}
		return content;
	}

	private String replaceOrInsert(String content, String fullText, String replacement) {
		if (fullText == null) {
			int importIndex = content.indexOf("\nimport");
			int googIndex = content.indexOf("\ngoog");
			int firstImport;
			if (importIndex == -1) {
				firstImport = googIndex + 1;
			} else {
				firstImport = importIndex + 1;
			}
			return content.substring(0, firstImport) + replacement + "\n" + content.substring(firstImport);
		}
		return content.replace(fullText, replacement);
	}

	private static boolean isClassName(String importedElement) {
		return Character.isUpperCase(importedElement.charAt(0));
	}

	private String getRequirePathFor(String callingFile, String targetFile) {
		Path caller = Paths.get(callingFile).getParent();
		Path targetPath = Paths.get(targetFile);
		String relativePath = caller.relativize(targetPath).toString().replaceAll("\\\\", "/");
		if (!relativePath.startsWith(".")) {
			return "./" + relativePath;
		}
		return relativePath;
	}

	private static String findSafeReferenceForGoogRequire(String documentText, String requiredNamespace,
														  Set<String> forbiddenShortReferences) {
		String[] namespaceParts = requiredNamespace.split("\\.");
		String newShortName = namespaceParts[namespaceParts.length - 1];

		boolean needsToBeUppercase = isClassName(newShortName);

		int namespacePartIndex = namespaceParts.length - 1;

		while (forbiddenShortReferences.contains(newShortName) || isShadedByVariableDefinition(documentText, newShortName)) {

			if (DEFAULT_REPLACEMENTS.containsKey(newShortName)) {
				newShortName = DEFAULT_REPLACEMENTS.get(newShortName);
				continue;
			}

			namespacePartIndex--;
			if (namespacePartIndex >= 0) {
				newShortName = namespaceParts[namespacePartIndex] + newShortName;
			} else {
				newShortName = "_" + newShortName;
			}
		}

		while (Pattern.compile("[^.\\w]" + newShortName + "\\.").matcher(documentText).find()) {
			namespacePartIndex--;
			if (namespacePartIndex >= 0) {
				newShortName = namespaceParts[namespacePartIndex] + "_" + newShortName;
			} else if (!newShortName.endsWith("s")) {
				newShortName += "s";
			} else {
				newShortName = "_" + newShortName;
			}
		}

		if (needsToBeUppercase) {
			newShortName = newShortName.substring(0, 1).toUpperCase() + newShortName.substring(1);
		}
		return newShortName;
	}

	private static boolean isShadedByVariableDefinition(String documentText, String newShortName) {
		return documentText.contains("var " + newShortName) || documentText.contains("let " + newShortName) || documentText.contains("const " + newShortName);
	}

	private String convertGoogProvideFile(List<GoogProvideOrModule> provides, File file,
										  final String originalContent, List<String> shortExports) {
		String content = originalContent;
		Set<String> exports = new TreeSet<>();

		content = fixGoogDefineKeywords(content, exports);

		provides.sort((provide1, provide2) -> provide2.namespace.length() - provide1.namespace.length());
		for (GoogProvideOrModule provide : provides) {
			String namespace = provide.namespace;
			String[] parts = namespace.split("\\.");
			String classOrFunction = parts[parts.length - 1];
			if (isProvideForPublicClassOrEnum(classOrFunction, namespace, content)) {
				// foo.bar.MyClass -> MyClass
				String shortClassName = classOrFunction;
				if (RESERVERD_KEYWORDS.contains(shortClassName)) {
					shortClassName = parts[parts.length - 2] + "_" + shortClassName;
				}
				content = content.replaceAll("(?m)^" + Pattern.quote(provide.namespace) + " =", "let " + shortClassName + " =");
				content = replaceFullyQualifiedCallWith(content, provide.namespace, shortClassName);
				exports.add(shortClassName);
			} else if (isTypeDef(classOrFunction, namespace, content)) {
				// Typedefs:
				// foo.bar.MyClass; -> let MyClass;
				String shortClassName = classOrFunction;
				if (RESERVERD_KEYWORDS.contains(shortClassName)) {
					shortClassName = parts[parts.length - 2] + "_" + shortClassName;
				}
				content = content.replaceAll("(?m)^" + Pattern.quote(namespace) + ";", "let " + shortClassName + ";");
				exports.add(shortClassName);
			} else {
				// Prepare export of non-private methods
				Pattern methodOrConstantPattern = Pattern
						.compile("(?m)^" + Pattern.quote(namespace) + "\\.([\\w\\d]+[\\w\\d]+)(\\s*=[^=])");
				Matcher matcher = methodOrConstantPattern.matcher(content);
				while (matcher.find()) {
					String methodOrConstantName = matcher.group(1);
					if (!methodOrConstantName.endsWith("_")) {
						exports.add(methodOrConstantName);
					}
					content = content.replaceAll("(?m)^" + Pattern.quote(matcher.group()), "let " + methodOrConstantName + matcher.group(2));
					content = replaceFullyQualifiedCallWith(content, namespace + "." + methodOrConstantName,
							methodOrConstantName);
				}
				// Prepare export of typedefs
				Pattern typedefPattern = Pattern
						.compile("(?m)^" + Pattern.quote(namespace) + "\\.([\\w\\d]+[\\w\\d]+);");
				matcher = typedefPattern.matcher(content);
				while (matcher.find()) {
					String typeName = matcher.group(1);
					if (!typeName.endsWith("_")) {
						exports.add(typeName);
					}
					content = content.replace(matcher.group(), "let " + typeName + ";");
					content = replaceFullyQualifiedCallWith(content, namespace + typeName,
							typeName);
				}
			}

			content = content.replace(provide.fullMatch, "");
		}

		exports.removeIf(StringUtils::isEmpty);
		if (exports.isEmpty()) {
			System.out.println("WARN: Don't know what to export, skipping: " + file.getPath());
			return originalContent;
		} else {
			shortExports.addAll(exports);
			return content + "\n\n" + "export {" + (exports.size() <= 1 ? exports.iterator().next()
					: StringUtils.joinDifferentLastDelimiter(new ArrayList<>(exports), ", ", ", ")) + "};";
		}
	}

	private static String replaceFullyQualifiedCallWith(String content, String fullyQualifiedCall, String newCall) {
		Matcher matcher = Pattern.compile("([^\\w])" + Pattern.quote(fullyQualifiedCall) + "([^\\w])").matcher(content);
		String[] invalidChars = {"'", "\""};
		while (matcher.find()) {
			String prefix = matcher.group(1);
			String suffix = matcher.group(2);
			if (StringUtils.equalsOneOf(prefix, invalidChars) || StringUtils.equalsOneOf(suffix, invalidChars)) {
				continue;
			}
			content = content.replace(matcher.group(), prefix + newCall + suffix);
		}
		return content;
	}

	private boolean isProvideForPublicClassOrEnum(String classOrFunction, String namespace, String content) {
		return !classOrFunction.endsWith("_")
				&& Pattern.compile("(?m)^\\s*" + Pattern.quote(namespace) + "\\s*=\\s*(class|function\\s+)?").matcher(content).find();
	}

	private boolean isTypeDef(String classOrFunction, String namespace, String content) {
		return !classOrFunction.endsWith("_")
				&& Pattern.compile("(?m)^\\s*" + Pattern.quote(namespace) + ";").matcher(content).find();
	}

	private String convertGoogleModuleFile(GoogProvideOrModule moduleOrProvide, String content) {
		content = content.replace(moduleOrProvide.fullMatch, "");
		content = content.replaceAll("goog\\.module\\.declareLegacyNamespace\\(\\);?\n?", "");

		List<GoogModuleExport> inlineExports = moduleOrProvide.exports.stream().filter(export -> export.isInlineExport).collect(toList());
		List<GoogModuleExport> globalExports = moduleOrProvide.exports.stream().filter(export -> !export.isInlineExport).collect(toList());

		for (GoogModuleExport export : inlineExports) {
			// Replace the closure version
			// 'exports foo ='
			// with
			// 'export foo ='
			content = content.replace(export.fullMatch, "export " + export.exportName + " =");
			content = content.replace("export " + export.exportName + " = " + export.exportName, "export {" + export.exportName + "}");
		}

		List<String> exportedNames = globalExports.stream().map(export -> export.exportName).collect(toList());
		content = fixGoogDefineKeywords(content, exportedNames);

		if (globalExports.isEmpty()) {
			return content;
		}

		// Remove Closure's 'exports {...}' and then append ES6's 'export {...}' to the file content
		content = content.replace(globalExports.get(0).fullMatch, "");
		return content + "\n\nexport {" + StringUtils.concat(exportedNames, ",") + "};";
	}
}
