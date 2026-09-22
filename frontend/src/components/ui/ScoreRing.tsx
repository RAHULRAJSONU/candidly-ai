import { toneForScore } from './Badge'

const RING_COLORS: Record<string, string> = {
  green: 'var(--color-score-excellent)',
  blue: 'var(--color-score-strong)',
  amber: 'var(--color-score-good)',
  red: 'var(--color-score-weak)',
  slate: '#64748b',
  purple: '#7c3aed',
}

/** A circular percentage ring matching the mock's match-score/profile-completeness rings everywhere. */
export function ScoreRing({
  value,
  size = 96,
  strokeWidth = 8,
  label,
}: {
  /** 0-1 */
  value: number
  size?: number
  strokeWidth?: number
  label?: string
}) {
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius
  const clamped = Math.max(0, Math.min(1, value))
  const offset = circumference * (1 - clamped)
  const color = RING_COLORS[toneForScore(clamped)]

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#e2e8f0" strokeWidth={strokeWidth} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
        />
      </svg>
      <div className="absolute flex flex-col items-center justify-center">
        <span className="font-bold text-slate-900" style={{ fontSize: size * 0.22 }}>
          {Math.round(clamped * 100)}%
        </span>
        {label && <span className="text-[10px] text-slate-500">{label}</span>}
      </div>
    </div>
  )
}
