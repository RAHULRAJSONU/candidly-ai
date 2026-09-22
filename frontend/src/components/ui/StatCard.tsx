import type { ReactNode } from 'react'
import clsx from 'clsx'
import { Card } from './Card'

export function StatCard({
  icon,
  iconTone = 'blue',
  value,
  label,
  delta,
}: {
  icon: ReactNode
  iconTone?: 'blue' | 'green' | 'purple' | 'amber'
  value: ReactNode
  label: string
  delta?: string
}) {
  const iconBg: Record<string, string> = {
    blue: 'bg-blue-50 text-blue-600',
    green: 'bg-emerald-50 text-emerald-600',
    purple: 'bg-violet-50 text-violet-600',
    amber: 'bg-amber-50 text-amber-600',
  }
  return (
    <Card className="flex items-center gap-3">
      <div className={clsx('flex h-10 w-10 shrink-0 items-center justify-center rounded-full', iconBg[iconTone])}>
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <span className="text-xl font-bold text-slate-900">{value}</span>
          {delta && <span className="text-xs font-medium text-emerald-600">{delta}</span>}
        </div>
        <p className="truncate text-xs text-slate-500">{label}</p>
      </div>
    </Card>
  )
}

export function ProgressBar({ value, tone = 'blue' }: { value: number; tone?: 'blue' | 'green' | 'violet' | 'amber' }) {
  const barColor: Record<string, string> = {
    blue: 'bg-blue-600',
    green: 'bg-emerald-600',
    violet: 'bg-violet-600',
    amber: 'bg-amber-500',
  }
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
      <div className={clsx('h-full rounded-full', barColor[tone])} style={{ width: `${Math.round(value * 100)}%` }} />
    </div>
  )
}
