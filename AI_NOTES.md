# AI Collaboration & Decision Notes

> Scratch notes for the README "AI transparency" section. Records which AI suggestions we
> trusted, which we challenged, and the reasoning behind the key engineering decisions.

## How we worked with AI
- An initial analysis (`case_analysis.md`) and the Faz 0 / Faz 1 scaffolding were produced with
  one AI session, then critically reviewed in a second session before continuing.
- We did **not** accept AI output blindly — several decisions below were changed after review.

## Key decisions

### 1. Locking strategy: pessimistic, not optimistic+retry  *(changed an earlier AI decision)*
The first analysis recommended **optimistic locking (`@Version`) + Spring Retry**. On review we
rejected it as the *primary* mechanism: the evaluation explicitly hammers a single product with
many concurrent requests (one hot row). There, optimistic+retry causes a retry storm and
surfaces `OptimisticLockException` (a lock error that looks like a bug) and risks
retry-exhaustion false failures.

We switched to a **pessimistic write lock** (`@Lock(PESSIMISTIC_WRITE)` on
`ProductRepository.findByIdForUpdate`, `SELECT ... FOR UPDATE`):
- Concurrent orders for the same product serialize cleanly on the DB row lock.
- Surplus requests get a correct **business** failure (`InsufficientStockException`), not a lock
  error.
- The concurrency test is deterministic (no flakiness).
- Products are locked in **ascending id order** to prevent deadlocks on multi-item orders.

`@Version` is kept on `Product` as a cheap secondary safety net. `spring-retry` and
`spring-boot-starter-aop` were **removed** from the build once the pessimistic-lock decision was
final: there is no `@Retryable` anywhere, and proxy-based `@Transactional` does not need the AOP
starter (core `spring-aop` comes via `spring-context`). The `OrderServiceRollbackTest` confirms
transactions still roll back correctly after the removal. Carrying an unused dependency you can't
justify is a YAGNI/interview liability.

### 2. Why not an atomic conditional UPDATE?
`UPDATE products SET stock = stock - :q WHERE id = :id AND stock >= :q` is the most
performant/bulletproof option, but it bypasses the JPA persistence context (the in-memory entity
goes stale, `@Version` isn't bumped through JPA) and pushes the invariant out of the domain model
back into SQL. Since the rest of the design is a rich JPA domain model and the goal is to
demonstrate transaction/locking reasoning, pessimistic locking fits better. The atomic UPDATE
would be the right call for a high-throughput, many-distinct-products production inventory service.

### 3. Rich domain model over anemic entities
Invariants live on the entities: `Product.decreaseStock()` enforces "stock never negative",
`OrderItem.of()` snapshots unit price and computes subtotal, `Order` owns total calculation and
status transitions. Services orchestrate; they don't hold business rules.

### 4. Transaction boundary
`OrderService.createOrder` is a single `@Transactional` covering stock decrement + order/items +
payment. Any failure (insufficient stock, failed payment) rolls everything back — no half order,
no changed stock. All custom exceptions are unchecked so default rollback applies (no
`rollbackFor` foot-gun).

### 5. Parallel processing & failure isolation
- `BulkOrderService` is a **separate bean** from `OrderService` so it calls `createOrder` through
  the Spring proxy (a self-invocation would bypass the `@Transactional` proxy).
- `processBulkOrders` is intentionally **not** `@Transactional`: transactions are thread-bound, so
  each `CompletableFuture` task opens/commits its own transaction on an executor thread — that is
  what gives per-order isolation. One order's rollback can't affect another.
- A dedicated bounded `ThreadPoolTaskExecutor` (with `CallerRunsPolicy`) is used instead of
  `ForkJoinPool.commonPool()` to avoid starving a JVM-wide pool with blocking JDBC work.
- Each task wraps `createOrder` in try/catch and returns a per-order result, so one failure never
  aborts the batch.

### 6. Design pattern: Strategy + Registry for payments
`PaymentStrategy` with one implementation per method, auto-collected into
`PaymentStrategyRegistry` (an `EnumMap`) by Spring. Adding a new payment method = adding one
`@Component`; no existing code changes (Open/Closed). Chosen over Template Method because the
methods' behavior is fully independent.

### 7. Bulk response: HTTP 200 + per-item results (not 207 Multi-Status)
The bulk call itself succeeds; individual order outcomes are data carried in a structured body
(`total/succeeded/failed/results[]`). 207 Multi-Status was considered but rejected as
client-surprising for marginal semantic gain.

### 8. API maturity & CORS
- Consistent error contract via `@RestControllerAdvice` (`ApiError`): insufficient stock → 409,
  payment failed → 422, not found → 404, validation / malformed body / type mismatch → 400,
  anything unexpected → 500. Malformed JSON and unknown enum values are caught
  (`HttpMessageNotReadableException`) so the client always gets a clean `ApiError`, never a raw
  stack trace.
- CORS is enabled at the MVC layer (`WebConfig` + `CorsProperties`) with **configurable origins**
  (`app.cors.allowed-origins`, dev defaults `localhost:5173/3000`). This prepares the API to be
  consumed by the planned test-harness frontend without code changes. We did NOT pull in Spring
  Security for this — plain MVC CORS is sufficient (YAGNI); a disallowed origin's preflight is
  correctly rejected with 403.

### 9. Docker
Multi-stage `Dockerfile`: a Maven stage packages the jar (dependency layer cached separately for
faster rebuilds), and a slim `eclipse-temurin:17-jre` runtime stage runs it as a non-root user.
`docker-compose.yml` exposes 8080 and adds **no database service** — H2 is in-memory, so a
separate DB container would be dead weight. Tests run via `mvn test`, not inside the image build
(`-DskipTests`). Verified end-to-end: `docker compose up --build` boots the container, seeds the
8 products, and serves orders on 8080.

The H2 console is blocked for "remote" clients by default; in a container the host browser counts
as remote. Rather than weakening the local config, compose sets
`SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true` only for the container (relaxed-binding override),
so `application.yml` keeps the secure `false` default locally.

### 10. Test harness (static frontend) + supporting endpoints
A vanilla-JS panel under `src/main/resources/static/` is served same-origin on 8080 (no separate
server, no CORS). It mirrors the JUnit tests as one-click scenario buttons (happy path, stock/payment
rollbacks, 500-order concurrency, deadlock-freedom, parallel bulk, error matrix). Two small backend
additions support it: `PATCH /api/products/{id}/stock` to reset stock between runs, and a
configurable `app.payment.credit-card-limit` (default 10000) so a credit-card order above the limit
returns `PaymentResult.failure` — driving the existing 422 + rollback flow live (bank transfer and
crypto stay limitless, used for the large/concurrent tests). Verified end-to-end against the running
Docker container with real parallel HTTP (`xargs -P`): 60 concurrent orders on stock=10 → exactly 10
× 201 + 50 × 409, final stock 0; 100 reverse-ordered concurrent orders → all 201, no deadlock.

## Local environment note (not a project decision)
On the dev machine the project path contains a non-ASCII character (`...\Masaüstü\...`), which
breaks the forked JVM used by `mvn spring-boot:run` and Surefire ("Could not find or load main
class"). Locally we run the app via `java -cp` directly and tests with `mvn test -DforkCount=0`.
On a normal path the documented single commands (`mvn spring-boot:run`, `mvn test`) work as-is.
