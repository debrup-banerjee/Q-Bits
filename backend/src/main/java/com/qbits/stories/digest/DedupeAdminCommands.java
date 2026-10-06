package com.qbits.stories.digest;

import com.qbits.stories.domain.GroupCandidate;
import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operator command to hold back repeats that are already live (spec 008 R4), run with the {@code
 * admin} profile and no web server, from the app's working directory:
 *
 * <pre>
 * java -jar backend.jar --spring.profiles.active=admin \
 *   --spring.main.web-application-type=none --dedupe-now
 * </pre>
 *
 * Prints each group to the console (not the log) and exits. Counts as one summary-service call.
 */
@Component
@Profile("admin")
public class DedupeAdminCommands implements ApplicationRunner {

  private final EditionDeduplicator deduplicator;
  private final EditionQueries editions;
  private final Clock clock;
  private final ApplicationContext context;

  public DedupeAdminCommands(
      EditionDeduplicator deduplicator,
      EditionQueries editions,
      Clock clock,
      ApplicationContext context) {
    this.deduplicator = deduplicator;
    this.editions = editions;
    this.clock = clock;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (args.containsOption("dedupe-now")) {
      dedupeNow(System.out);
      System.exit(SpringApplication.exit(context, () -> 0));
    }
  }

  /** Runs the clean-up over the current window and prints what it did (R4.2). */
  public EditionDeduplicator.CleanUp dedupeNow(PrintStream out) {
    EditionDeduplicator.CleanUp result =
        deduplicator.cleanUpPublished(editions.windowEnd(), clock.instant());
    out.print(report(result));
    return result;
  }

  static String report(EditionDeduplicator.CleanUp result) {
    Map<UUID, GroupCandidate> byId =
        result.candidates().stream()
            .collect(Collectors.toMap(GroupCandidate::id, Function.identity()));
    Map<UUID, List<UUID>> byKept =
        result.heldBack().entrySet().stream()
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getValue,
                    java.util.TreeMap::new,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    StringBuilder text = new StringBuilder();
    byKept.forEach(
        (kept, held) -> {
          text.append("KEPT     ").append(line(byId.get(kept))).append('\n');
          held.stream()
              .sorted()
              .forEach(h -> text.append("  held   ").append(line(byId.get(h))).append('\n'));
        });
    text.append("Checked ")
        .append(result.candidates().size())
        .append(" live stories: ")
        .append(byKept.size())
        .append(" events with repeats, ")
        .append(result.heldBack().size())
        .append(" held back, ")
        .append(result.dropped())
        .append(" invalid groups ignored.\n");
    return text.toString();
  }

  private static String line(GroupCandidate c) {
    return c.id() + "  " + c.sourceName() + "  " + c.headline();
  }
}
