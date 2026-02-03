# Notification Service

A highly scalable, template-driven notification service built with **Java 17+** and **Spring Boot 3.x**, supporting pluggable channels: **SMS**, **Email**, **Push**, and **WhatsApp**.

## Documentation

The full architecture and implementation plan is in **[docs/NOTIFICATION_SERVICE_PLAN.md](docs/NOTIFICATION_SERVICE_PLAN.md)**. Use it as the single source of truth for design and iteration.

## Quick Start

**Requirements:** Java 17+, Maven 3.8+ (or use `./mvnw` after running `mvn wrapper:wrapper` once)

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

The app starts on port 8080. Health check: `GET http://localhost:8080/actuator/health`

## Project Structure

```
notification-service/
├── docs/
│   └── NOTIFICATION_SERVICE_PLAN.md   # Architecture & implementation plan
├── src/main/java/.../NotificationServiceApplication.java
├── src/main/resources/
│   └── application.yml
├── README.md
└── pom.xml
```

## Roadmap

Implementation follows the phases in the plan:

1. **Phase 1** – REST API, entities, template service, one channel (e.g. email)
2. **Phase 2** – Message queue, async producer/consumer, retries, DLQ
3. **Phase 3** – Channel registry, Email + SMS + stubs for Push/WhatsApp
4. **Phase 4** – Push (FCM/APNs), WhatsApp Business API
5. **Phase 5** – Rate limiting, circuit breakers, idempotency, observability

## Push to GitHub

1. **Create a new repo** on [github.com/new](https://github.com/new) (no README/.gitignore).
2. From this project root run:

```bash
git init
git add .
git commit -m "Initial commit: plan + Spring Boot skeleton"
git remote add origin https://github.com/YOUR_USERNAME/notification-service.git
git branch -M main
git push -u origin main
```

See **[docs/GITHUB_SETUP.md](docs/GITHUB_SETUP.md)** for more detail.

## License

MIT (or your choice)
