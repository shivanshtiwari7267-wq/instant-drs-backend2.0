/**
 * GET  /api/drs/sessions/[id]/decision        — Get latest review decision
 * GET  /api/drs/sessions/[id]/review-decision — Alias (same handler, routed via vercel.json)
 * POST /api/drs/sessions/[id]/decision        — Save a new review decision
 * POST /api/drs/sessions/[id]/review-decision — Alias
 *
 * Preserves the exact ReviewDecisionController + ReviewDecisionService behaviour:
 *
 * POST request body:
 *   {
 *     "decision":         "OUT",                           // required. alias: reviewDecision, userDecision
 *     "selectedEventId":  3,                               // optional. alias: eventId, timelineEventId
 *     "notes":            "Hitting stumps (Umpire's call)",// optional
 *     "reviewedBy":       "third_umpire",                  // optional
 *     "reviewedAt":       "2026-10-17T02:03:00"            // optional. alias: timestamp, reviewTimestamp
 *   }
 *
 * POST response 201 Created / GET response 200 OK (ReviewDecisionResponse shape):
 *   {
 *     "id": 1,
 *     "sessionId": 42,
 *     "decision": "OUT",
 *     "reviewDecision": "OUT",       // duplicate alias field
 *     "selectedEventId": 3,
 *     "notes": "...",
 *     "reviewedBy": "third_umpire",
 *     "reviewedAt": "2026-10-17T02:03:00",
 *     "createdAt":  "2026-10-17T02:03:01"
 *   }
 *
 * POST behaviour:
 *  1. Saves the decision.
 *  2. Updates drs_sessions.decision to match (so GET /sessions/{id} reflects it).
 *  Never calculates or auto-assigns the decision — only persists what the user provides.
 */

import {
  handleCors, parseBody, ok, created, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../lib/jwt.js';
import { query, execute } from '../../../../lib/db.js';

function toDecision(row) {
  const decision = row.decision;
  return {
    id:              Number(row.id),
    sessionId:       Number(row.session_id),
    decision,
    reviewDecision:  decision,   // duplicate alias for Android compat
    selectedEventId: row.selected_event_id != null ? Number(row.selected_event_id) : null,
    notes:           row.notes ?? null,
    reviewedBy:      row.reviewed_by ?? null,
    reviewedAt:      row.reviewed_at   ? (row.reviewed_at.toISOString   ? row.reviewed_at.toISOString()   : row.reviewed_at)   : null,
    createdAt:       row.created_at    ? (row.created_at.toISOString    ? row.created_at.toISOString()    : row.created_at)    : null,
  };
}

/** Resolve field aliases from incoming body */
function resolveBody(body) {
  return {
    decision:        body.decision        ?? body.reviewDecision ?? body.userDecision,
    selectedEventId: body.selectedEventId ?? body.eventId        ?? body.timelineEventId ?? null,
    notes:           body.notes           ?? null,
    reviewedBy:      body.reviewedBy      ?? null,
    reviewedAt:      body.reviewedAt      ?? body.timestamp      ?? body.reviewTimestamp ?? null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET', 'POST'])) return;

  const { id } = req.query;
  const sessionId = parseInt(id, 10);

  if (!id || isNaN(sessionId)) return badReq(res, 'Invalid session id');

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  try {
    // Validate session exists and is owned by authenticated user
    const session = await verifySessionOwnership(sessionId, user.userId, res);
    if (!session) return;

    // -----------------------------------------------------------------------
    // GET — latest decision
    // -----------------------------------------------------------------------
    if (req.method === 'GET') {
      const rows = await query(
        `SELECT * FROM review_decisions
         WHERE session_id = ?
         ORDER BY reviewed_at DESC
         LIMIT 1`,
        [sessionId]
      );

      if (rows.length === 0) {
        return notFound(res, `No review decision found for session id: ${sessionId}`);
      }

      return ok(res, toDecision(rows[0]));
    }

    // -----------------------------------------------------------------------
    // POST — save decision
    // -----------------------------------------------------------------------
    if (req.method === 'POST') {
      const raw  = await parseBody(req);
      const body = resolveBody(raw);

      if (!body.decision || !body.decision.toString().trim()) {
        return badReq(res, 'Decision cannot be blank');
      }

      const decision        = body.decision.toString().trim();
      const selectedEventId = body.selectedEventId != null ? Number(body.selectedEventId) : null;

      // Validate selectedEventId if provided
      if (selectedEventId !== null) {
        const evRows = await query(
          'SELECT id, session_id FROM timeline_events WHERE id = ?',
          [selectedEventId]
        );

        if (evRows.length === 0) {
          return badReq(res, `Selected timeline event not found with id: ${selectedEventId}`);
        }

        if (Number(evRows[0].session_id) !== sessionId) {
          return badReq(
            res,
            `Selected event with id ${selectedEventId} does not belong to session id ${sessionId}`
          );
        }
      }

      const reviewedAt = body.reviewedAt ?? new Date().toISOString();

      const { insertId } = await execute(
        `INSERT INTO review_decisions
           (session_id, decision, selected_event_id, notes, reviewed_by, reviewed_at, created_at)
         VALUES (?, ?, ?, ?, ?, ?, NOW())`,
        [sessionId, decision, selectedEventId, body.notes, body.reviewedBy, reviewedAt]
      );

      // Update session.decision — same side-effect as ReviewDecisionService.saveDecision()
      await execute(
        'UPDATE drs_sessions SET decision = ? WHERE id = ?',
        [decision, sessionId]
      );

      const rows = await query(
        'SELECT * FROM review_decisions WHERE id = ?',
        [Number(insertId)]
      );

      if (rows.length === 0) {
        return serverErr(res, 'Decision saved but could not be retrieved');
      }

      return created(res, toDecision(rows[0]));
    }

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}/decision]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
