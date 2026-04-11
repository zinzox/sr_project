import bcrypt from 'bcryptjs';

export function hashPassword(password) {
  const salt = bcrypt.genSaltSync(10);
  return bcrypt.hashSync(password, salt);
}

export function verifyPassword(password, hash) {
  return bcrypt.compareSync(password, hash);
}

export function validateEmail(email) {
  const emailPattern = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/;
  return emailPattern.test(email);
}

export function validatePhone(phone) {
  const phonePattern = /^[0-9+ ]{8,20}$/;
  return phonePattern.test(phone);
}

export function validateCIN(cin) {
  const cinPattern = /^[0-9]{8,12}$/;
  return cinPattern.test(cin);
}

export function validatePassword(password) {
  return password && password.length >= 8;
}
