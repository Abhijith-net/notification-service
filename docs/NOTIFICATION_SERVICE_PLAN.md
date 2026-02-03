# Notification Service – Architecture & Implementation Plan

## 1. Overview

A **highly scalable**, **template-driven** notification service built with **Java 17+** and **Spring Boot 3.x**, supporting multiple **pluggable channels** (SMS, Email, Push, WhatsApp) with a single API and consistent delivery semantics.

---

## 2. Goals & Non-Goals

| Goals | Non-Goals |
|-------|-----------|
| Single API for all notification types | Building channel-specific UIs |
| Template-based content with variables | Real-time chat / WebSocket streaming |
| Pluggable channels (add/remove without core changes) | In-app only notifications |
| At-least-once delivery, retries, dead-letter | Guaranteed exactly-once (trade-off for scale) |
| Horizontal scaling, async processing | Synchronous request-response for all flows |

---

## 3. High-Level Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    API Layer (REST)                       │
                    │  POST /api/v1/notifications  GET /api/v1/notifications   │
                    └─────────────────────────────────────────────────────────┘
                                              │
                                              ▼
                    ┌─────────────────────────────────────────────────────────┐
                    │              Notification Orchestrator                   │
                    │  • Validate request • Resolve template • Enqueue         │
                    └─────────────────────────────────────────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
            ┌───────────────┐         ┌───────────────┐         ┌───────────────┐
            │   Template    │         │   Channel     │         │   Message      │
            │   Service     │         │   Registry    │         │   Queue        │
            │ (configurable)│         │ (pluggable)   │         │ (e.g. Kafka/   │
            └───────────────┘         └───────────────┘         │  RabbitMQ/SQS) │
                    │                         │                  └───────────────┘
                    │                         │                         │
                    │                         │                         ▼
                    │                         │                  ┌───────────────┐
                    │                         │                  │   Consumer    │
                    │                         │                  │   Workers     │
                    │                         │                  └───────────────┘
                    │                         │                         │
                    │                         ▼                         ▼
                    │                  ┌─────────────────────────────────────────┐
                    │                  │           Channel Implementations        │
                    └─────────────────►│  SMS  │  Email  │  Push  │  WhatsApp   │
                                       └─────────────────────────────────────────┘
