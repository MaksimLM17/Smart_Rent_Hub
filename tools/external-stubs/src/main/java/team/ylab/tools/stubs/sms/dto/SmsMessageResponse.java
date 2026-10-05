package team.ylab.tools.stubs.sms.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsMessageResponse {

  private String messageId;
  private String phoneNumber;
  private String message;
  private String code;
  private Instant sentAt;
  private String status;
}
