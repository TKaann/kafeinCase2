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

To keep the invariant un-bypassable, entities expose **no blanket `@Setter`** — stock changes only
through `decreaseStock`/`changeStockTo`, and the bidirectional back-references use a
package-private `setOrder` so only the `Order` aggregate can wire them. (Hibernate uses field
access, so dropping setters doesn't affect loading.)

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

### 11. Pre-submission cleanup
Removed dead code to keep the model lean: the unused `Order` methods `markFailed()`, `isPaid()`,
`removeItem()`, and the never-produced enum values (`OrderStatus.FAILED/CANCELLED`,
`PaymentStatus.PENDING/FAILED/REFUNDED`) — leaving only what the flow actually emits
(`OrderStatus{PENDING,CONFIRMED}`, `PaymentStatus{COMPLETED}`). Failed orders roll back rather than
persist, so failure states were genuinely unreachable.

## Local environment note (not a project decision)
On the dev machine the project path contains a non-ASCII character (`...\Masaüstü\...`), which
breaks the forked JVM used by `mvn spring-boot:run` and Surefire ("Could not find or load main
class"). Locally we run the app via `java -cp` directly and tests with `mvn test -DforkCount=0`.
On a normal path the documented single commands (`mvn spring-boot:run`, `mvn test`) work as-is.

---

# Türkçe Çeviri — AI İşbirliği & Karar Notları

> README'nin "AI şeffaflık" bölümü için çalışma notları. Hangi AI önerisine güvendiğimizi,
> hangisini sorguladığımızı ve temel mühendislik kararlarının gerekçelerini kaydeder.

## AI ile nasıl çalıştık
- İlk analiz (`case_analysis.md`) ve Faz 0 / Faz 1 iskeleti bir AI oturumunda üretildi, sonra
  devam etmeden önce ikinci bir oturumda eleştirel olarak gözden geçirildi.
- AI çıktısını **körü körüne kabul etmedik** — aşağıdaki kararların birçoğu inceleme sonrası
  değiştirildi.

## Temel kararlar

### 1. Locking stratejisi: optimistic+retry değil, pessimistic *(önceki AI kararı değiştirildi)*
İlk analiz **optimistic locking (`@Version`) + Spring Retry** öneriyordu. İncelemede bunu *birincil*
mekanizma olarak reddettik: değerlendirme, tek bir ürüne çok sayıda eşzamanlı istek atıyor (tek hot
row). Orada optimistic+retry retry storm üretir, `OptimisticLockException` (bug gibi görünen bir
lock hatası) ortaya çıkarır ve retry-tükenmesi kaynaklı false-failure riski taşır.

**Pessimistic write lock**'a geçtik (`ProductRepository.findByIdForUpdate` üzerinde
`@Lock(PESSIMISTIC_WRITE)`, `SELECT ... FOR UPDATE`):
- Aynı ürüne gelen eşzamanlı siparişler DB satır kilidinde temiz biçimde serialize olur.
- Fazla istekler lock hatası değil, doğru bir **iş hatası** (`InsufficientStockException`) alır.
- Eşzamanlılık testi deterministiktir (flaky değil).
- Çok-kalemli siparişlerde deadlock'u önlemek için ürünler **artan id sırasıyla** kilitlenir.

`@Version`, Product'ta ucuz bir ikincil savunma olarak kalır. Pessimistic-lock kararı kesinleşince
`spring-retry` ve `spring-boot-starter-aop` build'den **kaldırıldı**: kodda hiçbir `@Retryable` yok
ve proxy-tabanlı `@Transactional` AOP starter'ına ihtiyaç duymaz (çekirdek `spring-aop` zaten
`spring-context` ile gelir). `OrderServiceRollbackTest`, kaldırma sonrası transaction'ların hâlâ
doğru rollback yaptığını doğrular. Gerekçelendiremediğin kullanılmayan bir bağımlılık taşımak
YAGNI/mülakat açısından zayıflıktır.

