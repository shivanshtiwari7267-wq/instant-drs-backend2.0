/**
 * InstantDRS — JWT Authentication & Session Authorization
 * ========================================================
 * Uses the `jose` library (lightweight, zero-dependency, serverless-native).
 * Provides token generation, token verification, route authentication,
 * and session-level ownership authorization.
 */

import { SignJWT, jwtVerify } from 'jose';
import { unauthorized, forbidden, notFound } from './helpers.js';
import { query } from './db.js';

// Secret key for HS256 JWT signing
// In production, must be set via process.env.JWT_SECRET (min 32 characters)
const DEFAULT_DEV_SECRET = 'instantdrs-development-jwt-secret-key-32chars!';

export function getJwtSecretKey() {
  const secret = process.env.JWT_SECRET || DEFAULT_DEV_SECRET;
  if (!process.env.JWT_SECRET && process.env.NODE_ENV === 'production') {
    console.warn('[JWT WARNING] JWT_SECRET is not set in production! Using fallback.');
  }
  return new TextEncoder().encode(secret);
}

/**
 * Generate a signed JWT token for an authenticated user.
 *
 * @param {object} user
 * @param {number|string} user.userId   — User database ID
 * @param {string}        user.username — Username
 * @param {string}       [user.role]    — Role (default: 'USER')
 * @returns {Promise<string>} Signed JWT string
 */
export async function generateToken({ userId, username, role = 'USER' }) {
  const secretKey = getJwtSecretKey();

  return new SignJWT({
    userId: Number(userId),
    username,
    role,
  })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(String(userId))
    .setIssuedAt()
    .setExpirationTime('7d') // 7-day token validity
    .sign(secretKey);
}

/**
 * Verify and decode a JWT token string.
 *
 * @param {string} token
 * @returns {Promise<object>} Decoded token payload
 * @throws {Error} If token is invalid or expired
 */
export async function verifyToken(token) {
  const secretKey = getJwtSecretKey();
  const { payload } = await jwtVerify(token, secretKey);
  return payload;
}

/**
 * Authentication Middleware for Vercel Functions.
 * Extracts and verifies the Bearer token from the Authorization header.
 *
 * If missing or invalid, immediately sends a 401 Unauthorized response
 * and returns null (caller should return early).
 * If valid, returns the authenticated user payload: { userId, username, role }.
 *
 * @param {import('@vercel/node').VercelRequest}  req
 * @param {import('@vercel/node').VercelResponse} res
 * @returns {Promise<{userId: number, username: string, role: string}|null>}
 */
export async function requireAuth(req, res) {
  const authHeader = req.headers.authorization || req.headers.Authorization;

  if (!authHeader || typeof authHeader !== 'string') {
    unauthorized(res, 'Missing Authorization header. Expected: Bearer <token>');
    return null;
  }

  const parts = authHeader.trim().split(' ');
  if (parts.length !== 2 || parts[0].toLowerCase() !== 'bearer') {
    unauthorized(res, 'Invalid Authorization header format. Expected: Bearer <token>');
    return null;
  }

  const token = parts[1];

  try {
    const payload = await verifyToken(token);
    return {
      userId:   Number(payload.userId || payload.sub),
      username: payload.username,
      role:     payload.role || 'USER',
    };
  } catch (err) {
    unauthorized(res, `Invalid or expired token: ${err.message}`);
    return null;
  }
}

/**
 * Session Ownership Authorization Helper.
 * Confirms that:
 *  1. The session exists in the database.
 *  2. The authenticated user is the owner of the session (user_id matches).
 *
 * If the session does not exist, sends 404 Not Found and returns null.
 * If the session belongs to a different user, sends 403 Forbidden and returns null.
 * If authorized, returns the existing session row.
 *
 * @param {number} sessionId
 * @param {number} userId
 * @param {import('@vercel/node').VercelResponse} res
 * @returns {Promise<object|null>} The session database row, or null if denied
 */
export async function verifySessionOwnership(sessionId, userId, res) {
  const rows = await query(
    'SELECT * FROM drs_sessions WHERE id = ?',
    [sessionId]
  );

  if (rows.length === 0) {
    notFound(res, `DrsSession not found with id: ${sessionId}`);
    return null;
  }

  const session = rows[0];

  // Prevent cross-user access: if user_id is set and does not match, reject
  if (session.user_id != null && Number(session.user_id) !== Number(userId)) {
    forbidden(res, `Access denied: You do not have permission to access session ${sessionId}`);
    return null;
  }

  return session;
}
