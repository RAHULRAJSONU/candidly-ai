import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Candidate } from '../api/types'

interface CandidateContextValue {
  candidates: Candidate[]
  selected: Candidate | null
  selectedId: string | null
  setSelectedId: (id: string) => void
  loading: boolean
  refresh: () => void
}

const CandidateContext = createContext<CandidateContextValue | null>(null)

const STORAGE_KEY = 'candidly.selectedCandidateId'

export function CandidateProvider({ children }: { children: ReactNode }) {
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [selectedId, setSelectedIdState] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY))
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    setLoading(true)
    api.candidates
      .list()
      .then((list) => {
        setCandidates(list)
        setLoading(false)
        setSelectedIdState((current) => {
          if (current && list.some((c) => c.id === current)) return current
          return list[0]?.id ?? null
        })
      })
      .catch(() => setLoading(false))
  }, [version])

  const setSelectedId = (id: string) => {
    localStorage.setItem(STORAGE_KEY, id)
    setSelectedIdState(id)
  }

  const selected = useMemo(() => candidates.find((c) => c.id === selectedId) ?? null, [candidates, selectedId])

  return (
    <CandidateContext.Provider
      value={{ candidates, selected, selectedId, setSelectedId, loading, refresh: () => setVersion((v) => v + 1) }}
    >
      {children}
    </CandidateContext.Provider>
  )
}

export function useCandidate() {
  const ctx = useContext(CandidateContext)
  if (!ctx) throw new Error('useCandidate must be used within CandidateProvider')
  return ctx
}
