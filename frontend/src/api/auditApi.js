import { authOptions, request } from "./client";

export function listAuditEvents(token, filters = {}) {
  const params = new URLSearchParams();
  const add = (key, value) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    params.set(key, String(value));
  };

  add("action", filters.action);
  add("result", filters.result);
  add("userId", filters.userId);
  add("analysisId", filters.analysisId);
  add("binaryId", filters.binaryId);
  add("from", filters.from);
  add("to", filters.to);
  add("limit", filters.limit ?? 50);
  add("offset", filters.offset ?? 0);

  return request(`/audit/events?${params.toString()}`, authOptions(token));
}
