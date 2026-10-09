package com.fleetflow.notification.infrastructure.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id private UUID id;
    protected ProcessedEvent() { }
    public ProcessedEvent(UUID id) { this.id = id; }
}
