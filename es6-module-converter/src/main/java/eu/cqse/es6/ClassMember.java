package eu.cqse.es6;

import eu.cqse.JsCodeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.cqse.JsCodeUtils.multilineSafeNamespacePattern;

public class ClassMember {
	private static final Pattern METHOD_DELEGATION_PATTERN = Pattern.compile("(?m)\\s*=\\s*(?:/\\*\\*[^*]+\\*/\\s*\\(\\s*)?[\\w_.\\s]+" + multilineSafeNamespacePattern(".prototype.") + "([\\w_]+)\\)?;");
	private static final Pattern FUNCTION_DELEGATION_PATTERN = Pattern.compile("(?m)\\s*=\\s*(?:/\\*\\*[^*]+\\*/\\s*\\(\\s*)?([\\w_.\\s]+\\.[\\w_]+(\\('[^']+'\\))?)\\)?;");
	private static final Pattern TYPE_UNION_WITH_UNDEFINED = Pattern.compile("@type \\{.*undefined(?!>).*}");
	private static final Pattern PRIMITIVE_NON_NULLABLE_TYPE = Pattern.compile("@type \\{(number|boolean|string|KeyCodes)}");

	public final String fullMatch;
	public final String docComment;
	public final String classNamespace;
	public final String memberName;
	public final String declaration;
	private final boolean isStatic;

	public ClassMember(String fullMatch, String docComment, String classNamespace, String memberName, String declaration, boolean isStatic) {
		this.fullMatch = fullMatch;
		this.docComment = docComment;
		this.classNamespace = classNamespace;
		this.memberName = memberName;
		this.declaration = declaration;
		this.isStatic = isStatic;
	}

	public boolean isMethod() {
		return this.declaration.matches("(?ms)\\s?=\\s*function.*")
				|| (this.declaration.matches("(?ms)\\s?=\\s*goog\\.nullFunction;.*") && !docComment.contains("{Function}"))
				|| docComment.contains("@param")
				|| docComment.contains("@return")
				|| isExplicitAbstractMethod(declaration);
	}

	public boolean isField() {
		return !isMethod();
	}

	public String getDocComment() {
		String docComment = this.docComment.replaceAll("\\* @(private|protected|public) \\{", "* @$1\r\n  * @type {");
		if (isExplicitAbstractMethod(declaration)) {
			return docComment
					.replaceAll("\\s*\\* @type \\{function\\(\\) : void}\r?\n", "")
					.replaceAll("(\\s*)\\*/\\s*$", "$1* @abstract$0");
		}
		if (hasNoInitializer(declaration)) {
			if (PRIMITIVE_NON_NULLABLE_TYPE.matcher(docComment).find()) {
				return docComment.replaceAll("@type \\{(.*)}", "@type {$1|null}");
			}
		}
		return docComment;
	}

	public String getDeclaration(GoogInheritsInfo googInheritsInfo) {
		String declaration = this.declaration;
		String methodHeader = memberName + "(" + getInferredParameterList() + ") {";
		if (isStatic) {
			methodHeader = "static " + methodHeader;
		}
		if (hasNoInitializer(declaration) || isExplicitAbstractMethod(declaration)) {
			if (hasNoInitializer(declaration) && docComment.contains("@override")) {
				// Special case in BaseNode where getChildAt is redeclared
				return methodHeader +"{\r\n  return super." + memberName + "(" + getInferredParameterList() + ");\r\n}";
			} else {
				return methodHeader + "}";
			}
		} else if (isFunctionDelegation(declaration)) {
			Matcher matcher = METHOD_DELEGATION_PATTERN.matcher(declaration);
			if (matcher.find()) {
				String delegate = matcher.group(1);
				return methodHeader+"\r\n  return this." + delegate + "(" + getInferredParameterList() + ");\r\n}";
			} else {
				Matcher functionMatcher = FUNCTION_DELEGATION_PATTERN.matcher(declaration);
				functionMatcher.find();
				String delegate = functionMatcher.group(1);
				if (delegate.equals("goog.nullFunction")) {
					return methodHeader+"}";
				}
				return methodHeader + "\r\n  return " + delegate + "(" + getInferredParameterList() + ");\r\n}";
			}
		} else {
			Pattern functionAssignmentPattern = Pattern.compile("\\s?=\\s*function");
			if(functionAssignmentPattern.matcher(declaration).lookingAt()) {
				declaration = functionAssignmentPattern.matcher(declaration).replaceFirst(memberName);
			} else {
				throw new IllegalArgumentException("Unexpected declaration: "+declaration);
			}
			if (googInheritsInfo != null) {
				declaration = declaration
						.replaceAll(multilineSafeNamespacePattern(googInheritsInfo.fullClassNamespace + ".base")
								+ "\\(\\s*this,\\s*'constructor',?\\s*", "super(");
				declaration = declaration
						.replaceAll(multilineSafeNamespacePattern(googInheritsInfo.fullClassNamespace + ".base")
								+ "\\(\\s*this,\\s*'(\\w+)',?\\s*", "super.$1(");
				declaration = declaration
						.replaceAll(multilineSafeNamespacePattern(googInheritsInfo.fullClassNamespace + ".superClass_.")
								+ "([\\w_]+)\\s*\\.\\s*call\\s*\\(\\s*this,?\\s*", "super.$1(");
			}
			if (isStatic) {
				declaration = "static " + declaration;
			}
			return declaration;
		}
	}

	private boolean isFunctionDelegation(String declaration) {
		return FUNCTION_DELEGATION_PATTERN.matcher(declaration).matches();
	}

	public boolean isAbstract() {
		return this.docComment.contains("@abstract") || isExplicitAbstractMethod(this.declaration);
	}

	private boolean hasNoInitializer(String declaration) {
		return declaration.equals(";");
	}

	protected boolean isExplicitAbstractMethod(String declaration) {
		return declaration.matches("\\s*=\\s*goog\\.abstractMethod;");
	}

	protected String getAsEs6Method(GoogInheritsInfo googInheritsInfo) {
		String docComment = getDocComment();
		String declaration = getDeclaration(googInheritsInfo);
		return docComment + declaration;
	}

	/**
	 * Infers the parameters for method definitions that are defined only as
	 * `goog.crypt.BlockCipher.prototype.decrypt;` Where only the doc comment
	 * gives clues how the method should look like.
	 */
	private String getInferredParameterList() {
		return JsCodeUtils.getInferredParameterList(this.docComment);
	}

	public String getEs6Representation(GoogInheritsInfo googInheritsInfo) {
		if (isField()) {
			return getAsEs6Field();
		} else {
			return getAsEs6Method(googInheritsInfo);
		}
	}

	public String getAsEs6Field() {
		String docComment = this.getDocComment();
		String declaration = this.declaration;
		if (hasNoInitializer(declaration)) {
			if (TYPE_UNION_WITH_UNDEFINED.matcher(docComment).find()) {
				declaration = " = undefined;";
			} else {
				declaration = " = null;";
			}
		}
		declaration = declaration.replaceFirst("\\s?=\\s*", Matcher.quoteReplacement("this." + memberName) + "$0");
		return docComment + declaration;
	}

	public boolean isStatic() {
		return isStatic;
	}
}
