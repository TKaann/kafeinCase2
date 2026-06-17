# Order Service — E-Ticaret Sipariş İşleme Servisi

Küçük bir e-ticaret sisteminin sipariş işleme servisi. Odak: **doğru eşzamanlılık (race
condition yönetimi)**, **atomik transaction yönetimi** ve **yerinde design pattern kullanımı**.

## Teknoloji

| | |
|---|---|
| Dil | Java 17 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation) |
| Veritabanı | H2 (in-memory) |
| Build | Maven |
| Test | JUnit 5, Mockito, Spring Boot Test, MockMvc |

## Çalıştırma (tek komut)

```bash
mvn spring-boot:run
```

Uygulama `http://localhost:8080` üzerinde ayağa kalkar ve açılışta **8 örnek ürünle** dolu gelir
(bkz. `DataInitializer`). H2 konsolu: `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:orderdb`, kullanıcı `sa`, şifre boş).

## Test (tek komut)

```bash
mvn test
```

39 test koşar (birim + integration + eşzamanlılık + MockMvc). Tümü deterministiktir; race
condition testleri `CountDownLatch` ile kurgulanmıştır, `Thread.sleep` veya log'a bağımlılık
yoktur.

> **Windows / ASCII-dışı klasör yolu notu.** Yukarıdaki iki komut normal bir klonda olduğu gibi
> çalışır. Ancak proje yolu ASCII-dışı karakter içeriyorsa (ör. Türkçe karakterli
> `...\Masaüstü\...`), `mvn spring-boot:run` forklanan JVM'in classpath argfile'ı bozulduğu için
> başlatılamaz ("Could not find or load main class"). Çalışan alternatif — paketleyip jar'ı
> doğrudan çalıştır:
> ```bash
> mvn clean package -DskipTests
> java -jar target/order-service-0.0.1-SNAPSHOT.jar
> ```
> Surefire de forklandığı için testleri fork'suz çalıştır:
> ```bash
> mvn test -DforkCount=0
> ```

## Docker ile çalıştırma

Maven kurmaya gerek yok; tek komutla:

```bash
docker compose up --build
```

Servis `http://localhost:8080` üzerinde ayağa kalkar. `Dockerfile` multi-stage'tir: build
aşamasında Maven ile paketlenir, runtime aşamasında yalnızca slim bir JRE imajı çalışır (uygulama
imajda root olmayan kullanıcıyla koşar). H2 in-memory olduğundan ayrı bir veritabanı servisi
yoktur. Hızlı sağlık kontrolü:

```bash
curl http://localhost:8080/api/products
```

H2 konsolu (`/h2-console`) Docker'da da çalışır: container içindeki H2, host tarayıcısını "remote"
sayacağından `docker-compose.yml` yalnızca container'da `SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true`
verir; yereldeki güvenli varsayılan (`false`) değişmez.

> Maven yerelde kuruluysa Docker'a gerek yoktur; yukarıdaki `mvn spring-boot:run` / `mvn test`
> komutları doğrudan çalışır.

## Test paneli (web harness)

Uygulama ayağa kalktıktan sonra tarayıcıdan **`http://localhost:8080/`** açıldığında basit bir test
paneli gelir (Spring tarafından `static/` altından same-origin servis edilir — ayrı sunucu/CORS
yok). Panelde: ürün-stok listesi (+ yenile), stok ayarlama kontrolü, manuel sipariş formu ve
otomatik testlerimizin **canlı karşılığı olan tek-tık senaryo butonları** (happy path, yetersiz
stok rollback, çok-kalemli rollback, ödeme reddi, 500 eşzamanlı sipariş, deadlock-freedom, paralel
bulk, hata matrisi). Her senaryo gerçek API çağrıları yapar ve sonucu/doğrulamasını çıktı
panelinde gösterir. Vanilla JS + `fetch`; framework/build adımı yoktur.

---

## API

Tüm uçlar JSON döner. Base path `/api`.

