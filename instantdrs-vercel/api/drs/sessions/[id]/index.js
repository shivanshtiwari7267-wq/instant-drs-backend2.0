/**
 * GET   /api/drs/sessions/[id]  — Get session by ID
 * PATCH /api/drs/sessions/[id]  — Update session (status, processedVideoUrl, decision, completedAt)
 *
 * The PATCH method is NEW — it is called by the Android app after on-device
 * video processing and Cloudinary upload are complete, to update the session
 * with the CDN URL and COMPLETED status.
 *
 * PATCH request body (all fields optional):
 *   {
 *     "status": "COMPLETED",
 *     "processedVideoUrl": "https://res.cloudinary.com/...",
 *     "decision": "OUT",
 *     "completedAt": "2026-10-17T02:00:00.000Z"
 *   }
 *
 * GET response shape (identical to Spring Boot DrsSession serialisation):
 *   {
 *     "id": 1,
 *     "game": "cricket",
 *     "status": "COMPLETED",
 *     "originalVideoPath": null,
 *     "processedVideoPath": null,
 *     "processedVideoUrl": "https://res.cloudinary.com/...",
 *     "decision": "OUT",
 *     "createdAt": "2026-10-17T02:00:00",
 *     "completedAt": "2026-10-17T02:01:30"
 *   }
 */

import {
  handleCors, parseBody, ok, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../lib/jwt.js';
import { query, execute } from '../../../../lib/db.js';

function toSession(row) {
  return {
    id:                 Number(row.id),
    userId:             row.user_id != null ? Number(row.user_id) : null,
    game:               row.game,
    status:             row.status,
    originalVideoPath:  null,
    processedVideoPath: null,
    processedVideoUrl:  row.processed_video_url ?? null,
    decision:           row.decision ?? null,
    createdAt:          row.created_at   ? (row.created_at.toISOString   ? row.created_at.toISOString()   : row.created_at)   : null,
    completedAt:        row.completed_at ? (row.completed_at.toISOString ? row.completed_at.toISOString() : row.completed_at) : null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET', 'PATCH'])) return;

  const { id } = req.query;
  const sessionId = parseInt(id, 10);

  if (!id || isNaN(sessionId)) {
    return badReq(res, 'Invalid session id');
  }

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  try {
    // -----------------------------------------------------------------------
    // GET /api/drs/sessions/{id}
    // -----------------------------------------------------------------------
    if (req.method === 'GET') {
      const session = await verifySessionOwnership(sessionId, user.userId, res);
      if (!session) return;

      return ok(res, toSession(session));
    }

    // -----------------------------------------------------------------------
    // PATCH /api/drs/sessions/{id}
    // Android calls this after local processing + Cloudinary upload
    // -----------------------------------------------------------------------
    if (req.method === 'PATCH') {
      // Confirm session exists and is owned by authenticated user
      const session = await verifySessionOwnership(sessionId, user.userId, res);
      if (!session) return;

      const body = await parseBody(req);

      const updates = [];
      const params  = [];

      if (body.status !== undefined) {
        updates.push('status = ?');
        params.push(body.status);
      }

      if (body.processedVideoUrl !== undefined) {
        updates.push('processed_video_url = ?');
        params.push(body.processedVideoUrl);
      }

      if (body.decision !== undefined) {
        updates.push('decision = ?');
        params.push(body.decision);
      }

      if (body.completedAt !== undefined) {
        updates.push('completed_at = ?');
        params.push(body.completedAt);
      } else if (body.status === 'COMPLETED') {
        // Auto-set completedAt when status transitions to COMPLETED
        updates.push('completed_at = NOW()');
      }

      if (updates.length === 0) {
        return badReq(res, 'No updatable fields provided');
      }

      params.push(sessionId);

      await execute(
        `UPDATE drs_sessions SET ${updates.join(', ')} WHERE id = ?`,
        params
      );

      // Return updated session
      const rows = await query(
        'SELECT * FROM drs_sessions WHERE id = ?',
        [sessionId]
      );

      return ok(res, toSession(rows[0]));
    }

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
