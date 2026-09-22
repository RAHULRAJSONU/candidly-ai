import { useState, type KeyboardEvent } from 'react'
import { X } from 'lucide-react'

function parseChips(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** Chip-based tag input for comma-separated skill strings. Keeps the same
 * `value`/`onChange` contract as a plain comma-separated text field (a joined
 * string) so callers that already do `skills.split(',')...` don't need to change. */
export function SkillChipInput({
  value,
  onChange,
  placeholder = 'Type a skill and press Enter',
}: {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}) {
  const [draft, setDraft] = useState('')
  const chips = parseChips(value)

  const commit = (raw: string) => {
    const next = raw.trim()
    if (!next || chips.includes(next)) return
    onChange([...chips, next].join(', '))
  }

  const removeChip = (chip: string) => {
    onChange(chips.filter((c) => c !== chip).join(', '))
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      commit(draft)
      setDraft('')
    } else if (e.key === 'Backspace' && draft === '' && chips.length > 0) {
      removeChip(chips[chips.length - 1])
    }
  }

  return (
    <div className="flex min-h-[42px] flex-wrap items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-2 focus-within:border-blue-400">
      {chips.map((chip) => (
        <span
          key={chip}
          className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20"
        >
          {chip}
          <button
            type="button"
            onClick={() => removeChip(chip)}
            className="text-blue-500 hover:text-blue-700"
            aria-label={`Remove ${chip}`}
          >
            <X size={12} />
          </button>
        </span>
      ))}
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => {
          if (draft.trim()) {
            commit(draft)
            setDraft('')
          }
        }}
        placeholder={chips.length === 0 ? placeholder : ''}
        className="min-w-[120px] flex-1 border-none bg-transparent text-sm outline-none placeholder:text-slate-400"
      />
    </div>
  )
}
