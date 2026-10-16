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
function getConnection() {
  const url = process.env.DATABASE_URL;

  if (!url) {
    throw new Error(
      'DATABASE_URL environment variable is not set. ' +
      'Add it in your Vercel project → Settings → Environment Variables.'
    );
  }

  // PlanetScale's connect() accepts { url } directly, including host, credentials, database, and SSL options
  try {
    return connect({ url });
  } catch {
    const parsed = new URL(url);
    const database = parsed.pathname.replace(/^\//, '');
    return connect({
      host:     parsed.hostname,
      username: parsed.username,
      password: parsed.password,
      database: database || undefined,
    });
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
