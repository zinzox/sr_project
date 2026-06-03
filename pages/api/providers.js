import { query as dbQuery } from '../../lib/db/mysql';

export default async function handler(req, res) {
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: 'Method Not Allowed' });
  }

  try {
    const limit = Math.max(1, Math.min(100, parseInt(req.query.limit || '20', 10)));
    const sql = `SELECT first_name, last_name, phone, email, photo_url, service_type, main_activity, work_title, work_description, created_at FROM providers ORDER BY created_at DESC LIMIT ${limit}`;
    const providers = await dbQuery(sql);
    return res.status(200).json({ providers });
  } catch (err) {
    console.error('API /api/providers error:', err);
    return res.status(500).json({ error: 'Erreur serveur' });
  }
}
