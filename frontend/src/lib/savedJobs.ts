/** "Save Job" / "Not Interested" on Job Discovery are per-browser preferences only - there's
 * no backend model for either (no `POST /api/job-postings/{id}/save` etc. exists), so this
 * mirrors the same local-only, per-candidate-scoped pattern connectedAccounts.ts already uses
 * rather than faking a server round-trip. */

function key(kind: 'saved' | 'dismissed', candidateId: string): string {
  return `candidly.jobDiscovery.${kind}.${candidateId}`
}

function readSet(kind: 'saved' | 'dismissed', candidateId: string): Set<string> {
  try {
    const raw = localStorage.getItem(key(kind, candidateId))
    return new Set(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    return new Set()
  }
}

function writeSet(kind: 'saved' | 'dismissed', candidateId: string, ids: Set<string>): void {
  try {
    localStorage.setItem(key(kind, candidateId), JSON.stringify([...ids]))
  } catch {
    // best-effort only - this is a per-browser convenience, not a source of truth
  }
}

export function loadJobPrefs(candidateId: string): { saved: Set<string>; dismissed: Set<string> } {
  return { saved: readSet('saved', candidateId), dismissed: readSet('dismissed', candidateId) }
}

export function toggleSaved(candidateId: string, jobId: string, saved: Set<string>): Set<string> {
  const next = new Set(saved)
  if (next.has(jobId)) next.delete(jobId)
  else next.add(jobId)
  writeSet('saved', candidateId, next)
  return next
}

export function toggleDismissed(candidateId: string, jobId: string, dismissed: Set<string>): Set<string> {
  const next = new Set(dismissed)
  if (next.has(jobId)) next.delete(jobId)
  else next.add(jobId)
  writeSet('dismissed', candidateId, next)
  return next
}
