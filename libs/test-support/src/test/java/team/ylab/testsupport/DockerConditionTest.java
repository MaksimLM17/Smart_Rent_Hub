package team.ylab.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import team.ylab.testsupport.condition.DockerCondition;
import team.ylab.testsupport.condition.DockerTestUtils;

class DockerConditionTest {

  @Test
  @DisplayName("DockerCondition correctly reports Docker availability")
  void testDockerConditionEvaluation() {
    DockerCondition condition = new DockerCondition();
    ConditionEvaluationResult result = condition.evaluateExecutionCondition(null);

    boolean isAvailable = DockerTestUtils.isDockerAvailable();
    if (isAvailable) {
      assertThat(result.isDisabled()).isFalse();
      assertThat(result.getReason().orElse("")).contains("Docker is available");
    } else {
      assertThat(result.isDisabled()).isTrue();
      assertThat(result.getReason().orElse("")).contains("Docker is not available");
    }
  }
}
