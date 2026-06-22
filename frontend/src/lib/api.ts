// lib/api.ts — thin fetch wrapper around the Java backend

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export async function fetchCase(caseId: string) {
  const res = await fetch(`${BASE_URL}/cases/${caseId}`);
  if (!res.ok) throw new Error(`GET /cases/${caseId} → ${res.status}`);
  return res.json();
}

export async function postFollowUp(caseId: string, payload: unknown) {
  const res = await fetch(`${BASE_URL}/cases/${caseId}/follow-ups`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`POST /cases/${caseId}/follow-ups → ${res.status}`);
  return res.json();
}

export async function postQuery(caseId: string, fieldPath: string, question: string) {
  const res = await fetch(`${BASE_URL}/queries`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ case_id: caseId, field_path: fieldPath, question }),
  });
  if (!res.ok) throw new Error(`POST /queries → ${res.status}`);
  return res.json();
}

export async function fetchQueries(caseId: string) {
  const res = await fetch(`${BASE_URL}/queries?caseId=${encodeURIComponent(caseId)}`);
  if (!res.ok) throw new Error(`GET /queries → ${res.status}`);
  return res.json();
}
