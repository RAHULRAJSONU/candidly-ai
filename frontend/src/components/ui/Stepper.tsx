import { Check } from 'lucide-react'
import clsx from 'clsx'

export interface StepperStep {
  label: string
  description?: string
}

/** Vertical step indicator for multi-step wizards - numbered circles connected by a
 * line, filled blue for the current step and check-marked for completed ones. */
export function Stepper({ steps, currentIndex }: { steps: StepperStep[]; currentIndex: number }) {
  return (
    <ol className="space-y-0">
      {steps.map((step, i) => {
        const isDone = i < currentIndex
        const isCurrent = i === currentIndex
        return (
          <li key={step.label} className="relative flex gap-3 pb-8 last:pb-0">
            {i < steps.length - 1 && (
              <span
                className={clsx(
                  'absolute left-[15px] top-8 h-[calc(100%-1.5rem)] w-px',
                  isDone ? 'bg-blue-500' : 'bg-slate-200',
                )}
              />
            )}
            <span
              className={clsx(
                'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors',
                isDone && 'bg-blue-600 text-white',
                isCurrent && 'bg-blue-600 text-white ring-4 ring-blue-100',
                !isDone && !isCurrent && 'bg-slate-100 text-slate-400',
              )}
            >
              {isDone ? <Check size={15} /> : i + 1}
            </span>
            <div className="pt-1">
              <p
                className={clsx(
                  'text-sm font-medium',
                  isCurrent ? 'text-slate-900' : isDone ? 'text-slate-700' : 'text-slate-400',
                )}
              >
                {step.label}
              </p>
              {step.description && <p className="mt-0.5 text-xs text-slate-400">{step.description}</p>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
