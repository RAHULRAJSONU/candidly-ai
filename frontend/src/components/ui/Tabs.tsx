import clsx from 'clsx'

export function Tabs({
  tabs,
  active,
  onChange,
}: {
  tabs: { key: string; label: string; count?: number }[]
  active: string
  onChange: (key: string) => void
}) {
  return (
    <div className="flex gap-6 border-b border-slate-200">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          className={clsx(
            'relative -mb-px flex items-center gap-1.5 border-b-2 px-1 pb-3 text-sm font-medium transition-colors',
            active === tab.key
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-700',
          )}
        >
          {tab.label}
          {tab.count !== undefined && (
            <span
              className={clsx(
                'rounded-full px-1.5 text-xs',
                active === tab.key ? 'bg-blue-50 text-blue-600' : 'bg-slate-100 text-slate-500',
              )}
            >
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  )
}
