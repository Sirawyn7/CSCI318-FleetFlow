# Prompt 0005 — UC1 Order Service implementation against existing layered architecture

**Date:** 2026-09-08

**Used for:** UC1 – Manage Delivery Order in `order-service`, covering FR1 Create Order, FR2 Query Order Status, and FR3 Cancel Order.

**Why this one's worth citing:** the prompt led to a full vertical implementation across the existing `api`, `application`, `domain`, and `infrastructure` layers rather than generating code in one place. It also required the assistant to follow the project's existing DDD conventions and shared-contract boundary.

## Prompt (verbatim)

```text
Help me implement UC1 – Manage Delivery Order using the existing FleetFlow project structure. Keep the code simple and follow the existing api, application, domain and infrastructure layers.
```

## Response (verbatim excerpt)

```text
For UC1, keep the responsibilities separated like this:

domain/
    Order.java
    Address.java
    OrderStatus.java
    OrderRepository.java

application/
    OrderApplicationService.java
    OrderEventPort.java

api/
    OrderController.java

infrastructure/
    OrderJpaEntity.java
    OrderJpaRepository.java
    OrderRepositoryAdapter.java
    OrderEventPublisher.java

OrderRepository stays in the domain as the persistence abstraction.
OrderRepositoryAdapter implements it in infrastructure.

OrderEventPort stays in application so the application layer does not depend directly
on Kafka infrastructure.

OrderEventPublisher implements that port in infrastructure and publishes the existing
OrderPlaced and OrderCancelled contracts.

OrderController exposes:
POST /orders
GET /orders/{id}
PUT /orders/{id}/cancel
```

**Note on what prompted this:** UC1 needed to be implemented inside an already-scaffolded Order service. The main concern was understanding where each class belonged and keeping the implementation consistent with the existing layered/DDD structure rather than placing repository, REST, and Kafka responsibilities together.
