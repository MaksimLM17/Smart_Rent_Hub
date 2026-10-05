package team.ylab.tools.stubs.sms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendRequest {

  @NotBlank(message = "Phone number must not be blank")
  private String phoneNumber;

  private String message;

  private String code;
}
