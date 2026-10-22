package team.ylab.inventory.api.error;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team.ylab.inventory.domain.exception.EntityNotFoundException;
import team.ylab.inventory.domain.exception.InventoryDomainException;
import team.ylab.inventory.domain.exception.VersionConflictException;

/** Controller advice translating domain and validation exceptions into RFC 9457 Problem Details. */
@RestControllerAdvice
public class InventoryExceptionHandler {

  private static final URI NOT_FOUND_TYPE = URI.create("urn:problem-type:not-found");
  private static final URI VERSION_CONFLICT_TYPE = URI.create("urn:problem-type:version-conflict");
  private static final URI BAD_REQUEST_TYPE = URI.create("urn:problem-type:bad-request");
  private static final URI FORBIDDEN_TYPE = URI.create("urn:problem-type:forbidden");
  private static final URI INTERNAL_ERROR_TYPE =
      URI.create("urn:problem-type:internal-server-error");

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setType(NOT_FOUND_TYPE);
    problem.setTitle("Resource Not Found");
    problem.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  @ExceptionHandler({VersionConflictException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ProblemDetail> handleVersionConflict(Exception ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.getMessage());
    problem.setType(VERSION_CONFLICT_TYPE);
    problem.setTitle("VERSION_CONFLICT");
    problem.setProperty("timestamp", Instant.now());
    if (ex instanceof VersionConflictException vce) {
      if (vce.getExpectedVersion() != null) {
        problem.setProperty("expectedVersion", vce.getExpectedVersion());
      }
      if (vce.getActualVersion() != null) {
        problem.setProperty("actualVersion", vce.getActualVersion());
      }
    }
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problem.setType(BAD_REQUEST_TYPE);
    problem.setTitle("Validation Error");
    problem.setProperty("timestamp", Instant.now());

    Map<String, String> errors = new HashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    problem.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problem.setType(FORBIDDEN_TYPE);
    problem.setTitle("Access Denied");
    problem.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(InventoryDomainException.class)
  public ResponseEntity<ProblemDetail> handleDomainException(InventoryDomainException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(BAD_REQUEST_TYPE);
    problem.setTitle("Domain Rule Violation");
    problem.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex) {
    org.slf4j.LoggerFactory.getLogger(InventoryExceptionHandler.class)
        .error("Unhandled exception: ", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
    problem.setType(INTERNAL_ERROR_TYPE);
    problem.setTitle("Internal Server Error");
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("exceptionClass", ex.getClass().getName());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
