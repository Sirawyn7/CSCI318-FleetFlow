package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.tracking.DeliveryAtRisk;
import com.fleetflow.tracking.domain.RiskObservation;
import com.fleetflow.tracking.domain.RiskPolicy;
import com.fleetflow.tracking.infrastructure.messaging.CourierDeliveriesSnapshot;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.state.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;
import java.time.Duration;
import java.util.function.BiFunction;

@Configuration
public class RiskDetectionTopology {
    @Bean
    public RiskPolicy riskPolicy(
            @Value("${tracking.risk.window:PT10M}") Duration window,
            @Value("${tracking.risk.minimum-observation:PT5M}") Duration minimum,
            @Value("${tracking.risk.maximum-ping-gap:PT2M}") Duration gap,
            @Value("${tracking.risk.stationary-radius-meters:30}") double radius,
            @Value("${tracking.risk.minimum-progress-ratio:0.1}") double progress,
            @Value("${tracking.risk.alert-cooldown:PT10M}") Duration cooldown) {
        return new RiskPolicy(window, minimum, gap, radius, progress, cooldown);
    }

    @Bean
    public TimestampExtractor locationTimestampExtractor() {
        return (record, partitionTime) -> record.value() instanceof CourierLocationUpdated ping
                ? ping.capturedAt().toEpochMilli() : record.timestamp();
    }

    @Bean
    public StoreBuilder<WindowStore<String, RiskObservation>> riskObservationWindow(RiskPolicy policy) {
        return Stores.windowStoreBuilder(Stores.persistentWindowStore(RiskDetectionProcessor.WINDOW,
                policy.window().plusMinutes(5), policy.window(), false), Serdes.String(), new JsonSerde<>(RiskObservation.class));
    }

    @Bean
    public StoreBuilder<KeyValueStore<String, Long>> riskLastPing() {
        return Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(RiskDetectionProcessor.LAST_PING), Serdes.String(), Serdes.Long());
    }

    @Bean
    public StoreBuilder<KeyValueStore<String, Long>> riskArrived() {
        return Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(RiskDetectionProcessor.ARRIVED), Serdes.String(), Serdes.Long());
    }

    @Bean
    public StoreBuilder<KeyValueStore<String, Long>> riskLastAlert() {
        return Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(RiskDetectionProcessor.ALERTS), Serdes.String(), Serdes.Long());
    }

    @Bean
    public BiFunction<KStream<String, CourierLocationUpdated>, KTable<String, CourierDeliveriesSnapshot>,
            KStream<String, DeliveryAtRisk>> riskDetection(RiskPolicy policy) {
        return (pings, deliveries) -> pings.join(deliveries, DeliveryLocation::join)
                .flatMapValues(values -> values)
                .process(() -> new RiskDetectionProcessor(policy), RiskDetectionProcessor.WINDOW,
                        RiskDetectionProcessor.LAST_PING, RiskDetectionProcessor.ALERTS, RiskDetectionProcessor.ARRIVED);
    }
}
