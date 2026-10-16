/**
 * InstantDRS — PlanetScale Database Connection
 * =============================================
 * Uses the @planetscale/database HTTP driver.
 * No persistent TCP connections required — safe for serverless functions.
 *
 * Required Vercel Environment Variable:
 *   DATABASE_URL=mysql://<user>:<password>@<host>/<database>?ssl={"rejectUnauthorized":true}
 */

import { connect } from '@planetscale/database';

/**
 * Build a PlanetScale connection from DATABASE_URL.
 * Called once per function invocation (connections are HTTP, so no pooling needed).
 */
function sanitizeDatabaseUrl(raw) {
  if (!raw) return '';
  let url = raw.trim();

  // Strip leading variable assignment if user pasted DATABASE_URL=...
  if (url.startsWith('DATABASE_URL=')) {
    url = url.slice(13).trim();
  }

  // Strip surrounding single or double quotes
  if ((url.startsWith("'") && url.endsWith("'")) || (url.startsWith('"') && url.endsWith('"'))) {
    url = url.slice(1, -1).trim();
  }

  // Strip JDBC prefix (e.g. jdbc:mysql:// -> mysql://) which breaks URL parsing
  if (url.toLowerCase().startsWith('jdbc:')) {
    url = url.slice(5).trim();
  }

  // If missing scheme, prepend mysql://
  if (!url.includes('://')) {
    url = `mysql://${url}`;
  }

  // PlanetScale HTTP API uses HTTPS port 443, not TCP MySQL port 3306
  if (url.includes('.psdb.cloud:3306')) {
    url = url.replace(':3306', '');
  }

  return url;
}

function inspectUrlSafely(str) {
  if (!str) return 'EMPTY';
  const len = str.length;
  const start = str.slice(0, 8);
  const end = str.slice(-8);
  const hasAt = str.includes('@');
  const hasColon = str.includes(':');
  const hasSlash = str.includes('/');
  return `length=${len}, startsWith=${JSON.stringify(start)}, endsWith=${JSON.stringify(end)}, hasAt=${hasAt}, hasColon=${hasColon}, hasSlash=${hasSlash}`;
}

function getConnection() {
  const rawUrl = process.env.DATABASE_URL;

  if (!rawUrl) {
    throw new Error(
      'DATABASE_URL environment variable is not set. ' +
      'Add it in your Vercel project → Settings → Environment Variables.'
    );
  }

  const url = sanitizeDatabaseUrl(rawUrl);

  try {
    return connect({ url });
  } catch (err) {
    // If connect({ url }) failed with Invalid URL, attempt manual parsing of host/username/password
    try {
      // Regex match: mysql://[user]:[pass]@[host]:[port]/[database]
      const match = url.match(/^(?:mysql:\/\/)?([^:]+):([^@]+)@([^/:]+)(?::\d+)?(?:\/([^?]+))?/i);
      if (match) {
        const [, username, password, host, database] = match;
        return connect({
          host,
          username,
          password,
          database: database || undefined,
        });
      }
    } catch {
      // ignore regex fallback failure
    }

    throw new Error(
      `DATABASE_URL configuration error (${err.message}; ${inspectUrlSafely(rawUrl)}). Expected format: mysql://<user>:<password>@<host>/<database>`
    );
  }
}

/**
 * Execute a SQL query and return all rows.
 *
 * @param {string} sql     — Parameterised SQL query (use ? for placeholders)
 * @param {Array}  params  — Ordered parameter array
 * @returns {Promise<Array>}
 */
export async function query(sql, params = []) {
  const conn = getConnection();
  const result = await conn.execute(sql, params);
  return result.rows;
}

/**
 * Execute an INSERT / UPDATE / DELETE and return the result metadata.
 * Includes `insertId` for INSERT statements.
 *
 * @param {string} sql
 * @param {Array}  params
 * @returns {Promise<{insertId: string|number, rowsAffected: number}>}
 */
export async function execute(sql, params = []) {
  const conn = getConnection();
  const result = await conn.execute(sql, params);
  return {
    insertId:     result.insertId,
    rowsAffected: result.rowsAffected,
  };
}
