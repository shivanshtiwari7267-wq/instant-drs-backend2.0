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
  return url;
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
    throw new Error(
      `DATABASE_URL configuration error (${err.message}). Expected format: mysql://<user>:<password>@<host>/<database>`
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
