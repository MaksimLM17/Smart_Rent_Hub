package team.ylab.security.sod;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import team.ylab.security.context.SecurityUserContext;

/** Filter that intercepts incoming HTTP requests and audits or enforces SoD preconditions. */
@Slf4j
public class SodFilter extends OncePerRequestFilter {

  public static final String HEADER_RESOURCE_OWNER = "X-SRH-Resource-Owner";
  public static final String HEADER_SECOND_APPROVER = "X-SRH-Second-Approver";
  public static final String HEADER_SOD_ACTION = "X-SRH-SoD-Action";

  private final SodPolicyEnforcer sodPolicyEnforcer;
  private final SecurityUserContext securityUserContext;

  public SodFilter(SodPolicyEnforcer sodPolicyEnforcer, SecurityUserContext securityUserContext) {
    this.sodPolicyEnforcer = sodPolicyEnforcer;
    this.securityUserContext = securityUserContext;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String actionHeader = request.getHeader(HEADER_SOD_ACTION);
    if (actionHeader != null && !actionHeader.isBlank()) {
      try {
        SodAction action = SodAction.valueOf(actionHeader.trim().toUpperCase());
        String currentUserId = securityUserContext.getUserId().orElse(null);
        String resourceOwner = request.getHeader(HEADER_RESOURCE_OWNER);
        String secondApprover = request.getHeader(HEADER_SECOND_APPROVER);

        SodRequest sodRequest =
            SodRequest.builder()
                .userId(currentUserId)
                .action(action)
                .resourceOwnerId(resourceOwner)
                .secondApproverId(secondApprover)
                .reason(request.getParameter("reason"))
                .build();

        SodDecision decision = sodPolicyEnforcer.evaluate(sodRequest);
        if (!decision.isAllowed()) {
          log.warn(
              "SoD filter blocked request: {} for user {}", decision.getReason(), currentUserId);
          response.sendError(
              HttpServletResponse.SC_FORBIDDEN, "SoD Policy Violation: " + decision.getReason());
          return;
        }
      } catch (IllegalArgumentException e) {
        log.warn("Invalid SoD action header: {}", actionHeader);
      }
    }

    filterChain.doFilter(request, response);
  }
}
