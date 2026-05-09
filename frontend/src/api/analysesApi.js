import { authOptions, jsonOptions, request } from "./client";

export function createAnalysis(token, binaryId, profile = "STATIC_BASELINE") {
  return request("/analyses", jsonOptions("POST", { binaryId, profile }, token));
}

export function getAnalysis(token, analysisId) {
  return request(`/analyses/${analysisId}`, authOptions(token));
}

export function listAnalyses(token, limit = 50) {
  const params = new URLSearchParams({ limit: String(limit) });
  return request(`/analyses?${params.toString()}`, authOptions(token));
}

export function getAnalysisResults(token, analysisId) {
  return request(`/analyses/${analysisId}/results`, authOptions(token));
}
