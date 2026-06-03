require('dotenv').config();
require('dotenv').config({ path: '.env.local' });
const mysql = require('mysql2/promise');
const bcrypt = require('bcryptjs');

async function run() {
  const conn = await mysql.createConnection({
    host: process.env.MYSQL_HOST || 'localhost',
    user: process.env.MYSQL_USER || 'root',
    password: process.env.MYSQL_PASSWORD || '',
    database: process.env.MYSQL_DATABASE || 'sarbi_rohek',
    port: parseInt(process.env.MYSQL_PORT || '3306', 10),
  });

  const email = 'testuser@example.com';
  const password = 'TestPass123';
  const firstName = 'Test';
  const lastName = 'User';
  const phone = '00000000';
  const cin = '00000000';
  const role = 'CLIENT';

  try {
    const [rows] = await conn.execute('SELECT * FROM accounts WHERE email = ?', [email]);
    if (rows.length > 0) {
      console.log('User already exists:', email);
      return;
    }

    const passwordHash = bcrypt.hashSync(password, 10);

    await conn.execute(
      'INSERT INTO accounts (email, firstName, lastName, phone, cin, role, passwordHash) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [email, firstName, lastName, phone, cin, role, passwordHash]
    );

    console.log('Test user created:', email, 'password:', password);
  } catch (err) {
    console.error('Error creating test user:', err);
    process.exit(1);
  } finally {
    await conn.end();
  }
}

run();
