# Bài tập: Cấu hình Logstash nhận log JSON qua TCP (codec json_lines)

**Ngày thực hiện:** 07/11/2026
**Dự án:** StoreX (Spring Boot 3.3, Java 17) gồm `user-service` và `order-service`, dùng chung PostgreSQL
**Môi trường chạy:** AWS EC2 (Ubuntu, instance c7i-flex.large), toàn bộ chạy bằng Docker Compose

---

## 1. Cấu hình gốc và vấn đề

### Tình huống

Backend StoreX dùng Logback gửi log qua TCP tới Logstash ở cổng `5000` bằng định dạng JSON. Tuy nhiên Logstash không parse được input: dữ liệu vào Elasticsearch ở dạng chuỗi thô thay vì các field có cấu trúc.

### Phía ứng dụng (Logback)

Mỗi service dùng `LogstashTcpSocketAppender` của thư viện `logstash-logback-encoder`. Mỗi dòng log là một JSON object kết thúc bằng ký tự xuống dòng:

```xml
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
    <destination>${LOGSTASH_DEST}</destination>
    <reconnectionDelay>5 seconds</reconnectionDelay>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"app_name":"${APP_NAME}","env":"${APP_ENV}"}</customFields>
    </encoder>
</appender>
```

### Cấu hình gốc của Logstash (`logstash.conf`)

```
input {
  tcp {
    port => 5000
    # Lỗi: định dạng gửi là JSON nhưng input dùng codec plain mặc định
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
  }
}
```

### Vấn đề

`tcp` input không khai báo codec nên dùng `plain` mặc định. Codec này chỉ coi mỗi dòng nhận được là một chuỗi văn bản, không hiểu JSON. Kết quả là cả dòng JSON bị nhét vào một field `message` duy nhất, và các field như `level`, `app_name`, `stack_trace` không tồn tại ở cấp ngoài. Kibana không thể lọc hay tìm kiếm theo các field đó.

---

## 2. Yêu cầu và cách thực hiện

### Yêu cầu

Sửa file cấu hình Logstash để nhận đúng codec `json_lines` cho cổng `5000`.

### Bước 1. Dựng hệ thống StoreX

`docker-compose.yml` gồm 6 service: `postgres` (hai database `userdb`, `orderdb`), `user-service` (8081), `order-service` (8082), `logstash`, `elasticsearch`, `kibana`. Logstash đọc pipeline từ thư mục `logstash/pipeline/`. `order-service` gọi sang `user-service` để kiểm tra user trước khi tạo đơn, nên một request tạo ra log ở nhiều mức (INFO, WARN, ERROR).

```bash
docker compose up -d --build
docker compose ps
```

### Bước 2. Tái hiện lỗi

Sinh log rồi xem document trong Elasticsearch (chỉ tìm trong index của Logstash):

```bash
curl -s 'localhost:9200/_cat/indices?v'
curl -s 'localhost:9200/logstash-*/_search?size=1&pretty&filter_path=hits.total,hits.hits._source'
```

Kết quả cho thấy toàn bộ JSON nằm trong field `message` dạng chuỗi (có dấu `\"` escape):

```json
{
  "message" : "{\"@timestamp\":\"2026-11-07T08:50:53.697553047Z\",\"@version\":\"1\",\"message\":\"Initializing Servlet 'dispatcherServlet'\",\"logger_name\":\"org.springframework.web.servlet.DispatcherServlet\",\"level\":\"INFO\",...,\"app_name\":\"order-service\",\"env\":\"dev\"}",
  "@version" : "1",
  "host" : "storex-order-service.storex_default",
  "@timestamp" : "2026-11-07T08:50:53.706Z",
  "port" : 58842
}
```

Ngoài `message`, chỉ có các field do Logstash tự thêm (`host`, `port`, `@version`, `@timestamp`).

### Bước 3. Sửa `logstash.conf`

Thêm `codec => json_lines` vào `tcp` input:

```
input {
  tcp {
    port => 5000
    codec => json_lines
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
  }
}
```

Áp dụng bằng cách khởi động lại Logstash (mất khoảng 30-60 giây):

```bash
docker compose restart logstash
docker compose logs logstash | grep -i "pipeline started\|Successfully started"
```

Hai service tự kết nối lại sau 5 giây nhờ `reconnectionDelay`. Log phát sinh trong lúc Logstash tắt có thể bị mất.

### Bước 4. Kiểm chứng

Sinh log mới (INFO, WARN, ERROR) rồi truy vấn các document đã được parse. Các document cũ vẫn còn trong index nên dùng `_exists_:level` để chỉ lấy document mới (chỉ document đã parse mới có field `level`):

