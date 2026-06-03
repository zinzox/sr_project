// Load .env first, then .env.local (if present)
require('dotenv').config();
require('dotenv').config({ path: '.env.local' });
const mysql = require('mysql2/promise');

async function run() {
  const conn = await mysql.createConnection({
    host: process.env.MYSQL_HOST || 'localhost',
    user: process.env.MYSQL_USER || 'root',
    password: process.env.MYSQL_PASSWORD || '',
    database: process.env.MYSQL_DATABASE || 'sarbi_rohek',
    port: parseInt(process.env.MYSQL_PORT || '3306', 10),
  });

  console.log('Connected to MySQL, creating tables if missing...');

  const accountsSql = `
CREATE TABLE IF NOT EXISTS accounts (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE NOT NULL,
  firstName VARCHAR(100),
  lastName VARCHAR(100),
  phone VARCHAR(20),
  cin VARCHAR(20),
  role VARCHAR(50),
  passwordHash VARCHAR(255),
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`;

  const blacklistSql = `
CREATE TABLE IF NOT EXISTS moderation_blacklist (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255),
  phone VARCHAR(20),
  type VARCHAR(50),
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`;

  const bansSql = `
CREATE TABLE IF NOT EXISTS moderation_bans (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255),
  bannedUntil DATETIME,
  reason TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`;

  try {
    await conn.query(accountsSql);
    await conn.query(blacklistSql);
    await conn.query(bansSql);
    console.log('Tables created or already exist.');
  } catch (err) {
    console.error('Error creating tables:', err);
    process.exit(1);
  } finally {
    await conn.end();
  }
}

run().then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(1); });
