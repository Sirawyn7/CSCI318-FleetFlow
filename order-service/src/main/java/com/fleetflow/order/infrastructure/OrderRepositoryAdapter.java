package com.fleetflow.order.infrastructure;

import com.fleetflow.order.domain.Order;
import com.fleetflow.order.domain.OrderRepository;
import com.fleetflow.order.domain.OrderStatus;
import com.fleetflow.order.domain.Address;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


@Repository
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository repository;

    public OrderRepositoryAdapter(OrderJpaRepository repository) {
        this.repository = repository;
    }

@Override
public Order save(Order order) {

    OrderJpaEntity entity = new OrderJpaEntity();

    entity.setId(order.getId());
    entity.setCustomerId(order.getCustomerId());

    entity.setPickupStreet(order.getPickupAddress().street());
    entity.setPickupSuburb(order.getPickupAddress().suburb());
    entity.setPickupState(order.getPickupAddress().state());
    entity.setPickupPostcode(order.getPickupAddress().postcode());
    entity.setPickupLatitude(order.getPickupAddress().latitude());
    entity.setPickupLongitude(order.getPickupAddress().longitude());

    entity.setDropoffStreet(order.getDropoffAddress().street());
    entity.setDropoffSuburb(order.getDropoffAddress().suburb());
    entity.setDropoffState(order.getDropoffAddress().state());
    entity.setDropoffPostcode(order.getDropoffAddress().postcode());
    entity.setDropoffLatitude(order.getDropoffAddress().latitude());
    entity.setDropoffLongitude(order.getDropoffAddress().longitude());

    entity.setPackageDetails(order.getPackageDetails());
    entity.setServiceLevel(order.getServiceLevel());
    entity.setRequestedDeliveryBy(order.getRequestedDeliveryBy());
    entity.setStatus(order.getStatus().name());
    entity.setCreatedAt(order.getCreatedAt());

    repository.save(entity);

    return order;
}

@Override
public Optional<Order> findById(UUID id) {

    return repository.findById(id)
            .map(entity -> new Order(
                    entity.getId(),
                    entity.getCustomerId(),
                    new Address(
                            entity.getPickupStreet(),
                            entity.getPickupSuburb(),
                            entity.getPickupState(),
                            entity.getPickupPostcode(),
                            entity.getPickupLatitude(),
                            entity.getPickupLongitude()
                    ),
                    new Address(
                            entity.getDropoffStreet(),
                            entity.getDropoffSuburb(),
                            entity.getDropoffState(),
                            entity.getDropoffPostcode(),
                            entity.getDropoffLatitude(),
                            entity.getDropoffLongitude()
                    ),
                    entity.getPackageDetails(),
                    entity.getServiceLevel(),
                    entity.getRequestedDeliveryBy(),
                    OrderStatus.valueOf(entity.getStatus()),
                    entity.getCreatedAt()
            ));
}

}