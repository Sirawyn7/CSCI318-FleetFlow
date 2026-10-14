# UC8 — Notify and Explain Delivery Status (FR15–FR16)

Notification delivers lifecycle alerts to a persisted in-app inbox and provides a
conversational delivery status explainer. It consumes Dispatch and Tracking events
and reads Order and Tracking REST APIs using the contracts below.

## Implemented

- FR15 consumes the five original lifecycle events plus `OrderReassigned` and
  `AssignmentFailed`, renders persisted templates,
  delivers to a durable **IN_APP inbox**, and publishes the existing NotificationSent
  audit event. No email/SMS provider is required or claimed.
- Reassignment and assignment-failure messages omit internal diagnostic reasons and use
  the same recipient lookup, deduplication, inbox and audit pipeline. Raw reasons remain
  in the internal notification record. Legacy persisted assignment templates also use
  the safe customer wording.
- Notification kinds and template codes use `VARCHAR(64)`. Hibernate schema update
  converts the earlier native H2 enum columns, preserving existing rows.
- FR16 provides conversational questions and follow-ups using LangChain4j AiServices
  and two read-only, order-scoped REST tools. Conversation turns and fallback use are
  persisted. Only the last three turns go to the model; a conversation accepts 50
  turns, then the client starts a new one.
- Missing Gemini credentials or invalid/failed model output gives a factual fallback,
  with order status and risk flags described in plain words rather than internal codes.
  Missing upstream data is explicitly unavailable, never interpreted as no risk.
- Events deduplicate by envelope.eventId. The processed-event ledger, pending message
  and delivery/order correlation commit together. Duplicate concurrent inserts roll
  back and retry through Kafka's consumer error handling.

## REST (port 8085)

| Method | Path | Input / result |
|---|---|---|
| GET | /notifications?customerId={uuid}&page=0&size=20 | Customer inbox; size 1–100; newest source-event time first |
| POST | /orders/{orderId}/explanations | Question, optional conversationId; returns answer, conversationId, fallback, and source facts |

Example first question:

~~~json
{"question":"Why is my delivery late?"}
~~~

Send a follow-up to the same order with the returned conversationId:

~~~json
{"question":"What is the latest ETA?","conversationId":"<returned UUID>"}
~~~

Questions must contain 1–2000 characters. Invalid requests return 400; an unknown
order or conversation (including reuse for a different order) returns 404. Concurrent
updates to a conversation return 409 so the client can retry. Missing upstream data
returns an explanation with availability flags; fallback=true means the model path
failed or was not configured, not necessarily that every upstream is unavailable.

As with the other coursework APIs, caller identity is not authenticated. customerId,
orderId and conversationId are selectors, not authorization. Connect authentication
and ownership checks before exposing customer data outside the local demo.

## Upstream REST contracts

### Order REST — existing format

ORDER_SERVICE_URL defaults to http://localhost:8081. GET /orders/{orderId} must return:

~~~json
{
  "id":"<order UUID>",
  "customerId":"<customer UUID>",
  "status":"IN_TRANSIT",
  "requestedDeliveryBy":"2026-10-07T08:00:00Z"
}
~~~

Additional fields from the existing OrderResponse are ignored. customerId is required
for recipient resolution. The model sees only status and deadline, not addresses,
package data or the customer ID. Notifications remain pending if Order is offline
or the order is not yet available; no recipient is invented.

### Tracking REST — implemented in tracking-service

TRACKING_SERVICE_URL defaults to http://localhost:8084. The configurable
TRACKING_STATUS_PATH defaults to /deliveries/order/{orderId}/status and expects:

~~~json
{
  "orderId":"<order UUID>",
  "estimatedArrival":"2026-10-07T08:10:00Z",
  "riskFlags":["STATIONARY"],
  "updatedAt":"2026-10-07T07:55:00Z"
}
~~~

Tracking implements this endpoint using the newest delivery for the order and
persisted projections of DeliveryEtaUpdated and DeliveryAtRisk. No Notification client configuration
change is needed for the default path. See
[ADR 0016](../docs/decisions/0016-tracking-status-read-model.md).

