package com.qbits.resources.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a URL found in a feed entry into a typed, canonical open-source link, or drops it (spec 005
 * R2). Only GitHub, GitLab, Hugging Face and arXiv project pages are accepted. Pure: no I/O.
 */
public final class ResourceLinkNormaliser {

  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
  private static final Pattern ARXIV_ID =
      Pattern.compile("^(\\d{4}\\.\\d{4,5}|[a-z-]+(?:\\.[A-Z]{2})?/\\d{7})(?:v\\d+)?(?:\\.pdf)?$");

  /**
   * First path segments that are GitHub site pages, not owners (R2.3). Organisation names are never
   * listed here: {@code github.com/github/gitignore} is a real repository.
   */
  private static final Set<String> GITHUB_RESERVED =
      Set.of(
          "features",
          "topics",
          "sponsors",
          "login",
          "join",
          "marketplace",
          "orgs",
          "settings",
          "about",
          "pricing",
          "collections",
          "trending",
          "explore",
          "apps",
          "enterprise",
          "security",
          "site",
          "events",
          "search",
          "notifications",
          "new",
          "codespaces",
          "customer-stories",
          "readme",
          "resources",
          "solutions",
          "team",
          "contact");

  private static final Set<String> GITLAB_RESERVED =
      Set.of("explore", "users", "help", "dashboard", "search", "projects", "groups", "-");

  /**
   * First path segments that are Hugging Face site pages, not organisations (R2.3). The {@code
   * huggingface} organisation publishes real models, so it is not listed.
   */
  private static final Set<String> HF_RESERVED =
      Set.of(
          "blog",
          "docs",
          "pricing",
          "models",
          "learn",
          "join",
          "login",
          "settings",
          "tasks",
          "posts",
          "collections",
          "chat",
          "enterprise",
          "api",
          "search",
          "new",
          "organizations",
          "terms-of-service",
          "privacy",
          "hub",
          "inference-endpoints",
          "changelog",
          "jobs",
          "brand",
          "notifications");

  public Optional<Candidate> normalise(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    URI uri;
    try {
      uri = new URI(raw.trim());
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
      return Optional.empty();
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    List<String> segs = segments(uri.getPath());
    return switch (host) {
      case "github.com", "www.github.com" -> github(segs);
      case "gitlab.com", "www.gitlab.com" -> gitlab(segs);
      case "huggingface.co", "www.huggingface.co" -> huggingFace(segs);
      case "arxiv.org", "www.arxiv.org", "export.arxiv.org" -> arxiv(segs);
      default -> Optional.empty();
    };
  }

  private static Optional<Candidate> github(List<String> s) {
    if (s.size() < 2 || GITHUB_RESERVED.contains(s.get(0).toLowerCase(Locale.ROOT))) {
      return Optional.empty();
    }
    String owner = s.get(0);
    String repo = stripGit(s.get(1));
    if (!valid(owner) || !valid(repo)) {
      return Optional.empty();
    }
    String name = owner + "/" + repo;
    return Optional.of(
        new Candidate(
            ResourceType.CODE, ResourceHost.GITHUB, "https://github.com/" + name, name, false));
  }

  private static Optional<Candidate> gitlab(List<String> s) {
    List<String> path = new ArrayList<>();
    for (String seg : s) {
      if (seg.equals("-")) {
        break; // GitLab puts /-/tree, /-/blob … after the project path
      }
      path.add(seg);
    }
    if (path.size() < 2 || GITLAB_RESERVED.contains(path.get(0).toLowerCase(Locale.ROOT))) {
      return Optional.empty();
    }
    path.set(path.size() - 1, stripGit(path.getLast()));
    if (!path.stream().allMatch(ResourceLinkNormaliser::valid)) {
      return Optional.empty();
    }
    String name = String.join("/", path);
    return Optional.of(
        new Candidate(
            ResourceType.CODE, ResourceHost.GITLAB, "https://gitlab.com/" + name, name, false));
  }

  private static Optional<Candidate> huggingFace(List<String> s) {
    if (s.isEmpty()) {
      return Optional.empty();
    }
    String first = s.get(0).toLowerCase(Locale.ROOT);
    switch (first) {
      case "datasets":
        return hfRepo(s.subList(1, s.size()), ResourceType.DATASET, "datasets/", false);
      case "spaces":
        return hfRepo(s.subList(1, s.size()), ResourceType.CODE, "spaces/", true);
      case "papers":
        return s.size() >= 2 ? arxivId(s.get(1)) : Optional.empty();
      default:
        if (HF_RESERVED.contains(first)) {
          return Optional.empty();
        }
        return hfRepo(s, ResourceType.MODEL, "", false);
    }
  }

  private static Optional<Candidate> hfRepo(
      List<String> s, ResourceType type, String prefix, boolean demo) {
    if (s.size() < 2 || !valid(s.get(0)) || !valid(s.get(1))) {
      return Optional.empty();
    }
    String name = s.get(0) + "/" + s.get(1);
    return Optional.of(
        new Candidate(
            type, ResourceHost.HUGGINGFACE, "https://huggingface.co/" + prefix + name, name, demo));
  }

  private static Optional<Candidate> arxiv(List<String> s) {
    if (s.size() < 2) {
      return Optional.empty();
    }
    String kind = s.get(0).toLowerCase(Locale.ROOT);
    if (!kind.equals("abs") && !kind.equals("pdf") && !kind.equals("html")) {
      return Optional.empty();
    }
    String id = String.join("/", s.subList(1, s.size()));
    return arxivId(id);
  }

  private static Optional<Candidate> arxivId(String raw) {
    Matcher m = ARXIV_ID.matcher(raw);
    if (!m.matches()) {
      return Optional.empty();
    }
    String id = m.group(1);
    return Optional.of(
        new Candidate(
            ResourceType.PAPER, ResourceHost.ARXIV, "https://arxiv.org/abs/" + id, id, false));
  }

  private static List<String> segments(String path) {
    if (path == null) {
      return List.of();
    }
    return Arrays.stream(path.split("/")).filter(seg -> !seg.isEmpty()).toList();
  }

  private static String stripGit(String repo) {
    return repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
  }

  private static boolean valid(String part) {
    return NAME.matcher(part).matches() && !part.equals(".") && !part.equals("..");
  }
}
