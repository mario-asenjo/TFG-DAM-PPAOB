import { authOptions, jsonOptions, request } from "./client";

export function listUsers(token) {
  return request("/admin/users", authOptions(token));
}

export function updateUserRoles(token, userId, roles) {
  return request(`/admin/users/${userId}/roles`, jsonOptions("PUT", { roles }, token));
}

export function updateUserEnabled(token, userId, enabled) {
  return request(`/admin/users/${userId}/enabled`, jsonOptions("PATCH", { enabled }, token));
}
