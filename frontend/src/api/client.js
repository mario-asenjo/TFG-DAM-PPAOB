export const API_BASE = import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1";

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function defaultHeaders(token) {
  const headers = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options);
  const payload = await parseResponsePayload(response);

  if (!response.ok) {
    const message = extractErrorMessage(payload, path);
    throw new ApiError(message, response.status);
  }

  return payload;
}

async function parseResponsePayload(response) {
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return response.json().catch(() => ({}));
  }

  const text = await response.text();
  if (!text) {
    return {};
  }

  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function extractErrorMessage(payload, path) {
  if (payload && typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }

  if (payload && typeof payload.error === "string" && payload.error.trim()) {
    return payload.error;
  }

  return `Request failed: ${path}`;
}

export function jsonOptions(method, body, token) {
  return {
    method,
    headers: {
      "Content-Type": "application/json",
      ...defaultHeaders(token)
    },
    body: JSON.stringify(body)
  };
}

export function authOptions(token) {
  return {
    headers: defaultHeaders(token)
  };
}

export function jsonOptionsWithCredentials(method, body, token) {
  return {
    ...jsonOptions(method, body, token),
    credentials: "include"
  };
}

export function credentialsOptions(method = "POST") {
  return {
    method,
    credentials: "include"
  };
}
