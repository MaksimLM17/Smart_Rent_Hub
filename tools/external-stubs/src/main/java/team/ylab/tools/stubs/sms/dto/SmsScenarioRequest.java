package team.ylab.tools.stubs.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsScenarioRequest {

  @Builder.Default private String scenario = "SUCCESS"; // SUCCESS, FAILURE, DELAY

  @Builder.Default private long delayMs = 0;

  @Builder.Default private int failureStatusCode = 500;

  private String errorMessage;
}
