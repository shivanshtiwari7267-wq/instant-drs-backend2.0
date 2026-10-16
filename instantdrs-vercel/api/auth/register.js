/**
 * POST /api/auth/register
 * =======================
 * Creates a new user account.
 *
 * Request body:
 *   { "username": "alice", "password": "secret123" }
 *
 * Response 200 OK:
 *   { "success": true, "message": "Registration successful", "userId": 42, "username": "alice" }
 *
 * Response 200 OK (failure — matches Spring Boot behaviour of returning 200 even on logical errors):
 *   { "success": false, "message": "Username already exists" }
 *
 * Validation rules mirror AuthService.register():
 *  - username: required, 1-50 chars
 *  - password: required, ≥ 4 chars
 */

import { handleCors, parseBody, ok, serverErr } from '../../lib/helpers.js';
import { hashPassword } from '../../lib/auth.js';
import { generateToken } from '../../lib/jwt.js';
import { query, execute } from '../../lib/db.js';

export default async function handler(req, res) {
  if (handleCors(req, res)) return;

  if (req.method !== 'POST') {
    return ok(res, { success: false, message: 'Method not allowed' });
  }

  try {
    const body = await parseBody(req);

    // --- Input validation (mirrors AuthService.register) ---
    const username = (body?.username ?? '').toString().trim();
    const password = (body?.password ?? '').toString();

    if (!username) {
      return ok(res, { success: false, message: 'Username cannot be blank', userId: null, username: null });
    }

    if (username.length > 50) {
      return ok(res, { success: false, message: 'Username cannot exceed 50 characters', userId: null, username: null });
    }

    if (!password) {
      return ok(res, { success: false, message: 'Password cannot be blank', userId: null, username: null });
    }

    if (password.length < 4) {
      return ok(res, { success: false, message: 'Password must be at least 4 characters', userId: null, username: null });
    }

    // --- Check duplicate username ---
    const existing = await query(
      'SELECT id FROM users WHERE username = ?',
      [username]
    );

    if (existing.length > 0) {
      return ok(res, { success: false, message: 'Username already exists', userId: null, username: null });
    }

    // --- Hash password (BCrypt, 10 rounds — identical to Spring Security) ---
    const hashedPassword = await hashPassword(password);

    // --- Persist user ---
    const { insertId } = await execute(
      'INSERT INTO users (username, password, role, created_at) VALUES (?, ?, ?, NOW())',
      [username, hashedPassword, 'USER']
    );

    const token = await generateToken({
      userId:   Number(insertId),
      username: username,
      role:     'USER',
    });

    return ok(res, {
      success:  true,
      message:  'Registration successful',
      userId:   Number(insertId),
      username: username,
      token:    token,
    });

  } catch (err) {
    console.error('[POST /api/auth/register]', err);
    return serverErr(res, 'Registration failed due to a server error');
  }
}
