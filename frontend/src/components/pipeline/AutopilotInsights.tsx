import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { DomainInsight, InsightsSummary, SkillInsight, SourceInsight } from '../../api/types'
import { Card, CardHeader } from '../ui/Card'
import { ProgressBar } from '../ui/StatCard'

const SOURCE_LABEL: Record<string, string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  ASHBY: 'Ashby',
  MANUAL: 'Manual',
}

function pct(rate: number | null): string {
  return rate === null ? 'no data yet' : `${Math.round(rate * 100)}%`
}

function RateRow({
  label,
  countLabel,
  rate,
  tone = 'blue',
}: {
  label: string
  countLabel: string
  rate: number | null
  tone?: 'blue' | 'green' | 'violet' | 'amber'
}) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-sm">
        <span className="font-medium text-slate-700">{label}</span>
        <span className="text-xs text-slate-500">
          {countLabel} &middot; {pct(rate)}
        </span>
      </div>
      <ProgressBar value={rate ?? 0} tone={tone} />
    </div>
  )
}

function SourceCard({ sources }: { sources: SourceInsight[] }) {
  const withData = sources.filter((s) => s.discovered > 0)
  return (
    <Card>
      <CardHeader title="By Source" subtitle="Which discovery source's postings actually get approved" />
      <div className="space-y-3">
        {withData.map((s) => (
          <RateRow
            key={s.source}
            label={SOURCE_LABEL[s.source] ?? s.source}
            countLabel={`${s.approved}/${s.approved + s.rejected} reviewed approved, ${s.interviews} interview${s.interviews === 1 ? '' : 's'}`}
            rate={s.approvalRate}
            tone="green"
          />
        ))}
        {withData.length === 0 && <p className="text-sm text-slate-400">No discovered postings yet.</p>}
      </div>
    </Card>
  )
}

function DomainCard({ domains }: { domains: DomainInsight[] }) {
  return (
    <Card>
      <CardHeader title="By Domain" subtitle="Which industries shortlist and approve at the highest rate" />
      <div className="space-y-3">
        {domains.map((d) => (
          <RateRow
            key={d.domain}
            label={d.domain}
            countLabel={`${d.shortlisted}/${d.discovered} shortlisted`}
            rate={d.shortlistRate}
            tone="violet"
          />
        ))}
        {domains.length === 0 && <p className="text-sm text-slate-400">No domain-tagged postings yet.</p>}
      </div>
    </Card>
  )
}

function SkillCard({ skills }: { skills: SkillInsight[] }) {
  return (
    <Card>
      <CardHeader title="By Mandatory Skill" subtitle="Which required skills correlate with a shortlist" />
      <div className="space-y-3">
        {skills.map((s) => (
          <RateRow
            key={s.skillId}
            label={s.skillLabel}
            countLabel={`${s.shortlisted}/${s.postingsRequiring} shortlisted`}
            rate={s.shortlistRate}
            tone="amber"
          />
        ))}
        {skills.length === 0 && <p className="text-sm text-slate-400">No skill-tagged postings yet.</p>}
      </div>
    </Card>
  )
}

/** "Autopilot Insights" panel (plan Phase 3): explainable source/domain/skill ->
 * approval/interview correlation, computed by the backend's InsightsService from rows
 * already in the audit-backed pipeline (job postings, match scorecards, tailored
 * artifacts, interviews) - a human-readable signal to act on by hand (e.g. tracking more
 * companies from a strong source, or revisiting `candidly.matching.weights.*`), never an
 * input this app feeds back into scoring automatically. Loaded once, not polled -
 * unlike LivePipelineFeed this is a slower-moving aggregate, not "live" activity. */
export function AutopilotInsights() {
  const [summary, setSummary] = useState<InsightsSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.aiOps
      .insights()
      .then(setSummary)
      .catch((e) => setError(e instanceof Error ? e.message : 'failed to load'))
  }, [])

  if (!summary) {
    return (
      <Card>
        <CardHeader title="Autopilot Insights" subtitle="Which sources, domains, and skills correlate with approvals" />
        <p className="text-sm text-slate-400">{error ? `Couldn't load insights: ${error}` : 'Loading…'}</p>
      </Card>
    )
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <SourceCard sources={summary.bySource} />
      <DomainCard domains={summary.byDomain} />
      <SkillCard skills={summary.byMandatorySkill} />
    </div>
  )
}
