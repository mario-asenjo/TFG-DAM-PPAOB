import { authOptions, jsonOptions, request, ApiError, API_BASE } from "./client";

export function createHtmlReport(token, analysisId) {
  return request("/reports", jsonOptions("POST", { analysisId, type: "HTML" }, token));
}

export function listReports(token, limit = 50) {
  return request(`/reports?limit=${limit}`, authOptions(token));
}

export async function downloadReport(token, artifactId) {
  const response = await fetch(`${API_BASE}/reports/${artifactId}/download`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(error.message || "Report download failed", response.status);
  }

  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
  const fileName = decodeURIComponent(match?.[1] || match?.[2] || `${artifactId}.html`);

  return { blob, fileName };
}
