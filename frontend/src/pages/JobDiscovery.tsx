import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  RefreshCw,
  Search,
  MapPin,
  X,
  CheckCircle2,
  XCircle,
  Bookmark,
  BookmarkCheck,
  ThumbsDown,
  Sparkles,
  Info,
  ArrowRight,
  ChevronRight,
  ChevronDown,
  SlidersHorizontal,
} from 'lucide-react'
import { api } from '../api/client'
import type { Candidate, DiscoverySourceStatus, JobPosting, JobPostingSource, MatchScorecard } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card } from '../components/ui/Card'
import { Badge, toneForScore } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { BrandIcon } from '../components/ui/BrandIcon'
import { ScoreRing } from '../components/ui/ScoreRing'
import { ProgressBar } from '../components/ui/StatCard'
import { Tabs } from '../components/ui/Tabs'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import { formatRelativeTime, skillLabel } from '../lib/format'
import { formatCompRange, DEFAULT_CURRENCY, currencySymbol, toMinorUnits } from '../lib/currency'
import { loadJobPrefs, toggleDismissed, toggleSaved } from '../lib/savedJobs'

type DetailTab = 'overview' | 'why' | 'requirements' | 'company' | 'similar'

/** Mirrors CompositeScoringService's private weights (app/.../matching/CompositeScoringService.java)
 * purely for display labels/tones - the composite score itself always comes from the backend. */
const SCORE_ROWS: { key: keyof MatchScorecard; label: string; tone: 'blue' | 'violet' | 'green' | 'amber' }[] = [
  { key: 'skillScore', label: 'Skills Match', tone: 'blue' },
  { key: 'experienceScore', label: 'Experience Match', tone: 'violet' },
  { key: 'semanticScore', label: 'Semantic Fit', tone: 'green' },
  { key: 'domainScore', label: 'Domain Relevance', tone: 'amber' },
]

/** Mirrors EligibilityGateService.mandatorySkillMinimum (application.yml
 * candidly.thresholds.mandatory-skill-minimum: 0.60) - hand-kept in sync like the rest of
 * this frontend's DTOs, since there's no shared schema (see CLAUDE.md). */
const MANDATORY_SKILL_MINIMUM = 0.6

const SOURCE_ICON_ID: Record<JobPostingSource, string> = {
  GREENHOUSE: 'greenhouse',
  LEVER: 'lever',
  ASHBY: 'ashby',
  WORKDAY: 'workday',
  JSONLD: 'jsonld',
  MANUAL: 'custom',
}

const SOURCE_LABEL: Record<JobPostingSource, string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  ASHBY: 'Ashby',
  WORKDAY: 'Workday',
  JSONLD: 'Company career page',
  MANUAL: 'Manual',
}

/** A mock-aligned "label above, value+chevron below" filter pill that opens a small panel
 * of real controls on click - richer than a bare <select> while still only exposing filters
 * backed by real JobPosting fields (see the "no Job Type filter" note below). */
