# Pay Computation Service

Spring Boot microservice that computes staff payroll (PAYE, pension, NHF, loans), persists report summaries/details to MongoDB, and exposes reports, dashboard, loans, metadata, and audit APIs.

## Stack

- Java 17, Spring Boot 3.2
- MongoDB, Redis
- OAuth2 Resource Server (JWT / Keycloak)
- WebSocket (STOMP) for async payroll job progress

## Key endpoints

| Area | Base path |
|------|-----------|
| Start payroll | `POST /compute/payroll/start` |
| Job status | `GET /compute/payroll/status/{jobId}` |
| Reports | `/compute/reports/**` |
| Loans | `/compute/loans/**` |
| Dashboard | `/compute/dashboard/**` |
| Metadata | `/compute/metadata/**` |
| Audit | `/compute/user-trail`, `/compute/audit-trail` |
| Health | `/actuator/health` |

## Security

- JWT required for API access (except health; Swagger only outside `prod`)
- Company isolation: request `companyId` must match JWT `CompanyID` when `xykine.security.enforce-company-access=true` (default)
- Do not commit secrets. Use K8s Secrets / GitHub Actions secrets. See `src/k8s/secret.yml.example` and `k8Reame.md`.

## Local run

```bash
# Requires MongoDB + Redis locally (or override via env)
mvn spring-boot:run
```

Config: `src/main/resources/application.yaml`

## Tests

```bash
mvn verify
```

Integration tests use Testcontainers (Mongo + Redis).

## Deploy

```bash
kubectl apply -f src/k8s/secret.yml   # from secret.yml.example, filled with real values
kubectl apply -f src/k8s/deployment.yml
kubectl apply -f src/k8s/service.yml
```

CI: `.github/workflows/deploy-image.yml` runs `mvn verify` then builds/publishes the image.
