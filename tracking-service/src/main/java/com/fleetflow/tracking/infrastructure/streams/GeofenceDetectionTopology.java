package com.fleetflow.tracking.infrastructure.streams;

import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.contracts.tracking.DeliveryArrived;
import com.fleetflow.contracts.tracking.DeliveryNearDestination;
import com.fleetflow.tracking.domain.GeoCoordinate;
import com.fleetflow.tracking.domain.GeofenceEvaluationService;
import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshot;
import org.apache.catalina.Store;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.BiFunction;

/**
 * FR12: geofence arrival detection. Two separate stream-table joins (near-detection,
 * arrival-detection) rather than one join emitting both DeliveryNearDestination and
 * DeliveryArrived -- a single Kafka Streams function has one output type, and routing
 * two different event types out of one join to two different topics needs either a
 * multi-output array return (risky to get right with Spring Cloud Stream's binder
 * without a broker to verify against) or dropping to raw Topology#to() calls (a bigger
 * departure from every other topology in this codebase). Two small, independently
 * testable joins reading the same input topics is the simpler, lower-risk choice; the
 * duplicate join is cheap and not a real cost at this scale.
 *
 * Both reuse the same GeofenceCandidate join shape as EtaRecalculationTopology's
 * EtaCandidate, computing distance instead of an ETA.
 */

@Configuration
public class GeofenceDetectionTopology {

    @Bean
    public StoreBuilder<KeyValueStore<String, String>> nearDestinationPublishedStore() {
        return Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(NearDestinationProcessor.STORE_NAME),
                Serdes.String(), Serdes.String());
    }

    @Bean
    public StoreBuilder<KeyValueStore<String, String>> arrivedPublishedStore() {
        return Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(ArrivedProcessor.STORE_NAME),
                Serdes.String(), Serdes.String());
    }

    @Bean
    public BiFunction<KStream<String, CourierLocationUpdated>, KTable<String, ActiveDeliverySnapshot>,
            KStream<String, DeliveryNearDestination>> nearDestinationDetection() {
        return (locationPings, activeDeliveries) -> locationPings
                .join(activeDeliveries, GeofenceDetectionTopology::computeCandidate)
                .process(NearDestinationProcessor::new, NearDestinationProcessor.STORE_NAME);
    }

    @Bean
    public BiFunction<KStream<String, CourierLocationUpdated>, KTable<String, ActiveDeliverySnapshot>,
            KStream<String, DeliveryArrived>> arrivedDetection() {
        return (locationPings, activeDeliveries) -> locationPings
                .join(activeDeliveries, GeofenceDetectionTopology::computeCandidate)
                .process(ArrivedProcessor::new, ArrivedProcessor.STORE_NAME);
    }

    private static GeofenceCandidate computeCandidate(CourierLocationUpdated location,
                                                      ActiveDeliverySnapshot snapshot) {
        GeoCoordinate current = new GeoCoordinate(location.location().latitude(), location.location().longitude());
        GeoCoordinate destination = new GeoCoordinate(snapshot.destinationLatitude(), snapshot.destinationLongitude());
        double distanceMeters = GeofenceEvaluationService.distanceMeters(current, destination);
        return new GeofenceCandidate(snapshot.deliveryId(), distanceMeters);
    }
}
