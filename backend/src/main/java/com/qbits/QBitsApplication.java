package com.qbits;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the Q-Bits backend: feed ingestion jobs and the read API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class QBitsApplication {

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(QBitsApplication.class, args);
  }
}
