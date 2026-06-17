# Stripe Setup (CineBook)

CineBook uses **Stripe Checkout (hosted)** in **TEST mode**. Seats are held while you pay on
Stripe's page; the booking is confirmed on return (and by a webhook backstop). Cancellations
issue real tiered refunds.

> All Stripe secrets live on the **backend**. This hosted-redirect flow needs **no** Stripe.js
> and **no publishable key** on the frontend.

---

## 1. Create a Stripe account & turn on Test mode

1. Sign up / log in at <https://dashboard.stripe.com>.
2. Toggle **Test mode** ON (top-right). Everything below uses test keys (`sk_test_…`, `whsec_…`).

## 2. Get your keys

| Key | Where | Goes in |
|---|---|---|
| **Secret key** `sk_test_…` | Dashboard → **Developers → API keys → "Secret key"** | env var `STRIPE_SECRET_KEY` |
| **Webhook signing secret** `whsec_…` | from the Stripe CLI (local) or a Dashboard webhook endpoint (deployed) — see step 4 | env var `STRIPE_WEBHOOK_SECRET` |
| Publishable key `pk_test_…` | — | **not needed** for this flow |

## 3. Provide the keys to the backend

The backend reads `app.stripe.*` from `application.properties`, which default to env vars
(`backend/src/main/resources/application.properties`):

```properties
app.stripe.secret-key=${STRIPE_SECRET_KEY:}
app.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}
app.stripe.success-url=${STRIPE_SUCCESS_URL:http://localhost:4200/payment/success}
app.stripe.cancel-url=${STRIPE_CANCEL_URL:http://localhost:4200/payment/cancel}
app.stripe.currency=inr
app.stripe.hold-ttl-minutes=30
```

Set the env vars before starting the backend (PowerShell):

```powershell
$env:STRIPE_SECRET_KEY = "sk_test_xxx"
$env:STRIPE_WEBHOOK_SECRET = "whsec_xxx"
cd backend; .\mvnw.cmd spring-boot:run
```

> Until `STRIPE_SECRET_KEY` is set, payment endpoints fail fast with
> "Stripe is not configured." The rest of the app works normally.

## 4. Webhook signing secret

**Local development (recommended):** install the [Stripe CLI](https://stripe.com/docs/stripe-cli), then:

```bash
stripe login
stripe listen --forward-to localhost:8181/api/payments/webhook
```

The CLI prints a `whsec_…` — use it as `STRIPE_WEBHOOK_SECRET`, and keep `stripe listen` running.
Stripe's servers cannot reach `localhost`, so the CLI forwards events to your machine.

> The happy path also works **without** the CLI — the success page confirms the payment
> server-side. The webhook just guarantees confirmation if the user closes the tab after paying.

**Deployed backend:** Dashboard → **Developers → Webhooks → Add endpoint** →
URL `https://<your-host>/api/payments/webhook`, events `checkout.session.completed` and
`checkout.session.expired` → copy the revealed `whsec_…`.

## 5. Pay with a test card

On Stripe's hosted page use:

- Card `4242 4242 4242 4242`
- Any **future** expiry (e.g. `12/34`), any CVC (e.g. `123`), any postal code

## How it flows

1. Pick seats → **Proceed to Payment** → backend holds the seats and returns a Checkout URL.
2. You pay on Stripe's hosted page.
3. Back on `/payment/success`, the booking is finalized to **CONFIRMED** (the webhook is the backstop).
4. Cancel within the policy window → a real Stripe **Refund** is issued (100/80/50/0% by hours-to-show).

Refund tiers (relative to showtime): **≥24h → 100%**, **12–24h → 80%**, **2–12h → 50%**, **<2h → 0%**.
