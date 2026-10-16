/**
 * GET /api/drs/sessions/[id]/decisions
 * =====================================
 * Returns the full audit trail of all review decisions for a session,
 * sorted newest-to-oldest by reviewed_at — mirrors
 * ReviewDecisionService.getAllDecisionsForSession().
 *
 * Response: Array of ReviewDecisionResponse objects (same shape as decision.js)
 */

import {
  handleCors, ok, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../lib/jwt.js';
import { query } from '../../../../lib/db.js';

function toDecision(row) {
  const decision = row.decision;
  return {
    id:              Number(row.id),
    sessionId:       Number(row.session_id),
    decision,
    reviewDecision:  decision,
    selectedEventId: row.selected_event_id != null ? Number(row.selected_event_id) : null,
    notes:           row.notes ?? null,
    reviewedBy:      row.reviewed_by ?? null,
    reviewedAt:      row.reviewed_at  ? (row.reviewed_at.toISOString  ? row.reviewed_at.toISOString()  : row.reviewed_at)  : null,
    createdAt:       row.created_at   ? (row.created_at.toISOString   ? row.created_at.toISOString()   : row.created_at)   : null,
  };
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['GET'])) return;

  const { id } = req.query;
  const sessionId = parseInt(id, 10);

  if (!id || isNaN(sessionId)) return badReq(res, 'Invalid session id');

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  try {
    const session = await verifySessionOwnership(sessionId, user.userId, res);
    if (!session) return;

    const rows = await query(
      `SELECT * FROM review_decisions
       WHERE session_id = ?
       ORDER BY reviewed_at DESC`,
      [sessionId]
    );

    return ok(res, rows.map(toDecision));

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}/decisions]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
