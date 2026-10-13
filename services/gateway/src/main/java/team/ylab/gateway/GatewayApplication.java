package team.ylab.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** API Gateway and BFF application entry point (SDD v1.1 Section 4.1). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