### 2. Neden atomik koşullu UPDATE değil?
`UPDATE products SET stock = stock - :q WHERE id = :id AND stock >= :q` en performanslı/sağlam
seçenek olurdu ama JPA persistence context'ini bypass eder (bellekteki entity bayatlar, `@Version`
JPA üzerinden artmaz) ve invariant'ı domain modelinden SQL'e geri iter. Tasarımın geri kalanı zengin
bir JPA domain modeli olduğu ve amaç transaction/locking muhakemesini göstermek olduğu için
pessimistic locking daha uygun. Atomik UPDATE, çok sayıda farklı ürünlü, yüksek-throughput'lu bir
production envanter servisinde doğru tercih olurdu.

### 3. Anemic entity yerine zengin domain modeli
Invariant'lar entity'lerde yaşar: `Product.decreaseStock()` "stok negatif olamaz"ı korur,
`OrderItem.of()` birim fiyatı snapshot'layıp subtotal'ı hesaplar, `Order` toplam hesabını ve durum
geçişlerini sahiplenir. Servisler orkestrasyon yapar; iş kuralı tutmaz.

Invariant'ın bypass edilememesi için entity'ler **toptan `@Setter` açmaz** — stok yalnızca
`decreaseStock`/`changeStockTo` üzerinden değişir, bidirectional geri-referanslar paket-özel
`setOrder` kullanır; böylece yalnızca `Order` aggregate'i bağlayabilir. (Hibernate alan erişimi
kullandığı için setter'ları kaldırmak yüklemeyi etkilemez.)

### 4. Transaction sınırı
`OrderService.createOrder` tek bir `@Transactional`'dır: stok düşürme + sipariş/kalemler + ödeme.
Herhangi bir hata (yetersiz stok, başarısız ödeme) her şeyi geri sarar — yarım sipariş, değişmiş
stok yok. Tüm custom exception'lar unchecked'tir; böylece default rollback geçerli olur
(`rollbackFor` tuzağı yok).

