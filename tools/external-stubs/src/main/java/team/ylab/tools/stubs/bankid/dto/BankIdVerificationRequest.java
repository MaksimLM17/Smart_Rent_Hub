package team.ylab.tools.stubs.bankid.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankIdVerificationRequest {

  @NotBlank(message = "clientId must not be blank")
  private String clientId;

  private String passportNumber;

  private String phoneNumber;

  private String fullName;

  private String birthDate;
}