| Method | Path | Açıklama |
|---|---|---|
| `GET` | `/api/products` | Ürünleri (stok + fiyat) listele |
| `GET` | `/api/products/{id}` | Tek ürün detayı |
| `PATCH` | `/api/products/{id}/stock` | Stoğu mutlak değere ayarla (test-harness reset'i) |
| `POST` | `/api/orders` | Tek sipariş oluştur |
| `POST` | `/api/orders/bulk` | Birden fazla siparişi **paralel** işle |
| `GET` | `/api/orders/{id}` | Sipariş detayı |

### Tek sipariş — `POST /api/orders`

İstek:
```json
{
  "customerId": 42,
  "paymentMethod": "CREDIT_CARD",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

Yanıt `201 Created`:
```json
{
  "id": 1,
  "customerId": 42,
  "status": "CONFIRMED",
  "totalAmount": 3029.88,
  "paymentMethod": "CREDIT_CARD",
  "paymentStatus": "COMPLETED",
  "transactionReference": "CC-6d86fafd-5d39-4ead-b233-2e2d4a8ac3fb",
  "createdAt": "2026-06-15T23:55:48.6",
  "items": [
    { "productId": 1, "productName": "Laptop Pro 14", "quantity": 2, "unitPrice": 1499.99, "subtotal": 2999.98 },
    { "productId": 2, "productName": "Wireless Mouse", "quantity": 1, "unitPrice": 29.90, "subtotal": 29.90 }
  ]
}
```

`paymentMethod` değerleri: `CREDIT_CARD`, `BANK_TRANSFER`, `CRYPTO`.

### Paralel toplu sipariş — `POST /api/orders/bulk`

İstek:
```json
{
  "orders": [
    { "customerId": 1, "paymentMethod": "CREDIT_CARD", "items": [{ "productId": 8, "quantity": 4 }] },
    { "customerId": 2, "paymentMethod": "CRYPTO",      "items": [{ "productId": 8, "quantity": 100 }] }
  ]
}
```

Yanıt `200 OK` — her sipariş için bağımsız sonuç (biri başarısız olsa da diğeri etkilenmez):
```json
{
  "total": 2,
  "succeeded": 1,
  "failed": 1,
  "results": [
    { "index": 0, "status": "SUCCESS", "orderId": 1, "error": null },
    { "index": 1, "status": "FAILED", "orderId": null,
      "error": "Insufficient stock for product 'Limited Edition Pen' (id=8): available=6, requested=100" }
  ]
}
```

### Hata modeli

Tüm hatalar tek tip `ApiError` gövdesiyle döner:
```json
{
  "timestamp": "2026-06-15T23:56:07.09",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product 'Limited Edition Pen' (id=8): available=10, requested=100",
  "path": "/api/orders"
}
```

| Durum | HTTP |
|---|---|
| Başarı | `200` / `201` |
| Validation hatası, bozuk JSON, geçersiz enum, tip uyuşmazlığı | `400` |
| Ürün/sipariş bulunamadı | `404` |
| Yetersiz stok | `409` |
| Ödeme hatası | `422` |
| Beklenmeyen hata | `500` |

---

## Transaction / rollback senaryosunu test etme

Tek sipariş akışı (sipariş + stok düşürme + ödeme) **tek transaction**'dır; herhangi bir adım
başarısız olursa hiçbir değişiklik kalıcı olmaz.

**Otomatik testler:**
- `OrderServiceRollbackTest` — ödeme patlayınca stok geri alınıyor ve sipariş kaydedilmiyor mu?
- `MultiItemOrderTest` — iki ürünlü siparişte ikinci kalem stoksuzsa, ilk ürünün stoğu dahil her
  şey geri sarılıyor mu?

**Elle (curl) doğrulama** — stoğu bol bir ürün + stoğu aşan bir ürün aynı siparişte:
```bash
# Önce stok: GET /api/products  (ör. Mouse id=2 stok=200, Pen id=8 stok=10)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":7,"paymentMethod":"CRYPTO","items":[{"productId":2,"quantity":5},{"productId":8,"quantity":100}]}'
# -> 409 Conflict; ardından GET /api/products ile Mouse stoğunun HÂLÂ 200 olduğunu doğrulayın.
```

**Elle doğrulama (Windows PowerShell):** PowerShell'de `curl`, `Invoke-WebRequest`'in takma adıdır
(`-X` gibi bayrakları tanımaz) ve `Invoke-RestMethod` hata gövdesini güvenilir göstermez. Bu yüzden
`curl.exe` kullanın ve JSON'u bir dosyaya yazın:
```powershell
# JSON'u dosyaya yaz (tırnak derdi olmasın)
'{"customerId":7,"paymentMethod":"CRYPTO","items":[{"productId":2,"quantity":5},{"productId":8,"quantity":100}]}' | Out-File -Encoding ascii body.json

# -i = durum satırı (409) + gövde birlikte
curl.exe -s -i -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" --data "@body.json"

# rollback kanıtı: stok hâlâ 200 olmalı
(Invoke-RestMethod http://localhost:8080/api/products/2).stockQuantity
```

**Otomatik testleri terminalden koşturma** (yukarıda adı geçen iki test, tek komut — PowerShell'de
virgülün tanınması için `-Dtest` tırnak içinde):
```powershell
mvn test -DforkCount=0 "-Dtest=OrderServiceRollbackTest,MultiItemOrderTest"
```

## Eşzamanlılık (race condition) senaryosunu test etme

`OrderConcurrencyTest`: stok=10 olan bir ürüne `CountDownLatch` ile 50 eşzamanlı istek atılır.
Sonuç her koşuda aynıdır: tam **10 sipariş başarılı**, 40'ı "yetersiz stok", stok **0** ve asla
negatif değil. `OrderedLockingConcurrencyTest` ise ürünleri ters sırada isteyen iki grup thread'le
deadlock olmadığını doğrular.

---

## Yapılandırma

`application.yml` üzerinden (ortam değişkeni/CLI ile override edilebilir):

| Property | Varsayılan | Açıklama |
|---|---|---|
| `app.payment.credit-card-limit` | `10000` | Tek bir kredi kartı ödemesinin üst sınırı; aşılırsa ödeme reddedilir (→ 422 + rollback). Banka havalesi ve kripto limitsizdir. |
| `app.cors.allowed-origins` | `localhost:5173,3000` | Dış (farklı origin) frontend için izinli origin'ler. Dahili test paneli same-origin olduğu için CORS gerektirmez. |

## Tasarım kararları

### Eşzamanlılık: Pessimistic Locking
Stok tutarlılığı için `ProductRepository.findByIdForUpdate` ile **pessimistic write lock**
(`SELECT ... FOR UPDATE`) kullanıldı. Değerlendirme senaryosu tek bir ürüne çok sayıda eşzamanlı
istek atıyor (tek "hot row"); bu durumda pessimistic lock istekleri DB seviyesinde temiz biçimde
serialize eder, stok bittiğinde fazlalar **doğru iş hatası** (`InsufficientStockException`) alır ve
test deterministik olur. Çok-kalemli siparişlerde deadlock'u önlemek için ürünler **artan id
sırasıyla** kilitlenir. `@Version` (optimistic) ucuz bir ikincil savunma olarak Product'ta korunur.

**Neden optimistic + retry değil?** Tek hot row'da optimistic locking retry storm üretir;
`OptimisticLockException` (bug gibi görünen bir lock hatası) ve retry tükenmesi riski doğar — yani
tam da test edilen noktada en kırılgan yaklaşım. (Detay: AI kullanımı bölümü.)

**Neden atomik UPDATE değil?** `UPDATE ... WHERE stock >= :q` en performanslısı olurdu ama JPA
persistence context'ini bypass eder (bellekteki entity bayatlar, `@Version` JPA üzerinden artmaz)
ve invariant'ı domain modelinden SQL'e taşır. Rich domain model + transaction/locking'i gösterme
amacıyla pessimistic lock daha uygun. Atomik UPDATE, çok sayıda farklı ürünlü yüksek-throughput bir
production envanter servisinde tercih edilirdi.

### Transaction sınırı
`OrderService.createOrder` tek `@Transactional`'dır: stok düşürme + sipariş/kalem kaydı + ödeme
hep birlikte. Tüm custom exception'lar `RuntimeException` türevidir; böylece default rollback
davranışı geçerli olur (`rollbackFor` unutma tuzağına düşülmez).

### Paralel işlem ve hata izolasyonu
`BulkOrderService`, `OrderService`'ten **ayrı bir bean**'dir; `createOrder`'ı Spring proxy
üzerinden çağırır (aynı sınıftan çağrı `@Transactional` proxy'sini bypass ederdi). Toplu metot
bilinçli olarak `@Transactional` **değildir**: transaction'lar thread-bound olduğundan her
`CompletableFuture` görevi kendi transaction'ını executor thread'inde açar/commit'ler — izolasyon
buradan gelir. `ForkJoinPool.commonPool()` yerine sınırlı (bounded) bir `ThreadPoolTaskExecutor`
kullanılır (`CallerRunsPolicy` ile yük altında sipariş düşmez).

### Design Pattern: Strategy + Registry (ödeme)
Her ödeme yöntemi bir `PaymentStrategy` implementasyonudur; Spring hepsini toplayıp
`PaymentStrategyRegistry`'ye (bir `EnumMap`) yazar. Yeni bir ödeme yöntemi eklemek = yeni bir
`@Component` eklemek; mevcut kod değişmez (**Open/Closed Principle**). `PaymentRegistryCompletenessTest`
her enum değerinin bir strategy'e çözüldüğünü garanti eder.

### Bağımlılık sadeliği
Pessimistic lock kararı kesinleşince `spring-retry` ve `spring-boot-starter-aop` **kaldırıldı**:
kodda `@Retryable` yok ve proxy-tabanlı `@Transactional` AOP starter'ına ihtiyaç duymaz (çekirdek
`spring-aop` zaten `spring-context` ile gelir). `OrderServiceRollbackTest`, kaldırma sonrası
transaction rollback'in çalışmaya devam ettiğini kanıtlar.

### Domain modeli
İş kuralları entity'lerde yaşar (anemic model değil): `Product.decreaseStock()` "stok negatif
olamaz" invariant'ını korur, `OrderItem.of()` sipariş anındaki fiyatı snapshot'layıp subtotal'ı
hesaplar, `Order` toplam hesabı ve durum geçişlerini yönetir. Para her yerde `BigDecimal`'dir.

---

## Test stratejisi

| Kapsam | Test |
|---|---|
| Stok yarışı (no oversell) | `OrderConcurrencyTest` |
| Deadlock-freedom (sıralı kilit) | `OrderedLockingConcurrencyTest` |
| Paralel hata izolasyonu | `BulkOrderIsolationTest` |
| Transaction rollback (ödeme) | `OrderServiceRollbackTest` |
| Çok-kalemli atomiklik (happy + stok rollback) | `MultiItemOrderTest` |
| HTTP hata matrisi | `OrderApiRegressionTest` (`@WebMvcTest`) |
| Domain invariant'ları | `ProductTest`, `OrderItemTest` |
| Ödeme alt sistemi | `PaymentStrategyTest`, `PaymentStrategyRegistryTest`, `PaymentServiceTest`, `PaymentRegistryCompletenessTest` |
| Mapper'lar | `OrderMapperTest`, `ProductMapperTest` |

Birim testler context'siz (saf JUnit/Mockito) ve hızlıdır; yalnızca gerçek transaction/eşzamanlılık
gerektirenler `@SpringBootTest` kullanır.

---

## AI Kullanımı ve Şeffaflık

Bu proje AI araçları (Claude) ile geliştirildi. Amaç AI çıktısını körü körüne almak değil, **her
kararı sorgulayıp gerekçelendirmekti**. Kararların ayrıntılı gerekçeleri `AI_NOTES.md` dosyasında;
tam sohbet geçmişi teslimata eklenmiştir.

AI'nın önerisini **reddedip değiştirdiğimiz** en belirgin örnek:

- **Locking stratejisi (optimistic → pessimistic).** İlk analiz ve ilk iskelet, stok için
  **optimistic locking + Spring Retry** öneriyordu. Bunu değerlendirme senaryosuna karşı
  sorguladık: tek ürüne yüzlerce eşzamanlı istek geldiğinde optimistic yaklaşım retry storm ve
  false-failure üretirdi. **Pessimistic lock + artan-id sıralı kilitleme**ye geçtik; bu sayede
  retry mekanizmasına hiç gerek kalmadı ve buna bağlı olarak `spring-retry` / `spring-boot-starter-aop`
  bağımlılıklarını da kaldırdık.

Diğer eleştirel düzeltmeler:
- İlk plan junior seviyede over-engineering içeriyordu (event-driven notification, MapStruct,
  HTTP 207). Bildirimi basit `log` ile, mapping'i elle, toplu yanıtı `200 + per-item sonuç` ile
  sade tuttuk (YAGNI/KISS).
- AI'nın atladığı `@Retryable` + `@Transactional` proxy-sıralama tuzağını pessimistic karar
  sayesinde tamamen elimine ettik.
- Entity'lerin anemic kaldığı yerleri tespit edip iş kurallarını domain modeline taşıdık.
