/**
 * InstantDRS Lightweight Vercel API Gateway
 * Forwards supported REST API requests to the hosted Spring Boot backend.
 * 
 * Target Backend is dynamically read from process.env.BACKEND_URL.
 */

export const config = {
  api: {
    bodyParser: false, // Handle raw streams for multipart and json
    responseLimit: false
  }
};

export default async function handler(req, res) {
  // 1. CORS Preflight Handling
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // 2. Validate Target Backend Environment Variable
  const backendBaseUrl = process.env.BACKEND_URL;
  if (!backendBaseUrl) {
    return res.status(500).json({
      error: "BACKEND_URL is not configured",
      message: "Please set the BACKEND_URL environment variable in your Vercel project settings."
    });
  }

  const normalizedBackendUrl = backendBaseUrl.replace(/\/+$/, '');
  const requestUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const targetUrl = `${normalizedBackendUrl}${requestUrl.pathname}${requestUrl.search}`;

  // 3. Platform Constraint Check: Vercel 4.5MB Payload Limit
  const contentLength = parseInt(req.headers['content-length'] || '0', 10);
  const isVideoUpload = req.method === 'POST' && /\/api\/drs\/sessions\/\d+\/video/.test(requestUrl.pathname);

  if (isVideoUpload && contentLength > 4.5 * 1024 * 1024) {
    return res.status(413).json({
      error: "Payload Too Large for Vercel Gateway",
      message: "Vercel Serverless Functions enforce a strict 4.5MB request payload limit. Sports review videos must be uploaded directly to the backend.",
      directBackendUploadUrl: targetUrl
    });
  }

  // 4. Forward Request to Hosted Spring Boot Backend
  try {
    // Read incoming body buffer
    const chunks = [];
    for await (const chunk of req) {
      chunks.push(chunk);
    }
    const bodyBuffer = chunks.length > 0 ? Buffer.concat(chunks) : undefined;

    // Filter forward headers
    const forwardHeaders = {};
    for (const [key, value] of Object.entries(req.headers)) {
      const lowerKey = key.toLowerCase();
      if (!['host', 'connection', 'content-length'].includes(lowerKey)) {
        forwardHeaders[lowerKey] = value;
      }
    }

    if (bodyBuffer && bodyBuffer.length > 0) {
      forwardHeaders['content-length'] = bodyBuffer.length.toString();
    }

    const backendResponse = await fetch(targetUrl, {
      method: req.method,
      headers: forwardHeaders,
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : bodyBuffer,
      redirect: 'manual'
    });

    // Forward backend status
    res.status(backendResponse.status);

    // Forward backend headers
    backendResponse.headers.forEach((val, key) => {
      const lowerKey = key.toLowerCase();
      if (!['content-encoding', 'transfer-encoding', 'connection'].includes(lowerKey)) {
        res.setHeader(key, val);
      }
    });

    // Stream backend response body to client
    const arrayBuffer = await backendResponse.arrayBuffer();
    return res.send(Buffer.from(arrayBuffer));

  } catch (error) {
    console.error(`[Gateway Proxy Error] Target: ${targetUrl}`, error);
    return res.status(502).json({
      error: "Bad Gateway",
      message: "Unable to reach the InstantDRS backend.",
      targetUrl: targetUrl,
      details: error.message
    });
  }
}
