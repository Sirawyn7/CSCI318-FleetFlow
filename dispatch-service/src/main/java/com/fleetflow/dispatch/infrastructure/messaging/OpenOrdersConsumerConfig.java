package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.dispatch.application.AssignmentService;
import com.fleetflow.dispatch.application.OpenOrdersProjectionService;
import com.fleetflow.dispatch.infrastructure.persistence.OpenOrderView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Functional consumer beans for both events feeding open_orders_view. Kept in one
 * config class, unlike {@link CourierRegisteredConsumerConfig}'s single bean, since
 * both consumers here serve the same projection (docs/decisions/0001) rather than two
 * unrelated slices -- colocating them keeps the wiring for one read model in one place.
 * Binding name convention unchanged: <bean-name>-in-0 (see application.yml,
 * spring.cloud.function.definition + spring.cloud.stream.bindings). Consumer group =
 * service name, so every dispatch-service instance shares one logical subscription
 * rather than each receiving its own copy of every event.
 *
 * <p>orderPlacedConsumer also triggers FR7's rule-based assignment attempt, in the
 * same consumer rather than a second one independently subscribed to OrderPlaced.
 * Two independent consumer groups on the same topic would race: the assignment side
 * could delete-or-skip an open_orders_view row before the projection side has
 * inserted it, then have that insert land afterward and leave a phantom backlog row
 * forever. Sequencing inside one consumer removes that race -- the projection is
 * always committed before the assignment attempt runs. OpenOrdersProjectionService
 * itself is untouched by this, so its existing unit test is unaffected.
 */
@Configuration
public class OpenOrdersConsumerConfig {

    @Bean
    public Consumer<OrderPlaced> orderPlacedConsumer(
            OrderPlacedTranslator translator,
            OpenOrdersProjectionService projectionService,
            AssignmentService assignmentService) {
        return event -> {
            OpenOrderView snapshot = translator.translate(event);
            projectionService.applyOrderPlaced(
                    event.envelope().eventId(),
                    event.envelope().eventType(),
                    snapshot);
            assignmentService.attemptAssignment(snapshot);
        };
    }

    @Bean
    public Consumer<OrderCancelled> orderCancelledConsumer(
            OrderCancelledTranslator translator,
            OpenOrdersProjectionService projectionService) {
        return event -> projectionService.applyOrderCancelled(
                event.envelope().eventId(),
                event.envelope().eventType(),
                translator.translate(event));
    }
}
