import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="flex-1 overflow-y-auto p-6 print:overflow-visible print:p-0">{children}</main>
      </div>
    </div>
  )
}
