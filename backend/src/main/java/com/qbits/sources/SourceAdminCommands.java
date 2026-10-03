package com.qbits.sources;

import com.qbits.ingestion.persistence.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operator commands, run with the {@code admin} profile (spec 001 R8.2):
 *
 * <pre>java -jar qbits.jar --spring.profiles.active=admin --purge-source=&lt;id&gt;</pre>
 *
 * <p>Deletes every stored item of the source, then exits.
 */
@Component
@Profile("admin")
public class SourceAdminCommands implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SourceAdminCommands.class);
  static final String PURGE = "purge-source";

  private final ItemRepository items;
  private final ApplicationContext context;

  public SourceAdminCommands(ItemRepository items, ApplicationContext context) {
    this.items = items;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption(PURGE)) {
      return;
    }
    for (String sourceId : args.getOptionValues(PURGE)) {
      purge(sourceId);
    }
    System.exit(SpringApplication.exit(context, () -> 0));
  }

  /** Deletes all items of a source. Returns how many were removed. */
  public int purge(String sourceId) {
    int deleted = items.deleteBySource(sourceId);
    log.warn("source purged source={} itemsDeleted={}", sourceId, deleted);
    return deleted;
  }
}
