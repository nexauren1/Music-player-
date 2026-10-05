/**
 * Music Player — PayPal Cloudflare Worker
 *
 * Keeps PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET server-side and
 * automatically provisions the PayPal catalog product + quarterly plan.
 *
 * Expected Cloudflare Worker secrets:
 *   PAYPAL_CLIENT_ID
 *   PAYPAL_CLIENT_SECRET
 *   FIREBASE_PROJECT_ID
 *   FIREBASE_CLIENT_EMAIL
 *   FIREBASE_PRIVATE_KEY
 *
 * Production mode:
 *   PayPal is permanently LIVE for production releases.
 *   Keep PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET as Cloudflare Worker secrets.
 *   PUBLIC_BASE_URL is optional and used for checkout redirects.
 */

const PRODUCT_IDS = {
  quarterly: "MUSIC_PLAYER_QUARTERLY",
  lifetime: "MUSIC_PLAYER_LIFETIME",
};

const PLAN = {
  name: "Music Player Premium — Quarterly",
  description: "Music Player Premium — $5 every 3 months.",
  amount: "5.00",
  currency: "USD",
};

const LIFETIME = {
  name: "Music Player Premium — Lifetime",
  description: "Music Player Premium — lifetime access.",
  amount: "30.00",
  currency: "USD",
};

function paypalBase() {
  return "https://api-m.paypal.com";
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "Content-Type",
    },
  });
}

function text(message, status = 200) {
  return new Response(message, {
    status,
    headers: {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
    },
  });
}

async function paypalToken(env) {
  if (!env.PAYPAL_CLIENT_ID || !env.PAYPAL_CLIENT_SECRET) {
    throw new Error("PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET are required.");
  }

  const credentials = btoa(
    `${env.PAYPAL_CLIENT_ID}:${env.PAYPAL_CLIENT_SECRET}`
  );

  const response = await fetch(
    `${paypalBase()}/v1/oauth2/token`,
    {
      method: "POST",
      headers: {
        Authorization: `Basic ${credentials}`,
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "application/json",
        "Accept-Language": "en_US",
      },
      body: "grant_type=client_credentials",
    }
  );

  const body = await safeJson(response);
  if (!response.ok || !body?.access_token) {
    throw new Error(
      `PayPal OAuth failed (${response.status}): ${JSON.stringify(body)}`
    );
  }

  return body.access_token;
}

async function safeJson(response) {
  const raw = await response.text();
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    return { raw };
  }
}

async function paypalRequest(env, token, path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  headers.set("Accept", "application/json");
  headers.set("Content-Type", "application/json");

  const response = await fetch(`${paypalBase()}${path}`, {
    ...options,
    headers,
  });

  const body = await safeJson(response);

  if (!response.ok) {
    const error = new Error(
      `PayPal API ${options.method || "GET"} ${path} failed (${response.status}): ${JSON.stringify(body)}`
    );
    error.status = response.status;
    error.body = body;
    throw error;
  }

  return body;
}

async function getProduct(env, token, id) {
  try {
    return await paypalRequest(
      env,
      token,
      `/v1/catalogs/products/${encodeURIComponent(id)}`
    );
  } catch (error) {
    if (error.status === 404) return null;
    throw error;
  }
}

async function ensureProduct(env, token, { id, name, description }) {
  const existing = await getProduct(env, token, id);
  if (existing?.id) return existing;

  const requestId = `music-player-product-${id.toLowerCase()}`;
  try {
    return await paypalRequest(env, token, "/v1/catalogs/products", {
      method: "POST",
      headers: {
        Prefer: "return=representation",
        "PayPal-Request-Id": requestId,
      },
      body: JSON.stringify({
        id,
        name,
        description,
        type: "DIGITAL",
      }),
    });
  } catch (error) {
    // If the product was created by a concurrent request, fetch it again.
    if (error.status === 409 || error.status === 422) {
      const retry = await getProduct(env, token, id);
      if (retry?.id) return retry;
    }
    throw error;
  }
}

function planMatches(plan) {
  const cycle = plan?.billing_cycles?.find(
    (item) => item?.tenure_type === "REGULAR"
  );
  const fixed = cycle?.pricing_scheme?.fixed_price;
  return (
    plan?.name === PLAN.name &&
    cycle?.frequency?.interval_unit === "MONTH" &&
    Number(cycle?.frequency?.interval_count) === 3 &&
    fixed?.currency_code === PLAN.currency &&
    String(fixed?.value) === PLAN.amount
  );
}

