package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.tracking.DeliveryEtaUpdated;
import com.fleetflow.tracking.domain.EtaCalculationService;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshot;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.function.BiFunction;

/**
 * FR11: Kafka Streams topology. A stream-table join, matching AGENTS.md's own
 * description ("ETA recalculation -- stream-table join against active deliveries") --
 * the KStream side is location pings (high-frequency, per CourierLocationUpdated's
 * Javadoc), the KTable side is the active-delivery snapshot materialized from the
 * internal topic ActiveDeliverySnapshotPublisher writes to. Both are keyed by
 * courierId (see CourierLocationUpdatedEventPublisher's FR11 Javadoc for why that
 * keying was added), so Kafka Streams can co-partition them for the join.
 *
 * Bound to the kafka-streams binder (application.yml), separate from the plain
 * functional Consumer&lt;T&gt; beans in this same service, which stay on the regular
 * Kafka binder -- Spring Cloud Stream supports both binder types in one application.
 */
@Configuration
public class EtaRecalculationTopology {

    @Bean
    public StoreBuilder<KeyValueStore<String, Long>> lastEtaPerDeliveryStore() {
        return Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(EtaMaterialChangeProcessor.STORE_NAME),
                Serdes.String(), Serdes.Long());
    }

    @Bean
    public BiFunction<KStream<String, CourierLocationUpdated>, KTable<String, ActiveDeliverySnapshot>,
            KStream<String, DeliveryEtaUpdated>> etaRecalculation() {
        return (locationPings, activeDeliveries) -> locationPings
                .join(activeDeliveries, EtaRecalculationTopology::computeCandidate)
                .process(EtaMaterialChangeProcessor::new, EtaMaterialChangeProcessor.STORE_NAME);
    }

    private static EtaCandidate computeCandidate(CourierLocationUpdated location, ActiveDeliverySnapshot snapshot) {
        GeoCoordinate current = new GeoCoordinate(location.location().latitude(), location.location().longitude());
        GeoCoordinate destination = new GeoCoordinate(snapshot.destinationLatitude(), snapshot.destinationLongitude());
        Instant estimatedArrival = EtaCalculationService.estimateArrival(
                current, destination, snapshot.averageSpeedKmh(), Instant.now());
        return new EtaCandidate(snapshot.deliveryId(), estimatedArrival);
    }
}