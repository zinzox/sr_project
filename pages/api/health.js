export default function handler(req, res) {
  res.status(200).json({
    status: 'ok',
    message: 'API Sarbi Rohek est opérationnelle',
    timestamp: new Date().toISOString(),
    version: '1.0.0'
  });
}