async function findQuarterlyPlan(env, token, productId) {
  const params = new URLSearchParams({
    product_id: productId,
    page: "1",
    page_size: "20",
    total_required: "true",
  });

  const result = await paypalRequest(
    env,
    token,
    `/v1/billing/plans?${params.toString()}`
  );

  return (result?.plans || []).find(planMatches) || null;
}

async function createQuarterlyPlan(env, token, productId) {
  const requestId = "music-player-quarterly-plan-v1";
  return paypalRequest(env, token, "/v1/billing/plans", {
    method: "POST",
    headers: {
      Prefer: "return=representation",
      "PayPal-Request-Id": requestId,
    },
    body: JSON.stringify({
      name: PLAN.name,
      description: PLAN.description,
      product_id: productId,
      billing_cycles: [
        {
          frequency: {
            interval_unit: "MONTH",
            interval_count: 3,
          },
          tenure_type: "REGULAR",
          sequence: 1,
          total_cycles: 0,
          pricing_scheme: {
            fixed_price: {
              value: PLAN.amount,
              currency_code: PLAN.currency,
            },
          },
        },
      ],
      payment_preferences: {
        auto_bill_outstanding: true,
        payment_failure_threshold: 1,
      },
    }),
  });
}

async function activatePlan(env, token, planId) {
  const status = await paypalRequest(
    env,
    token,
    `/v1/billing/plans/${encodeURIComponent(planId)}`,
    {
      method: "GET",
    }
  );

  if (status?.status === "ACTIVE") return status;

  await paypalRequest(
    env,
    token,
    `/v1/billing/plans/${encodeURIComponent(planId)}/activate`,
    {
      method: "POST",
      body: "{}",
    }
  );

  return await paypalRequest(
    env,
    token,
    `/v1/billing/plans/${encodeURIComponent(planId)}`,
    {
      method: "GET",
    }
  );
}

async function ensureQuarterlyPlan(env, token, productId) {
  let plan = await findQuarterlyPlan(env, token, productId);

  if (!plan) {
    try {
      plan = await createQuarterlyPlan(env, token, productId);
    } catch (error) {
      // A concurrent request can create the same plan. Look it up again.
      if (error.status === 409 || error.status === 422) {
        plan = await findQuarterlyPlan(env, token, productId);
      }
      if (!plan) throw error;
    }
  }

  if (!plan?.id) {
    throw new Error("PayPal quarterly plan was created without an ID.");
  }

  if (plan.status !== "ACTIVE") {
    plan = await activatePlan(env, token, plan.id);
  }

  return plan;
}

async function provision(env) {
  const token = await paypalToken(env);

  const quarterlyProduct = await ensureProduct(env, token, {
    id: PRODUCT_IDS.quarterly,
    name: "Music Player Premium — Quarterly",
    description: PLAN.description,
  });

  const lifetimeProduct = await ensureProduct(env, token, {
    id: PRODUCT_IDS.lifetime,
    name: LIFETIME.name,
    description: LIFETIME.description,
  });

  const quarterlyPlan = await ensureQuarterlyPlan(
    env,
    token,
    quarterlyProduct.id
  );

  return {
    environment: "live",
    quarterly: {
      productId: quarterlyProduct.id,
      planId: quarterlyPlan.id,
      status: quarterlyPlan.status,
      price: "$5.00",
      interval: "every 3 months",
    },
    lifetime: {
      productId: lifetimeProduct.id,
      price: "$30.00",
      interval: "lifetime",
      checkout: "orders-v2",
    },
  };
}

function publicBaseUrl(request, env) {
  return String(env.PUBLIC_BASE_URL || new URL(request.url).origin).replace(
    /\/$/,
    ""
  );
}

