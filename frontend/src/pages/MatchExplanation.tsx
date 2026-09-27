import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, XCircle, Sparkles, ListChecks, ArrowRight } from 'lucide-react'
import { api, ApiError } from '../api/client'
import type { MatchScorecard } from '../api/types'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge, labelForScore } from '../components/ui/Badge'
import { ScoreRing } from '../components/ui/ScoreRing'
import { ProgressBar } from '../components/ui/StatCard'
import { Button } from '../components/ui/Button'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { formatCompRange } from '../lib/currency'

const SCORE_ROWS: { key: keyof MatchScorecard; label: string; tone: 'blue' | 'violet' | 'green' | 'amber' }[] = [
  { key: 'skillScore', label: 'Skills Match', tone: 'blue' },
  { key: 'experienceScore', label: 'Experience Match', tone: 'violet' },
  { key: 'semanticScore', label: 'Semantic Fit', tone: 'green' },
  { key: 'domainScore', label: 'Domain Relevance', tone: 'amber' },
]

/** Deterministic, template-derived copy from the match's own fields - not a new TypeSafe
 * judgment call (the mock's "AI Insights" card doesn't need to be one; see task scope). */
function buildInsights(match: MatchScorecard): string[] {
  const insights: string[] = []
  const pct = (v: number) => Math.round(v * 100)
  if (match.semanticScore >= 0.8) {
    insights.push(`Your semantic fit score of ${pct(match.semanticScore)}% suggests strong alignment with the role's core responsibilities.`)
  } else if (match.semanticScore >= 0.6) {
    insights.push(`A semantic fit score of ${pct(match.semanticScore)}% indicates reasonable alignment with the role, with some gaps worth reviewing.`)
  } else {
    insights.push(`A semantic fit score of ${pct(match.semanticScore)}% suggests this role's responsibilities diverge somewhat from your experience.`)
  }
  if (match.skillScore >= 0.8) {
    insights.push(`Your verified skills cover ${pct(match.skillScore)}% of what this posting asks for.`)
  } else {
    insights.push(`Only ${pct(match.skillScore)}% of the mandatory/preferred skills are currently verified in your Career Vault - consider adding evidence for the rest.`)
  }
  if (match.domainScore >= 0.7) {
    insights.push(`Domain relevance is high (${pct(match.domainScore)}%), meaning your background closely matches ${match.jobPosting.domain ?? 'this'} work.`)
  }
  if (!match.hardEligibilityPassed) {
    insights.push('One or more hard eligibility gates (location or work authorization) did not pass - review before proceeding.')
  }
  return insights
}

