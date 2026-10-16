/**
 * POST /api/drs/sessions/[id]/upload-token
 * ==========================================
 * Returns a signed Cloudinary upload token so the Android app can
 * upload the processed video directly to Cloudinary without routing
 * binary data through Vercel (which has a 4.5 MB request body limit).
 *
 * Flow:
 *  1. Android finishes on-device video processing.
 *  2. Android calls POST /api/drs/sessions/{id}/upload-token.
 *  3. This function generates a signed Cloudinary upload signature.
 *  4. Android uploads the video directly to Cloudinary using the signed params.
 *  5. Android receives the Cloudinary CDN URL.
 *  6. Android calls PATCH /api/drs/sessions/{id} with { processedVideoUrl, status: "COMPLETED" }.
 *
 * Required Vercel Environment Variables:
 *   CLOUDINARY_CLOUD_NAME   — e.g. "instantdrs"
 *   CLOUDINARY_API_KEY      — from Cloudinary dashboard
 *   CLOUDINARY_API_SECRET   — from Cloudinary dashboard
 *
 * Response 200 OK:
 *   {
 *     "cloudName":  "instantdrs",
 *     "apiKey":     "123456789",
 *     "timestamp":  1729100000,
 *     "signature":  "abc123...",
 *     "folder":     "drs/sessions/42",
 *     "publicId":   "drs/sessions/42/processed",
 *     "uploadUrl":  "https://api.cloudinary.com/v1_1/instantdrs/video/upload"
 *   }
 */

import crypto from 'crypto';
import {
  handleCors, created, badReq, notFound, serverErr, methodNotAllowed
} from '../../../../lib/helpers.js';
import { requireAuth, verifySessionOwnership } from '../../../../lib/jwt.js';
import { query } from '../../../../lib/db.js';

/**
 * Generate a Cloudinary signed upload signature.
 * See: https://cloudinary.com/documentation/upload_images#generating_authentication_signatures
 */
function signCloudinaryUpload(params, apiSecret) {
  // Sort params alphabetically, join as key=value&key=value, then SHA-1 with secret
  const paramString = Object.keys(params)
    .sort()
    .map(k => `${k}=${params[k]}`)
    .join('&');

  return crypto
    .createHash('sha1')
    .update(paramString + apiSecret)
    .digest('hex');
}

export default async function handler(req, res) {
  if (handleCors(req, res)) return;
  if (methodNotAllowed(req, res, ['POST'])) return;

  const { id } = req.query;
  const sessionId = parseInt(id, 10);

  if (!id || isNaN(sessionId)) return badReq(res, 'Invalid session id');

  // JWT Authentication — private DRS endpoint
  const user = await requireAuth(req, res);
  if (!user) return;

  // Validate environment
  const cloudName = process.env.CLOUDINARY_CLOUD_NAME;
  const apiKey    = process.env.CLOUDINARY_API_KEY;
  const apiSecret = process.env.CLOUDINARY_API_SECRET;

  if (!cloudName || !apiKey || !apiSecret) {
    return serverErr(
      res,
      'Cloudinary environment variables are not configured. ' +
      'Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET in Vercel.'
    );
  }

  try {
    // Validate session exists and is owned by authenticated user
    const session = await verifySessionOwnership(sessionId, user.userId, res);
    if (!session) return;

    const timestamp = Math.floor(Date.now() / 1000);
    const folder    = `drs/sessions/${sessionId}`;
    const publicId  = `${folder}/processed`;

    const paramsToSign = {
      folder,
      public_id:    publicId,
      resource_type:'video',
      timestamp,
    };

    const signature = signCloudinaryUpload(paramsToSign, apiSecret);

    return created(res, {
      cloudName,
      apiKey,
      timestamp,
      signature,
      folder,
      publicId,
      resourceType: 'video',
      uploadUrl: `https://api.cloudinary.com/v1_1/${cloudName}/video/upload`,
    });

  } catch (err) {
    console.error(`[/api/drs/sessions/${id}/upload-token]`, err);
    return serverErr(res, err.message ?? 'Unexpected server error');
  }
}
