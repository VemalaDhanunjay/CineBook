# CineBook Live Deployment Guide

This guide records the current live deployment setup and the fixes learned during the first deployment.

Architecture:

```text
Vercel Angular frontend
  -> /api rewrite
  -> Render Spring Boot backend
  -> Alwaysdata MariaDB/MySQL database
  -> Stripe Checkout
```

## Current Live URLs

Frontend:

```text
https://cine-book-one-rosy.vercel.app
```

Backend:

```text
https://cinebook2-ie7v.onrender.com
```

Stripe webhook endpoint:

```text
https://cinebook2-ie7v.onrender.com/api/payments/webhook
```

## Important Precautions

Never commit real secrets to GitHub.

These values must stay only in provider dashboards such as Render, Stripe, and Alwaysdata:

```text
Alwaysdata database password
Stripe secret key, sk_test_... or sk_live_...
Stripe webhook secret, whsec_...
JWT secret
```

Rotate any secret that was pasted into chat, documentation, GitHub, screenshots, or logs:

```text
Stripe secret key
Alwaysdata database password
JWT secret
```

After changing the JWT secret, existing users need to log in again.

Render environment variables must be plain values. Do not paste Spring property syntax into Render.

Wrong:

```env
APP_STRIPE_SUCCESS_URL=${STRIPE_SUCCESS_URL:https://cine-book-one-rosy.vercel.app/payment/success}
APP_STRIPE_CANCEL_URL="app.stripe.cancel-url=${STRIPE_CANCEL_URL:https://cine-book-one-rosy.vercel.app/payment/cancel}"
```

Correct:

```env
APP_STRIPE_SUCCESS_URL=https://cine-book-one-rosy.vercel.app/payment/success
APP_STRIPE_CANCEL_URL=https://cine-book-one-rosy.vercel.app/payment/cancel
```

Also do not wrap Render values in quotes.

Wrong:

```env
SPRING_DATASOURCE_URL="jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC"
```

Correct:

```env
SPRING_DATASOURCE_URL=jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
```

## Required Code Files

### Backend Dockerfile

File:

```text
backend/Dockerfile
```

Current content:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/cinebook-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Backend MariaDB Dependency

File:

```text
backend/pom.xml
```

Alwaysdata uses MariaDB-compatible hosting. Keep this dependency:

```xml
<dependency>
    <groupId>org.mariadb.jdbc</groupId>
    <artifactId>mariadb-java-client</artifactId>
    <scope>runtime</scope>
</dependency>
```

This dependency is needed only once in `pom.xml`. You do not add it again for every deploy.

The MySQL dependency can stay if you still use local MySQL:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Vercel Rewrite

File:

```text
frontend/vercel.json
```

Current content:

```json
{
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://cinebook2-ie7v.onrender.com/api/:path*"
    },
    {
      "source": "/:path*",
      "destination": "/index.html"
    }
  ]
}
```

This lets the frontend keep:

```ts
apiUrl: "/api"
```

The browser calls Vercel:

```text
https://cine-book-one-rosy.vercel.app/api/...
```

Vercel forwards it to Render:

```text
https://cinebook2-ie7v.onrender.com/api/...
```

Because of this, Vercel needs no frontend environment variables.

## Alwaysdata Database Data

Use these non-secret values:

```text
Host: mysql-cinebook1.alwaysdata.net
Port: 3306
Database: cinebook1_db
Username: cinebook1_abhi
Password: store only in Render, not in Git
```

Correct JDBC URL:

```text
jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
```

Important: it must start with `jdbc:`.

Wrong:

```text
mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
```

Correct:

```text
jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
```

## Render Backend Settings

Create or update the Render service:

```text
Service type: Web Service
Runtime: Docker
Root directory: backend
Branch: main
```

Use this environment variable set in Render.

Replace the placeholder values before saving:

