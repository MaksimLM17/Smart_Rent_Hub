package team.ylab.tools.stubs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the External Stubs application. Emulates external services: SMS Gateway, Bank ID
 * / Identity Provider, Payment Gateway.
 */
@SpringBootApplication
public class ExternalStubsApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExternalStubsApplication.class, args);
  }
}
