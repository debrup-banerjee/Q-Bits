package com.qbits.stories.digest;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operator command to cut and submit an edition immediately (spec 006 R6.3):
 *
 * <pre>java -jar qbits.jar --spring.profiles.active=admin --run-edition-now</pre>
 *
 * The app keeps running so the edition job can poll and publish the batch.
 */
@Component
@Profile("admin")
public class EditionAdminCommands implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(EditionAdminCommands.class);

  private final EditionJob job;

  public EditionAdminCommands(EditionJob job) {
    this.job = job;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (args.containsOption("run-edition-now")) {
      UUID id = runNow();
      log.warn("edition cut on request edition={}", id);
    }
  }

  public UUID runNow() {
    return job.cutNow();
  }
}
