package team.ylab.spikes.s1.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import team.ylab.spikes.s1.entity.SpikeEntity;
import team.ylab.spikes.s1.repository.SpikeRepository;

@Slf4j
@Component("taskADelegate")
@RequiredArgsConstructor
public class TaskADelegate implements JavaDelegate {

  private final SpikeRepository repository;

  @Override
  public void execute(DelegateExecution execution) {
    String businessKey = execution.getProcessBusinessKey();
    log.info("Executing Task A for businessKey={}", businessKey);
    SpikeEntity entity = new SpikeEntity();
    entity.setBusinessKey(businessKey);
    entity.setStatus("TASK_A_COMPLETED");
    repository.save(entity);
  }
}
