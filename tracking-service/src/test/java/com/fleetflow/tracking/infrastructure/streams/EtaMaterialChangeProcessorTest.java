package com.fleetflow.tracking.infrastructure.streams;
import com.fleetflow.contracts.tracking.DeliveryEtaUpdated;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
class EtaMaterialChangeProcessorTest {
    @Test @SuppressWarnings("unchecked") void suppressesDuplicatesOldPingsAndSmallChanges() {
        ProcessorContext<String, DeliveryEtaUpdated> context = mock(ProcessorContext.class);
        KeyValueStore<String,Long> etas=mock(KeyValueStore.class), pings=mock(KeyValueStore.class);
        Map<String,Long> etaMap=new HashMap<>(), pingMap=new HashMap<>();
        when(etas.get(anyString())).thenAnswer(i->etaMap.get(i.getArgument(0)));
        when(pings.get(anyString())).thenAnswer(i->pingMap.get(i.getArgument(0)));
        doAnswer(i->{etaMap.put(i.getArgument(0),i.getArgument(1));return null;}).when(etas).put(anyString(),anyLong());
        doAnswer(i->{pingMap.put(i.getArgument(0),i.getArgument(1));return null;}).when(pings).put(anyString(),anyLong());
        when(context.getStateStore(EtaMaterialChangeProcessor.STORE_NAME)).thenReturn(etas);
        when(context.getStateStore(EtaMaterialChangeProcessor.PING_STORE)).thenReturn(pings);
        var processor=new EtaMaterialChangeProcessor(); processor.init(context);
        UUID id=UUID.randomUUID(); Instant time=Instant.parse("2026-09-29T00:00:00Z");
        processor.process(new Record<>("courier",new EtaCandidate(id,time.plusSeconds(600),time),0));
        processor.process(new Record<>("courier",new EtaCandidate(id,time.plusSeconds(900),time),0));
        processor.process(new Record<>("courier",new EtaCandidate(id,time.plusSeconds(900),time.minusSeconds(1)),0));
        processor.process(new Record<>("courier",new EtaCandidate(id,time.plusSeconds(650),time.plusSeconds(1)),0));
        processor.process(new Record<>("courier",new EtaCandidate(id,time.plusSeconds(700),time.plusSeconds(2)),0));
        verify(context,times(2)).forward(any());
    }
}
