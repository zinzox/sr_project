const http = require('http');
const data = JSON.stringify({ email: 'azizweslati2319@gmail.com', password: 'testpassword' });

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
