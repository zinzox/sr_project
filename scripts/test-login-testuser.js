const http = require('http');
const data = JSON.stringify({ email: 'testuser@example.com', password: 'TestPass123' });

const options = {
  hostname: 'localhost',
  port: 3001,
  path: '/api/auth/login',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = http.request(options, (res) => {
  console.log('status', res.statusCode);
  let body = '';
  res.on('data', (chunk) => { body += chunk; });
  res.on('end', () => { console.log('body:', body); });
});

req.on('error', (e) => { console.error('error:', e); });
req.write(data);
req.end();
