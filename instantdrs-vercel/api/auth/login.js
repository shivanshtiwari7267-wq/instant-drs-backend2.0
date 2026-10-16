/**
 * POST /api/auth/login
 * ====================
 * Authenticates a user and returns their profile.
 *
 * Request body:
 *   { "username": "alice", "password": "secret123" }
 *
 * Response 200 OK (success):
 *   { "success": true, "message": "Login successful", "userId": 42, "username": "alice" }
 *
 * Response 200 OK (failure — matches Spring Boot behaviour):
 *   { "success": false, "message": "Invalid username or password" }
 *
 * Deliberately uses the same generic failure message for both "user not found"
 * and "wrong password" to avoid username enumeration, matching AuthService.login().
 */

import { handleCors, parseBody, ok, serverErr } from '../../lib/helpers.js';
import { verifyPassword } from '../../lib/auth.js';
import { generateToken } from '../../lib/jwt.js';
import { query } from '../../lib/db.js';

const INVALID_MSG = 'Invalid username or password';

export default async function handler(req, res) {
  if (handleCors(req, res)) return;

  if (req.method !== 'POST') {
    return ok(res, { success: false, message: 'Method not allowed' });
  }

  try {
    const body = await parseBody(req);

    const username = (body?.username ?? '').toString().trim();
    const password = (body?.password ?? '').toString();

    if (!username || !password) {
      return ok(res, { success: false, message: INVALID_MSG, userId: null, username: null });
    }

    // --- Look up user ---
    const rows = await query(
      'SELECT id, username, password FROM users WHERE username = ?',
      [username]
    );

    if (rows.length === 0) {
      return ok(res, { success: false, message: INVALID_MSG, userId: null, username: null });
    }

    const user = rows[0];

    // --- BCrypt verify (compatible with Spring Security stored hashes) ---
    const valid = await verifyPassword(password, user.password);

    if (!valid) {
      return ok(res, { success: false, message: INVALID_MSG, userId: null, username: null });
    }

    const token = await generateToken({
      userId:   Number(user.id),
      username: user.username,
      role:     'USER',
    });

    return ok(res, {
      success:  true,
      message:  'Login successful',
      userId:   Number(user.id),
      username: user.username,
      token:    token,
    });

  } catch (err) {
    console.error('[POST /api/auth/login]', err);
    return serverErr(res, 'Login failed due to a server error');
  }
}
