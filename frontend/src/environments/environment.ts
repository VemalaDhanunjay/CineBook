export const environment = {
  production: false,
  // Calls go through the dev-server proxy (proxy.conf.json) to http://localhost:8181
  apiUrl: "/api"
  // Stripe note: payments use hosted Checkout (the backend returns a redirect URL),
  // so NO Stripe.js or publishable key is needed on the frontend. All Stripe secrets
  // live on the backend — see STRIPE_SETUP.md.
};