```env
SERVER_PORT=10000

SPRING_DATASOURCE_URL=jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=cinebook1_abhi
SPRING_DATASOURCE_PASSWORD=<ROTATED_ALWAYS_DATA_PASSWORD>
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update

SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=1
SPRING_DATASOURCE_HIKARI_MINIMUMIDLE=0
SPRING_DATASOURCE_HIKARI_IDLETIMEOUT=30000
SPRING_DATASOURCE_HIKARI_MAXLIFETIME=600000
SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT=30000

APP_JWT_SECRET=<ROTATED_BASE64_JWT_SECRET>
APP_JWT_EXPIRATION_MS=86400000

APP_STRIPE_SECRET_KEY=<ROTATED_STRIPE_SECRET_KEY>
APP_STRIPE_WEBHOOK_SECRET=<STRIPE_WEBHOOK_SECRET_AFTER_WEBHOOK_CREATION>
APP_STRIPE_SUCCESS_URL=https://cine-book-one-rosy.vercel.app/payment/success
APP_STRIPE_CANCEL_URL=https://cine-book-one-rosy.vercel.app/payment/cancel
APP_STRIPE_CURRENCY=inr
APP_STRIPE_HOLD_TTL_MINUTES=30

JAVA_TOOL_OPTIONS=-Xmx384m
```

Optional:

```env
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MariaDBDialect
```

Hibernate can select the MariaDB dialect automatically when the MariaDB URL and driver are correct. If this env var is present, a warning may appear saying MariaDBDialect does not need to be specified. That warning is harmless.

### Hikari Connection Pool Warning

Alwaysdata free tier has a low connection limit. Without pool limits, the app can fail with:

```text
User cinebook1_abhi already has more than 'max_user_connections' active connections
```

Use this first:

```env
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=1
```

If the app is stable and Alwaysdata does not complain, you may try:

```env
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=2
```

Do not use these Render env names:

```env
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT
SPRING_DATASOURCE_HIKARI_MAX_LIFETIME
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT
```

They can bind incorrectly. Use the no-extra-underscore versions:

```env
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE
SPRING_DATASOURCE_HIKARI_MINIMUMIDLE
SPRING_DATASOURCE_HIKARI_IDLETIMEOUT
SPRING_DATASOURCE_HIKARI_MAXLIFETIME
SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT
```

## Vercel Frontend Settings

Create or update the Vercel project:

```text
Framework: Angular
Root directory: frontend
Install command: npm ci
Build command: npm run build
Output directory: dist/cinebook/browser
Environment variables: none
```

If `npm ci` fails because of dependency peer warnings, use:

```text
npm ci --legacy-peer-deps
```

## Stripe Setup

### Render Stripe Env

Use plain URL values in Render:

```env
APP_STRIPE_SECRET_KEY=<ROTATED_STRIPE_SECRET_KEY>
APP_STRIPE_SUCCESS_URL=https://cine-book-one-rosy.vercel.app/payment/success
APP_STRIPE_CANCEL_URL=https://cine-book-one-rosy.vercel.app/payment/cancel
APP_STRIPE_CURRENCY=inr
APP_STRIPE_HOLD_TTL_MINUTES=30
```

The previous payment failure:

```text
Could not start payment: Not a valid URL; code: url_invalid
```

was caused by malformed Render values such as `${STRIPE_SUCCESS_URL:...}` or quoted property syntax.

### Stripe Webhook

In Stripe Dashboard:

```text
Developers -> Webhooks -> Add endpoint
```

Endpoint URL:

```text
https://cinebook2-ie7v.onrender.com/api/payments/webhook
```

Events:

```text
checkout.session.completed
checkout.session.expired
```

Stripe will show a webhook signing secret:

```text
whsec_...
```

Add it to Render:

```env
APP_STRIPE_WEBHOOK_SECRET=<STRIPE_WEBHOOK_SECRET>
```

Restart or redeploy Render after adding it.

## Local Development Notes

Local `application.properties` can use placeholders:

```properties
app.stripe.secret-key=${STRIPE_SECRET_KEY:}
app.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}
app.stripe.success-url=${STRIPE_SUCCESS_URL:http://localhost:4200/payment/success}
app.stripe.cancel-url=${STRIPE_CANCEL_URL:http://localhost:4200/payment/cancel}
app.stripe.currency=inr
app.stripe.hold-ttl-minutes=30
```

For local PowerShell:

```powershell
$env:STRIPE_SECRET_KEY="sk_test_your_key_here"
$env:STRIPE_SUCCESS_URL="http://localhost:4200/payment/success"
$env:STRIPE_CANCEL_URL="http://localhost:4200/payment/cancel"
```

