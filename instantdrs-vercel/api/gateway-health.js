/**
 * InstantDRS Gateway Independent Health Check Endpoint
 * Returns gateway status immediately without contacting the Spring Boot backend.
 */

export default function handler(req, res) {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');
  res.setHeader('Content-Type', 'application/json');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const backendConfigured = Boolean(process.env.BACKEND_URL && process.env.BACKEND_URL.trim() !== '');

  return res.status(200).json({
    status: "UP",
    gateway: "InstantDRS API Gateway",
    service: "online",
    backendConfigured: backendConfigured,
    timestamp: new Date().toISOString()
  });
}
