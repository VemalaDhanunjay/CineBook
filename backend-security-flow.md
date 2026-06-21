# CineBook Backend Security Flow

This file explains CineBook backend security in a simple beginner-friendly way.

## Short Version

```text
Login/Register
-> backend creates JWT token
-> frontend stores token
-> frontend sends token with every protected API request
-> backend verifies token
-> backend creates logged-in user identity
-> backend checks role and ownership
-> request is allowed or rejected
```

## Main Flow

```text
1. User registers or logs in
   |
   v
2. AuthController receives username and password
   |
   v
3. AuthService checks the user details
   |
   v
4. Password is hashed/checked using BCryptPasswordEncoder
   |
   v
5. JwtService creates a JWT token
   |
   v
6. Backend returns AuthResponse with user details and token
   |
   v
7. Frontend stores the token
   |
   v
8. Frontend sends this header on protected requests:

   Authorization: Bearer <token>
   |
   v
9. JwtAuthFilter reads the token from the request
   |
   v
10. JwtService verifies the token
   |
   v
11. Backend creates AuthPrincipal
   |
   v
12. Spring Security checks if the user is logged in
   |
   v
13. @PreAuthorize checks the role, such as USER or ADMIN
   |
   v
14. Controller passes userId or theaterId to the service
   |
   v
15. Service checks ownership
   |
   v
16. Request succeeds, or backend returns 401/403
```

## Important Files

## 1. SecurityConfig

File:

```text
backend/src/main/java/com/cinebook/config/SecurityConfig.java
```

This is the main Spring Security configuration.

What it does:

- Enables method-level security using `@EnableMethodSecurity`.
- Creates `PasswordEncoder` using `BCryptPasswordEncoder`.
- Disables CSRF because the backend uses stateless JWT authentication.
- Makes the app stateless using `SessionCreationPolicy.STATELESS`.
- Allows public access to `/api/auth/**`.
- Allows public access to `/api/payments/webhook`.
- Requires login for all other API requests.
- Adds `JwtAuthFilter` before Spring's username/password filter.

Important part:

```java
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/api/payments/webhook").permitAll()
.anyRequest().authenticated()
```

Meaning:

- Login/register APIs are public.
- Stripe webhook is public because Stripe calls it directly.
- Everything else needs a valid JWT token.

## 2. AuthController

File:

```text
backend/src/main/java/com/cinebook/controller/AuthController.java
```

This controller handles authentication routes.

Functions:

```java
register()
```

Handles:

```text
POST /api/auth/register
```

Creates a normal moviegoer account with role `USER`.

```java
registerAdmin()
```

Handles:

```text
POST /api/auth/register-admin
```

Creates a theater owner account with role `ADMIN`.

```java
login()
```

Handles:

```text
POST /api/auth/login
```

Checks username and password, then returns a token.

## 3. AuthService

File:

```text
backend/src/main/java/com/cinebook/service/AuthService.java
```

This class contains the actual register/login logic.

Functions:

```java
register(RegisterRequest request)
```

What it does:

- Checks if username is available.
- Creates a new `User`.
- Hashes the password.
- Sets role as `USER`.
- Saves user in database.
- Returns user details and JWT token.

```java
registerAdmin(RegisterAdminRequest request)
```

What it does:

- Checks if username is available.
- Creates a new admin user.
- Hashes the password.
- Sets role as `ADMIN`.
- Creates a theater.
- Links the theater to the admin using `theaterId`.
- Returns admin details and JWT token.

```java
login(LoginRequest request)
```

What it does:

- Finds user by username.
- Checks password using `passwordEncoder.matches(...)`.
- If correct, returns user details and JWT token.
- If wrong, returns unauthorized error.

```java
ensureUsernameAvailable(String username)
```

What it does:

- Checks username is not empty.
- Checks username is not already taken.

```java
toResponse(User user)
```

What it does:

- Calls `jwtService.generateToken(user)`.
- Builds `AuthResponse`.
- Sends token back to frontend.

## 4. JwtService

File:

```text
backend/src/main/java/com/cinebook/security/JwtService.java
```

This class creates and verifies JWT tokens.

Functions:

```java
generateToken(User user)
```

What it does:

- Creates a token after login/register.
- Stores username as token subject.
- Stores these extra values inside the token:
  - `userId`
  - `role`
  - `theaterId`
- Adds issue time.
- Adds expiry time.
- Signs token using the JWT secret.

```java
parse(String token)
```

What it does:

- Checks if token is valid.
- Checks if token signature is correct.
- Checks if token is expired.
- Returns token claims if valid.

```java
toPrincipal(Claims claims)
```

What it does:

- Reads values from token claims.
- Creates an `AuthPrincipal`.

## 5. JwtAuthFilter

File:

```text
backend/src/main/java/com/cinebook/security/JwtAuthFilter.java
```

