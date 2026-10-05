package team.ylab.spikes.s1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "spike_entity", schema = "bpm")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpikeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "business_key", unique = true)
  private String businessKey;

  @Column(name = "status")
  private String status;
}
