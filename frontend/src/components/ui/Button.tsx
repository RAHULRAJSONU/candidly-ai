import type { ButtonHTMLAttributes, ReactNode } from 'react'
import clsx from 'clsx'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const variantClasses: Record<Variant, string> = {
  primary: 'bg-blue-600 text-white hover:bg-blue-700 shadow-sm',
  secondary: 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50',
  danger: 'bg-white text-red-600 border border-red-200 hover:bg-red-50',
  ghost: 'text-slate-600 hover:bg-slate-100',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  icon?: ReactNode
}

export function Button({ variant = 'primary', icon, className, children, ...rest }: ButtonProps) {
  return (
    <button
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        variantClasses[variant],
        className,
      )}
      {...rest}
    >
      {icon}
      {children}
    </button>
  )
}