function html(title, message, redirectUrl = "") {
  const safeTitle = String(title).replace(/[<>&"]/g, "");
  const safeMessage = String(message).replace(/[<>&]/g, "");
  const redirect = redirectUrl
    ? "<script>setTimeout(function(){window.location.href=" + JSON.stringify(redirectUrl) + ";},400);</script>"
    : "";
  const link = redirectUrl
    ? '<p><a href="' + redirectUrl + '">Return to Music Player</a></p>'
    : "";
  const body =
    "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" +
    safeTitle +
    "</title><style>body{font-family:system-ui,sans-serif;background:linear-gradient(135deg,#11091d,#071923);color:#fff;min-height:100vh;display:grid;place-items:center;margin:0}main{max-width:520px;margin:24px;padding:28px;border-radius:28px;background:rgba(255,255,255,.08);backdrop-filter:blur(20px);text-align:center;box-shadow:0 24px 80px rgba(0,0,0,.35)}h1{margin:0 0 10px}p{color:#d6d5df;line-height:1.55}a{display:inline-block;padding:12px 18px;border-radius:999px;background:#8c63ff;color:#fff;text-decoration:none;font-weight:700}</style></head><body><main><h1>" +
    safeTitle +
    "</h1><p>" +
    safeMessage +
    "</p>" +
    link +
    "</main>" +
    redirect +
    "</body></html>";
  return new Response(body, {
    status: 200,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function unixMillis(value) {
  const time = Date.parse(String(value || ""));
  return Number.isFinite(time) ? time : null;
}

function bytesToBase64Url(bytes) {
  let binary = "";
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < view.length; i += 1) {
    binary += String.fromCharCode(view[i]);
  }
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function textToBase64Url(value) {
  return bytesToBase64Url(new TextEncoder().encode(value));
}

function pemToArrayBuffer(pem) {
  const normalized = String(pem || "")
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function firebaseAccessToken(env) {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY || !env.FIREBASE_PROJECT_ID) {
    throw new Error(
      "FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL and FIREBASE_PRIVATE_KEY are required."
    );
  }

  const issuedAt = Math.floor(Date.now() / 1000);
  const header = textToBase64Url(JSON.stringify({
    alg: "RS256",
    typ: "JWT",
  }));
  const claims = textToBase64Url(JSON.stringify({
    iss: env.FIREBASE_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: issuedAt,
    exp: issuedAt + 3600,
  }));
  const unsigned = header + "." + claims;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(env.FIREBASE_PRIVATE_KEY),
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned)
  );

  const assertion = unsigned + "." + bytesToBase64Url(signature);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body:
      "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" +
      encodeURIComponent(assertion),
  });

  const body = await safeJson(response);
  if (!response.ok || !body?.access_token) {
    throw new Error(
      "Firebase service-account OAuth failed (" +
        response.status +
        "): " +
        JSON.stringify(body)
    );
  }

  return body.access_token;
}

function firestoreValue(value) {
  if (value === null || value === undefined) {
    return { nullValue: "NULL_VALUE" };
  }
  if (typeof value === "boolean") {
    return { booleanValue: value };
  }
  if (typeof value === "number") {
    return Number.isInteger(value)
      ? { integerValue: String(value) }
      : { doubleValue: value };
  }
  return { stringValue: String(value) };
}

async function writeFirebaseEntitlement(env, {
  uid,
  plan,
  expiresAtMillis,
  orderId,
  subscriptionId,
}) {
  if (!uid) throw new Error("uid is required for Firebase entitlement.");
  const token = await firebaseAccessToken(env);
  const projectId = String(env.FIREBASE_PROJECT_ID);
  const path =
    "users/" +
    encodeURIComponent(uid) +
    "/entitlement/premium";
  const endpoint =
    "https://firestore.googleapis.com/v1/projects/" +
    encodeURIComponent(projectId) +
    "/databases/(default)/documents/" +
    path;

  const fields = {
    plan: firestoreValue(plan),
    verified: firestoreValue(true),
    expiresAtMillis: firestoreValue(expiresAtMillis),
    orderId: firestoreValue(orderId),
    subscriptionId: firestoreValue(subscriptionId),
    updatedAt: {
      timestampValue: new Date().toISOString(),
    },
  };

  const response = await fetch(endpoint, {
    method: "PATCH",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ fields }),
  });

  const body = await safeJson(response);
  if (!response.ok) {
    throw new Error(
      "Firestore entitlement write failed (" +
        response.status +
        "): " +
        JSON.stringify(body)
    );
  }

  return true;
}

async function getOrder(env, token, orderId) {
  return paypalRequest(
    env,
    token,
    "/v2/checkout/orders/" + encodeURIComponent(orderId),
    { method: "GET" }
  );
}

async function captureLifetime(env, token, orderId) {
  const existing = await getOrder(env, token, orderId);
  if (existing?.status === "COMPLETED") return existing;
  if (existing?.status !== "APPROVED") {
    throw new Error(
      "Lifetime order is not ready for capture: " + (existing?.status || "UNKNOWN")
    );
  }

  return await paypalRequest(
    env,
    token,
    "/v2/checkout/orders/" + encodeURIComponent(orderId) + "/capture",
    {
      method: "POST",
      body: "{}",
      headers: {
        "PayPal-Request-Id": "music-player-capture-" + orderId,
        Prefer: "return=representation",
      },
    }
  );
}

