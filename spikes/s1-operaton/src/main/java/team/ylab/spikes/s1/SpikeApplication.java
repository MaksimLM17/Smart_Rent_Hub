package team.ylab.spikes.s1;

import org.operaton.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableProcessApplication
public class SpikeApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpikeApplication.class, args);
  }
}
