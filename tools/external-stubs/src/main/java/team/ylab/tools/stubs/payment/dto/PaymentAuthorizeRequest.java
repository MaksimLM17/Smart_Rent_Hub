package team.ylab.tools.stubs.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuthorizeRequest {

  private String paymentId;

  @NotBlank(message = "bookingId must not be blank")
  private String bookingId;

  @NotNull(message = "amount must not be null")
  @Positive(message = "amount must be positive")
  private BigDecimal amount;

  @Builder.Default private String currency = "RUB";

  private String cardNumber;
}
