import { useEffect, useMemo, useState } from 'react'
import { RefreshCw, Search, ExternalLink, MapPin, X, CheckCircle2, XCircle } from 'lucide-react'
import { api } from '../api/client'
import type { JobPosting, MatchScorecard } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card } from '../components/ui/Card'
import { Badge, toneForScore } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { ScoreRing } from '../components/ui/ScoreRing'
import { Tabs } from '../components/ui/Tabs'
import { formatCompRange, skillLabel } from '../lib/format'

type DetailTab = 'overview' | 'requirements' | 'company'

export function JobDiscovery() {
  const { selected } = useCandidate()
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [matches, setMatches] = useState<MatchScorecard[]>([])
  const [query, setQuery] = useState('')
  const [selectedJob, setSelectedJob] = useState<JobPosting | null>(null)
  const [detailTab, setDetailTab] = useState<DetailTab>('overview')
  const [polling, setPolling] = useState(false)
  const [pollResult, setPollResult] = useState<string | null>(null)
  const [lastPolledAt, setLastPolledAt] = useState<Date | null>(null)
  const [scoring, setScoring] = useState(false)

  // Filters - all client-side, no backend filter endpoint exists (see scope note).
  const [locationFilter, setLocationFilter] = useState('')
  const [remoteOnly, setRemoteOnly] = useState(false)
  const [minCompK, setMinCompK] = useState('')
  const [maxCompK, setMaxCompK] = useState('')
  const [skillFilter, setSkillFilter] = useState('')

  const load = () => api.jobPostings.list().then(setJobs)

  useEffect(() => {
    load()
  }, [])

  useEffect(() => {
    if (!selected) {
      setMatches([])
      return
    }
    api.matches.listForCandidate(selected.id).then(setMatches)
  }, [selected])

  const matchByJobId = useMemo(() => {
    const map = new Map<string, MatchScorecard>()
    for (const m of matches) map.set(m.jobPosting.id, m)
    return map
  }, [matches])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    const loc = locationFilter.trim().toLowerCase()
    const skill = skillFilter.trim().toLowerCase()
    const min = minCompK ? Number(minCompK) * 100_000 : null
    const max = maxCompK ? Number(maxCompK) * 100_000 : null
    return jobs.filter((j) => {
      if (q && !(j.title.toLowerCase().includes(q) || j.company.toLowerCase().includes(q) || j.domain?.toLowerCase().includes(q))) {
        return false
      }
      if (loc && !(j.remote ? 'remote'.includes(loc) : j.location.toLowerCase().includes(loc))) return false
      if (remoteOnly && !j.remote) return false
      if (min != null && j.compMaxMinorUnits != null && j.compMaxMinorUnits < min) return false
      if (max != null && j.compMinMinorUnits != null && j.compMinMinorUnits > max) return false
      if (skill && !j.mandatorySkillIds.some((s) => skillLabel(s).toLowerCase().includes(skill))) return false
      return true
    })
  }, [jobs, query, locationFilter, remoteOnly, minCompK, maxCompK, skillFilter])

  const activeFilterChips = useMemo(() => {
    const chips: { key: string; label: string; clear: () => void }[] = []
    if (locationFilter) chips.push({ key: 'loc', label: `Location: ${locationFilter}`, clear: () => setLocationFilter('') })
    if (remoteOnly) chips.push({ key: 'remote', label: 'Remote only', clear: () => setRemoteOnly(false) })
    if (minCompK) chips.push({ key: 'min', label: `Min $${minCompK}k`, clear: () => setMinCompK('') })
    if (maxCompK) chips.push({ key: 'max', label: `Max $${maxCompK}k`, clear: () => setMaxCompK('') })
    if (skillFilter) chips.push({ key: 'skill', label: `Skill: ${skillFilter}`, clear: () => setSkillFilter('') })
    return chips
  }, [locationFilter, remoteOnly, minCompK, maxCompK, skillFilter])

  const runPoll = async () => {
    setPolling(true)
    setPollResult(null)
    try {
      const result = await api.discovery.poll()
      setPollResult(`Discovered ${result.discovered}, ingested ${result.ingested}.`)
      setLastPolledAt(new Date())
      await load()
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

  const selectedMatch = selectedJob ? matchByJobId.get(selectedJob.id) : undefined

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Job Discovery</h1>
          <p className="mt-1 text-sm text-slate-500">
            Postings pulled from Greenhouse and Lever's real public APIs, screened before they ever reach here.
          </p>
        </div>
        <Button variant="secondary" icon={<RefreshCw size={16} className={polling ? 'animate-spin' : ''} />} onClick={runPoll} disabled={polling}>
          {polling ? 'Polling...' : 'Poll for new postings'}
        </Button>
      </div>

      {(pollResult || lastPolledAt) && (
        <Card className="flex items-center gap-3 bg-blue-50/60">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600">
            <RefreshCw size={18} className={polling ? 'animate-spin' : ''} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">{pollResult ?? 'Ready to sync'}</p>
            <p className="text-xs text-slate-500">
              {lastPolledAt ? `Last updated ${lastPolledAt.toLocaleTimeString()}` : 'Poll pulls live postings from GitLab (Greenhouse) and Palantir (Lever).'}
            </p>
          </div>
        </Card>
      )}

      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2 space-y-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by title, company, or domain..."
              className="w-full rounded-lg border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm focus:border-blue-400 focus:outline-none"
            />
          </div>

          <Card padded={false} className="p-4">
            <div className="flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-1.5 text-sm text-slate-600">
                <MapPin size={14} className="text-slate-400" />
                <input
                  value={locationFilter}
                  onChange={(e) => setLocationFilter(e.target.value)}
                  placeholder="Location"
                  className="w-32 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                />
              </label>
              <label className="flex items-center gap-1.5 text-sm text-slate-600">
                <input type="checkbox" checked={remoteOnly} onChange={(e) => setRemoteOnly(e.target.checked)} />
                Remote only
              </label>
              <label className="flex items-center gap-1.5 text-sm text-slate-600">
                Min $
                <input
                  value={minCompK}
                  onChange={(e) => setMinCompK(e.target.value.replace(/[^0-9]/g, ''))}
                  placeholder="0"
                  className="w-16 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                />
                k
              </label>
              <label className="flex items-center gap-1.5 text-sm text-slate-600">
                Max $
                <input
                  value={maxCompK}
                  onChange={(e) => setMaxCompK(e.target.value.replace(/[^0-9]/g, ''))}
                  placeholder="300"
                  className="w-16 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                />
                k
              </label>
              <label className="flex items-center gap-1.5 text-sm text-slate-600">
                Skill
                <input
                  value={skillFilter}
                  onChange={(e) => setSkillFilter(e.target.value)}
                  placeholder="e.g. React"
                  className="w-28 rounded-md border border-slate-200 px-2 py-1 text-sm focus:border-blue-400 focus:outline-none"
                />
              </label>
              {activeFilterChips.length > 0 && (
                <button
                  className="ml-auto text-xs font-medium text-blue-600 hover:underline"
                  onClick={() => {
                    setLocationFilter('')
                    setRemoteOnly(false)
                    setMinCompK('')
                    setMaxCompK('')
                    setSkillFilter('')
                  }}
                >
                  Clear all
                </button>
              )}
            </div>
            {activeFilterChips.length > 0 && (
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

          <p className="text-sm text-slate-500">{filtered.length} jobs found</p>

          <div className="space-y-2">
            {filtered.map((job) => {
              const match = matchByJobId.get(job.id)
              return (
                <button
                  key={job.id}
                  onClick={() => {
                    setSelectedJob(job)
                    setDetailTab('overview')
                  }}
                  className={`flex w-full items-center gap-4 rounded-xl border p-4 text-left transition-colors ${
                    selectedJob?.id === job.id ? 'border-blue-400 bg-blue-50/40' : 'border-slate-200 bg-white hover:border-slate-300'
                  }`}
                >
                  <CompanyAvatar name={job.company} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-slate-900">{job.title}</p>
                    <p className="truncate text-sm text-slate-500">{job.company}</p>
                  </div>
                  <span className="hidden w-32 text-sm text-slate-500 sm:block">
                    {job.remote ? 'Remote' : job.location}
                  </span>
                  <span className="hidden w-28 text-sm text-slate-500 md:block">
                    {formatCompRange(job.compMinMinorUnits, job.compMaxMinorUnits)}
                  </span>
                  {match && <ScoreRing value={match.compositeScore} size={36} strokeWidth={4} />}
                  <Badge tone={job.screeningDecision === 'PASS' ? 'green' : 'amber'}>{job.screeningDecision}</Badge>
                </button>
              )
            })}
            {filtered.length === 0 && (
              <Card>
                <p className="text-sm text-slate-500">
                  {jobs.length === 0
                    ? "No postings yet. Try “Poll for new postings” to pull real ones from Greenhouse/Lever."
                    : 'No postings match the current filters.'}
                </p>
              </Card>
            )}
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
                  {selectedMatch && <ScoreRing value={selectedMatch.compositeScore} size={56} strokeWidth={5} />}
                </div>
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                  <Badge tone="slate">{selectedJob.remote ? 'Remote' : selectedJob.location}</Badge>
                  <Badge tone="slate">{formatCompRange(selectedJob.compMinMinorUnits, selectedJob.compMaxMinorUnits)}</Badge>
                  {selectedJob.domain && <Badge tone="slate">{selectedJob.domain}</Badge>}
                  {selectedMatch && <Badge tone={toneForScore(selectedMatch.compositeScore)}>{Math.round(selectedMatch.compositeScore * 100)}% match</Badge>}
                </div>

                {selectedMatch && (
                  <div
                    className={`mt-4 flex items-start gap-2 rounded-lg p-3 text-sm ${
                      selectedMatch.hardEligibilityPassed ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
                    }`}
                  >
                    {selectedMatch.hardEligibilityPassed ? (
                      <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
                    ) : (
                      <XCircle size={16} className="mt-0.5 shrink-0" />
                    )}
                    <span className="font-medium">
                      {selectedMatch.hardEligibilityPassed ? 'You are eligible' : 'One or more hard gates failed'}
                    </span>
                  </div>
                )}

                <div className="mt-4">
                  <Tabs
                    tabs={[
                      { key: 'overview', label: 'Overview' },
                      { key: 'requirements', label: 'Requirements' },
                      { key: 'company', label: 'Company' },
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
                    {selectedMatch && selectedMatch.reasonCodes.length > 0 && (
                      <div>
                        <p className="mb-1.5 text-xs font-semibold uppercase text-slate-400">Why it matches</p>
                        <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
                          {selectedMatch.reasonCodes.map((r, i) => (
                            <li key={i}>{r}</li>
                          ))}
                        </ul>
                      </div>
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
                      Screening decision: <span className="font-medium text-slate-600">{selectedJob.screeningDecision}</span>
                    </p>
                    {selectedJob.screeningReasons && <p>{selectedJob.screeningReasons}</p>}
                    <p className="flex items-center gap-1 text-xs text-slate-400">
                      <ExternalLink size={12} /> Source hash {selectedJob.dedupeHash.slice(0, 12)}&hellip;
                    </p>
                  </div>
                )}
              </div>

              <div className="space-y-2 border-t border-slate-100 p-5">
                <Button className="w-full" onClick={scoreForCandidate} disabled={!selected || scoring}>
                  {scoring ? 'Scoring...' : selectedMatch ? 'Rescore against candidate' : `Score against ${selected?.fullName ?? 'candidate'}`}
                </Button>
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
