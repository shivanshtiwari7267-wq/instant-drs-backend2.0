/**
 * InstantDRS — Live Integration Verification Script
 * ===================================================
 * Runs live end-to-end tests against a deployed Vercel backend.
 *
 * Usage:
 *   node test/live-integration.js https://your-instantdrs.vercel.app
 *   # or against local dev server:
 *   node test/live-integration.js http://localhost:3000
 */

const baseUrl = (process.argv[2] || process.env.DEPLOYMENT_URL || '').trim().replace(/\/+$/, '');

if (!baseUrl) {
  console.error('Error: Deployment URL is required.');
  console.error('Usage: node test/live-integration.js <deployment-url>');
  console.error('Example: node test/live-integration.js https://instantdrs-vercel.app');
  process.exit(1);
}

console.log(`\n========================================================`);
console.log(` InstantDRS Live Backend Integration Verification`);
console.log(` Target: ${baseUrl}`);
console.log(`========================================================\n`);

async function api(path, options = {}) {
  const url = `${baseUrl}${path}`;
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  });

  let data;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    data = await res.json();
  } else {
    data = await res.text();
  }

  return { status: res.status, headers: res.headers, data };
}

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  [PASS] ${message}`);
    passed++;
  } else {
    console.error(`  [FAIL] ${message}`);
    failed++;
  }
}

async function run() {
  const runId = Math.floor(Math.random() * 100000);
  const usernameA = `test_user_a_${runId}`;
  const usernameB = `test_user_b_${runId}`;
  const password = `TestPass123!_${runId}`;

  let tokenA = null;
  let tokenB = null;
  let userIdA = null;
  let userIdB = null;
  let sessionIdA = null;
  let timelineEventId = null;

  // 1. Health check
  console.log('1. Health Check Endpoint');
  try {
    const res = await api('/api/gateway-health');
    assert(res.status === 200 && res.data.status === 'UP', 'GET /api/gateway-health returns status 200 UP');
  } catch (err) {
    assert(false, `GET /api/gateway-health failed: ${err.message}`);
  }

  // 2. User Registration (User A)
  console.log('\n2. User Registration (User A)');
  try {
    const res = await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username: usernameA, password }),
    });
    assert(res.status === 200 && res.data.success === true, `Register user A: ${usernameA}`);
    assert(typeof res.data.userId === 'number', 'Returns numeric userId');
    assert(typeof res.data.token === 'string' && res.data.token.length > 20, 'Returns valid JWT token');
    tokenA = res.data.token;
    userIdA = res.data.userId;
  } catch (err) {
    assert(false, `Register failed: ${err.message}`);
  }

  // 3. User Login (User A)
  console.log('\n3. User Login (User A)');
  try {
    const res = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: usernameA, password }),
    });
    assert(res.status === 200 && res.data.success === true, 'Login successful with BCrypt validation');
    assert(res.data.userId === userIdA, 'Login matches registered userId');
    assert(typeof res.data.token === 'string', 'Login returns fresh JWT token');
    tokenA = res.data.token;
  } catch (err) {
    assert(false, `Login failed: ${err.message}`);
  }

  // 4. User B Registration (for multi-tenant isolation testing)
  console.log('\n4. User B Registration (Tenant Isolation)');
  try {
    const res = await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username: usernameB, password }),
    });
    assert(res.status === 200 && res.data.success === true, `Register user B: ${usernameB}`);
    tokenB = res.data.token;
    userIdB = res.data.userId;
  } catch (err) {
    assert(false, `Register User B failed: ${err.message}`);
  }

  // 5. Unauthenticated Access Rejection
  console.log('\n5. Authentication Enforcement (Unauthenticated Request)');
  try {
    const res = await api('/api/drs/sessions');
    assert(res.status === 401, 'GET /api/drs/sessions without Authorization header rejected with 401 Unauthorized');
  } catch (err) {
    assert(false, `Auth rejection check failed: ${err.message}`);
  }

  // 6. Authenticated Session Creation (User A)
  console.log('\n6. Authenticated DRS Session Creation (User A)');
  try {
    const res = await api('/api/drs/sessions', {
      method: 'POST',
      headers: { Authorization: `Bearer ${tokenA}` },
      body: JSON.stringify({ game: 'cricket', status: 'CREATED' }),
    });
    assert(res.status === 200 && typeof res.data.id === 'number', 'POST /api/drs/sessions created session');
    assert(res.data.userId === userIdA, 'Session row is owned by User A');
    assert(res.data.originalVideoPath === null, 'Preserves originalVideoPath: null for Android contract');
    sessionIdA = res.data.id;
  } catch (err) {
    assert(false, `Create session failed: ${err.message}`);
  }

  // 7. List Sessions (User A sees only their own sessions)
  console.log('\n7. User Session List Scoping');
  try {
    const resA = await api('/api/drs/sessions', {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    assert(resA.status === 200 && Array.isArray(resA.data), 'GET /api/drs/sessions returns session array');
    assert(resA.data.some(s => s.id === sessionIdA), 'User A session list contains created session');

    // User B should NOT see User A's session
    const resB = await api('/api/drs/sessions', {
      headers: { Authorization: `Bearer ${tokenB}` },
    });
    assert(!resB.data.some(s => s.id === sessionIdA), 'User B session list DOES NOT contain User A session (tenant isolation)');
  } catch (err) {
    assert(false, `Session list scoping failed: ${err.message}`);
  }

  // 8. Multi-Tenant Session ID Tampering Protection
  console.log('\n8. Session ID Tampering Protection (Cross-User Access)');
  try {
    // User B attempts to read User A's session
    const readAttempt = await api(`/api/drs/sessions/${sessionIdA}`, {
      headers: { Authorization: `Bearer ${tokenB}` },
    });
    assert(readAttempt.status === 403, `User B reading User A session ${sessionIdA} rejected with 403 Forbidden`);

    // User B attempts to patch User A's session
    const patchAttempt = await api(`/api/drs/sessions/${sessionIdA}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${tokenB}` },
      body: JSON.stringify({ status: 'COMPLETED' }),
    });
    assert(patchAttempt.status === 403, `User B modifying User A session rejected with 403 Forbidden`);
  } catch (err) {
    assert(false, `Tampering protection test failed: ${err.message}`);
  }

  // 9. Timeline Event Creation & Retrieval (User A)
  console.log('\n9. Timeline Event Creation & Retrieval');
  try {
    const createEvent = await api(`/api/drs/sessions/${sessionIdA}/timeline`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${tokenA}` },
      body: JSON.stringify({
        eventType: 'BALL_PITCH',
        timestampMs: 1420,
        durationMs: 80,
        description: 'Middle stump pitching',
      }),
    });
    assert(createEvent.status === 201 && typeof createEvent.data.id === 'number', 'POST timeline event created with 201');
    assert(createEvent.data.type === 'BALL_PITCH', 'Duplicate alias "type" populated for Android');
    timelineEventId = createEvent.data.id;

    const listEvents = await api(`/api/drs/sessions/${sessionIdA}/timeline`, {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    assert(listEvents.status === 200 && listEvents.data.length > 0, 'GET timeline events list retrieved');
  } catch (err) {
    assert(false, `Timeline event operations failed: ${err.message}`);
  }

  // 10. Review Decision Creation & Retrieval
  console.log('\n10. Review Decision Operations');
  try {
    const createDecision = await api(`/api/drs/sessions/${sessionIdA}/decision`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${tokenA}` },
      body: JSON.stringify({
        decision: 'OUT',
        selectedEventId: timelineEventId,
        notes: 'Three reds — hitting stumps',
        reviewedBy: 'third_umpire_live_test',
      }),
    });
    assert(createDecision.status === 201 && createDecision.data.decision === 'OUT', 'POST decision created with 201');
    assert(createDecision.data.reviewDecision === 'OUT', 'Duplicate alias "reviewDecision" preserved');

    const getDecision = await api(`/api/drs/sessions/${sessionIdA}/decision`, {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    assert(getDecision.status === 200 && getDecision.data.decision === 'OUT', 'GET latest decision retrieved');

    const listDecisions = await api(`/api/drs/sessions/${sessionIdA}/decisions`, {
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    assert(listDecisions.status === 200 && listDecisions.data.length > 0, 'GET decisions audit trail retrieved');
  } catch (err) {
    assert(false, `Decision operations failed: ${err.message}`);
  }

  // 11. Cloudinary Signed Upload Token & Security
  console.log('\n11. Cloudinary Signed Upload Token');
  try {
    const res = await api(`/api/drs/sessions/${sessionIdA}/upload-token`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${tokenA}` },
    });
    if (res.status === 201) {
      assert(typeof res.data.signature === 'string' && res.data.signature.length === 40, 'Valid 40-char SHA-1 upload signature returned');
      assert(res.data.uploadUrl.startsWith('https://api.cloudinary.com/'), 'Upload URL points directly to Cloudinary CDN');
      assert(res.data.CLOUDINARY_API_SECRET === undefined && res.data.apiSecret === undefined, 'CLOUDINARY_API_SECRET is NOT leaked in response');
    } else if (res.status === 500 && res.data.message?.includes('Cloudinary environment variables')) {
      console.log('  [WARN] Cloudinary environment variables are not yet configured on this deployment.');
    } else {
      assert(false, `Unexpected response: ${res.status} ${JSON.stringify(res.data)}`);
    }
  } catch (err) {
    assert(false, `Upload token test failed: ${err.message}`);
  }

  // 12. Final Session Status Update (Android Flow Completion)
  console.log('\n12. Android Flow Completion (PATCH Session)');
  try {
    const patchRes = await api(`/api/drs/sessions/${sessionIdA}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${tokenA}` },
      body: JSON.stringify({
        status: 'COMPLETED',
        processedVideoUrl: `https://res.cloudinary.com/instantdrs/video/upload/v1/drs/sessions/${sessionIdA}/processed.mp4`,
      }),
    });
    assert(patchRes.status === 200 && patchRes.data.status === 'COMPLETED', 'Session successfully updated to COMPLETED');
    assert(patchRes.data.processedVideoUrl.includes('processed.mp4'), 'processedVideoUrl persisted');
  } catch (err) {
    assert(false, `Session patch failed: ${err.message}`);
  }

  console.log(`\n========================================================`);
  console.log(` Live Test Results: ${passed} Passed, ${failed} Failed`);
  console.log(`========================================================\n`);

  if (failed > 0) {
    process.exit(1);
  }
}

run().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