### 5. Paralel işleme & hata izolasyonu
- `BulkOrderService`, `OrderService`'ten **ayrı bir bean**'dir; `createOrder`'ı Spring proxy'si
  üzerinden çağırır (self-invocation `@Transactional` proxy'sini bypass ederdi).
- `processBulkOrders` bilinçli olarak `@Transactional` **değildir**: transaction'lar thread-bound
  olduğundan her `CompletableFuture` görevi kendi transaction'ını executor thread'inde
  açar/commit'ler — per-order izolasyon buradan gelir. Bir siparişin rollback'i diğerini etkilemez.
- `ForkJoinPool.commonPool()` yerine özel, sınırlı bir `ThreadPoolTaskExecutor` (`CallerRunsPolicy`
  ile) kullanılır; bloklayan JDBC işiyle JVM-wide havuzu aç bırakmamak için.
- Her görev `createOrder`'ı try/catch içine alır ve per-order sonuç döner; bir hata batch'i
  durdurmaz.

### 6. Design pattern: ödeme için Strategy + Registry
Her yöntem için bir implementasyonla `PaymentStrategy`, Spring tarafından `PaymentStrategyRegistry`
(bir `EnumMap`) içinde otomatik toplanır. Yeni ödeme yöntemi = bir `@Component` eklemek; mevcut kod
değişmez (Open/Closed). Yöntemlerin davranışı tamamen bağımsız olduğu için Template Method yerine
seçildi.

### 7. Bulk yanıtı: HTTP 200 + per-item sonuçlar (207 Multi-Status değil)
Bulk çağrısının kendisi başarılıdır; tekil sipariş sonuçları yapısal bir gövdede taşınan veridir
(`total/succeeded/failed/results[]`). 207 Multi-Status değerlendirildi ama marjinal semantik kazanç
için client'ı şaşırtıcı bulunarak reddedildi.

### 8. API olgunluğu & CORS
- `@RestControllerAdvice` (`ApiError`) ile tutarlı hata sözleşmesi: yetersiz stok → 409, ödeme
  hatası → 422, bulunamadı → 404, validation / bozuk gövde / tip uyuşmazlığı → 400, beklenmeyen →
  500. Bozuk JSON ve bilinmeyen enum değerleri yakalanır (`HttpMessageNotReadableException`); böylece
  client her zaman temiz bir `ApiError` alır, ham stack trace değil.
- CORS, MVC katmanında (`WebConfig` + `CorsProperties`) **configurable origin'lerle**
  (`app.cors.allowed-origins`, dev varsayılanı `localhost:5173/3000`) açıktır. Bu, API'yi planlanan
  test-harness frontend'ine kod değişikliği olmadan hazırlar. Bunun için Spring Security
  **eklemedik** — düz MVC CORS yeterli (YAGNI); izinsiz bir origin'in preflight'ı doğru biçimde 403
  ile reddedilir.

### 9. Docker
Multi-stage `Dockerfile`: bir Maven aşaması jar'ı paketler (bağımlılık katmanı daha hızlı
yeniden-build için ayrı cache'lenir), slim bir `eclipse-temurin:17-jre` runtime aşaması onu
root-olmayan kullanıcıyla çalıştırır. `docker-compose.yml` 8080'i expose eder ve **ayrı bir DB
servisi eklemez** — H2 in-memory olduğundan ayrı bir DB container'ı ölü ağırlık olurdu. Testler imaj
build'i içinde değil, `mvn test` ile koşar (`-DskipTests`). Uçtan uca doğrulandı:
`docker compose up --build` container'ı ayağa kaldırır, 8 ürünü yükler ve siparişleri 8080'de servis
eder.

H2 konsolu varsayılan olarak "remote" client'lara kapalıdır; container'da host tarayıcısı remote
sayılır. Yerel config'i zayıflatmak yerine compose, `SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true`'yu
yalnızca container için ayarlar (relaxed-binding override); böylece `application.yml` yerelde güvenli
`false` varsayılanını korur.

### 10. Test harness (statik frontend) + destekleyici uçlar
`src/main/resources/static/` altındaki vanilla-JS panel 8080'de same-origin servis edilir (ayrı
sunucu yok, CORS yok). JUnit testlerini tek-tık senaryo butonları olarak yansıtır (happy path,
stok/ödeme rollback'leri, 500-sipariş eşzamanlılığı, deadlock-freedom, paralel bulk, hata matrisi).
İki küçük backend eklemesi bunu destekler: koşular arası stoğu sıfırlamak için
`PATCH /api/products/{id}/stock` ve configurable bir `app.payment.credit-card-limit` (varsayılan
10000) — limiti aşan kredi kartı siparişi `PaymentResult.failure` döndürerek mevcut 422 + rollback
akışını canlı tetikler (banka havalesi ve kripto limitsiz kalır, büyük/eşzamanlı testlerde kullanılır).
Çalışan Docker container'ına karşı gerçek paralel HTTP ile (`xargs -P`) uçtan uca doğrulandı: stok=10
ürüne 60 eşzamanlı sipariş → tam 10 × 201 + 50 × 409, son stok 0; 100 ters-sıralı eşzamanlı sipariş →
hepsi 201, deadlock yok.

### 11. Teslim öncesi temizlik
Modeli yalın tutmak için ölü kod kaldırıldı: kullanılmayan `Order` metotları `markFailed()`,
`isPaid()`, `removeItem()` ve hiç üretilmeyen enum değerleri (`OrderStatus.FAILED/CANCELLED`,
`PaymentStatus.PENDING/FAILED/REFUNDED`) — yalnızca akışın gerçekten ürettiği değerler bırakıldı
(`OrderStatus{PENDING,CONFIRMED}`, `PaymentStatus{COMPLETED}`). Başarısız siparişler persist
edilmeyip rollback olduğu için bu başarısızlık durumları gerçekten erişilemezdi.

## Yerel ortam notu (proje kararı değil)
Geliştirme makinesinde proje yolu ASCII-dışı bir karakter içeriyor (`...\Masaüstü\...`); bu,
`mvn spring-boot:run` ve Surefire'ın kullandığı forklanan JVM'i bozuyor ("Could not find or load
main class"). Yerelde uygulamayı doğrudan `java -cp` ile, testleri `mvn test -DforkCount=0` ile
çalıştırıyoruz. Normal bir yolda dokümante edilen tek komutlar (`mvn spring-boot:run`, `mvn test`)
olduğu gibi çalışır.
