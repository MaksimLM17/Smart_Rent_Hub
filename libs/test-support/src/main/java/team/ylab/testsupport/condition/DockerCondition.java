package team.ylab.testsupport.condition;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** JUnit 5 execution condition that disables tests when Docker daemon is not available. */
public class DockerCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (DockerTestUtils.isDockerAvailable()) {
      return ConditionEvaluationResult.enabled("Docker is available on this host");
    }
    return ConditionEvaluationResult.disabled(
        "Docker is not available in the current environment; skipping Testcontainers execution");
  }
}
