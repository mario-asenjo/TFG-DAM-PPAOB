import { authOptions, request } from "./client";

export function listBinaries(token) {
  return request("/binaries", authOptions(token));
}

export function uploadBinary(token, file) {
  const formData = new FormData();
  formData.append("file", file);

  return request("/binaries", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });
}
