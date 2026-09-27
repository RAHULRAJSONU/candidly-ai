import { useMemo, useState } from 'react'
import { Search } from 'lucide-react'
import { ICON_STORE } from '../../lib/iconStore'
import { BrandIcon } from './BrandIcon'

/** The customization UI for the icon store: a searchable grid the user picks a brand icon
 * from when connecting a custom service/app (see ConnectedAccountsCard). Not a full color
 * picker - each store entry already carries its own brand-accurate color. */
export function IconPicker({
  value,
  onChange,
}: {
  value: string | null
  onChange: (iconId: string) => void
}) {
  const [query, setQuery] = useState('')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return ICON_STORE
    return ICON_STORE.filter((entry) => entry.name.toLowerCase().includes(q))
  }, [query])

  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="relative mb-2">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search icons..."
          className="w-full rounded-md border border-slate-200 py-1.5 pl-8 pr-2 text-sm focus:border-blue-400 focus:outline-none"
        />
      </div>
      <div className="grid max-h-48 grid-cols-6 gap-2 overflow-y-auto sm:grid-cols-8">
        {filtered.map((entry) => (
          <button
            key={entry.id}
            type="button"
            onClick={() => onChange(entry.id)}
            title={entry.name}
            className={`flex items-center justify-center rounded-lg p-1 ${
              value === entry.id ? 'ring-2 ring-blue-500' : 'hover:bg-slate-50'
            }`}
          >
            <BrandIcon iconId={entry.id} />
          </button>
        ))}
        {filtered.length === 0 && <p className="col-span-full py-4 text-center text-xs text-slate-400">No icons found.</p>}
      </div>
    </div>
  )
}
