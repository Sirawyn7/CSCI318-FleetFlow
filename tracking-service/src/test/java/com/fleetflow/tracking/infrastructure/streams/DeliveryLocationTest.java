package com.fleetflow.tracking.infrastructure.streams;
import com.fleetflow.contracts.EventEnvelope;
import com.fleetflow.contracts.common.GeoPoint;
import com.fleetflow.contracts.fleet.CourierLocationUpdated;
import com.fleetflow.tracking.infrastructure.messaging.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryLocationTest {
    @Test void fansOutAndUsesCaptureTimeRatherThanWallClock() {
        Instant time=Instant.parse("2026-09-29T00:00:00Z"); UUID courier=UUID.randomUUID();
        var ping=new CourierLocationUpdated(new EventEnvelope(UUID.randomUUID(),"CourierLocationUpdated",time,"trace"),
                courier,new GeoPoint(-33.8,151.2),time);
        var first=new ActiveDeliverySnapshot(UUID.randomUUID(),-33.8,151.2,30,UUID.randomUUID(),courier,time.plusSeconds(3600),time);
        var second=new ActiveDeliverySnapshot(UUID.randomUUID(),-33.9,151.2,30,UUID.randomUUID(),courier,null,time);
        var future=new ActiveDeliverySnapshot(UUID.randomUUID(),-33.8,151.2,30,UUID.randomUUID(),courier,null,time.plusSeconds(1));
        assertEquals(2,DeliveryLocation.join(ping,new CourierDeliveriesSnapshot(List.of(first,second,future))).size());
        assertEquals(time,EtaRecalculationTopology.computeCandidate(ping,first).estimatedArrival());
    }
}
