import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Target, Filter, CheckCircle2 } from 'lucide-react'
import { api } from '../api/client'
import type { MatchScorecard } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card } from '../components/ui/Card'
import { Badge, labelForScore, toneForScore } from '../components/ui/Badge'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { ScoreRing } from '../components/ui/ScoreRing'
import { StatCard } from '../components/ui/StatCard'
import { Tabs } from '../components/ui/Tabs'
import { formatCompRange } from '../lib/format'

// The backend has no "watchlist"/"hidden" concept - these tiers are derived client-side
// from compositeScore to approximate the mock's 5-way tab split without new persistence.
type Tier = 'all' | 'strong' | 'good' | 'watchlist' | 'hidden'
type SortBy = 'score' | 'recency'

function tierOf(m: MatchScorecard): Exclude<Tier, 'all'> {
  if (m.compositeScore >= 0.8) return 'strong'
  if (m.compositeScore >= 0.6) return 'good'
  if (m.compositeScore >= 0.4) return 'watchlist'
  return 'hidden'
}

export function Matches() {
  const { selected } = useCandidate()
  const [matches, setMatches] = useState<MatchScorecard[]>([])
  const [tab, setTab] = useState<Tier>('all')
  const [sortBy, setSortBy] = useState<SortBy>('score')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!selected) return
    setLoading(true)
    api.matches
      .listForCandidate(selected.id)
      .then((m) => setMatches(m))
      .finally(() => setLoading(false))
  }, [selected])

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  const tiers = { strong: 0, good: 0, watchlist: 0, hidden: 0 }
  for (const m of matches) tiers[tierOf(m)]++
  const shortlisted = matches.filter((m) => m.shortlisted).length

  const byTab = tab === 'all' ? matches : matches.filter((m) => tierOf(m) === tab)
  const visible = byTab
    .slice()
    .sort((a, b) => (sortBy === 'score' ? b.compositeScore - a.compositeScore : new Date(b.decidedAt).getTime() - new Date(a.decidedAt).getTime()))

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Match Explorer</h1>
          <p className="mt-1 text-sm text-slate-500">
            Deterministic composite scoring (docs/03 §2.1) &mdash; skills + experience are arithmetic, semantic fit and
            domain relevance are TypeSafe Score judgments.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <StatCard icon={<Target size={18} />} iconTone="blue" value={matches.length} label="Discovered matches" />
        <StatCard icon={<Filter size={18} />} iconTone="amber" value={tiers.strong + tiers.good} label="Strong or good fit" />
        <StatCard icon={<CheckCircle2 size={18} />} iconTone="green" value={shortlisted} label="Shortlisted" />
      </div>

      <Card padded={false} className="p-2">
        <div className="flex flex-wrap items-center justify-between gap-3 px-3 pt-2">
          <Tabs
            tabs={[
              { key: 'all', label: 'All', count: matches.length },
              { key: 'strong', label: 'Strong', count: tiers.strong },
              { key: 'good', label: 'Good', count: tiers.good },
              { key: 'watchlist', label: 'Watchlist', count: tiers.watchlist },
              { key: 'hidden', label: 'Hidden', count: tiers.hidden },
            ]}
            active={tab}
            onChange={(k) => setTab(k as Tier)}
          />
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as SortBy)}
            className="rounded-md border border-slate-200 px-2 py-1 text-sm text-slate-600 focus:border-blue-400 focus:outline-none"
          >
            <option value="score">Sort: Best score</option>
            <option value="recency">Sort: Most recent</option>
          </select>
        </div>

        <div className="divide-y divide-slate-100">
          {loading && <p className="p-4 text-sm text-slate-400">Loading&hellip;</p>}
          {!loading && visible.length === 0 && (
            <p className="p-4 text-sm text-slate-500">
              No matches yet. Go to Job Discovery, pick a posting, and score it against {selected.fullName}.
            </p>
          )}
          {visible.map((m) => (
            <Link
              key={m.id}
              to={`/matches/${m.id}`}
              className="flex items-center gap-4 p-4 transition-colors hover:bg-slate-50"
            >
              <ScoreRing value={m.compositeScore} size={52} strokeWidth={5} />
              <CompanyAvatar name={m.jobPosting.company} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-slate-900">{m.jobPosting.title}</p>
                <p className="truncate text-sm text-slate-500">{m.jobPosting.company}</p>
              </div>
              <div className="hidden gap-2 sm:flex">
                <Badge tone="slate">{m.jobPosting.remote ? 'Remote' : m.jobPosting.location}</Badge>
                <Badge tone="slate">{formatCompRange(m.jobPosting.compMinMinorUnits, m.jobPosting.compMaxMinorUnits)}</Badge>
              </div>
              <Badge tone={toneForScore(m.compositeScore)}>{labelForScore(m.compositeScore)}</Badge>
              {m.shortlisted && <Badge tone="green">Shortlisted</Badge>}
            </Link>
          ))}
        </div>
      </Card>
    </div>
  )
}
