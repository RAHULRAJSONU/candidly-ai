import type { ReactNode } from 'react'
import clsx from 'clsx'

type Tone = 'green' | 'blue' | 'amber' | 'red' | 'slate' | 'purple'

const toneClasses: Record<Tone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  blue: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  amber: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/10',
  purple: 'bg-violet-50 text-violet-700 ring-violet-600/20',
}

export function Badge({ children, tone = 'slate', className }: { children: ReactNode; tone?: Tone; className?: string }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
        toneClasses[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

/** Score-to-tone mapping used consistently across match badges and score rings (docs/03 thresholds are separate - this is purely a display banding). */
export function toneForScore(score: number): Tone {
  if (score >= 0.9) return 'green'
  if (score >= 0.8) return 'blue'
  if (score >= 0.7) return 'amber'
  return 'red'
}

export function labelForScore(score: number): string {
  if (score >= 0.9) return 'Excellent match'
  if (score >= 0.8) return 'Strong match'
  if (score >= 0.7) return 'Good match'
  return 'Weak match'
}