Then run:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The local Angular dev server uses:

```text
frontend/proxy.conf.json
```

to proxy local `/api` calls to:

```text
http://localhost:8181
```

## Deployment Order

1. Push backend code with `backend/Dockerfile` and MariaDB dependency in `pom.xml`.
2. Deploy backend on Render using Docker and the Render env vars above.
3. Copy the Render backend URL.
4. Put the Render URL in `frontend/vercel.json`.
5. Deploy frontend on Vercel.
6. Update Render Stripe success/cancel URLs to the real Vercel frontend URL.
7. Create the Stripe webhook using the Render backend URL.
8. Add `APP_STRIPE_WEBHOOK_SECRET` in Render.
9. Restart or redeploy Render.
10. Test login, seat selection, payment, payment success, and my bookings.

## Testing Checklist

Test backend health:

```text
Open https://cinebook2-ie7v.onrender.com
```

If a root route is not defined, a 404 is okay. The important thing is that the service is running and not crashing.

Test frontend:

```text
Open https://cine-book-one-rosy.vercel.app
```

Test API rewrite:

```text
Open browser DevTools -> Network
Click login/register or load movies
Requests should go to /api/... on the Vercel domain
```

Test payment:

```text
Login as a normal USER
Pick a movie/show/seat
Click Proceed to payment
Stripe Checkout should open
Use test card: 4242 4242 4242 4242
Use any future expiry date and any CVC
After success, app should return to /payment/success
```

## Common Errors And Fixes

### Error: URL must start with jdbc

Cause:

```text
SPRING_DATASOURCE_URL started with mariadb:// instead of jdbc:mariadb://
```

Fix:

```env
SPRING_DATASOURCE_URL=jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
```

### Error: Unknown column RESERVED in WHERE

Cause:

```text
MySQL driver/dialect was used against Alwaysdata MariaDB.
```

Fix:

```xml
<dependency>
    <groupId>org.mariadb.jdbc</groupId>
    <artifactId>mariadb-java-client</artifactId>
    <scope>runtime</scope>
</dependency>
```

and:

```env
SPRING_DATASOURCE_URL=jdbc:mariadb://mysql-cinebook1.alwaysdata.net:3306/cinebook1_db?useSSL=false&serverTimezone=UTC
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
```

### Error: max_user_connections

Cause:

```text
Alwaysdata free tier connection limit was exceeded.
```

Fix:

```env
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=1
SPRING_DATASOURCE_HIKARI_MINIMUMIDLE=0
```

Wait 3-5 minutes, then redeploy.

### Error: Could not bind properties under spring.datasource.hikari.connection

Cause:

```text
Wrong Hikari env var naming, usually SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT.
```

Fix:

```env
SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT=30000
```

### Error: Could not start payment: Not a valid URL

Cause:

```text
APP_STRIPE_SUCCESS_URL or APP_STRIPE_CANCEL_URL was malformed.
```

Fix:

```env
APP_STRIPE_SUCCESS_URL=https://cine-book-one-rosy.vercel.app/payment/success
APP_STRIPE_CANCEL_URL=https://cine-book-one-rosy.vercel.app/payment/cancel
```

Do not use quotes, `${...}`, `localhost`, or `app.stripe.success-url=` inside the Render value.

### Error: Stripe is not configured

Cause:

```text
APP_STRIPE_SECRET_KEY missing or blank in Render.
```

Fix:

```env
APP_STRIPE_SECRET_KEY=<ROTATED_STRIPE_SECRET_KEY>
```

### Error: 401 or 403 on payment checkout

Cause:

```text
The user is not logged in, token expired, or the logged-in user is not ROLE_USER.
```

Fix:

```text
Log out, log in as a normal user, then try payment again.
```

## Final Production Notes

For a demo/student deployment, `SPRING_JPA_HIBERNATE_DDL_AUTO=update` is acceptable.

For a real production app, replace it with migrations:

```text
Flyway or Liquibase
```

and later use:

```env
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

Alwaysdata free tier is connection-limited. If more users need to access the app at the same time, upgrade the database plan or move to a database provider with higher connection limits.

