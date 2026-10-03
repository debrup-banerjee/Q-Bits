package com.qbits.sources.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses and validates the sources file (spec 001, R1). Collects every problem before failing so
 * the operator can fix the file in one pass.
 */
public final class SourcesFileParser {

  /** Section slugs a source may hint at. */
  public static final Set<String> SECTION_SLUGS =
      Set.of("global-ai-tech", "world-business", "india-ai", "innovations-research");

  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}");
  private static final Duration MIN_INTERVAL = Duration.ofMinutes(15);

  /** Parses the YAML text; checks structure and R1.4 (enabled sources need a terms review). */
  public List<Source> parse(String yamlText) {
    List<String> problems = new ArrayList<>();
    List<Source> sources = parseStructure(yamlText, problems);
    for (Source source : sources) {
      if (source.enabled() && source.termsUrl() == null) {
        problems.add(problem(source.id(), "termsUrl", "is required when enabled is true"));
      }
      if (source.enabled() && source.termsReviewedOn() == null) {
        problems.add(problem(source.id(), "termsReviewedOn", "is required when enabled is true"));
      }
    }
    if (!problems.isEmpty()) {
      throw new SourceConfigException(problems);
    }
    return List.copyOf(sources);
  }

  /** Parses the YAML text and checks structure only (R1.1, R1.2), ignoring R1.4. */
  public List<Source> parseStructureOnly(String yamlText) {
    List<String> problems = new ArrayList<>();
    List<Source> sources = parseStructure(yamlText, problems);
    if (!problems.isEmpty()) {
      throw new SourceConfigException(problems);
    }
    return List.copyOf(sources);
  }

  private List<Source> parseStructure(String yamlText, List<String> problems) {
    Object root;
    try {
      root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
    } catch (RuntimeException e) {
      throw new SourceConfigException(List.of("file is not valid YAML: " + e.getMessage()));
    }
    if (!(root instanceof Map<?, ?> map) || !(map.get("sources") instanceof List<?> entries)) {
      throw new SourceConfigException(List.of("file must have a top-level 'sources' list"));
    }

    List<Source> sources = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();
    for (int i = 0; i < entries.size(); i++) {
      if (!(entries.get(i) instanceof Map<?, ?> entry)) {
        problems.add("entry #" + (i + 1) + ": must be a mapping");
        continue;
      }
      Entry e = new Entry(entry, i, problems);
      String id = e.requiredString("id");
      if (id != null && !ID.matcher(id).matches()) {
        problems.add(problem(id, "id", "must be lower-case letters, digits and dashes"));
      }
      if (id != null && !seenIds.add(id)) {
        problems.add(problem(id, "id", "is a duplicate"));
      }
      e.label = id;

      Source source =
          new Source(
              id,
              e.requiredString("name"),
              e.requiredUrl("homepage"),
              e.requiredUrl("feedUrl"),
              e.requiredEnum("type", SourceType.class),
              e.optionalUrl("termsUrl"),
              e.optionalDate("termsReviewedOn"),
              e.requiredBoolean("enabled"),
              e.requiredBoolean("aiNative"),
              e.requiredEnum("region", Region.class),
              e.optionalSectionHint(),
              e.optionalInterval());
      sources.add(source);
    }
    return sources;
  }

  private static String problem(String id, String field, String message) {
    return "source '" + id + "': field '" + field + "' " + message;
  }

  /** Field accessors for one entry that record problems instead of throwing. */
  private static final class Entry {
    private final Map<?, ?> values;
    private final int index;
    private final List<String> problems;
    private String label;

    Entry(Map<?, ?> values, int index, List<String> problems) {
      this.values = values;
      this.index = index;
      this.problems = problems;
    }

    private String who() {
      return label != null ? label : "entry #" + (index + 1);
    }

    private void add(String field, String message) {
      problems.add(problem(who(), field, message));
    }

    String requiredString(String field) {
      Object v = values.get(field);
      if (v == null || v.toString().isBlank()) {
        add(field, "is required");
        return null;
      }
      return v.toString().trim();
    }

    URI requiredUrl(String field) {
      String v = requiredString(field);
      return v == null ? null : url(field, v);
    }

    URI optionalUrl(String field) {
      Object v = values.get(field);
      return v == null || v.toString().isBlank() ? null : url(field, v.toString().trim());
    }

    private URI url(String field, String value) {
      try {
        URI uri = new URI(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
          add(field, "must be an absolute http(s) URL");
          return null;
        }
        return uri;
      } catch (URISyntaxException e) {
        add(field, "must be an absolute http(s) URL");
        return null;
      }
    }

    boolean requiredBoolean(String field) {
      Object v = values.get(field);
      if (v instanceof Boolean b) {
        return b;
      }
      add(field, v == null ? "is required" : "must be true or false");
      return false;
    }

    <E extends Enum<E>> E requiredEnum(String field, Class<E> type) {
      String v = requiredString(field);
      if (v == null) {
        return null;
      }
      try {
        return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        add(field, "has unknown value '" + v + "'");
        return null;
      }
    }

    LocalDate optionalDate(String field) {
      Object v = values.get(field);
      if (v == null) {
        return null;
      }
      if (v instanceof java.util.Date d) {
        return d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
      }
      try {
        return LocalDate.parse(v.toString().trim());
      } catch (DateTimeParseException e) {
        add(field, "must be a date like 2026-10-03");
        return null;
      }
    }

    String optionalSectionHint() {
      Object v = values.get("sectionHint");
      if (v == null) {
        return null;
      }
      String hint = v.toString().trim();
      if (!SECTION_SLUGS.contains(hint)) {
        add("sectionHint", "must be one of " + SECTION_SLUGS);
        return null;
      }
      return hint;
    }

    Duration optionalInterval() {
      Object v = values.get("intervalMinutes");
      if (v == null) {
        return null;
      }
      if (!(v instanceof Integer minutes)) {
        add("intervalMinutes", "must be a whole number");
        return null;
      }
      Duration interval = Duration.ofMinutes(minutes);
      if (interval.compareTo(MIN_INTERVAL) < 0) {
        add("intervalMinutes", "must be at least 15");
        return null;
      }
      return interval;
    }
  }
}
