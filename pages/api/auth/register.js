import { query } from '@/lib/db/mysql';
import { hashPassword, validateEmail, validatePhone, validatePassword, validateCIN } from '@/lib/utils/validation';
import { generateToken } from '@/lib/utils/auth';

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'roheksarbi@gmail.com';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Méthode non autorisée' });
  }

  const { step } = req.body;

  if (step === 'verify-code') {
    return verifyCodeAndCreateAccount(req, res);
  }

  return requestVerificationCode(req, res);
}

async function requestVerificationCode(req, res) {
  const { email, firstName, lastName, phone, cin, role } = req.body;

  // Validation
  if (!email || !firstName || !lastName || !phone || !cin || !role) {
    return res.status(400).json({ error: 'Tous les champs sont obligatoires' });
  }

  if (!validateEmail(email)) {
    return res.status(400).json({ error: 'Format email invalide' });
  }

  if (!validatePhone(phone)) {
    return res.status(400).json({ error: 'Format téléphone invalide' });
  }

  if (!validateCIN(cin)) {
    return res.status(400).json({ error: 'Format CIN invalide' });
  }

  try {
    // Vérifier si l'utilisateur existe déjà
    const existing = await query('SELECT * FROM accounts WHERE email = ? OR phone = ?', [email, phone]);
    if (existing.length > 0) {
      return res.status(400).json({ error: 'Email ou téléphone déjà utilisé' });
    }

    // Générer code de vérification
    const verificationCode = Math.floor(100000 + Math.random() * 900000);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    // Stocker en cache (vous devriez utiliser Redis en production)
    global.verificationCodes = global.verificationCodes || {};
    global.verificationCodes[email] = {
      code: verificationCode,
      expiresAt,
      data: { email, firstName, lastName, phone, cin, role }
    };

    // Envoyer email (à implémenter avec nodemailer)
    console.log(`Verification code for ${email}: ${verificationCode}`);

    return res.status(200).json({ 
      message: 'Code de vérification envoyé',
      code: verificationCode // À retirer en production
    });
  } catch (error) {
    console.error('Registration error:', error);
    return res.status(500).json({ error: 'Erreur serveur' });
  }
}

async function verifyCodeAndCreateAccount(req, res) {
  const { email, verificationCode, password } = req.body;

  if (!email || !verificationCode || !password) {
    return res.status(400).json({ error: 'Code de vérification et mot de passe requis' });
  }

  if (!validatePassword(password)) {
    return res.status(400).json({ error: 'Le mot de passe doit contenir au moins 8 caractères' });
  }

  try {
    // Vérifier le code
    const codeData = global.verificationCodes?.[email];
    if (!codeData) {
      return res.status(400).json({ error: 'Code expiré ou invalide' });
    }

    if (new Date() > codeData.expiresAt) {
      delete global.verificationCodes[email];
      return res.status(400).json({ error: 'Code expiré' });
    }

    if (parseInt(verificationCode) !== codeData.code) {
      return res.status(400).json({ error: 'Code incorrect' });
    }

    // Créer le compte
    const { firstName, lastName, phone, cin, role } = codeData.data;
    const passwordHash = hashPassword(password);

    await query(
      'INSERT INTO accounts (email, firstName, lastName, phone, cin, role, passwordHash, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())',
      [email, firstName, lastName, phone, cin, role, passwordHash]
    );

    // Nettoyer
    delete global.verificationCodes[email];

    // Générer token
    const token = generateToken({ email, role, firstName, lastName });

    return res.status(201).json({
      message: 'Compte créé avec succès',
      token,
      user: { email, role, firstName, lastName }
    });
  } catch (error) {
    console.error('Verification error:', error);
    return res.status(500).json({ error: 'Erreur serveur' });
  }
}
