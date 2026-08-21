# Deploy Backend lên Production

Hướng dẫn đưa **PhotoTrip-API** lên production trên 1 máy chủ Ubuntu, nơi
MySQL đã chạy sẵn dạng Docker container trên **cùng máy**, kết nối qua IP
Tailscale, dùng **Nginx + Certbot** làm reverse proxy/HTTPS.

> Phần deploy frontend (`triptravel.thongtinchinhhieu.site`) chưa nằm trong
> tài liệu này — sẽ bổ sung sau.

## 0. Kiểm tra máy chủ đã sẵn sàng

```bash
# Docker + Compose plugin
docker --version
docker compose version

# Tailscale đang chạy và IP khớp với .env (DB_URL)
tailscale ip -4          # phải ra 100.121.122.102 (hoặc IP tailscale của máy này)

# MySQL container đang chạy và mở cổng 3306
docker ps | grep mysql
docker port <ten-container-mysql>
```

Nếu MySQL container publish cổng dạng `0.0.0.0:3306:3306` thì backend
container khác gọi được `100.121.122.102:3306` bình thường (traffic loop
qua interface Tailscale của chính máy). Nếu chỉ bind `127.0.0.1:3306:3306`
thì container backend **không** gọi được qua IP Tailscale — cần đổi sang
`0.0.0.0:3306:3306`, hoặc đổi `DB_URL` sang cách kết nối khác (network Docker
nội bộ / `host.docker.internal`).

## 1. Lấy code

```bash
git clone https://github.com/dautruongptit/PhotoTrip-API.git
cd PhotoTrip-API
# lần sau chỉ cần:
git pull origin main
```

## 2. Tạo `.env` thật từ `.env.production`

```bash
cp .env.production .env
nano .env
```

Bắt buộc kiểm tra/sửa trước khi chạy thật:

- `DB_PASSWORD` → khớp đúng mật khẩu MySQL container thật đang chạy.
  Nếu đổi mật khẩu MySQL, dùng:
  ```bash
  docker exec -it <ten-container-mysql> mysql -uroot -p
  ```
  ```sql
  ALTER USER 'root'@'%' IDENTIFIED BY '<password-moi>';
  FLUSH PRIVILEGES;
  ```
  (kiểm tra đúng host bằng `SELECT user, host FROM mysql.user;` nếu `root`
  không phải `@'%'`)
- `JWT_SECRET` → sinh bằng `openssl rand -base64 32`. Đổi giá trị này sẽ
  **logout toàn bộ user** đang đăng nhập (token cũ hết hiệu lực) — nên làm
  ngay từ lần deploy đầu tiên.
- `DEV_LOGIN_SECRET` → đổi giá trị khác, dù `DEV_LOGIN_ENABLED=false`
  (đã tắt tính năng dev-login-bypass, **tuyệt đối không bật `true`** ở
  production thật).
- `STORAGE_HOST_PATH` → đúng đường dẫn thật trên máy này để lưu ảnh.

Tạo thư mục lưu ảnh + phân quyền:

```bash
sudo mkdir -p /media/mos/Data1/DeployUbuntu/tripTravel/photo
sudo chown -R 100:100 /media/mos/Data1/DeployUbuntu/tripTravel/photo   # UID/GID user "app" trong container (xem Dockerfile)
```

## 3. Tạo Docker network dùng chung (nếu chưa có)

```bash
docker network inspect shared-network >/dev/null 2>&1 || docker network create shared-network
```

Không cần MySQL container nằm chung network này — backend gọi MySQL qua IP
Tailscale (TCP thường), không qua Docker DNS.

## 4. Build & chạy backend

```bash
docker compose up -d --build
docker compose logs -f backend
```

Theo dõi log tới khi Flyway migrate xong và Tomcat start ở port 8085, không
có exception.

## 5. Kiểm tra local trước khi public

```bash
curl -i http://127.0.0.1:8085/actuator/health
# kỳ vọng: HTTP/1.1 200, {"status":"UP"}
```

Nếu lỗi kết nối DB → kiểm tra lại bước 0 (bind MySQL) và `DB_PASSWORD`.

## 6. DNS

Vào nơi quản lý DNS của domain `thongtinchinhhieu.site`, thêm A record trỏ
về **IP public** của máy chủ này (không phải IP Tailscale — Tailscale chỉ
dùng nội bộ cho DB):

```
triptravel-api.thongtinchinhhieu.site  → <IP public server>
```

## 7. Cài Nginx + Certbot trên host

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
sudo ufw allow 'Nginx Full'   # mở 80/443 nếu dùng ufw
```

Tạo config cho domain API — `/etc/nginx/sites-available/triptravel-api`:

```nginx
server {
    listen 80;
    server_name triptravel-api.thongtinchinhhieu.site;

    location / {
        proxy_pass http://127.0.0.1:8085;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Kích hoạt site + lấy cert:

```bash
sudo ln -s /etc/nginx/sites-available/triptravel-api /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d triptravel-api.thongtinchinhhieu.site
```

Certbot tự sửa config thêm `listen 443 ssl` + redirect HTTP→HTTPS, và tự cài
cron renew.

## 8. Cập nhật Google OAuth Console

Vào Google Cloud Console → Credentials → OAuth Client
(`762204384061-n4ngsh81its82lno66tulvtpiu4esume`), thêm vào
**Authorized redirect URIs**:

```
https://triptravel-api.thongtinchinhhieu.site/login/oauth2/code/google
```

Thiếu URI này thì login Google sẽ báo `redirect_uri_mismatch`.

## 9. Test end-to-end

```bash
curl -i https://triptravel-api.thongtinchinhhieu.site/actuator/health
```

Rồi thử luồng login Google/JWT thật từ frontend.

## 10. Firewall — chỉ mở đúng cổng cần thiết

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'   # 80, 443
sudo ufw enable
# KHÔNG mở public cổng 8085 (chỉ nginx gọi localhost) và 3306 (chỉ qua Tailscale)
```

## 11. Cập nhật sau này

```bash
git pull origin main
docker compose up -d --build
docker compose logs -f backend
```

Vì `jpa.hibernate.ddl-auto: validate` (Flyway là nguồn schema duy nhất), mọi
thay đổi DB phải đi qua migration trong `src/main/resources/db/migration`,
không tự ALTER tay.

## 12. Rollback nếu lỗi

```bash
git log --oneline -5          # tìm commit trước đó
git checkout <commit-cu>
docker compose up -d --build
```

## Ghi chú bảo mật cần theo dõi

- `DB_PASSWORD` trong `.env.production` hiện vẫn là `1234567890` (yếu) —
  nên đổi trước khi public domain thật, kèm `ALTER USER` trên MySQL (xem
  bước 2).
- `JWT_SECRET` cần sinh mới, không dùng giá trị mẫu trong template.
- `STORAGE_ROOT_PATH` (bên trong container) và `STORAGE_HOST_PATH` (trên
  host) là 2 biến tách riêng — xem comment trong `docker-compose.yml` và
  `.env.production` nếu cần hiểu lại lý do tách.
