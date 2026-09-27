import { Bell, ChevronDown, MapPin, Search } from 'lucide-react'
import { useCandidate } from '../../context/CandidateContext'
import { CandidateAvatar } from '../ui/CandidateAvatar'

export function TopBar() {
  const { candidates, selectedId, setSelectedId, selected } = useCandidate()

  return (
    <header className="flex items-center gap-4 border-b border-slate-200 bg-white px-6 py-3 print:hidden">
      <div className="relative flex-1 max-w-xl">
        <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
        <input
          type="text"
          placeholder="Search jobs, companies, skills, or anything..."
          className="w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pl-9 pr-3 text-sm text-slate-700 placeholder:text-slate-400 focus:border-blue-400 focus:bg-white focus:outline-none"
        />
      </div>

      <div className="ml-auto flex items-center gap-4">
        {/* Candidate switcher stands in for a real session/auth concept (see CandidateContext) -
            styled with a location-pin affordance to match the mock's location selector, since
            the candidate's `location` field is the closest real data this app has to it. */}
        <div className="relative">
          <MapPin className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
          <select
            value={selectedId ?? ''}
            onChange={(e) => setSelectedId(e.target.value)}
            className="appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-8 pr-8 text-sm font-medium text-slate-700 focus:border-blue-400 focus:outline-none"
          >
            {candidates.length === 0 && <option value="">No candidates yet</option>}
            {candidates.map((c) => (
              <option key={c.id} value={c.id}>
                {c.fullName} — {c.location}
              </option>
            ))}
          </select>
          <ChevronDown className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
        </div>

        <button className="relative rounded-full p-2 text-slate-500 hover:bg-slate-100">
          <Bell size={18} />
          <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-red-500" />
        </button>

        <div className="flex items-center gap-2">
          {selected ? (
            <CandidateAvatar candidateId={selected.id} name={selected.fullName} size={32} />
          ) : (
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-300 text-xs font-bold text-white">?</div>
          )}
          <div className="leading-tight">
            <p className="text-sm font-semibold text-slate-800">{selected?.fullName ?? 'No candidate'}</p>
            <p className="text-xs text-slate-400">Job Seeker</p>
          </div>
          <ChevronDown className="text-slate-400" size={16} />
        </div>
      </div>
    </header>
  )
}