async function verifyPurchase(env, { plan, uid, orderId, subscriptionId }) {
  if (!uid) throw new Error("uid is required.");
  const token = await paypalToken(env);

  if (plan === "lifetime") {
    if (!orderId) throw new Error("orderId is required for lifetime verification.");
    const order = await captureLifetime(env, token, orderId);
    const purchase = order?.purchase_units?.[0];
    const amount = purchase?.amount;
    const customId = purchase?.custom_id;
    const completed =
      order?.status === "COMPLETED" &&
      customId === uid &&
      amount?.currency_code === LIFETIME.currency &&
      String(amount?.value) === LIFETIME.amount;

    if (!completed) {
      return {
        verified: false,
        plan: "none",
        expiresAtMillis: null,
        orderId,
        subscriptionId: null,
        paypalStatus: order?.status || null,
        cloudSynced: false,
      };
    }

    await writeFirebaseEntitlement(env, {
      uid,
      plan: "lifetime",
      expiresAtMillis: null,
      orderId,
      subscriptionId: null,
    });

    return {
      verified: true,
      plan: "lifetime",
      expiresAtMillis: null,
      orderId,
      subscriptionId: null,
      paypalStatus: order?.status || null,
      cloudSynced: true,
    };
  }

  if (plan === "quarterly") {
    if (!subscriptionId) throw new Error("subscriptionId is required for quarterly verification.");
    const subscription = await paypalRequest(
      env,
      token,
      "/v1/billing/subscriptions/" + encodeURIComponent(subscriptionId),
      { method: "GET" }
    );

    const customId = subscription?.custom_id;
    const status = subscription?.status;
    const verified = customId === uid && status === "ACTIVE";
    const expiresAtMillis = unixMillis(subscription?.billing_info?.next_billing_time);

    if (!verified) {
      return {
        verified: false,
        plan: "none",
        expiresAtMillis,
        orderId: null,
        subscriptionId,
        paypalStatus: status || null,
        cloudSynced: false,
      };
    }

    await writeFirebaseEntitlement(env, {
      uid,
      plan: "quarterly",
      expiresAtMillis,
      orderId: null,
      subscriptionId,
    });

    return {
      verified: true,
      plan: "quarterly",
      expiresAtMillis,
      orderId: null,
      subscriptionId,
      paypalStatus: status || null,
      cloudSynced: true,
    };
  }

  throw new Error("Unknown premium plan.");
}

async function createQuarterlyCheckout(request, env, uid, options = {}) {
  if (!uid) {
    return json(
      { error: "uid is required for a premium checkout." },
      400
    );
  }

  const token = await paypalToken(env);
  const quarterlyProduct = await ensureProduct(env, token, {
    id: PRODUCT_IDS.quarterly,
    name: "Music Player Premium — Quarterly",
    description: PLAN.description,
  });
  const quarterlyPlan = await ensureQuarterlyPlan(
    env,
    token,
    quarterlyProduct.id
  );

  const base = publicBaseUrl(request, env);
  const subscription = await paypalRequest(
    env,
    token,
    "/v1/billing/subscriptions",
    {
      method: "POST",
      headers: {
        Prefer: "return=representation",
        "PayPal-Request-Id": `music-player-sub-${crypto.randomUUID()}`,
      },
      body: JSON.stringify({
        plan_id: quarterlyPlan.id,
        custom_id: uid,
        app_switch_context: options.appSwitchContext
          ? {
              native_app: {
                return_app_url:
                  options.appSwitchContext?.nativeApp?.returnAppUrl ||
                  options.appSwitchContext?.native_app?.return_app_url ||
                  options.returnUrl ||
                  `musicplayer://paypal/success`,
                cancel_app_url:
                  options.appSwitchContext?.nativeApp?.cancelAppUrl ||
                  options.appSwitchContext?.native_app?.cancel_app_url ||
                  options.cancelUrl ||
                  `musicplayer://paypal/cancel`,
                os_type:
                  options.appSwitchContext?.nativeApp?.osType ||
                  options.appSwitchContext?.native_app?.os_type ||
                  "ANDROID",
                os_version:
                  options.appSwitchContext?.nativeApp?.osVersion ||
                  options.appSwitchContext?.native_app?.os_version ||
                  "unknown",
              },
            }
          : undefined,
        application_context: {
          brand_name: "Music Player",
          user_action: "SUBSCRIBE_NOW",
          return_url: options.returnUrl || `${base}/paypal/success?plan=quarterly&uid=${encodeURIComponent(uid)}`,
          cancel_url: options.cancelUrl || `${base}/paypal/cancel?plan=quarterly&uid=${encodeURIComponent(uid)}`,
        },
      }),
    }
  );

  const approveUrl = (subscription.links || []).find(
    (link) => link.rel === "approve"
  )?.href;

  return json({
    plan: "quarterly",
    subscriptionId: subscription.id,
    approveUrl: approveUrl || null,
    status: subscription.status,
  });
}

