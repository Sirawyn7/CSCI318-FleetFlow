package com.fleetflow.tracking.application;

import com.fleetflow.tracking.infrastructure.messaging.ActiveDeliverySnapshotPublisher;
import com.fleetflow.tracking.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActiveDeliverySnapshotRecoveryTest {
    private final DeliveryJpaRepository deliveries = mock(DeliveryJpaRepository.class);
    private final CourierVehicleTypeViewRepository couriers = mock(CourierVehicleTypeViewRepository.class);
    private final ActiveDeliverySnapshotPublisher publisher = mock(ActiveDeliverySnapshotPublisher.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private final SimpleTransactionStatus firstTx = new SimpleTransactionStatus(), secondTx = new SimpleTransactionStatus();

    private void prepare() {
        when(deliveries.findCourierIds()).thenReturn(List.of(first, second));
        when(manager.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            return firstTx;
        }).thenReturn(secondTx);
        when(couriers.findForUpdate(second)).thenReturn(Optional.of(new CourierVehicleTypeView(second, "VAN")));
    }

    @Test void missingProjectionDoesNotPreventLaterCourierRecovery() {
        prepare();
        when(couriers.findForUpdate(first)).thenReturn(Optional.empty());
        new ActiveDeliverySnapshotRecovery(deliveries, couriers, publisher, manager).recover();
        verify(publisher, never()).publish(first);
        verify(publisher).publish(second);
        verify(manager).commit(firstTx);
        verify(manager).commit(secondTx);
    }

    @Test void commitsAndReleasesEachCourierBeforeLockingTheNext() {
        prepare();
        when(couriers.findForUpdate(first)).thenReturn(Optional.of(new CourierVehicleTypeView(first, "VAN")));
        new ActiveDeliverySnapshotRecovery(deliveries, couriers, publisher, manager).recover();
        var order = inOrder(couriers, publisher, manager);
        order.verify(couriers).findForUpdate(first);
        order.verify(publisher).publish(first);
        order.verify(manager).commit(firstTx);
        order.verify(couriers).findForUpdate(second);
        order.verify(publisher).publish(second);
        order.verify(manager).commit(secondTx);
    }

    @Test void failedPublicationRollsBackOnlyItsCourierAndContinues() {
        prepare();
        when(couriers.findForUpdate(first)).thenReturn(Optional.of(new CourierVehicleTypeView(first, "VAN")));
        doThrow(new IllegalStateException("broker failure")).when(publisher).publish(first);
        new ActiveDeliverySnapshotRecovery(deliveries, couriers, publisher, manager).recover();
        var order = inOrder(manager, publisher);
        order.verify(publisher).publish(first);
        order.verify(manager).rollback(firstTx);
        order.verify(publisher).publish(second);
        order.verify(manager).commit(secondTx);
    }
}