A known delivery returns 200 even before an ETA is available; estimatedArrival and
updatedAt are then null. An order without a Tracking delivery returns 404.
updatedAt is the ETA event's observation time, which the answer exposes to show
freshness. riskFlags lists the FR13 reasons currently raised for the delivery
(STATIONARY, OFF_PACE, SLA_BREACH); [] means Tracking reports no current risk, and
null means risk information is unavailable.
A 404, connection error, or mismatched orderId is unavailable data; the explainer
can still report Order facts when Tracking is unavailable.

### Existing Kafka contracts

| Event | Template | Correlation |
|---|---|---|
| OrderAssigned | ASSIGNED | orderId |
| OrderReassigned | REASSIGNED | orderId + reason |
| AssignmentFailed | ASSIGNMENT_FAILED | orderId + reason |
| DeliveryStarted | IN_TRANSIT | deliveryId + orderId; saves the local link |
| DeliveryNearDestination | NEAR_DESTINATION | deliveryId; waits for the local link |
| DeliveryCompleted | DELIVERED | deliveryId + orderId |
| DeliveryAtRisk | AT_RISK | deliveryId + orderId + riskReason |

Uses the exact existing fleetflow.dispatch.* and fleetflow.tracking.* topic names,
consumer group notification-service. ETA updates do not generate notifications.
Tracking's FR13 and FR14 implementations publish
DeliveryAtRisk and DeliveryCompleted for at-risk and completed alerts, respectively.

Invalid input retries and then goes to error.<source-topic>.notification-service.
Missing links or upstream Order data do not send valid events to the DLQ: their
notifications are persisted and retried by the worker. Worker pages rotate to avoid
an unresolved early message blocking all later messages. Pending rows survive restart.

The inbox commits before NotificationSent is published. The audit outbox retries
broker failures; a crash after broker acknowledgement can duplicate the audit event,
so its envelope eventId remains the persisted notification ID on every retry.
This is **at-least-once audit delivery**, with one inbox row per source event.
Messages are lifecycle history, not a mutable current-status view: out-of-order
arrival does not rewrite earlier messages, and occurredAt records the source time.

## Configuration and running

Requires Java 21, Maven and Kafka. Uses only the repository's approved dependencies.
The Gemini integration uses the same pinned LangChain4j 1.20.0 and configurable
Gemini model as Dispatch; credentials are optional for the fallback path.

| Environment variable | Default |
|---|---|
| KAFKA_BROKERS | localhost:9092 |
| ORDER_SERVICE_URL | http://localhost:8081 |
| TRACKING_SERVICE_URL | http://localhost:8084 |
| TRACKING_STATUS_PATH | /deliveries/order/{orderId}/status |
| GEMINI_API_KEY | empty (fallback) |
| GEMINI_MODEL | gemini-2.0-flash |

REST connect/read timeouts are 2/3 seconds; model requests use a 10-second timeout,
no retries, and at most three sequential tool-invocation rounds. This is a bounded
number of requests, not a single overall 10-second deadline. Agent output must be
nonblank, at most 4000 characters, and follow both tool reads. Prompts constrain
factual claims; automated validation cannot prove every generated sentence correct.

From the repository root:

~~~text
mvn -pl notification-service -am install -DskipTests
mvn -pl notification-service spring-boot:run
~~~

The worker polls every five seconds (notification.worker.poll-ms); disable with
notification.worker.enabled=false for controlled tests. H2 persists to
./data/notificationdb relative to the service working directory.

## Tests

~~~text
mvn -pl notification-service -am test
~~~

H2 tests cover templates, duplicate events, out-of-order correlation, rollback,
recipient resolution failure, audit retry, conversation continuation and fallback.
REST tests mock upstream HTTP responses; agent tests execute the real AiServices
loop with a deterministic ChatModel. MVC tests cover the API contracts.
NotificationKafkaIntegrationTest uses a real Kafka Testcontainer and mocks only the
upstream REST port; it skips explicitly when Docker is unavailable. No live Gemini
request is made by the automated suite.

## Persistence and design evidence

Notification owns notifications, notification_templates, agent_conversations,
agent_conversation_turns, processed_events, and delivery_order_links. The last two
are infrastructure ledgers/projections, not domain entities. Cross-context references
are IDs only; no foreign keys or queries into another service's database exist.
NotificationKind is the immutable domain vocabulary for lifecycle templates.
See [ADR 0015](../docs/decisions/0015-uc8-isolated-notification-integration.md) for the
inbox/outbox and deferred integration choices.
