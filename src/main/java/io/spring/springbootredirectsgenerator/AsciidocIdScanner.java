package io.spring.springbootredirectsgenerator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsciidocIdScanner {

	List<Id> findIds(List<String> lines) {
		Pattern idPattern = Pattern.compile("^\\.?\\[\\[(.+)\\]\\]");
		Pattern headerPattern = Pattern.compile("^=+ (.+)");
		ListIterator<String> iLines = lines.listIterator();
		List<Id> results = new ArrayList<>();
		while (iLines.hasNext()) {
			String line = iLines.next();
			Matcher idMatcher = idPattern.matcher(line);
			while (idMatcher.find()) {
				String id = idMatcher.group(1);
				String defaultText = null;
				if (iLines.hasNext()) {
					String nextLine = iLines.next();
					Matcher headerMatcher = headerPattern.matcher(nextLine);
					if (headerMatcher.matches()) {
						defaultText = headerMatcher.group(1);
					}
					iLines.previous();
				}
				results.add(new Id(id, defaultText));
			}
		}
		return results;
	}

	static class DocumentId {

		final Path path;

		final Id id;

		public DocumentId(Path path, Id id) {
			this.path = path;
			this.id = id;
		}

		public Path getPath() {
			return path;
		}

		public Id getId() {
			return id;
		}

		@Override
		public String toString() {
			return "DocumentId{" + "path=" + path + ", id=" + id + '}';
		}
	}

	static class Id {

		private final String id;

		private final String defaultText;

		public Id(String id, String defaultText) {
			this.id = id;
			this.defaultText = defaultText;
		}

		public String getId() {
			return id;
		}

		public String getDefaultText() {
			return defaultText;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Id id1 = (Id) o;
			return id.equals(id1.id) && Objects.equals(defaultText, id1.defaultText);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, defaultText);
		}

		@Override
		public String toString() {
			return "Id{" + "id='" + id + '\'' + ", defaultText='" + defaultText + '\'' + '}';
		}

	}

}
