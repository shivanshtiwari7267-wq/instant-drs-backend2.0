/**
 * GET  /api/drs/sessions/[id]/timeline  — List timeline events (sorted by timestampMs ASC)
 * POST /api/drs/sessions/[id]/timeline  — Create a new timeline event
 *
 * Preserved Android API contract from TimelineEventController.
 *
 * POST request body (mirrors TimelineEventRequest + aliases):
 *   {
 *     "eventType": "BALL_PITCH",   // alias: "type"
 *     "timestampMs": 1400,          // alias: "timestamp", "eventTimestampMs"
 *     "durationMs": 100,            // alias: "duration", "eventDurationMs"  (optional)
 *     "description": "On middle stump"  (optional)
 *   }
 *
 * Response shape (mirrors TimelineEventResponse):
 *   {
 *     "id": 1,
 *     "sessionId": 42,
 *     "eventType": "BALL_PITCH",
 *     "type": "BALL_PITCH",          // duplicate alias field — Android may read either
 *     "timestampMs": 1400,
 *     "timestamp": 1400,             // duplicate alias
 *     "durationMs": 100,
 *     "duration": 100,               // duplicate alias
 *     "description": "On middle stump",
 *     "createdAt": "2026-10-17T02:00:00"
 *   }
 */

import {
  handleCors, parseBody, ok, created, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../../lib/jwt.js';
import { query, execute } from '../../../../../lib/db.js';

function toEvent(row) {
  const eventType   = row.event_type;
  const timestampMs = Number(row.timestamp_ms);
  const durationMs  = row.duration_ms != null ? Number(row.duration_ms) : null;

  return {
    id:          Number(row.id),
    sessionId:   Number(row.session_id),
    eventType,
    type:        eventType,       // alias field for Android compat
    timestampMs,
    timestamp:   timestampMs,     // alias field
    durationMs,
    duration:    durationMs,      // alias field
    description: row.description ?? null,
    createdAt:   row.created_at ? (row.created_at.toISOString ? row.created_at.toISOString() : row.created_at) : null,
  };
}

/** Resolve field aliases in the incoming request body */
function resolveBody(body) {
  return {
    eventType:   body.eventType   ?? body.type,
    timestampMs: body.timestampMs ?? body.timestamp ?? body.eventTimestampMs,
    durationMs:  body.durationMs  ?? body.duration  ?? body.eventDurationMs ?? null,
    description: body.description ?? null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET', 'POST'])) return;

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
    // Validate session exists and is owned by authenticated user
    // -----------------------------------------------------------------------
    const session = await verifySessionOwnership(sessionId, user.userId, res);
    if (!session) return;

    // -----------------------------------------------------------------------
    // GET — list all events sorted by timestampMs ASC
    // -----------------------------------------------------------------------
    if (req.method === 'GET') {
      const rows = await query(
        'SELECT * FROM timeline_events WHERE session_id = ? ORDER BY timestamp_ms ASC',
        [sessionId]
      );
      return ok(res, rows.map(toEvent));
    }

    // -----------------------------------------------------------------------
    // POST — create timeline event
    // -----------------------------------------------------------------------
    if (req.method === 'POST') {
      const raw  = await parseBody(req);
      const body = resolveBody(raw);

      // Validation (mirrors TimelineEventService.createTimelineEvent)
      if (!body.eventType || !body.eventType.toString().trim()) {
        return badReq(res, 'Event type cannot be blank');
      }

      if (body.timestampMs == null) {
        return badReq(res, 'Timestamp in milliseconds is required');
      }

      const timestampMs = Number(body.timestampMs);
      if (isNaN(timestampMs) || timestampMs < 0) {
        return badReq(res, 'Timestamp in milliseconds must be non-negative');
      }

      const durationMs = body.durationMs != null ? Number(body.durationMs) : null;
      if (durationMs !== null && durationMs < 0) {
        return badReq(res, 'Duration in milliseconds must be non-negative');
      }

      const eventType   = body.eventType.toString().trim();
      const description = body.description ?? null;

      const { insertId } = await execute(
        `INSERT INTO timeline_events
           (session_id, event_type, timestamp_ms, duration_ms, description, created_at)
         VALUES (?, ?, ?, ?, ?, NOW())`,
        [sessionId, eventType, timestampMs, durationMs, description]
      );

      const rows = await query(
        'SELECT * FROM timeline_events WHERE id = ?',
        [Number(insertId)]
      );

      if (rows.length === 0) {
        return serverErr(res, 'Event created but could not be retrieved');
      }

      return created(res, toEvent(rows[0]));
    }

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}/timeline]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
