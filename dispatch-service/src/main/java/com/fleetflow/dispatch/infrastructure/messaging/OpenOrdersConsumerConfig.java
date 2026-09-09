package com.fleetflow.dispatch.infrastructure.messaging;

import com.fleetflow.contracts.order.OrderCancelled;
import com.fleetflow.contracts.order.OrderPlaced;
import com.fleetflow.dispatch.application.OpenOrdersProjectionService;
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
 */
@Configuration
public class OpenOrdersConsumerConfig {

    @Bean
    public Consumer<OrderPlaced> orderPlacedConsumer(
            OrderPlacedTranslator translator,
            OpenOrdersProjectionService projectionService) {
        return event -> projectionService.applyOrderPlaced(
                event.envelope().eventId(),
                event.envelope().eventType(),
                translator.translate(event));
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
