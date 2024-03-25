package io.spring.springbootredirectsgenerator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;

import io.spring.springbootredirectsgenerator.AsciidocIdScanner.Id;

class Redirects {

	private static final Logger logger = LoggerFactory.getLogger(Redirects.class);

	private final Path projectPath;

	private final Map<String, PageDetails> pageDetails = new HashMap<>();

	Redirects(Path checkoutDir) {
		this.projectPath = checkoutDir.resolve("spring-boot-project");
	}

	void add(String page, String path) throws IOException {
		PageDetails pageDetails = this.pageDetails.computeIfAbsent(page, (key) -> new PageDetails());
		Path modulePath = projectPath.resolve(path);
		Path antoraPath = modulePath.resolve("src/docs/antora");
		Assert.state(Files.isDirectory(antoraPath), () -> antoraPath + " does not exist");
		scanZipFiles(pageDetails, modulePath.resolve("build/generated/docs/antora-content"));
		scanModules(pageDetails, "", antoraPath.resolve("modules"));
		addAnchorRewrites(pageDetails, antoraPath.resolve("anchor-rewrite.properties"));
	}

	private void scanZipFiles(PageDetails pageDetails, Path zipFilesPath) throws IOException {
		logger.info("Scanning zip files in directory {}", zipFilesPath);
		Files.list(zipFilesPath).filter(Files::isRegularFile)
				.filter((candidate) -> candidate.toString().endsWith("aggregate-content.zip"))
				.forEach(ThrowingConsumer.of((zipFile) -> scanZipFile(pageDetails, zipFile)));
	}

	private void scanZipFile(PageDetails pageDetails, Path zipFile) throws IOException {
		scanModules(pageDetails, zipFile + "!", FileSystems.newFileSystem(zipFile).getPath("/modules"));
	}

	private void scanModules(PageDetails pageDetails, String prefix, Path modulesPath) throws IOException {
		Files.list(modulesPath).filter(Files::isDirectory)
				.forEach(ThrowingConsumer.of((modulePath) -> scanModule(pageDetails, prefix, modulePath)));
	}

	private void scanModule(PageDetails pageDetails, String prefix, Path modulePath) throws IOException {
		String moduleName = modulePath.getFileName().toString();
		scanAdocFiles(pageDetails, moduleName, prefix, modulePath.resolve("pages"), Type.PAGE);
		scanAdocFiles(pageDetails, moduleName, prefix, modulePath.resolve("partials"), Type.PARTIAL);
	}

	private void scanAdocFiles(PageDetails pageDetails, String moduleName, String prefix, Path path, Type type)
			throws IOException {
		if (!Files.isDirectory(path)) {
			return;
		}
		List<Path> adocFiles = Files.walk(path).filter(Files::isRegularFile)
				.filter((candidate) -> candidate.toString().endsWith(".adoc")).sorted().toList();
		for (Path adocFile : adocFiles) {
			logger.info("Scanning adoc file {}", prefix + adocFile);
			List<String> lines = Files.readAllLines(adocFile);
			XrefRoot xrefRoot = new XrefRoot(moduleName, path.relativize(adocFile).toString(), type);
			for (Id id : new AsciidocIdScanner().findIds(lines)) {
				pageDetails.fragmentToXrefRoots().put(id.getId(), xrefRoot);
			}
		}
	}

	private void addAnchorRewrites(PageDetails pageDetails, Path path) throws IOException {
		if (Files.isRegularFile(path)) {
			logger.info("Adding anchor rewrites {}", path);
			Properties properties = new Properties();
			try (InputStream in = new FileInputStream(path.toFile())) {
				properties.load(in);
			}
			properties.forEach((name, value) -> pageDetails.anchorRewrites().put((String) name, (String) value));
		}
	}

	public String generate() {
		Map<String, Map<String, String>> fragmentToXrefByPage = new HashMap<>();
		this.pageDetails.forEach((page, details) -> {
			Map<String, String> fragmentToXref = new HashMap<>();
			fragmentToXrefByPage.put(page, fragmentToXref);
			Map<String, String> fragmentToPagePath = new HashMap<>();
			details.fragmentToXrefRoots().forEach((fragment, xrefRoot) -> {
				if (xrefRoot.type() == Type.PAGE) {
					fragmentToPagePath.put(fragment, xrefRoot.path());
					fragmentToXref.put(fragment, xrefRoot.toXref(fragment));
				}
			});
			details.fragmentToXrefRoots().forEach((fragment, xrefRoot) -> {
				if (xrefRoot.type() == Type.PARTIAL) {
					String pagePath = findPagePath(fragmentToPagePath, fragment);
					fragmentToXref.put(fragment, xrefRoot.toXref(pagePath, fragment));
				}
			});
			details.anchorRewrites().forEach((previous, replacement) -> {
				String lookup = replacement;
				String xref = (replacement.startsWith("@")) ? replacement.substring(1) : fragmentToXref.get(lookup);
				if (xref == null) {
					Set<String> seen = new HashSet<>();
					while (xref == null) {
						Assert.state(seen.add(lookup), "Already seen " + lookup);
						lookup = details.anchorRewrites().get(lookup);
						Assert.state(lookup != null, "Can't find " + replacement);
						xref = fragmentToXref.get(lookup);
					}
					fragmentToXref.put(previous, xref);
				}
			});
		});
		Map<String, List<String>> xrefs = new TreeMap<String, List<String>>();
		fragmentToXrefByPage.forEach((page, fragmentToXref) -> {
			fragmentToXref.forEach((fragment, xref) -> {
				List<String> fragements = xrefs.computeIfAbsent(xref, (key) -> new ArrayList<>());
				fragements.add(page + "#" + fragment);
			});
		});
		StringBuilder result = new StringBuilder();
		result.append(":page-layout: redirect\n\n");
		xrefs.forEach((xref, fragments) -> fragments
				.forEach((fragment) -> result.append("* xref:" + xref + "[" + fragment + "]\n")));
		return result.toString();
	}

	private String findPagePath(Map<String, String> fragmentToPagePath, String fragment) {
		String key = fragment;
		while (true) {
			String pagePath = fragmentToPagePath.get(key);
			if (pagePath != null) {
				return pagePath;
			}
			int lastDot = key.lastIndexOf(".");
			Assert.state(lastDot != -1, "Unable to find page path for " + fragment);
			key = key.substring(0, lastDot);
		}
	}

	record PageDetails(Map<String, XrefRoot> fragmentToXrefRoots, Map<String, String> anchorRewrites) {

		PageDetails() {
			this(new HashMap<>(), new HashMap<>());
		}

	}

	record XrefRoot(String moduleName, String path, Type type) {

		public String toXref(String fragment) {
			Assert.state(type == Type.PAGE, "Must be a page type");
			return toXref(path(), fragment);
		}

		public String toXref(String pagePath, String fragment) {
			return moduleName() + ":" + pagePath + ((StringUtils.hasText(fragment)) ? "#" + fragment : "");
		}

	}

	enum Type {
		PAGE, PARTIAL
	}

}
