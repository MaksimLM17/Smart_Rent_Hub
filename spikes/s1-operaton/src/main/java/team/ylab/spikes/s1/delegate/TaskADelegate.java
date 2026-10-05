package team.ylab.spikes.s1.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import team.ylab.spikes.s1.entity.SpikeEntity;
import team.ylab.spikes.s1.repository.SpikeRepository;

@Component("taskADelegate")
public class TaskADelegate implements JavaDelegate {

  private final SpikeRepository repository;

  public TaskADelegate(SpikeRepository repository) {
    this.repository = repository;
  }

  @Override
  public void execute(DelegateExecution execution) {
    String businessKey = execution.getProcessBusinessKey();
    SpikeEntity entity = new SpikeEntity();
    entity.setBusinessKey(businessKey);
    entity.setStatus("TASK_A_COMPLETED");
    repository.save(entity);
  }
}
