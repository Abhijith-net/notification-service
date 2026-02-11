# EKS Infrastructure Plan — Notification Service

**Scope**: Internal API, Postgres + Redis + Kafka, ~2000 RPS, 1–10M notifications/day, async model, separate dev/stage/prod environments.

---

## 1. Overview

| Item | Choice |
|------|--------|
| API exposure | **Internal only** (private ALB / internal ingress) |
| Database | **Postgres** (RDS or Aurora) |
| Cache | **Redis** (ElastiCache) |
| Queue/stream | **Kafka** (Amazon MSK) |
| Traffic | ~2000 RPS API; 1–10M events/day via Kafka |
| Model | Async: API enqueues to Kafka → consumers send notifications |
| Environments | **Separate**: dev, staging, prod (separate clusters + data planes recommended) |

---

## 2. High-Level Architecture

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                        VPC (multi-AZ)                        │
                    │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
                    │  │   Private   │  │   Private   │  │   Private   │          │
                    │  │  Subnet 1a  │  │  Subnet 1b  │  │  Subnet 1c  │          │
                    │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │
                    │         │                │                │                  │
                    │         ▼                ▼                ▼                  │
                    │  ┌──────────────────────────────────────────────────────┐   │
                    │  │                    EKS Cluster                        │   │
                    │  │  ┌─────────────────┐    ┌─────────────────────────┐  │   │
                    │  │  │ API (Deployment) │    │ Consumer (Deployment)   │  │   │
                    │  │  │ Internal ALB    │    │ Kafka listener          │  │   │
                    │  │  └────────┬────────┘    └───────────┬─────────────┘  │   │
                    │  └───────────┼─────────────────────────┼─────────────────┘   │
                    │              │                         │                     │
                    │              ▼                         ▼                     │
                    │  ┌───────────────┐  ┌───────────────┐  ┌─────────────────┐  │
                    │  │ RDS Postgres  │  │ ElastiCache   │  │ Amazon MSK      │  │
                    │  │ (Multi-AZ)    │  │ Redis         │  │ (Kafka)         │  │
                    │  └───────────────┘  └───────────────┘  └─────────────────┘  │
                    └─────────────────────────────────────────────────────────────┘
                                         │
                    Internal clients ────┘ (VPC / VPN / PrivateLink)