export function MatchExplanation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [match, setMatch] = useState<MatchScorecard | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    api.matches.get(id).then(setMatch)
  }, [id])

  if (!match) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Loading match&hellip;</p>
      </Card>
    )
  }

  const submitForTailoring = async () => {
    setSubmitting(true)
    setSubmitError(null)
    try {
      await api.tailoring.submit(match.candidate.id, match.jobPosting.id)
      navigate('/applications')
    } catch (e) {
      setSubmitError(e instanceof ApiError ? e.message : 'Failed to submit for tailoring')
    } finally {
      setSubmitting(false)
    }
  }

  const insights = buildInsights(match)

  return (
    <div className="space-y-6">
      <Link to="/matches" className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
        <ArrowLeft size={14} /> Back to Matches
      </Link>

      <Card>
        <div className="flex items-center gap-4">
          <CompanyAvatar name={match.jobPosting.company} size={56} />
          <div className="flex-1">
            <h1 className="text-xl font-bold text-slate-900">{match.jobPosting.title}</h1>
            <p className="text-sm text-slate-500">{match.jobPosting.company}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Badge tone="slate">{match.jobPosting.remote ? 'Remote' : match.jobPosting.location}</Badge>
              <Badge tone="slate">{formatCompRange(match.jobPosting.compMinMinorUnits, match.jobPosting.compMaxMinorUnits, match.jobPosting.currency)}</Badge>
              {match.shortlisted && <Badge tone="green">Shortlisted</Badge>}
            </div>
          </div>
          <div className="text-center">
            <ScoreRing value={match.compositeScore} size={100} />
            <p className="mt-1 text-sm font-medium text-slate-600">{labelForScore(match.compositeScore)}</p>
          </div>
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader title="Eligibility (Hard Gates)" />
            <div className="flex items-center gap-2">
              {match.hardEligibilityPassed ? (
                <>
                  <CheckCircle2 className="text-emerald-600" size={20} />
                  <span className="font-medium text-emerald-700">All mandatory criteria satisfied</span>
                </>
              ) : (
                <>
                  <XCircle className="text-red-600" size={20} />
                  <span className="font-medium text-red-700">One or more hard gates failed</span>
                </>
              )}
            </div>
            <p className="mt-2 text-xs text-slate-400">
              Location and work authorization are hard gates evaluated separately from the weighted score below (docs/03
              §2.1).
            </p>
          </Card>

          <Card>
            <CardHeader title="Match Score Breakdown" />
            <div className="space-y-3">
              {SCORE_ROWS.map(({ key, label, tone }) => (
                <div key={label}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="text-slate-600">{label}</span>
                    <span className="font-semibold text-slate-900">{Math.round((match[key] as number) * 100)}%</span>
                  </div>
                  <ProgressBar value={match[key] as number} tone={tone} />
                </div>
              ))}
            </div>
          </Card>

          <Card>
            <CardHeader title="Reason Codes" subtitle="Deterministic, human-readable adverse-action explanation (docs/02 §4.4)." />
            {match.reasonCodes.length === 0 ? (
              <p className="text-sm text-slate-400">No reason codes recorded.</p>
            ) : (
              <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
                {match.reasonCodes.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="bg-emerald-50">
            <CardHeader title="Your Matching Summary" />
            <ul className="space-y-2 text-sm text-emerald-800">
              <li className="flex items-start gap-2">
                {match.hardEligibilityPassed ? (
                  <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600" />
                ) : (
                  <XCircle size={16} className="mt-0.5 shrink-0 text-red-500" />
                )}
                Eligibility gates {match.hardEligibilityPassed ? 'passed' : 'failed'}
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600" />
                Skills match at {Math.round(match.skillScore * 100)}%
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600" />
                Experience match at {Math.round(match.experienceScore * 100)}%
              </li>
              <li className="flex items-start gap-2">
                {match.shortlisted ? (
                  <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600" />
                ) : (
                  <XCircle size={16} className="mt-0.5 shrink-0 text-red-500" />
                )}
                {match.shortlisted ? 'Shortlisted for tailoring' : 'Not currently shortlisted'}
              </li>
            </ul>
          </Card>

          <Card className="bg-violet-50">
            <CardHeader title={
              <span className="flex items-center gap-1.5">
                <Sparkles size={16} className="text-violet-600" /> AI Insights
              </span>
            } />
            <ul className="space-y-2 text-sm text-violet-800">
              {insights.map((text, i) => (
                <li key={i}>{text}</li>
              ))}
            </ul>
          </Card>

          <Card className="bg-amber-50">
            <CardHeader title={
              <span className="flex items-center gap-1.5">
                <ListChecks size={16} className="text-amber-600" /> Next Steps
              </span>
            } />
            <ol className="space-y-2 text-sm text-amber-900">
              <li className="flex gap-2">
                <span className="font-semibold">1.</span> Review the tailored resume once generated
              </li>
              <li className="flex gap-2">
                <span className="font-semibold">2.</span> Confirm eligibility and work authorization details
              </li>
              <li className="flex gap-2">
                <span className="font-semibold">3.</span> Submit for human-in-the-loop approval
              </li>
            </ol>
          </Card>

          {submitError && <p className="text-sm text-red-600">{submitError}</p>}

          <Button
            className="w-full justify-center"
            icon={<ArrowRight size={16} />}
            onClick={submitForTailoring}
            disabled={!match.shortlisted || submitting}
          >
            {submitting ? 'Submitting...' : 'Proceed to Application'}
          </Button>
          <p className="text-center text-xs text-slate-400">
            Submitting queues an async tailoring run; a human reviews the generated resume before it&apos;s handed back
            to the candidate (docs/03 §4, docs/01 §2 HITL).
          </p>
          <Link to={`/career-vault/evidence/${match.jobPosting.id}`} className="block">
            <Button variant="secondary" className="w-full justify-center">
              View Evidence Graph
            </Button>
          </Link>
        </div>
      </div>
    </div>
  )
}