function FilterPill({
  label,
  value,
  isActive,
  isOpen,
  onToggle,
  children,
}: {
  label: string
  value: string
  isActive: boolean
  isOpen: boolean
  onToggle: () => void
  children: React.ReactNode
}) {
  return (
    <div className="relative">
      <button
        onClick={onToggle}
        className={`flex min-w-[8.5rem] flex-col items-start rounded-lg border px-3 py-1.5 text-left transition-colors ${
          isOpen ? 'border-blue-400 ring-1 ring-blue-100' : isActive ? 'border-blue-200 bg-blue-50/40' : 'border-slate-200 hover:border-slate-300'
        }`}
      >
        <span className="text-[11px] text-slate-400">{label}</span>
        <span className="flex items-center gap-1 text-sm font-medium text-slate-700">
          {value}
          <ChevronDown size={13} className={`text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
        </span>
      </button>
      {isOpen && (
        <div className="absolute left-0 top-full z-20 mt-1.5 w-64 rounded-lg border border-slate-200 bg-white p-3 shadow-lg">{children}</div>
      )}
    </div>
  )
}

interface EligibilityCheck {
  label: string
  passed: boolean
}

/** Client-side mirror of EligibilityGateService.evaluate - deterministic set/comparison logic
 * only, so it's safe to recompute here for display without another API round-trip. The
 * mandatory-skill-coverage check is the one line that depends on MANDATORY_SKILL_MINIMUM above. */
function computeEligibilityChecks(candidate: Candidate, job: JobPosting): EligibilityCheck[] {
  const authOk =
    job.acceptedWorkAuthorizations.length === 0 ||
    job.acceptedWorkAuthorizations.some((a) => candidate.workAuthorizations.includes(a))
  const locationOk = job.remote || job.location.toLowerCase() === candidate.location.toLowerCase()
  // Mirrors EligibilityGateService: minor units in different currencies aren't comparable
  // (no FX source), so a cross-currency posting can't fail this check - see CurrencyCodes.
  const compComparable = job.compMaxMinorUnits != null && job.currency === (candidate.preferredCurrency || DEFAULT_CURRENCY)
  const compOk = !compComparable || job.compMaxMinorUnits! >= candidate.compFloorMinorUnits
  const coverage =
    job.mandatorySkillIds.length === 0
      ? 1
      : job.mandatorySkillIds.filter((s) => candidate.skillIds.includes(s)).length / job.mandatorySkillIds.length

  return [
    { label: `Location preferences match (${job.remote ? 'Remote' : job.location})`, passed: locationOk },
    { label: 'Work authorization eligible', passed: authOk },
    { label: 'Compensation meets your floor', passed: compOk },
    { label: `Mandatory skills threshold met (${Math.round(coverage * 100)}%)`, passed: coverage >= MANDATORY_SKILL_MINIMUM },
  ]
}

export function JobDiscovery() {
  const { selected } = useCandidate()
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [matches, setMatches] = useState<MatchScorecard[]>([])
  const [query, setQuery] = useState('')
  const [selectedJob, setSelectedJob] = useState<JobPosting | null>(null)
  const [detailTab, setDetailTab] = useState<DetailTab>('overview')
  const [similarJobs, setSimilarJobs] = useState<JobPosting[]>([])
  const [loadingSimilar, setLoadingSimilar] = useState(false)
  const [polling, setPolling] = useState(false)
  const [pollResult, setPollResult] = useState<string | null>(null)
  const [scoring, setScoring] = useState(false)
  const [sources, setSources] = useState<DiscoverySourceStatus[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [howItWorksOpen, setHowItWorksOpen] = useState(false)
  const [openFilter, setOpenFilter] = useState<string | null>(null)
  const filterBarRef = useRef<HTMLDivElement>(null)

  const [jobPrefs, setJobPrefs] = useState<{ saved: Set<string>; dismissed: Set<string> }>({ saved: new Set(), dismissed: new Set() })

  // Filters - all client-side, no backend filter endpoint exists (see scope note).
  const [locationFilter, setLocationFilter] = useState('')
  const [workType, setWorkType] = useState<'any' | 'remote' | 'onsite'>('any')
  const [minYears, setMinYears] = useState(0)
  const [minCompK, setMinCompK] = useState('')
  const [maxCompK, setMaxCompK] = useState('')
  const [skillFilter, setSkillFilter] = useState('')
  const [eligibleOnly, setEligibleOnly] = useState(false)
  const [excludedSources, setExcludedSources] = useState<Set<JobPostingSource>>(new Set())

  const load = () => api.jobPostings.list().then(setJobs).catch((e) => setLoadError(describeError(e)))
  const loadSources = () => api.discovery.sources().then(setSources).catch((e) => setLoadError(describeError(e)))

  useEffect(() => {
    load()
    loadSources()
  }, [])

  useEffect(() => {
    if (!selectedJob || detailTab !== 'similar') return
    let cancelled = false
    setLoadingSimilar(true)
    api.jobPostings
      .similar(selectedJob.id)
      .then((result) => {
        if (!cancelled) setSimilarJobs(result)
      })
      .catch(() => {
        if (!cancelled) setSimilarJobs([])
      })
      .finally(() => {
        if (!cancelled) setLoadingSimilar(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedJob, detailTab])

  useEffect(() => {
    if (!openFilter) return
    const onClickOutside = (e: MouseEvent) => {
      if (filterBarRef.current && !filterBarRef.current.contains(e.target as Node)) setOpenFilter(null)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [openFilter])

  useEffect(() => {
    if (!selected) {
      setMatches([])
      setJobPrefs({ saved: new Set(), dismissed: new Set() })
      return
    }
    api.matches.listForCandidate(selected.id).then(setMatches).catch((e) => setLoadError(describeError(e)))
    setJobPrefs(loadJobPrefs(selected.id))
  }, [selected])

  const matchByJobId = useMemo(() => {
    const map = new Map<string, MatchScorecard>()
    for (const m of matches) map.set(m.jobPosting.id, m)
    return map
  }, [matches])

  const sourceCounts = useMemo(() => {
    const counts = new Map<JobPostingSource, number>()
    for (const j of jobs) counts.set(j.source, (counts.get(j.source) ?? 0) + 1)
    return counts
  }, [jobs])

  const lastPolledAt = useMemo(() => {
    const times = sources.map((s) => s.lastPolledAt).filter((t): t is string => !!t)
    if (times.length === 0) return null
    return times.reduce((latest, t) => (new Date(t) > new Date(latest) ? t : latest))
  }, [sources])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    const loc = locationFilter.trim().toLowerCase()
    const skill = skillFilter.trim().toLowerCase()
    // The Salary filter's numbers are entered in the selected candidate's currency (USD if
    // none selected) and only apply to postings in that same currency, for the same
    // not-comparable-across-currencies reason as compOk above.
    const filterCurrency = selected?.preferredCurrency || DEFAULT_CURRENCY
    const min = minCompK ? toMinorUnits(Number(minCompK) * 1000, filterCurrency) : null
    const max = maxCompK ? toMinorUnits(Number(maxCompK) * 1000, filterCurrency) : null
    return jobs.filter((j) => {
      if (jobPrefs.dismissed.has(j.id)) return false
      if (excludedSources.has(j.source)) return false
      if (q && !(j.title.toLowerCase().includes(q) || j.company.toLowerCase().includes(q) || j.domain?.toLowerCase().includes(q))) {
        return false
      }
      if (loc && !(j.remote ? 'remote'.includes(loc) : j.location.toLowerCase().includes(loc))) return false
      if (workType === 'remote' && !j.remote) return false
      if (workType === 'onsite' && j.remote) return false
      if (minYears > 0 && j.minYearsExperience < minYears) return false
      if ((min != null || max != null) && j.currency !== filterCurrency) return false
      if (min != null && j.compMaxMinorUnits != null && j.compMaxMinorUnits < min) return false
      if (max != null && j.compMinMinorUnits != null && j.compMinMinorUnits > max) return false
      if (skill && !j.mandatorySkillIds.some((s) => skillLabel(s).toLowerCase().includes(skill))) return false
      if (eligibleOnly && matchByJobId.get(j.id)?.hardEligibilityPassed !== true) return false
      return true
    })
  }, [jobs, query, locationFilter, workType, minYears, minCompK, maxCompK, skillFilter, eligibleOnly, excludedSources, jobPrefs, matchByJobId, selected])

  const activeFilterChips = useMemo(() => {
    const chips: { key: string; label: string; clear: () => void }[] = []
    if (locationFilter) chips.push({ key: 'loc', label: `Location: ${locationFilter}`, clear: () => setLocationFilter('') })
    if (workType !== 'any') chips.push({ key: 'work', label: workType === 'remote' ? 'Remote only' : 'On-site only', clear: () => setWorkType('any') })
    if (minYears > 0) chips.push({ key: 'years', label: `${minYears}+ years`, clear: () => setMinYears(0) })
    const filterSymbol = currencySymbol(selected?.preferredCurrency || DEFAULT_CURRENCY)
    if (minCompK) chips.push({ key: 'min', label: `Min ${filterSymbol}${minCompK}k`, clear: () => setMinCompK('') })
    if (maxCompK) chips.push({ key: 'max', label: `Max ${filterSymbol}${maxCompK}k`, clear: () => setMaxCompK('') })
    if (skillFilter) chips.push({ key: 'skill', label: `Skill: ${skillFilter}`, clear: () => setSkillFilter('') })
    if (eligibleOnly) chips.push({ key: 'eligible', label: 'Eligible roles only', clear: () => setEligibleOnly(false) })
    return chips
  }, [locationFilter, workType, minYears, minCompK, maxCompK, skillFilter, eligibleOnly, selected])

  const clearAllFilters = () => {
    setLocationFilter('')
    setWorkType('any')
    setMinYears(0)
    setMinCompK('')
    setMaxCompK('')
    setSkillFilter('')
    setEligibleOnly(false)
  }

  const runPoll = async () => {
    setPolling(true)
    setPollResult(null)
    try {
      const result = await api.discovery.poll()
      setPollResult(`Discovered ${result.discovered}, ingested ${result.ingested}.`)
      await load()
      await loadSources()
    } catch (e) {
      setPollResult(e instanceof Error ? e.message : 'Poll failed')
    } finally {
      setPolling(false)
    }
  }

  const scoreForCandidate = async () => {
    if (!selected || !selectedJob) return
    setScoring(true)
    try {
      const m = await api.matches.evaluate(selected.id, selectedJob.id)
      setMatches((prev) => [...prev.filter((p) => p.id !== m.id), m])
    } finally {
      setScoring(false)
    }
  }

  const toggleSave = (jobId: string) => {
    if (!selected) return
    setJobPrefs((prev) => ({ ...prev, saved: toggleSaved(selected.id, jobId, prev.saved) }))
  }

  const dismissJob = (jobId: string) => {
    if (!selected) return
    setJobPrefs((prev) => ({ ...prev, dismissed: toggleDismissed(selected.id, jobId, prev.dismissed) }))
    if (selectedJob?.id === jobId) setSelectedJob(null)
  }

  const selectedMatch = selectedJob ? matchByJobId.get(selectedJob.id) : undefined
  const eligibilityChecks = selected && selectedJob ? computeEligibilityChecks(selected, selectedJob) : []

  return (
    <div className="space-y-6">
      {loadError && <ErrorBanner message={`Couldn't load job postings: ${loadError}`} />}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Job Discovery</h1>
          <p className="mt-1 text-sm text-slate-500">
            Postings pulled from Greenhouse and Lever's real public APIs, screened before they ever reach here.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" icon={<Info size={16} />} onClick={() => setHowItWorksOpen((v) => !v)}>
            How it works?
          </Button>
          <Card className="flex items-center gap-3 py-2.5" padded={false}>
            <div className="pl-4">
              {lastPolledAt ? (
                <>
                  <p className="flex items-center gap-1.5 text-xs font-medium text-emerald-700">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> Last updated {formatRelativeTime(lastPolledAt)}
                  </p>
                  <p className="text-[11px] text-slate-400">
                    {sources.some((s) => s.enabled) ? 'Auto-sync runs hourly for enabled sources' : 'Auto-sync is off - manual poll only'}
                  </p>
                </>
              ) : (
                <p className="text-xs text-slate-500">No poll run yet</p>
              )}
            </div>
            <button
              onClick={runPoll}
              disabled={polling}
              className="mr-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-50"
              title="Poll for new postings"
            >
              <RefreshCw size={16} className={polling ? 'animate-spin' : ''} />
            </button>
          </Card>
        </div>
      </div>

      {howItWorksOpen && (
        <Card className="bg-blue-50/60">
          <p className="mb-2 text-sm font-semibold text-slate-900">How Job Discovery works</p>
          <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
            <li>Postings are pulled from Greenhouse's and Lever's public job-board APIs (no scraping, no login).</li>
            <li>Every posting is screened for prompt-injection attempts before it can reach matching or tailoring.</li>
            <li>Scoring runs a hard eligibility gate (location, work authorization, comp floor, mandatory skills) before a weighted composite score - a failing gate is never offset by a strong score elsewhere.</li>
            <li>The composite score blends real skill-overlap and experience math with two TypeSafe judgment calls (semantic fit, domain fit) over your actual work history.</li>
          </ul>
        </Card>
      )}

      {pollResult && (
        <div
          className={`rounded-lg border px-4 py-2.5 text-sm ${
            pollResult.startsWith('Discovered') ? 'border-blue-200 bg-blue-50 text-blue-700' : 'border-red-200 bg-red-50 text-red-700'
          }`}
        >
          {pollResult}
        </div>
      )}

      {sources.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {sources.map((s) => {
            const source = s.name.toUpperCase() as JobPostingSource
            const iconId = SOURCE_ICON_ID[source] ?? 'custom'
            const excluded = excludedSources.has(source)
            return (
              <button
                key={s.name}
                onClick={() =>
                  setExcludedSources((prev) => {
                    const next = new Set(prev)
                    if (next.has(source)) next.delete(source)
                    else next.add(source)
                    return next
                  })
                }
                className={`flex items-center gap-2.5 rounded-xl border px-3.5 py-2 text-left transition-colors ${
                  excluded ? 'border-slate-200 bg-slate-50 opacity-50' : 'border-slate-200 bg-white hover:border-slate-300'
                }`}
                title={s.lastPolledAt ? `Last poll: ${new Date(s.lastPolledAt).toLocaleString()}` : 'Never polled yet'}
              >
                <BrandIcon iconId={iconId} boxSize={32} size={16} />
                <span>
                  <span className="block text-sm font-medium text-slate-900">{SOURCE_LABEL[source] ?? s.name}</span>
                  <span className="block text-xs text-slate-500">
                    {sourceCounts.get(source) ?? 0} jobs{!s.enabled && ' · manual poll only'}
                  </span>
                </span>
                {excluded ? <X size={14} className="text-slate-400" /> : null}
              </button>
            )
          })}
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by title, company, or domain..."
              className="w-full rounded-lg border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm focus:border-blue-400 focus:outline-none"
            />
          </div>

          <div ref={filterBarRef}>
          <Card padded={false} className="overflow-visible p-4">
            <div className="flex flex-wrap items-center gap-2.5">
              <FilterPill
                label="Location"
                value={locationFilter || 'Any'}
                isActive={!!locationFilter}
                isOpen={openFilter === 'location'}
                onToggle={() => setOpenFilter((f) => (f === 'location' ? null : 'location'))}
              >
                <label className="flex items-center gap-1.5 rounded-md border border-slate-200 px-2 py-1.5 text-sm">
                  <MapPin size={14} className="shrink-0 text-slate-400" />
                  <input
                    autoFocus
                    value={locationFilter}
                    onChange={(e) => setLocationFilter(e.target.value)}
                    placeholder="City, state, or Remote"
                    className="w-full text-sm focus:outline-none"
                  />
                </label>
              </FilterPill>

              <FilterPill
                label="Work Type"
                value={workType === 'any' ? 'Any' : workType === 'remote' ? 'Remote' : 'On-site'}
                isActive={workType !== 'any'}
                isOpen={openFilter === 'workType'}
                onToggle={() => setOpenFilter((f) => (f === 'workType' ? null : 'workType'))}
              >
                <div className="space-y-1">
                  {([
                    ['any', 'Any'],
                    ['remote', 'Remote only'],
                    ['onsite', 'On-site only'],
                  ] as const).map(([val, lbl]) => (
                    <label key={val} className="flex cursor-pointer items-center gap-2 rounded-md px-1.5 py-1 text-sm text-slate-600 hover:bg-slate-50">
                      <input type="radio" name="workType" checked={workType === val} onChange={() => setWorkType(val)} />
                      {lbl}
                    </label>
                  ))}
                </div>
              </FilterPill>

              <FilterPill
                label="Experience"
                value={minYears === 0 ? 'Any' : `${minYears}+ years`}
                isActive={minYears > 0}
                isOpen={openFilter === 'experience'}
                onToggle={() => setOpenFilter((f) => (f === 'experience' ? null : 'experience'))}
              >
                <div className="space-y-1">
                  {[0, 2, 5, 8].map((y) => (
                    <label key={y} className="flex cursor-pointer items-center gap-2 rounded-md px-1.5 py-1 text-sm text-slate-600 hover:bg-slate-50">
                      <input type="radio" name="minYears" checked={minYears === y} onChange={() => setMinYears(y)} />
                      {y === 0 ? 'Any' : `${y}+ years`}
                    </label>
                  ))}
                </div>
              </FilterPill>

              <FilterPill
                label={`Salary (${selected?.preferredCurrency || DEFAULT_CURRENCY})`}
                value={
                  minCompK || maxCompK
                    ? `${currencySymbol(selected?.preferredCurrency || DEFAULT_CURRENCY)}${minCompK || '0'}k - ${currencySymbol(selected?.preferredCurrency || DEFAULT_CURRENCY)}${maxCompK || '∞'}k`
                    : 'Any'
                }
                isActive={!!(minCompK || maxCompK)}
                isOpen={openFilter === 'salary'}
                onToggle={() => setOpenFilter((f) => (f === 'salary' ? null : 'salary'))}
              >
                <div className="flex items-center gap-2">
                  <label className="flex items-center gap-1 text-sm text-slate-600">
                    Min {currencySymbol(selected?.preferredCurrency || DEFAULT_CURRENCY)}
                    <input
                      autoFocus
                      value={minCompK}
                      onChange={(e) => setMinCompK(e.target.value.replace(/[^0-9]/g, ''))}
                      placeholder="0"
                      className="w-16 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                    />
                    k
                  </label>
                  <label className="flex items-center gap-1 text-sm text-slate-600">
                    Max {currencySymbol(selected?.preferredCurrency || DEFAULT_CURRENCY)}
                    <input
                      value={maxCompK}
                      onChange={(e) => setMaxCompK(e.target.value.replace(/[^0-9]/g, ''))}
                      placeholder="300"
                      className="w-16 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                    />
                    k
                  </label>
                </div>
              </FilterPill>

              <FilterPill
                label="More Filters"
                value={[skillFilter && 'Skill', eligibleOnly && 'Eligible'].filter(Boolean).join(', ') || 'None'}
                isActive={!!skillFilter || eligibleOnly}
                isOpen={openFilter === 'more'}
                onToggle={() => setOpenFilter((f) => (f === 'more' ? null : 'more'))}
              >
                <div className="space-y-2.5">
                  <label className="flex items-center gap-1.5 text-sm text-slate-600">
                    <SlidersHorizontal size={13} className="shrink-0 text-slate-400" />
                    <input
                      autoFocus
                      value={skillFilter}
                      onChange={(e) => setSkillFilter(e.target.value)}
                      placeholder="Skill, e.g. React"
                      className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                    />
                  </label>
                  <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
                    <input type="checkbox" checked={eligibleOnly} onChange={(e) => setEligibleOnly(e.target.checked)} />
                    Eligible roles only
                  </label>
                </div>
              </FilterPill>

              {activeFilterChips.length > 0 && !openFilter && (
                <button className="ml-auto text-xs font-medium text-blue-600 hover:underline" onClick={clearAllFilters}>
                  Clear all
                </button>
              )}
            </div>
            {activeFilterChips.length > 0 && !openFilter && (
              <div className="mt-3 flex flex-wrap gap-1.5">
                {activeFilterChips.map((chip) => (
                  <button
                    key={chip.key}
                    onClick={chip.clear}
                    className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600 hover:bg-slate-200"
                  >
                    {chip.label}
                    <X size={12} />
                  </button>
                ))}
              </div>
            )}
          </Card>
          </div>

          <p className="text-sm text-slate-500">{filtered.length} jobs found</p>

          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
            <div className="hidden grid-cols-[1fr_8rem_7rem_5rem_2.5rem] gap-4 border-b border-slate-100 bg-slate-50 px-4 py-2 text-xs font-medium uppercase tracking-wide text-slate-400 sm:grid">
              <span>Job</span>
              <span>Location</span>
              <span>Salary</span>
              <span>Posted</span>
              <span />
            </div>
            <div className="divide-y divide-slate-100">
              {filtered.map((job) => {
                const match = matchByJobId.get(job.id)
                const isSelected = selectedJob?.id === job.id
                return (
                  <button
                    key={job.id}
                    onClick={() => {
                      setSelectedJob(job)
                      setDetailTab('overview')
                    }}
                    className={`grid w-full grid-cols-1 items-center gap-3 px-4 py-3 text-left transition-colors sm:grid-cols-[1fr_8rem_7rem_5rem_2.5rem] sm:gap-4 ${
                      isSelected ? 'bg-blue-50/60' : 'hover:bg-slate-50'
                    }`}
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <CompanyAvatar name={job.company} />
                      <span className="min-w-0">
                        <span className="flex items-center gap-2">
                          <span className="truncate font-medium text-slate-900">{job.title}</span>
                          {jobPrefs.saved.has(job.id) && <BookmarkCheck size={14} className="shrink-0 text-blue-600" />}
                        </span>
                        <span className="flex items-center gap-1.5 text-xs text-slate-500">
                          <span className="truncate">{job.company}</span>
                          {match && (
                            <>
                              <span>&middot;</span>
                              <Badge tone={toneForScore(match.compositeScore)}>{Math.round(match.compositeScore * 100)}% match</Badge>
                              <Badge tone={match.hardEligibilityPassed ? 'green' : 'red'}>
                                {match.hardEligibilityPassed ? 'Eligible' : 'Not eligible'}
                              </Badge>
                            </>
                          )}
                        </span>
                      </span>
                    </span>
                    <span className="hidden text-sm text-slate-500 sm:block">{job.remote ? 'Remote' : job.location}</span>
                    <span className="hidden text-sm text-slate-500 sm:block">{formatCompRange(job.compMinMinorUnits, job.compMaxMinorUnits, job.currency)}</span>
                    <span className="hidden items-center gap-1.5 text-xs text-slate-500 sm:flex">
                      {job.discoveredAt ? formatRelativeTime(job.discoveredAt) : '—'}
                    </span>
                    <span className="hidden items-center justify-between gap-1 sm:flex">
                      <BrandIcon iconId={SOURCE_ICON_ID[job.source] ?? 'custom'} boxSize={22} size={11} />
                      <ChevronRight size={16} className="text-slate-300" />
                    </span>
                  </button>
                )
              })}
              {filtered.length === 0 && (
                <div className="p-5">
                  <p className="text-sm text-slate-500">
                    {jobs.length === 0
                      ? 'No postings yet. Try “Poll for new postings” to pull real ones from Greenhouse/Lever.'
                      : 'No postings match the current filters.'}
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>

        <div>
          {selectedJob ? (
            <Card className="sticky top-0" padded={false}>
              <div className="p-5 pb-0">
                <div className="flex items-start gap-3">
                  <CompanyAvatar name={selectedJob.company} size={48} />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs text-slate-400">{selectedJob.company}</p>
                    <h3 className="truncate text-lg font-semibold text-slate-900">{selectedJob.title}</h3>
                  </div>
                  <button
                    onClick={() => toggleSave(selectedJob.id)}
                    disabled={!selected}
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-blue-600 disabled:opacity-40"
                    title={jobPrefs.saved.has(selectedJob.id) ? 'Saved' : 'Save job'}
                  >
                    {jobPrefs.saved.has(selectedJob.id) ? <BookmarkCheck size={18} className="text-blue-600" /> : <Bookmark size={18} />}
                  </button>
                </div>
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                  <Badge tone="slate">{selectedJob.remote ? 'Remote' : selectedJob.location}</Badge>
                  <Badge tone="slate">{formatCompRange(selectedJob.compMinMinorUnits, selectedJob.compMaxMinorUnits, selectedJob.currency)}</Badge>
                  {selectedJob.domain && <Badge tone="slate">{selectedJob.domain}</Badge>}
                  {selectedMatch && <Badge tone={toneForScore(selectedMatch.compositeScore)}>{Math.round(selectedMatch.compositeScore * 100)}% match</Badge>}
                </div>
                <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-400">
                  {selectedJob.discoveredAt && <>Discovered {formatRelativeTime(selectedJob.discoveredAt)} &middot; </>}
                  Source: {SOURCE_LABEL[selectedJob.source]}
                </p>

                {selected && (
                  <div
                    className={`mt-4 space-y-1.5 rounded-lg p-3 text-sm ${
                      selectedMatch ? (selectedMatch.hardEligibilityPassed ? 'bg-emerald-50' : 'bg-red-50') : 'bg-slate-50'
                    }`}
                  >
                    {eligibilityChecks.map((check) => (
                      <div key={check.label} className={`flex items-start gap-2 ${check.passed ? 'text-emerald-700' : 'text-red-700'}`}>
                        {check.passed ? (
                          <CheckCircle2 size={15} className="mt-0.5 shrink-0" />
                        ) : (
                          <XCircle size={15} className="mt-0.5 shrink-0" />
                        )}
                        <span>{check.label}</span>
                      </div>
                    ))}
                    {!selectedMatch && <p className="pt-1 text-xs text-slate-400">Score this posting to confirm against the hard eligibility gate.</p>}
                  </div>
                )}

                {selectedMatch && (
                  <div className="mt-4 flex items-center justify-center gap-4 rounded-lg border border-slate-100 py-3">
                    <ScoreRing value={selectedMatch.compositeScore} size={72} strokeWidth={6} label="AI Match Score" />
                    <Link to={`/matches/${selectedMatch.id}`} className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
                      View breakdown <ArrowRight size={14} />
                    </Link>
                  </div>
                )}

                <div className="mt-4 -mx-5 overflow-x-auto px-5">
                  <Tabs
                    tabs={[
                      { key: 'overview', label: 'Overview' },
                      { key: 'why', label: 'Why it matches' },
                      { key: 'requirements', label: 'Requirements' },
                      { key: 'company', label: 'Company' },
                      { key: 'similar', label: 'Similar Jobs' },
                    ]}
                    active={detailTab}
                    onChange={(k) => setDetailTab(k as DetailTab)}
                  />
                </div>
              </div>

              <div className="max-h-96 overflow-y-auto p-5 pt-4">
                {detailTab === 'overview' && (
                  <div className="space-y-3">
                    <p className="text-sm text-slate-600">{selectedJob.rawDescription}</p>
                  </div>
                )}

                {detailTab === 'why' && (
                  <div className="space-y-4">
                    {selectedMatch ? (
                      <>
                        <div className="space-y-3">
                          {SCORE_ROWS.map(({ key, label, tone }) => (
                            <div key={label}>
                              <div className="mb-1 flex items-center justify-between text-sm">
                                <span className="text-slate-600">{label}</span>
                                <span className="font-semibold text-slate-900">{Math.round((selectedMatch[key] as number) * 100)}%</span>
                              </div>
                              <ProgressBar value={selectedMatch[key] as number} tone={tone} />
                            </div>
                          ))}
                        </div>
                        {selectedMatch.reasonCodes.length > 0 && (
                          <div>
                            <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Reason codes</p>
                            <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
                              {selectedMatch.reasonCodes.map((r, i) => (
                                <li key={i}>{r}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </>
                    ) : (
                      <p className="text-sm text-slate-400">
                        {selected ? 'Score this posting against the candidate to see the breakdown.' : 'Select a candidate to score this posting.'}
                      </p>
                    )}
                  </div>
                )}

                {detailTab === 'requirements' && (
                  <div className="space-y-4">
                    <div>
                      <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Mandatory skills</p>
                      {selectedJob.mandatorySkillIds.length > 0 ? (
                        <div className="flex flex-wrap gap-1.5">
                          {selectedJob.mandatorySkillIds.map((s) => (
                            <Badge key={s} tone="blue">
                              {skillLabel(s)}
                            </Badge>
                          ))}
                        </div>
                      ) : (
                        <p className="text-sm text-slate-400">None specified</p>
                      )}
                    </div>
                    <div>
                      <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Preferred skills</p>
                      {selectedJob.preferredSkillIds.length > 0 ? (
                        <div className="flex flex-wrap gap-1.5">
                          {selectedJob.preferredSkillIds.map((s) => (
                            <Badge key={s} tone="slate">
                              {skillLabel(s)}
                            </Badge>
                          ))}
                        </div>
                      ) : (
                        <p className="text-sm text-slate-400">None specified</p>
                      )}
                    </div>
                    <div>
                      <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Minimum experience</p>
                      <p className="text-sm text-slate-600">{selectedJob.minYearsExperience} years</p>
                    </div>
                    <div>
                      <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Accepted work authorizations</p>
                      {selectedJob.acceptedWorkAuthorizations.length > 0 ? (
                        <div className="flex flex-wrap gap-1.5">
                          {selectedJob.acceptedWorkAuthorizations.map((w) => (
                            <Badge key={w} tone="slate">
                              {w}
                            </Badge>
                          ))}
                        </div>
                      ) : (
                        <p className="text-sm text-slate-400">Not specified</p>
                      )}
                    </div>
                  </div>
                )}

                {detailTab === 'company' && (
                  <div className="space-y-3 text-sm text-slate-600">
                    <p>
                      <span className="font-medium text-slate-900">{selectedJob.company}</span>
                    </p>
                    <p className="text-xs text-slate-400">
                      {jobs.filter((j) => j.company === selectedJob.company && j.id !== selectedJob.id).length} other open posting(s) from this company in this list
                    </p>
                    <p className="text-xs text-slate-400">
                      Screening decision: <span className="font-medium text-slate-600">{selectedJob.screeningDecision}</span>
                    </p>
                    {selectedJob.screeningReasons && <p>{selectedJob.screeningReasons}</p>}
                  </div>
                )}

                {detailTab === 'similar' && (
                  <div className="space-y-2">
                    {loadingSimilar && <p className="text-sm text-slate-400">Finding similar postings...</p>}
                    {!loadingSimilar && similarJobs.length === 0 && (
                      <p className="text-sm text-slate-400">No similar postings found in the current list.</p>
                    )}
                    {similarJobs.map((job) => (
                      <button
                        key={job.id}
                        onClick={() => {
                          setSelectedJob(job)
                          setDetailTab('overview')
                        }}
                        className="flex w-full items-center gap-3 rounded-lg border border-slate-100 p-2.5 text-left hover:border-slate-300"
                      >
                        <CompanyAvatar name={job.company} size={32} />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-medium text-slate-900">{job.title}</span>
                          <span className="block truncate text-xs text-slate-500">{job.company}</span>
                        </span>
                        <ChevronRight size={14} className="text-slate-300" />
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="space-y-2 border-t border-slate-100 p-5">
                <Button className="w-full justify-center" onClick={scoreForCandidate} disabled={!selected || scoring}>
                  {scoring ? 'Scoring...' : selectedMatch ? 'Rescore against candidate' : `Score against ${selected?.fullName ?? 'candidate'}`}
                </Button>
                <div className="flex gap-2">
                  <Button variant="secondary" className="flex-1 justify-center" icon={<Sparkles size={14} />} onClick={() => setDetailTab('similar')}>
                    Find Similar Jobs
                  </Button>
                  <Button variant="secondary" className="flex-1 justify-center" icon={<ThumbsDown size={14} />} onClick={() => dismissJob(selectedJob.id)}>
                    Not Interested
                  </Button>
                </div>
              </div>
            </Card>
          ) : (
            <Card>
              <p className="text-sm text-slate-500">Select a posting to see details and score it.</p>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