```

- **API**: Receives internal requests, persists notification record, publishes to Kafka topic `notification-send`.
- **Consumer**: Consumes from `notification-send`, loads template, calls channels (email/SMS/push/WhatsApp), updates status in Postgres, uses Redis for cache/rate limits.

---

## 3. Environments and Isolation

| Environment | Cluster | Data plane | Use |
|-------------|---------|------------|-----|
| **dev** | EKS dev (or shared dev cluster + namespace) | Dedicated RDS/Redis/MSK dev | Feature work, integration tests |
| **staging** | EKS staging | Dedicated RDS/Redis/MSK staging | Pre-prod, load tests, 2000 RPS validation |
| **prod** | EKS prod | Dedicated RDS/Redis/MSK prod | Live traffic, 1–10M/day |

Recommendation: **Separate EKS cluster per environment** for blast-radius and lifecycle (upgrade prod independently). If cost is a concern, dev + staging can share one cluster with separate namespaces and separate RDS/Redis/MSK per env.

---

## 4. Networking (VPC)

- **VPC** in a single region, **3 AZs**.
- **Subnets**:
  - **Private** (EKS nodes, RDS, ElastiCache, MSK brokers): 3 subnets, one per AZ.
  - **Public** (optional): only for NAT Gateways; no EKS nodes here if you want internal-only.
- **NAT**: One NAT Gateway per AZ (prod) for outbound (Twilio, FCM, WhatsApp, etc.); dev/staging can use 1 NAT to save cost.
- **VPC endpoints** (private link, same region):
  - ECR (api + dkr), S3 (gateway), CloudWatch Logs, Secrets Manager, STS, optionally SSM.
  - Reduces NAT usage and improves security.
- **No public ALB**; internal clients reach the API via:
  - **Internal Application Load Balancer** (created by AWS Load Balancer Controller with `alb.ingress.kubernetes.io/scheme: internal`), or
  - **Internal NLB** in front of the API Service.

---

## 5. EKS Cluster (per environment)

- **Kubernetes version**: 1.28+ (stay within support window).
- **Endpoint**: Private only, or public restricted by CIDR; no 0.0.0.0/0.
- **Node groups** (Managed Node Groups):
  - **System / app**: On-demand, 2–3 AZs, instance types e.g. `m5.large` or `m6i.large` (adjust from usage).
  - **Sizing (prod, 2000 RPS + consumers)**:
    - API: 2–4 replicas (HPA); ~0.5–1 vCPU per pod → 2–4 nodes for app + system.
    - Consumer: 2–6 replicas (scale on Kafka lag); similar CPU.
    - Start with **3–6 nodes** (e.g. 3 × m5.large), use **Cluster Autoscaler** or **Karpenter** to scale.
  - Optional: separate node group for **consumer** (e.g. spot) to isolate and save cost.
- **Add-ons**:
  - VPC CNI, CoreDNS, kube-proxy (managed).
  - EBS CSI driver (if you need PVCs).
  - **AWS Load Balancer Controller** (for internal ALB).
  - **Metrics Server** (for HPA).
  - **Cluster Autoscaler** or **Karpenter**.
  - Optional: External DNS (if using Route53 private zone for internal DNS).

---

## 6. Data Plane Sizing

### 6.1 RDS PostgreSQL

- **Engine**: PostgreSQL 15+.
- **Topology**: Multi-AZ (prod); single-AZ acceptable for dev.
- **Instance (prod)**: e.g. `db.r6g.large` (2 vCPU, 16 GiB) to start; scale to `db.r6g.xlarge` if needed for 10M/day writes + indexes.
- **Storage**: gp3, 100–200 GiB, autoscaling enabled.
- **Connections**: Pool size from app (e.g. 20–50 per pod); max_connections on RDS ≥ (pods × pool size) + buffer.
- **Security**: In private subnets; security group allows only EKS node SG (or pod SG if using security groups for pods).

### 6.2 ElastiCache Redis

- **Engine**: Redis 7.
- **Topology**: Cluster mode disabled for simplicity; 1 primary + 1 replica (Multi-AZ) in prod.
- **Node type (prod)**: e.g. `cache.r6g.large`; scale if cache hit rate or key count grows.
- **Dev/staging**: Single node `cache.t3.micro` or `cache.t3.small`.
- **Security**: Same private subnets; SG allows only EKS.

### 6.3 Amazon MSK (Kafka)

- **Topic**: `notification-send` (matches `notification.kafka.topic`).
- **Consumer group**: `notification-service`.
- **Broker type**: **Kafka 3.x**, **Provisioned** (recommended for 1–10M/day and predictable latency).
- **Broker count**: 3 (one per AZ) for HA.
- **Broker instance**: e.g. `kafka.t3.small` (dev), `kafka.m5.large` (prod).
- **Storage**: EBS per broker (e.g. 100–500 GiB gp3 for prod); retention 24–72 hours.
- **IAM auth**: Use IAM for producer/consumer auth; no SASL in app (use AWS MSK IAM auth).
- **VPC**: Deploy in same VPC private subnets; security group allows EKS → MSK 9098 (IAM), 9092 (plain) or 9094 (TLS) as per MSK setup.

---

## 7. Kubernetes Workloads

### 7.1 Two Deployment Shapes

1. **API deployment**
   - Image: `notification-service` (same codebase, profile `api` or default that enables HTTP and Kafka producer).
   - Replicas: 2 (min) in prod; HPA on CPU (e.g. target 70%) and/or RPS (custom metric from ALB).
   - Requests/limits: e.g. 500m CPU, 1Gi memory; adjust from real usage.
   - Env: `SPRING_PROFILES_ACTIVE=...`, `notification.queue.enabled=true`, DB URL, Redis URL, Kafka bootstrap (MSK), etc.
   - Probes: `readinessProbe`/`livenessProbe` on `/actuator/health`.

2. **Consumer deployment**
   - Same image, profile `consumer` (or single profile with listener enabled).
   - Replicas: 2–6; scale with **Kafka lag** (e.g. KEDA `KafkaTrigger` or custom HPA from consumer lag metric).
   - Same data plane env vars; no need to expose HTTP if you run only the consumer process (or same app with listener).

If you run API and consumer in the same process (single Deployment), scale horizontally and ensure `ConcurrentKafkaListenerContainerFactory` concurrency is set (e.g. 3–6) so one pod can process multiple partitions.

### 7.2 Internal Ingress (API)

- **Ingress** with annotation `alb.ingress.kubernetes.io/scheme: internal`.
- **Host**: e.g. `notification-api.internal.<domain>` or internal ALB DNS.
- **TLS**: Optional; use ACM with internal cert or leave HTTP inside VPC.
- **Target**: Service for API deployment (port 8080).

### 7.3 Autoscaling and Resilience

- **HPA (API)**: min 2, max 8, target CPU 70% (and custom metric if available).
- **HPA or KEDA (Consumer)**: scale on consumer lag (e.g. 1000 messages per consumer); min 2, max 10.
- **PDB**: minAvailable 1 for API and consumer (so rolling updates don’t take all down).
- **Topology spread**: spread pods across AZs (e.g. `topologySpreadConstraints` by zone).

---

## 8. Configuration and Secrets

- **Config**: ConfigMaps for non-sensitive (e.g. topic name, consumer group, feature flags). Environment-specific values (per env) in Helm/Kustomize.
- **Secrets**:
  - **AWS Secrets Manager** (or SSM Parameter Store): RDS credentials, Redis auth (if enabled), Kafka credentials (if not using IAM), Twilio/FCM/WhatsApp keys.
  - **External Secrets Operator** or **AWS Secrets Store CSI** to sync into K8s Secrets; pods mount or use env from there.
- **IRSA**: Separate IAM roles for API and consumer service accounts:
  - Read secrets (Secrets Manager).
  - Produce/consume MSK (if using IAM: `kafka-cluster:Connect`, `kafka-cluster:DescribeTopic`, `kafka-cluster:ReadData`, `kafka-cluster:WriteData` on the cluster + topic).
  - Optional: S3 for templates; CloudWatch Logs (if not using node role).

---

## 9. Observability

- **Logs**: Fluent Bit → CloudWatch Logs (log groups per env); optional OpenSearch for search.
- **Metrics**: Metrics Server + Amazon Managed Prometheus (or self-hosted Prometheus); scrape API and consumer pods.
- **Alerts**: CloudWatch Alarms (or Prometheus Alertmanager):
  - ALB 5xx, latency p99.
  - Consumer lag (MSK metric or exporter); RDS CPU, connections; Redis memory.
  - Pod restarts, OOMKilled.
- **Tracing**: Optional; AWS Distro for OpenTelemetry → X-Ray for API and consumer.

---

## 10. Capacity Summary (Prod, ballpark)

| Component | Sizing |
|-----------|--------|
| EKS nodes | 3–6 × m5.large (or Karpenter-managed mix); scale with HPA/KEDA |
| API pods | 2–4 (2000 RPS ≈ 500–1000 RPS per pod) |
| Consumer pods | 2–6 (scale on lag; 10M/day ≈ 115 msg/s average, bursts higher) |
| RDS | db.r6g.large Multi-AZ |
| Redis | cache.r6g.large, 1 primary + 1 replica |
| MSK | 3 × kafka.m5.large, 100–500 GiB per broker |

---

## 11. Terraform / IaC Module Layout

Suggested structure (Terraform):

```
infra/
├── env/
│   ├── dev/
│   │   └── main.tfvars
│   ├── staging/
│   │   └── main.tfvars
│   └── prod/
│       └── main.tfvars
├── modules/
│   ├── network/          # VPC, subnets, NAT, VPC endpoints
│   ├── eks/              # Cluster, node groups, add-ons (ALB controller, etc.)
│   ├── rds/              # RDS Postgres
│   ├── elasticache/      # Redis
│   ├── msk/              # MSK cluster, topic (optional: use provider or CLI)
│   ├── irsa/             # IAM roles for API and consumer
│   └── app/              # Namespace, External Secrets, Helm release (API + Consumer)
└── main.tf               # Calls modules per env (e.g. workspace or directory)
```

Apply order: `network` → `eks` → `rds`, `elasticache`, `msk` → `irsa` → `app`.

---

## 12. Application Config Snippets (reference)

- **Datasource**: Switch from H2 to Postgres; set `SPRING_DATASOURCE_URL`, `USERNAME`, `PASSWORD` from secrets.
- **Redis**: `spring.data.redis.host`, `port`, `password` (if any) from secrets.
- **Kafka**: `notification.kafka.bootstrap-servers` = MSK bootstrap string (IAM auth); enable `notification.queue.enabled=true`.
- **MSK IAM**: Use AWS SDK + IAM auth in Spring; or use `aws-msk-iam-auth` and SASL mechanism.

This plan gives you a concrete path to run the notification service on EKS with internal API, Postgres, Redis, and Kafka, sized for 2000 RPS and 1–10M events/day across separate environments.
