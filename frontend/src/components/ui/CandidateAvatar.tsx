import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import { useCandidate } from '../../context/CandidateContext'

const PALETTE = ['#7c3aed', '#0f172a', '#16a34a', '#dc2626', '#2563eb', '#ea580c', '#0891b2', '#db2777']

function initialsFor(name: string) {
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?'
}

function colorFor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

/** The candidate's uploaded profile photo (GET /api/candidates/{id}/photo), round, falling
 * back to initials-on-color (matching CompanyAvatar's palette convention) when no photo has
 * been uploaded yet or the request 404s. `photoVersion` from CandidateContext busts the
 * browser's image cache after an upload/replace/delete so this doesn't keep showing a
 * stale cached image in the same session. */
export function CandidateAvatar({ candidateId, name, size = 40 }: { candidateId: string; name: string; size?: number }) {
  const { photoVersion } = useCandidate()
  const [errored, setErrored] = useState(false)

  // Re-attempt loading whenever the candidate or its photo changes - covers both "just
  // uploaded a first photo" (errored was true) and switching to a different candidate.
  useEffect(() => setErrored(false), [candidateId, photoVersion])

  return (
    <div
      className="relative flex shrink-0 items-center justify-center overflow-hidden rounded-full text-white"
      style={{ width: size, height: size, backgroundColor: colorFor(name || '?'), fontSize: size * 0.38 }}
    >
      <span className="font-bold">{initialsFor(name || '?')}</span>
      {!errored && (
        <img
          key={`${candidateId}-${photoVersion}`}
          src={`${api.candidates.photoUrl(candidateId)}?v=${photoVersion}`}
          alt={name}
          onError={() => setErrored(true)}
          className="absolute inset-0 h-full w-full object-cover"
        />
      )}
    </div>
  )
}
