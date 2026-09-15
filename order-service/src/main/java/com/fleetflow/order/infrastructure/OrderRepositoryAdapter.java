package com.fleetflow.order.infrastructure;

import com.fleetflow.order.domain.Order;
import com.fleetflow.order.domain.OrderRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepositoryAdapter implements OrderRepository {
    private final OrderJpaRepository repository;
    public OrderRepositoryAdapter(OrderJpaRepository repository) { this.repository = repository; }
    @Override
    public Order save(Order order) { return repository.saveAndFlush(order); }
    @Override
    public Optional<Order> findById(UUID id) { return repository.findById(id); }
    @Override
    public Optional<Order> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(order -> {
            // Load child values after acquiring the root lock, inside the application transaction.
            order.getItems();
            order.getStatusHistory();
            return order;
        });
    }
}