```

---

## 4. Core Components

### 4.1 API Layer

- **REST API** (e.g. `/api/v1/notifications`)
  - **Request**: `templateId`, `channel` (or list), `recipient(s)`, `variables` (key-value for template), optional `metadata`, `priority`, `scheduledAt`.
  - **Response**: `notificationId`, `status` (ACCEPTED / INVALID / ERROR).
- **Idempotency**: Optional `Idempotency-Key` header to avoid duplicate sends.
- **Validation**: JSR-303/380 on DTOs; reject invalid template/channel/recipient early.

### 4.2 Notification Orchestrator

- Receives API request.
- **Template resolution**: Call Template Service to get final content (subject, body, etc.) per channel from template + variables.
- **Channel selection**: Single channel or multi-channel from request; validate against Channel Registry.
- **Persistence**: Store notification record (id, templateId, channel, recipient, variables, status, createdAt, etc.).
- **Enqueue**: Publish a message to the message queue (e.g. one event per channel-recipient pair) for async processing.
- Returns immediately with `notificationId` and ACCEPTED.

### 4.3 Template Service (Configurable Templates)

- **Storage**: DB (e.g. `templates` table: id, name, channel_type, locale, subject_template, body_template, metadata). Optional: file-based or CMS for non-developers.
- **Engine**: Use a simple placeholder engine (e.g. **Mustache**, **Thymeleaf**, or **Simple placeholder `${var}`**) to resolve variables.
- **Structure per channel**:
  - **Email**: subject + HTML/plain body.
  - **SMS**: single text body (with optional link shortener).
  - **Push**: title + body + optional data payload.
  - **WhatsApp**: text or template name + parameters (align with WhatsApp Business API).
- **Versioning**: Optional template version column to support A/B or rollback.
- **Caching**: Cache resolved template definitions (and optionally compiled templates) with TTL/invalidation.

### 4.4 Channel Registry (Pluggable Channels)

- **Interface**: e.g. `NotificationChannel` with `String getChannelType()`, `boolean supports(NotificationRequest)`, `CompletableFuture<SendResult> send(NotificationPayload payload)`.
- **Registry**: Spring-managed map or list of `NotificationChannel` beans; lookup by `channelType`.
- **Pluggable**: Each channel (SMS, Email, Push, WhatsApp) is a separate module or package implementing the interface; add/remove by adding/removing dependency and config.

### 4.5 Message Queue & Consumers

- **Queue**: Kafka (recommended for scale and replay) or RabbitMQ or AWS SQS.
- **Event**: Contains `notificationId`, `channel`, `recipient`, resolved content (or reference to it), retry count, etc.
- **Consumers**: One or more consumer groups that:
  - Load notification record.
  - Get channel implementation from registry.
  - Call `channel.send()`.
  - Update status (SENT / FAILED), persist delivery metadata (external id, latency).
  - On failure: retry with backoff (e.g. exponential); after max retries, move to DLQ and mark FAILED.

### 4.6 Channel Implementations (Pluggable)

| Channel   | Key concerns | Typical dependencies |
|-----------|----------------|----------------------|
| **SMS**   | Provider (Twilio, AWS SNS, etc.), rate limits, cost | REST client to provider API |
| **Email** | SMTP or SendGrid/Mailgun API, attachments, tracking | Spring Mail or HTTP client |
| **Push**  | FCM (Android), APNs (iOS), device tokens, payload shape | Firebase Admin SDK, APNs library |
| **WhatsApp** | WhatsApp Business API, template approval, media | HTTP client to Meta API |

Each implementation:

- Implements `NotificationChannel`.
- Uses configuration (e.g. `application.yml` or env) for API keys and endpoints.
- Handles provider-specific errors and maps to a common `SendResult` (success/failure + reason).

---

## 5. Data Model (Minimal)

- **notifications**
  - id (UUID), template_id, channel, recipient (e.g. phone/email/token), variables (JSON), status, priority, created_at, updated_at, sent_at, external_id, error_message, retry_count.
- **templates**
  - id, name, channel_type, locale, subject_template, body_template, extra (JSON), version, active.
- Optional: **delivery_logs** for audit per attempt.

---

## 6. Scalability & Resilience

- **Stateless API**: Horizontal scaling behind a load balancer.
- **Async processing**: All sends via queue; no long-running work in API threads.
- **Queue partitioning**: Partition by `channel` or `notificationId` for order or parallelism.
- **Consumer scaling**: Scale consumer instances with queue depth (Kafka partitions or RabbitMQ prefetch).
- **Rate limiting**: Per channel/provider (e.g. token bucket) in channel implementation or a dedicated rate-limiter component.
- **Circuit breaker**: Use Resilience4j for outbound calls to SMS/Email/Push/WhatsApp APIs.
- **Retries & DLQ**: Configurable max retries; DLQ for alerting and manual replay.
- **Idempotent consumers**: Use `notificationId` + attempt id so duplicate queue messages don't double-send (where provider allows).
- **Caching**: Template definitions and, if needed, resolved content cache (e.g. Caffeine/Redis).
- **DB**: Connection pooling, read replicas for status queries if needed.

---

## 7. Configuration & Security

- **Secrets**: API keys and credentials in env variables or a secret manager (e.g. Vault, AWS Secrets Manager); never in repo.
- **Channel toggles**: Feature flags or config to enable/disable channels per environment.
- **Templates**: Stored in DB; optional admin API with auth to create/update templates.
- **Rate limits**: Configurable per channel and per tenant (if multi-tenant).

---

## 8. Technology Stack (Suggested)

| Layer | Technology |
|-------|------------|
| Runtime | Java 17+ |
| Framework | Spring Boot 3.x |
| API | Spring Web (REST), optional OpenAPI |
| Messaging | Spring Kafka (or Spring AMQP / AWS SQS SDK) |
| DB | PostgreSQL (or MySQL) + Spring Data JPA / R2DBC if going reactive |
| Template engine | Mustache (e.g. JMustache) or Thymeleaf |
| Resilience | Resilience4j (retry, circuit breaker) |
| Cache | Caffeine (in-process) or Redis |
| Observability | Micrometer + Prometheus, structured logging, tracing (e.g. Sleuth/Brave) |

---

## 9. Implementation Phases

### Phase 1 – Foundation (Weeks 1–2)

- Spring Boot project, API with DTOs and validation.
- Notification and Template entities + repositories.
- Template Service: load from DB, simple placeholder resolution (e.g. `${var}`), cache.
- In-memory or same-JVM "sync" sender for one channel (e.g. Email via Spring Mail) to validate flow end-to-end.

### Phase 2 – Queue & Async (Week 2–3)

- Integrate message queue (e.g. Kafka).
- Producer in orchestrator; consumer that loads notification, resolves channel from registry, sends.
- Retry and DLQ configuration.
- Status updates and basic delivery metadata.

### Phase 3 – Channel Registry & Pluggable Channels (Weeks 3–4)

- Define `NotificationChannel` interface and Channel Registry.
- Implement Email (SMTP or provider API), SMS (e.g. Twilio), and add stub for Push and WhatsApp.
- Config per channel; register beans by `channelType`.

### Phase 4 – Push & WhatsApp (Weeks 4–5)

- Push: FCM (and optionally APNs) – device token storage, payload mapping from template.
- WhatsApp: WhatsApp Business API client, template-based messages, error handling.

### Phase 5 – Scale & Hardening (Weeks 5–6)

- Rate limiting, circuit breakers (Resilience4j).
- Idempotency key support at API.
- Observability: metrics (e.g. send latency, success/failure per channel), tracing, health checks.
- Optional: Admin API for templates and replay from DLQ.

---

## 10. API Example

**Request**

```http
POST /api/v1/notifications
Content-Type: application/json
Idempotency-Key: optional-key-123

{
  "templateId": "welcome-email",
  "channels": ["EMAIL"],
  "recipients": [
    { "channel": "EMAIL", "address": "user@example.com" }
  ],
  "variables": {
    "userName": "John",
    "loginUrl": "https://app.example.com/login"
  },
  "priority": "HIGH"
}
```

**Response**

```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED"
}
```

---

## 11. Template Example (DB)

| Column | Example |
|--------|--------|
| id | welcome-email |
| channel_type | EMAIL |
| subject_template | Welcome, ${userName}! |
| body_template | Hi ${userName}, please sign in here: ${loginUrl} |
| locale | en |

---

## 12. Success Criteria

- Single REST API accepts notifications for any supported channel.
- Templates are stored and editable (DB or admin API), with variable substitution.
- New channel = new implementation of `NotificationChannel` + config; no change to core orchestrator.
- System scales horizontally; async processing with retries and DLQ.
- Observability: latency, success/failure rates, and basic tracing in place.

This plan gives you a clear path to a **highly scalable**, **template-driven**, **pluggable-channel** notification service with Java and Spring Boot. If you want, next step can be a concrete project structure (packages, classes) or Phase 1 skeleton code.
