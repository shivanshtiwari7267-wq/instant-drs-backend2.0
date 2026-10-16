/**
 * InstantDRS — Auth Helpers
 * =========================
 * BCrypt hashing via bcryptjs — produces hashes identical to
 * spring-security-crypto BCryptPasswordEncoder, so all passwords
 * stored during Spring Boot operation remain valid.
 */

import bcrypt from 'bcryptjs';

const BCRYPT_ROUNDS = 10; // matches BCryptPasswordEncoder default strength

/**
 * Hash a plaintext password using BCrypt.
 * @param {string} password
 * @returns {Promise<string>} BCrypt hash
 */
export async function hashPassword(password) {
  return bcrypt.hash(password, BCRYPT_ROUNDS);
}

/**
 * Compare a plaintext password against a stored BCrypt hash.
 * @param {string} password  — plaintext
 * @param {string} hash      — BCrypt hash from database
 * @returns {Promise<boolean>}
 */
export async function verifyPassword(password, hash) {
  return bcrypt.compare(password, hash);
}
