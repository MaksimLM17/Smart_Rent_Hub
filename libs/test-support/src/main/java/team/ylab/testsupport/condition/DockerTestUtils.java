package team.ylab.testsupport.condition;

import org.testcontainers.DockerClientFactory;

/** Utility for detecting whether Docker daemon is available in the current environment. */
public final class DockerTestUtils {

  private DockerTestUtils() {}

  /**
   * Checks if Docker is available.
   *
   * @return true if Docker is available and operational, false otherwise
   */
  public static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }
}
