import { credentialsOptions, jsonOptions, jsonOptionsWithCredentials, request, authOptions } from "./client";

export function login(email, password) {
  return request("/auth/login", jsonOptionsWithCredentials("POST", { email, password }));
}

export function register(email, password) {
  return request("/auth/register", jsonOptions("POST", { email, password }));
}

export function getCurrentUser(token) {
  return request("/auth/me", authOptions(token));
}

export function refreshSession() {
  return request("/auth/refresh", credentialsOptions("POST"));
}

export function logoutSession() {
  return request("/auth/logout", credentialsOptions("POST"));
}
