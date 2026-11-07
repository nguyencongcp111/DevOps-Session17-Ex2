# StoreX

Backend giả lập gồm hai service Spring Boot dùng chung một PostgreSQL, gửi log JSON qua TCP tới Logstash, rồi vào Elasticsearch và Kibana.

```
user-service  (8081, userdb)  --\
                                 +--TCP:5000 (JSON)--> Logstash --> Elasticsearch --> Kibana (5601)
order-service (8082, orderdb) --/
   |
   +-- REST --> user-service  (kiểm tra user trước khi tạo đơn)
```

| Thành phần | Port trên host |
|---|---|
| user-service | 127.0.0.1:8081 |
| order-service | 127.0.0.1:8082 |
| PostgreSQL | 127.0.0.1:5434 |
| Logstash (tcp input) | 127.0.0.1:5000 |
| Elasticsearch | 127.0.0.1:9200 |
| Kibana | 127.0.0.1:5601 |

## Chạy

Cần khoảng 3-4GB RAM trống cho cả stack. Nếu stack cũ (order-app, EFK) còn chạy thì tắt trước, vì trùng port 9200 và 5601:

```bash
cd ~/3Nov26 && docker compose down      # stack cũ (không dùng -v để giữ dữ liệu)
cd ~/storex
docker compose up -d --build
docker compose ps
```

## Thử bằng curl

```bash
# user-service (đã seed sẵn 2 user: id 1 và 2)
curl -s localhost:8081/api/users
curl -s -X POST localhost:8081/api/users -H 'Content-Type: application/json' \
  -d '{"fullName":"Le Van C","email":"c@storex.local"}'

# order-service (gọi sang user-service để kiểm tra user)
curl -s -X POST localhost:8082/api/orders -H 'Content-Type: application/json' \
  -d '{"userId":1,"productName":"Ban phim co","quantity":2,"amount":2400000}'
curl -s localhost:8082/api/orders

# Tạo log WARN: user không tồn tại (404)
curl -s -X POST localhost:8082/api/orders -H 'Content-Type: application/json' \
  -d '{"userId":999,"productName":"Chuot","quantity":1,"amount":350000}'

# Tạo log ERROR có stack trace
curl -s localhost:8082/api/debug/error
docker compose stop user-service      # rồi tạo đơn: order-service báo 503 + log ERROR kèm stack trace
docker compose start user-service
```

## Gửi thử một dòng JSON thẳng vào Logstash

```bash
echo '{"message":"hello from nc","level":"INFO","app_name":"manual-test"}' | nc localhost 5000
curl -s 'localhost:9200/_cat/indices?v'
```

## Kiểm tra dữ liệu trong Elasticsearch

```bash
curl -s 'localhost:9200/_search?size=1&pretty'
```

## Ghi chú

- `logstash/pipeline/logstash.conf` hiện đang **cố tình sai** (xem bài tập): input `tcp` chưa khai báo codec.
- Sau khi sửa `logstash.conf`: `docker compose restart logstash`.
- Hai service gửi log bằng `LogstashTcpSocketAppender` (thư viện `logstash-logback-encoder`), mỗi dòng log là một JSON object kết thúc bằng ký tự xuống dòng. Mỗi dòng đã có sẵn `app_name` (`user-service` / `order-service`) và `env`.
- Elasticsearch khóa index khi ổ đĩa đầy trên 95%. Kiểm tra `df -h /` nếu Logstash ghi không được.
