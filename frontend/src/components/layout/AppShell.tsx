import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import { ErrorBanner } from '../ui/ErrorBanner'
import { useCandidate } from '../../context/CandidateContext'

export function AppShell({ children }: { children: ReactNode }) {
  const { error } = useCandidate()
  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-0">
          {error && (
            <div className="mb-4">
              <ErrorBanner message={`Couldn't load candidates: ${error}`} />
            </div>
          )}
          {children}
        </main>
      </div>
    </div>
  )
}
