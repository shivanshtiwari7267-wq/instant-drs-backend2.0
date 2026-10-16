/**
 * GET  /api/drs/sessions        — List all sessions
 * POST /api/drs/sessions        — Create a new DRS session
 *
 * Preserved Android API contract from DrsSessionController:
 *
 * POST request body:
 *   { "game": "cricket", "status": "CREATED" }
 *
 * Response shape matches DrsSession entity JSON serialisation:
 *   {
 *     "id": 1,
 *     "game": "cricket",
 *     "status": "CREATED",
 *     "originalVideoPath": null,    // legacy field — kept for Android compat
 *     "processedVideoPath": null,   // legacy field — kept for Android compat
 *     "processedVideoUrl": null,    // NEW — CDN URL after Android-side processing
 *     "decision": null,
 *     "createdAt": "2026-10-17T02:00:00",
 *     "completedAt": null
 *   }
 *
 * The legacy `originalVideoPath` / `processedVideoPath` fields are always null
 * in the Vercel backend (files are not stored server-side), but are preserved
 * in the response so existing Android clients don't crash on null handling.
 */

import {
  handleCors, parseBody, ok, created, badReq, serverErr, methodNotAllowed
} from '../../../lib/helpers.js';
import { requireAuth } from '../../../lib/jwt.js';
import { query, execute } from '../../../lib/db.js';

/** Map a DB row to the DrsSession JSON shape Android expects */
function toSession(row) {
  return {
    id:                   Number(row.id),
    userId:               row.user_id != null ? Number(row.user_id) : null,
    game:                 row.game,
    status:               row.status,
    originalVideoPath:    null,                         // always null in Vercel backend
    processedVideoPath:   null,                         // always null in Vercel backend
    processedVideoUrl:    row.processed_video_url ?? null,
    decision:             row.decision ?? null,
    createdAt:            row.created_at   ? row.created_at.toISOString   ? row.created_at.toISOString() : row.created_at : null,
    completedAt:          row.completed_at ? row.completed_at.toISOString ? row.completed_at.toISOString() : row.completed_at : null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET', 'POST'])) return;

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  try {
    // -----------------------------------------------------------------------
    // GET /api/drs/sessions — list only sessions belonging to authenticated user
    // -----------------------------------------------------------------------
    if (req.method === 'GET') {
      const rows = await query(
        'SELECT * FROM drs_sessions WHERE user_id = ? ORDER BY created_at DESC',
        [user.userId]
      );
      return ok(res, rows.map(toSession));
    }

    // -----------------------------------------------------------------------
    // POST /api/drs/sessions — create session owned by authenticated user
    // -----------------------------------------------------------------------
    if (req.method === 'POST') {
      const body = await parseBody(req);

      const game   = (body?.game   ?? '').toString().trim();
      const status = (body?.status ?? 'CREATED').toString().trim() || 'CREATED';

      if (!game) {
        return badReq(res, 'game is required');
      }

      const { insertId } = await execute(
        'INSERT INTO drs_sessions (user_id, game, status, created_at) VALUES (?, ?, ?, NOW())',
        [user.userId, game, status]
      );

      // Fetch the full row to return identical shape
      const rows = await query(
        'SELECT * FROM drs_sessions WHERE id = ?',
        [Number(insertId)]
      );

      if (rows.length === 0) {
        return serverErr(res, 'Session created but could not be retrieved');
      }

      return ok(res, toSession(rows[0]));
    }

  } catch (err) {
    console.error('[/api/drs/sessions]', err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
