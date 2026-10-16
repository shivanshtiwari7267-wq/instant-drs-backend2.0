/**
 * GET /api/drs/sessions/[id]/timeline/[eventId]
 * ===============================================
 * Returns a single timeline event.
 * Validates that the event belongs to the requested session — mirrors
 * TimelineEventService.getTimelineEvent() ownership check.
 *
 * Response shape: same as timeline/index.js (TimelineEventResponse)
 * Error 404 if: session not found, event not found, event belongs to a different session.
 */

import {
  handleCors, ok, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../../lib/jwt.js';
import { query } from '../../../../../lib/db.js';

function toEvent(row) {
  const eventType   = row.event_type;
  const timestampMs = Number(row.timestamp_ms);
  const durationMs  = row.duration_ms != null ? Number(row.duration_ms) : null;

  return {
    id:          Number(row.id),
    sessionId:   Number(row.session_id),
    eventType,
    type:        eventType,
    timestampMs,
    timestamp:   timestampMs,
    durationMs,
    duration:    durationMs,
    description: row.description ?? null,
    createdAt:   row.created_at ? (row.created_at.toISOString ? row.created_at.toISOString() : row.created_at) : null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET'])) return;

  const { id, eventId } = req.query;
  const sessionId = parseInt(id,      10);
  const evId      = parseInt(eventId, 10);

  if (!id || isNaN(sessionId)) return badReq(res, 'Invalid session id');
  if (!eventId || isNaN(evId)) return badReq(res, 'Invalid event id');

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  try {
    // Validate session exists and is owned by authenticated user
    const session = await verifySessionOwnership(sessionId, user.userId, res);
    if (!session) return;

    // Fetch event
    const rows = await query(
      'SELECT * FROM timeline_events WHERE id = ?',
      [evId]
    );

    if (rows.length === 0) {
      return notFound(res, `Timeline event not found with id: ${evId}`);
    }

    // Ownership check — event must belong to the requested session
    const event = rows[0];
    if (Number(event.session_id) !== sessionId) {
      return notFound(
        res,
        `Timeline event with id ${evId} does not belong to session id ${sessionId}`
      );
    }

    return ok(res, toEvent(event));

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}/timeline/${eventId}]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
