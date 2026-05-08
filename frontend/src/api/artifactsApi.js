import { authOptions, request, ApiError, API_BASE } from "./client";

export function listArtifacts(token, { analysisId, type, limit = 50 } = {}) {
  const params = new URLSearchParams();
  if (analysisId) {
    params.set("analysisId", analysisId);
  }
  if (type) {
    params.set("type", type);
  }
  params.set("limit", String(limit));
  return request(`/artifacts?${params.toString()}`, authOptions(token));
}

export function downloadArtifactRaw(token, artifactId) {
  return downloadArtifact(token, artifactId, false);
}

export function downloadArtifactPretty(token, artifactId) {
  return downloadArtifact(token, artifactId, true);
}

async function downloadArtifact(token, artifactId, pretty) {
  const suffix = pretty ? "/pretty" : "";
  const response = await fetch(`${API_BASE}/artifacts/${artifactId}/download${suffix}`, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(error.message || "Artifact download failed", response.status);
  }

  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") || "";
  const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
  const fallbackExtension = pretty ? "json" : "bin";
  const fileName = decodeURIComponent(match?.[1] || match?.[2] || `${artifactId}.${fallbackExtension}`);

  return { blob, fileName };
}
