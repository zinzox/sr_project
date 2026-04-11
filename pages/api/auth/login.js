import { query } from '@/lib/db/mysql';
import { verifyPassword, validateEmail, validatePassword } from '@/lib/utils/validation';
import { generateToken } from '@/lib/utils/auth';

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'roheksarbi@gmail.com';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Méthode non autorisée' });
  }

  const { email, password } = req.body;

  // Validation
  if (!email || !password) {
    return res.status(400).json({ error: 'Email et mot de passe obligatoires' });
  }

  if (!validateEmail(email)) {
    return res.status(400).json({ error: 'Format email invalide' });
  }

  if (!validatePassword(password)) {
    return res.status(400).json({ error: 'Le mot de passe doit contenir au moins 8 caractères' });
  }

  try {
    // Vérifier si le compte existe
    const accounts = await query('SELECT * FROM accounts WHERE email = ?', [email]);
    
    if (accounts.length === 0) {
      return res.status(400).json({ error: 'Compte introuvable. Inscris-toi d\'abord.' });
    }

    const account = accounts[0];

    // Vérifier le blacklist
    const blacklist = await query(
      'SELECT * FROM moderation_blacklist WHERE (email = ? OR phone = ?) AND type = "BLACKLIST"',
      [email, account.phone]
    );

    if (blacklist.length > 0) {
      return res.status(403).json({ 
        error: `Compte blacklisté. Contactez l'administrateur: ${ADMIN_EMAIL}` 
      });
    }

    // Vérifier le ban temporaire
    const tempBan = await query(
      'SELECT * FROM moderation_bans WHERE email = ? AND bannedUntil > NOW()',
      [email]
    );

    if (tempBan.length > 0) {
      return res.status(403).json({ error: 'Compte temporairement suspendu (5 minutes).' });
    }

    // Vérifier le mot de passe
    if (!verifyPassword(password, account.passwordHash)) {
      return res.status(401).json({ error: 'Mot de passe incorrect.' });
    }

    // Générer token
    const token = generateToken({
      email: account.email,
      role: account.role,
      firstName: account.firstName,
      lastName: account.lastName
    });

    return res.status(200).json({
      message: 'Connexion réussie',
      token,
      user: {
        email: account.email,
        role: account.role,
        firstName: account.firstName,
        lastName: account.lastName
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    return res.status(500).json({ error: 'Erreur serveur' });
  }
}