async function createLifetimeCheckout(request, env, uid, options = {}) {
  if (!uid) {
    return json(
      { error: "uid is required for a premium checkout." },
      400
    );
  }

  const token = await paypalToken(env);
  const lifetimeProduct = await ensureProduct(env, token, {
    id: PRODUCT_IDS.lifetime,
    name: LIFETIME.name,
    description: LIFETIME.description,
  });

  const base = publicBaseUrl(request, env);
  const order = await paypalRequest(
    env,
    token,
    "/v2/checkout/orders",
    {
      method: "POST",
      headers: {
        Prefer: "return=representation",
        "PayPal-Request-Id": `music-player-life-${crypto.randomUUID()}`,
      },
      body: JSON.stringify({
        intent: "CAPTURE",
        purchase_units: [
          {
            reference_id: lifetimeProduct.id,
            custom_id: uid,
            description: LIFETIME.description,
            amount: {
              currency_code: LIFETIME.currency,
              value: LIFETIME.amount,
            },
          },
        ],
        app_switch_context: {
          native_app: {
            return_app_url:
              options.appSwitchContext?.nativeApp?.returnAppUrl ||
              options.appSwitchContext?.native_app?.return_app_url ||
              options.returnUrl ||
              `musicplayer://paypal/success`,
            cancel_app_url:
              options.appSwitchContext?.nativeApp?.cancelAppUrl ||
              options.appSwitchContext?.native_app?.cancel_app_url ||
              options.cancelUrl ||
              `musicplayer://paypal/cancel`,
            os_type:
              options.appSwitchContext?.nativeApp?.osType ||
              options.appSwitchContext?.native_app?.os_type ||
              "ANDROID",
            os_version:
              options.appSwitchContext?.nativeApp?.osVersion ||
              options.appSwitchContext?.native_app?.os_version ||
              "unknown",
          },
        },
        application_context: {
          brand_name: "Music Player",
          user_action: "PAY_NOW",
          return_url: options.returnUrl || `${base}/paypal/success?plan=lifetime&uid=${encodeURIComponent(uid)}`,
          cancel_url: options.cancelUrl || `${base}/paypal/cancel?plan=lifetime&uid=${encodeURIComponent(uid)}`,
        },
      }),
    }
  );

  const approveUrl = (order.links || []).find(
    (link) => link.rel === "approve"
  )?.href;

  return json({
    plan: "lifetime",
    productId: lifetimeProduct.id,
    orderId: order.id,
    approveUrl: approveUrl || null,
    status: order.status,
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    const url = new URL(request.url);

    try {
      if (url.pathname === "/" || url.pathname === "/health") {
        return json({
          ok: true,
          service: "music-player-paypal",
          environment: "live",
        });
      }

      if (url.pathname === "/paypal/provision" && request.method === "POST") {
        return json(await provision(env));
      }

      if (url.pathname === "/paypal/plans" && request.method === "GET") {
        return json(await provision(env));
      }

      if (url.pathname === "/paypal/checkout/quarterly") {
        if (request.method === "GET") {
          const uid = url.searchParams.get("uid");
          const response = await createQuarterlyCheckout(request, env, uid, { returnUrl: url.searchParams.get("returnUrl") || "", cancelUrl: url.searchParams.get("cancelUrl") || "" });
          const data = await response.clone().json();
          if (data?.approveUrl) return Response.redirect(data.approveUrl, 302);
          return response;
        }
        if (request.method === "POST") {
          const body = await safeJson(request);
          return await createQuarterlyCheckout(request, env, body?.uid, {
  returnUrl: body?.returnUrl || body?.appSwitchContext?.nativeApp?.returnAppUrl || "",
  cancelUrl: body?.cancelUrl || body?.appSwitchContext?.nativeApp?.cancelAppUrl || "",
  appSwitchContext: body?.appSwitchContext || null,
});
        }
      }

      if (url.pathname === "/paypal/checkout/lifetime") {
        if (request.method === "GET") {
          const uid = url.searchParams.get("uid");
          const response = await createLifetimeCheckout(request, env, uid, { returnUrl: url.searchParams.get("returnUrl") || "", cancelUrl: url.searchParams.get("cancelUrl") || "" });
          const data = await response.clone().json();
          if (data?.approveUrl) return Response.redirect(data.approveUrl, 302);
          return response;
        }
        if (request.method === "POST") {
          const body = await safeJson(request);
          return await createLifetimeCheckout(request, env, body?.uid, { returnUrl: body?.returnUrl || body?.appSwitchContext?.nativeApp?.returnAppUrl || "", cancelUrl: body?.cancelUrl || body?.appSwitchContext?.nativeApp?.cancelAppUrl || "", appSwitchContext: body?.appSwitchContext || null });
        }
      }

      if (url.pathname === "/paypal/verify" && request.method === "GET") {
        const result = await verifyPurchase(env, {
          plan: url.searchParams.get("plan"),
          uid: url.searchParams.get("uid"),
          orderId: url.searchParams.get("orderId") || url.searchParams.get("token"),
          subscriptionId: url.searchParams.get("subscriptionId"),
        });
        return json(result);
      }

      if (url.pathname === "/paypal/success" && request.method === "GET") {
        const plan = url.searchParams.get("plan");
        const uid = url.searchParams.get("uid");
        const orderId = url.searchParams.get("token") || url.searchParams.get("orderId");
        const subscriptionId = url.searchParams.get("subscription_id") || url.searchParams.get("subscriptionId");

        try {
          const result = await verifyPurchase(env, { plan, uid, orderId, subscriptionId });
          if (!result.verified) {
            return html(
              "Payment not confirmed",
              "PayPal returned to the store, but the payment has not reached a confirmed state yet. Please reopen Music Player and try again."
            );
          }

          const deepLink = new URL("musicplayer://paypal/success");
          deepLink.searchParams.set("plan", result.plan);
          if (result.orderId) deepLink.searchParams.set("orderId", result.orderId);
          if (result.subscriptionId) deepLink.searchParams.set("subscriptionId", result.subscriptionId);
          const appLink = deepLink.toString();
          const intentLink =
            "intent://paypal/success" +
            deepLink.search +
            "#Intent;scheme=musicplayer;package=com.musicplayer.app;end";
          const safeLink = appLink.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
          const safeIntent = intentLink.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
          const page =
            "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Premium activated</title>" +
            "<style>body{font-family:system-ui,sans-serif;background:linear-gradient(135deg,#5b21b6,#0891b2);color:#fff;min-height:100vh;display:grid;place-items:center;margin:0}main{text-align:center;max-width:520px;margin:24px;padding:32px;border-radius:30px;background:rgba(255,255,255,.14);backdrop-filter:blur(20px)}a{display:inline-block;margin-top:16px;padding:14px 22px;border-radius:999px;background:#fff;color:#4c1d95;text-decoration:none;font-weight:800}.secondary{margin-left:8px;background:rgba(255,255,255,.18);color:#fff}</style></head><body><main><div style=\"font-size:54px\">✓</div><h1>Premium activated</h1><p>Payment verified successfully. Returning to Music Player…</p>" +
            "<a href=\"" + safeLink + "\">Open Music Player</a><a class=\"secondary\" href=\"" + safeIntent + "\">Open app</a>" +
            "<script>(function(){var app=" + JSON.stringify(appLink) + ";var intent=" + JSON.stringify(intentLink) + ";try{window.location.replace(app)}catch(e){}setTimeout(function(){try{window.location.href=intent}catch(e){}},900)})();</script></main></body></html>";
          return new Response(page, {
            status: 200,
            headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
          });
        } catch (error) {
          return html(
            "Payment verification failed",
            error instanceof Error ? error.message : String(error)
          );
        }
      }

      if (url.pathname === "/paypal/cancel" && request.method === "GET") {
        const deepLink = new URL("musicplayer://paypal/cancel");
        return html("Payment cancelled", "No premium access was granted.", deepLink.toString());
      }

      return json({ error: "Not found" }, 404);
    } catch (error) {
      return json(
        {
          error: error instanceof Error ? error.message : String(error),
        },
        Number(error?.status) >= 400 ? Number(error.status) : 500
      );
    }
  },
};