This filter runs for every request.

Main function:

```java
doFilterInternal(...)
```

What it does:

```text
Read Authorization header
-> check it starts with "Bearer "
-> remove "Bearer " and get only the token
-> verify token using JwtService
-> convert token into AuthPrincipal
-> create Spring authentication object
-> store authentication in SecurityContextHolder
-> continue request
```

If token is missing or invalid:

- It does not log the user in.
- Protected APIs will fail with `401 Unauthorized`.

## 6. AuthPrincipal

File:

```text
backend/src/main/java/com/cinebook/security/AuthPrincipal.java
```

This is the logged-in user's identity inside the backend.

It contains:

```java
Long userId
String username
Role role
Long theaterId
```

Controllers read it using:

```java
@AuthenticationPrincipal AuthPrincipal principal
```

Example:

```java
principal.userId()
principal.role()
principal.theaterId()
```

## 7. Roles

File:

```text
backend/src/main/java/com/cinebook/entity/Role.java
```

CineBook has two roles:

```java
USER
ADMIN
```

`USER` means normal moviegoer.

`ADMIN` means theater owner/admin.

## Role-Based Access

Spring checks roles using `@PreAuthorize`.

Example:

```java
@PreAuthorize("hasRole('USER')")
```

Only users with role `USER` can access this.

Example:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Only users with role `ADMIN` can access this.

## Example 1: Login Flow

```text
POST /api/auth/login
   |
   v
AuthController.login()
   |
   v
AuthService.login()
   |
   v
Find user by username
   |
   v
Check password using BCrypt
   |
   v
JwtService.generateToken()
   |
   v
Return AuthResponse with token
```

## Example 2: User Booking Flow

```text
GET /api/bookings/me
   |
   v
Frontend sends Authorization: Bearer <token>
   |
   v
JwtAuthFilter verifies token
   |
   v
AuthPrincipal is created
   |
   v
@PreAuthorize("hasRole('USER')") checks role
   |
   v
BookingController uses principal.userId()
   |
   v
BookingService.listForUser(userId)
   |
   v
Only that user's bookings are returned
```

Why this is secure:

- The frontend does not send `userId`.
- Backend gets `userId` from the verified token.
- A user cannot ask for another user's bookings.

## Example 3: Admin Show Creation Flow

```text
POST /api/shows
   |
   v
Frontend sends admin token
   |
   v
JwtAuthFilter verifies token
   |
   v
AuthPrincipal is created with role ADMIN and theaterId
   |
   v
@PreAuthorize("hasRole('ADMIN')") checks role
   |
   v
ShowController passes principal.theaterId()
   |
   v
ShowService.createShow(request, theaterId)
   |
   v
Show is created only for that admin's theater
```

Why this is secure:

- The admin does not send `theaterId` from frontend.
- Backend gets `theaterId` from JWT.
- One theater admin cannot create shows for another theater.

## Example 4: Admin Booking View Flow

```text
GET /api/admin/bookings
   |
   v
Frontend sends admin token
   |
   v
JwtAuthFilter verifies token
   |
   v
@PreAuthorize("hasRole('ADMIN')") checks role
   |
   v
AdminBookingController uses principal.theaterId()
   |
   v
BookingService.listForTheater(theaterId)
   |
   v
Only bookings for that admin's theater are returned
```

## Example 5: Stripe Webhook Flow

Stripe webhook is different.

It does not use JWT because Stripe calls it, not a logged-in user.

Flow:

```text
Stripe sends POST /api/payments/webhook
   |
   v
SecurityConfig allows this endpoint
   |
   v
PaymentController receives payload and Stripe-Signature header
   |
   v
StripePaymentService.handleWebhook()
   |
   v
StripeGateway.parseEvent()
   |
   v
Stripe signature is verified
   |
   v
If valid, booking/payment logic runs
```

So the webhook is public in Spring Security, but protected using Stripe's signature.

## Error Types

Common security errors:

```text
401 Unauthorized
```

Means:

- No token was sent.
- Token is invalid.
- Token expired.
- User is not logged in.

```text
403 Forbidden
```

Means:

- User is logged in.
- But user does not have permission.
- Example: normal USER tries to access ADMIN API.
- Example: user tries to access another user's booking.

## Final Mental Model

Think of the JWT token like an entry pass.

```text
Token proves who you are.
Role proves what type of user you are.
userId proves which user data belongs to you.
theaterId proves which theater an admin owns.
```

So the backend security is:

```text
Authentication:
Who are you?

Authorization:
Are you allowed to do this?

Ownership:
Does this data belong to you?
```

## Very Short Revision

```text
Login -> token
Token -> sent with request
JwtAuthFilter -> verifies token
AuthPrincipal -> stores logged-in user
@PreAuthorize -> checks role
Service -> checks ownership
Response -> allowed or denied
```
