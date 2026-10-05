/**
 * Music Player — PayPal Cloudflare Worker
 *
 * Keeps PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET server-side and
 * automatically provisions the PayPal catalog product + quarterly plan.
 *
 * Expected Cloudflare Worker secrets:
 *   PAYPAL_CLIENT_ID
 *   PAYPAL_CLIENT_SECRET
 *
 * Optional variables:
 *   PAYPAL_ENV = "sandbox" | "live" (default: sandbox)
 *   PUBLIC_BASE_URL = Worker public URL, used for checkout redirects.
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

function paypalBase(env) {
  return String(env.PAYPAL_ENV || "sandbox").toLowerCase() === "live"
    ? "https://api-m.paypal.com"
    : "https://api-m.sandbox.paypal.com";
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
    `${paypalBase(env)}/v1/oauth2/token`,
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

  const response = await fetch(`${paypalBase(env)}${path}`, {
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
    environment: String(env.PAYPAL_ENV || "sandbox").toLowerCase(),
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

async function createQuarterlyCheckout(request, env, uid) {
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
        application_context: {
          brand_name: "Music Player",
          user_action: "SUBSCRIBE_NOW",
          return_url: `${base}/paypal/success?plan=quarterly&uid=${encodeURIComponent(uid)}`,
          cancel_url: `${base}/paypal/cancel?plan=quarterly&uid=${encodeURIComponent(uid)}`,
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

async function createLifetimeCheckout(request, env, uid) {
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
        application_context: {
          brand_name: "Music Player",
          user_action: "PAY_NOW",
          return_url: `${base}/paypal/success?plan=lifetime&uid=${encodeURIComponent(uid)}`,
          cancel_url: `${base}/paypal/cancel?plan=lifetime&uid=${encodeURIComponent(uid)}`,
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
          environment: String(env.PAYPAL_ENV || "sandbox").toLowerCase(),
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
          const response = await createQuarterlyCheckout(request, env, uid);
          const data = await response.clone().json();
          if (data?.approveUrl) return Response.redirect(data.approveUrl, 302);
          return response;
        }
        if (request.method === "POST") {
          const body = await safeJson(request);
          return await createQuarterlyCheckout(request, env, body?.uid);
        }
      }

      if (url.pathname === "/paypal/checkout/lifetime") {
        if (request.method === "GET") {
          const uid = url.searchParams.get("uid");
          const response = await createLifetimeCheckout(request, env, uid);
          const data = await response.clone().json();
          if (data?.approveUrl) return Response.redirect(data.approveUrl, 302);
          return response;
        }
        if (request.method === "POST") {
          const body = await safeJson(request);
          return await createLifetimeCheckout(request, env, body?.uid);
        }
      }

      if (url.pathname === "/paypal/success") {
        return text("Payment approved. Return to Music Player to verify your premium access.");
      }

      if (url.pathname === "/paypal/cancel") {
        return text("Payment cancelled. No premium entitlement was granted.");
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