```bash
curl -s -X POST localhost:8082/api/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"productName":"Ban phim co","quantity":2,"amount":2400000}'
curl -s -X POST localhost:8082/api/orders -H 'Content-Type: application/json' \
  -d '{"userId":999,"productName":"Chuot","quantity":1,"amount":350000}'
curl -s localhost:8082/api/debug/error
sleep 10

curl -s 'localhost:9200/logstash-*/_search?q=_exists_:level&size=1&sort=@timestamp:desc&pretty&filter_path=hits.total,hits.hits._source'
curl -s 'localhost:9200/logstash-*/_search?q=level:ERROR&size=1&sort=@timestamp:desc&pretty&filter_path=hits.total,hits.hits._source'
```

### Giải thích: vì sao dùng `json_lines`

TCP là luồng byte liên tục, không có ranh giới giữa các message. Logback gửi mỗi log là một JSON trên một dòng, kết thúc bằng `\n`. Codec `json_lines` dùng ký tự xuống dòng làm ranh giới để tách luồng thành từng dòng, rồi parse mỗi dòng thành một JSON event. Codec `json` thường không phù hợp với luồng TCP vì nó giả định mỗi lần nhận là một JSON trọn vẹn, nên dễ lỗi khi dữ liệu bị gộp hoặc cắt giữa chừng.

---

## 3. Kết quả

### Document sau khi sửa

Truy vấn `level:ERROR` trả về 1 document (tổng cộng 10 document có field `level` tại thời điểm kiểm tra). Dưới đây là kết quả đã rút gọn các frame của Spring và Tomcat:

```json
{
  "message" : "Payment processing failed for orderId=123",
  "thread_name" : "http-nio-8082-exec-9",
  "level" : "ERROR",
  "level_value" : 40000,
  "logger_name" : "com.storex.order.controller.DebugController",
  "app_name" : "order-service",
  "env" : "dev",
  "host" : "storex-order-service.storex_default",
  "port" : 35652,
  "@version" : "1",
  "@timestamp" : "2026-11-07T08:55:33.179Z",
  "stack_trace" : "java.lang.IllegalStateException: Invalid payment amount\n\tat com.storex.order.controller.DebugController.processPayment(DebugController.java:35)\n\tat com.storex.order.controller.DebugController.triggerError(DebugController.java:24)\n\t... (các frame của Spring MVC, Tomcat) ...\nCaused by: java.lang.NumberFormatException: For input string: \"abc\"\n\t... 51 common frames omitted\n"
}
```

### So sánh trước và sau

| | Trước (codec `plain`) | Sau (`json_lines`) |
|---|---|---|
| Field `message` | Cả dòng JSON dạng chuỗi thô | Chỉ nội dung log |
| Field `level`, `logger_name`, `thread_name` | Không có ở cấp ngoài | Có, là field riêng |
| Field `app_name`, `env` | Nằm trong chuỗi `message` | Là field riêng, lọc được |
| Exception | Chìm trong chuỗi | Field `stack_trace` riêng |
| Lọc trên Kibana | Không lọc được theo field | Lọc trực tiếp, ví dụ `level: "ERROR" and app_name: "order-service"` |

### Nhận xét

- Chỉ cần thêm một dòng `codec => json_lines` là Logstash parse thành công JSON và đẩy dữ liệu có cấu trúc sang Elasticsearch, đúng kết quả mong muốn của đề.
- Không cần sửa code ứng dụng hay Logback. Lỗi nằm hoàn toàn ở phía nhận (Logstash).
- Thư mục `logstash/pipeline` được mount cả thư mục (không mount từng file) nên sửa file rồi `docker compose restart logstash` là Logstash nhận cấu hình mới.
- Các document cũ (trước khi sửa) vẫn còn trong index với `message` dạng chuỗi thô. Chúng không tự được sửa lại, chỉ log mới mới có cấu trúc.

### Lưu ý và hướng mở rộng

- Trong document có các field thừa `APP_NAME`, `APP_ENV`, `LOGSTASH_DEST`. Nguyên nhân là `<springProperty scope="context">` đưa các biến vào context của Logback, và `LogstashEncoder` mặc định ghi cả context vào JSON. Khắc phục bằng cách thêm `<includeContext>false</includeContext>` vào encoder. Cách này tránh làm lộ cấu hình nội bộ (như địa chỉ Logstash) vào log.
- Index đang hiện `yellow` vì Elasticsearch mặc định tạo 1 replica, mà môi trường chỉ có một node. Không ảnh hưởng, nhưng có thể đặt `number_of_replicas: 0`.
- Tên index mặc định là `logstash-YYYY.MM.DD-000001` (do ILM). Có thể đặt tên rõ hơn bằng `index => "storex-%{+YYYY.MM.dd}"` trong `output`.
- Khi cần gỡ lỗi pipeline, thêm `stdout { codec => rubydebug }` vào `output` để xem trực tiếp event trong `docker compose logs logstash`.
- Có thể thêm `filter` để chuẩn hóa dữ liệu, ví dụ `date { match => ["@timestamp", "ISO8601"] }` hoặc xóa các field không cần.
