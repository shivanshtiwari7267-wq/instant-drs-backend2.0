/**
 * InstantDRS — Shared API Utilities
 * ===================================
 * CORS headers, JSON response helpers, and request body parser.
 * Used by every Vercel Function in this project.
 */

// ---------------------------------------------------------------------------
// CORS
// ---------------------------------------------------------------------------

/** Standard CORS headers matching the original Spring Boot / gateway config */
export const CORS_HEADERS = {
  'Access-Control-Allow-Origin':  '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
  'Access-Control-Allow-Headers':
    'Content-Type, Authorization, X-Requested-With, Accept, X-Api-Version',
  'Access-Control-Allow-Credentials': 'true',
};

/**
 * Apply CORS headers and handle OPTIONS preflight.
 * Returns true if the request was a preflight (caller should return).
 *
 * @param {import('@vercel/node').VercelRequest}  req
 * @param {import('@vercel/node').VercelResponse} res
 * @returns {boolean} true if OPTIONS preflight was handled
 */
export function handleCors(req, res) {
  Object.entries(CORS_HEADERS).forEach(([k, v]) => res.setHeader(k, v));
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return true;
  }
  return false;
}

// ---------------------------------------------------------------------------
// Response helpers
// ---------------------------------------------------------------------------

/**
 * Send a JSON response with the given status code.
 */
export function json(res, status, body) {
  res.setHeader('Content-Type', 'application/json');
  return res.status(status).json(body);
}

/** 200 OK */
export const ok          = (res, body) => json(res, 200, body);
/** 201 Created */
export const created     = (res, body) => json(res, 201, body);
/** 400 Bad Request */
export const badReq      = (res, msg)  => json(res, 400, { error: 'Bad Request',           message: msg, timestamp: now() });
/** 401 Unauthorized */
export const unauthorized= (res, msg)  => json(res, 401, { error: 'Unauthorized',          message: msg, timestamp: now() });
/** 403 Forbidden */
export const forbidden   = (res, msg)  => json(res, 403, { error: 'Forbidden',             message: msg, timestamp: now() });
/** 404 Not Found */
export const notFound    = (res, msg)  => json(res, 404, { error: 'Not Found',             message: msg, timestamp: now() });
/** 409 Conflict */
export const conflict    = (res, msg)  => json(res, 409, { error: 'Conflict',              message: msg, timestamp: now() });
/** 500 Internal Server Error */
export const serverErr   = (res, msg)  => json(res, 500, { error: 'Internal Server Error', message: msg, timestamp: now() });

/** ISO-8601 timestamp string */
export const now = () => new Date().toISOString();

// ---------------------------------------------------------------------------
// Body parser
// ---------------------------------------------------------------------------

/**
 * Read the raw request body and parse it as JSON.
 * Vercel Functions receive the body pre-parsed when using default config,
 * but this helper handles both parsed and raw stream forms gracefully.
 *
 * @param {import('@vercel/node').VercelRequest} req
 * @returns {Promise<object>}
 */
export async function parseBody(req) {
  // Vercel auto-parses JSON bodies when Content-Type is application/json
  if (req.body !== undefined) return req.body ?? {};

  // Fallback: manually drain the stream
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf-8');
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve({});
      }
    });
    req.on('error', reject);
  });
}

// ---------------------------------------------------------------------------
// Method guard
// ---------------------------------------------------------------------------

/**
 * Return true if the request method is not in the allowed list,
 * and send a 405 response automatically.
 */
export function methodNotAllowed(req, res, allowed = []) {
  if (!allowed.includes(req.method)) {
    res.setHeader('Allow', allowed.join(', '));
    json(res, 405, { error: 'Method Not Allowed', allowed });
    return true;
  }
  return false;
}
