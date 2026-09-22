import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bot,
  CheckCircle2,
  FileText,
  Mail,
  MailCheck,
  MessageCircle,
  Search,
  Send,
  Sparkles,
  Star,
  Trash2,
  TrendingUp,
  Trophy,
  Upload,
  Users,
  type LucideIcon,
} from 'lucide-react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
} from 'recharts'
import { api } from '../api/client'
import type {
  AuditEvent,
  AuditEventType,
  AutopilotStatusView,
  CareerVaultView,
  JobPosting,
  MatchScorecard,
  PipelineSummary,
  TailoringJob,
} from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { StatCard } from '../components/ui/StatCard'
import { Badge, labelForScore, toneForScore } from '../components/ui/Badge'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { Button } from '../components/ui/Button'
import { ScoreRing } from '../components/ui/ScoreRing'
import { formatCompRange, formatRelativeTime } from '../lib/format'

const EVENT_LABEL: Record<string, string> = {
  JOB_POSTING_SCREENED: 'Job posting screened',
  MATCH_SCORED: 'Match scored',
  TAILORED_ARTIFACT_GENERATED: 'Tailored resume generated',
  TAILORED_ARTIFACT_REVIEWED: 'Application reviewed',
  CANDIDATE_DATA_ERASED: 'Data erasure request',
  AUTOPILOT_CYCLE_STARTED: 'Autopilot cycle started',
  AUTOPILOT_APPLICATION_SUBMITTED: 'Autopilot submitted an application',
  AUTOPILOT_FOLLOW_UP_LOGGED: 'Autopilot logged a follow-up',
  AUTOPILOT_CYCLE_COMPLETED: 'Autopilot cycle completed',
  EMAIL_CLASSIFIED: 'Email classified',
  EMAIL_REPLY_REVIEWED: 'Email reply reviewed',
  CANDIDATE_SUBMITTED_APPLICATION: 'Application submitted',
}

/** Per-event-type icon + tone, keyed by AuditEventType - purely a display treatment so
 * the Recent Activity list reads at a glance instead of every row sharing one blue dot. */
const EVENT_ICON: Record<AuditEventType, { icon: LucideIcon; className: string }> = {
  JOB_POSTING_SCREENED: { icon: Search, className: 'bg-blue-50 text-blue-600' },
  MATCH_SCORED: { icon: Star, className: 'bg-emerald-50 text-emerald-600' },
  TAILORED_ARTIFACT_GENERATED: { icon: FileText, className: 'bg-violet-50 text-violet-600' },
  TAILORED_ARTIFACT_REVIEWED: { icon: CheckCircle2, className: 'bg-blue-50 text-blue-600' },
  CANDIDATE_DATA_ERASED: { icon: Trash2, className: 'bg-red-50 text-red-600' },
  AUTOPILOT_CYCLE_STARTED: { icon: Bot, className: 'bg-blue-50 text-blue-600' },
  AUTOPILOT_APPLICATION_SUBMITTED: { icon: Send, className: 'bg-emerald-50 text-emerald-600' },
  AUTOPILOT_FOLLOW_UP_LOGGED: { icon: MessageCircle, className: 'bg-amber-50 text-amber-600' },
  AUTOPILOT_CYCLE_COMPLETED: { icon: CheckCircle2, className: 'bg-emerald-50 text-emerald-600' },
  EMAIL_CLASSIFIED: { icon: Mail, className: 'bg-blue-50 text-blue-600' },
  EMAIL_REPLY_REVIEWED: { icon: MailCheck, className: 'bg-violet-50 text-violet-600' },
  CANDIDATE_SUBMITTED_APPLICATION: { icon: Send, className: 'bg-blue-50 text-blue-600' },
}

/** Composite score at/above this counts as a "strong match" for the stat tile and the
 * Match Quality donut's top tier - the same 0.8 banding `toneForScore` already uses for
 * "blue" (docs/03 thresholds are separate; this is purely a display banding). */
const STRONG_MATCH_THRESHOLD = 0.8

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000

function isWithinLastSevenDays(iso: string): boolean {
  return Date.now() - new Date(iso).getTime() <= SEVEN_DAYS_MS
}

const PIPELINE_STAGE_LABELS: { key: keyof PipelineSummary; label: string }[] = [
  { key: 'matched', label: 'Matched' },
  { key: 'shortlisted', label: 'Shortlisted' },
  { key: 'tailoring', label: 'Tailoring' },
  { key: 'pendingApproval', label: 'Pending' },
  { key: 'approved', label: 'Approved' },
  { key: 'interviews', label: 'Interviews' },
  { key: 'offers', label: 'Offers' },
]

const MATCH_TIERS: { key: string; label: string; min: number; color: string }[] = [
  { key: 'excellent', label: '90-100%', min: 0.9, color: 'var(--color-score-excellent)' },
  { key: 'strong', label: '80-89%', min: 0.8, color: 'var(--color-score-strong)' },
  { key: 'good', label: '70-79%', min: 0.7, color: 'var(--color-score-good)' },
  { key: 'weak', label: 'Below 70%', min: 0, color: 'var(--color-score-weak)' },
]

export function Dashboard() {
  const { selected } = useCandidate()
  const [jobPostings, setJobPostings] = useState<JobPosting[]>([])
  const [matches, setMatches] = useState<MatchScorecard[]>([])
  const [activity, setActivity] = useState<AuditEvent[]>([])
  const [autopilot, setAutopilot] = useState<AutopilotStatusView | null>(null)
  const [pipelineSummary, setPipelineSummary] = useState<PipelineSummary | null>(null)
  const [vault, setVault] = useState<CareerVaultView | null>(null)
  const [tailoringJobs, setTailoringJobs] = useState<TailoringJob[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!selected) return
    setLoading(true)
    Promise.all([
      api.jobPostings.list(),
      api.matches.listForCandidate(selected.id),
      api.audit.eventsForSubject(selected.id),
      api.autopilot.status(selected.id),
      api.pipeline.summary(selected.id),
      api.vault.get(selected.id),
      api.tailoring.listJobsForCandidate(selected.id),
    ])
      .then(([jobs, m, events, autopilotStatus, summary, vaultView, jobsForCandidate]) => {
        setJobPostings(jobs)
        setMatches(m)
        setActivity(events.slice().reverse())
        setAutopilot(autopilotStatus)
        setPipelineSummary(summary)
        setVault(vaultView)
        setTailoringJobs(jobsForCandidate)
      })
      .finally(() => setLoading(false))
  }, [selected])

  const shortlisted = matches.filter((m) => m.shortlisted)
  const strongMatches = matches.filter((m) => m.compositeScore >= STRONG_MATCH_THRESHOLD)
  const strongMatchesThisWeek = strongMatches.filter((m) => isWithinLastSevenDays(m.decidedAt)).length
  const applicationsThisWeek = tailoringJobs.filter((j) => isWithinLastSevenDays(j.submittedAt)).length
  const approvedArtifacts = tailoringJobs.filter((j) => j.resultArtifact?.status === 'APPROVED').length

  const topMatches = matches
    .slice()
    .sort((a, b) => b.compositeScore - a.compositeScore)
    .slice(0, 5)

  const pipelineChartData = useMemo(
    () =>
      pipelineSummary
        ? PIPELINE_STAGE_LABELS.map(({ key, label }) => ({ stage: label, value: pipelineSummary[key] }))
        : [],
    [pipelineSummary],
  )

  const matchTierData = useMemo(
    () =>
      MATCH_TIERS.map((tier, i) => {
        const upperExclusive = i === 0 ? Infinity : MATCH_TIERS[i - 1].min
        const count = matches.filter((m) => m.compositeScore >= tier.min && m.compositeScore < upperExclusive).length
        return { ...tier, count }
      }),
    [matches],
  )
  const hasMatchTierData = matchTierData.some((t) => t.count > 0)

  const vaultChecklist = vault
    ? [
        { label: 'Skills', done: vault.candidate.skillIds.length > 0 || vault.candidate.rawSkillMentions.length > 0 },
        { label: 'Experience', done: vault.experiences.length > 0 },
        { label: 'Achievements', done: vault.achievements.length > 0 },
        { label: 'Education', done: vault.education.length > 0 },
        { label: 'Certifications', done: vault.certifications.length > 0 },
      ]
    : []

  const upcomingTasks = pipelineSummary
    ? [
        pipelineSummary.pendingApproval > 0 && {
          label: `Review ${pipelineSummary.pendingApproval} pending application${pipelineSummary.pendingApproval === 1 ? '' : 's'}`,
          to: '/applications',
          urgent: true,
        },
        shortlisted.length > 0 && {
          label: `${shortlisted.length} new shortlisted match${shortlisted.length === 1 ? '' : 'es'} to review`,
          to: '/matches',
          urgent: false,
        },
        vault && vault.completeness < 1 && {
          label: 'Finish setting up your Career Vault',
          to: '/career-vault',
          urgent: false,
        },
        pipelineSummary.interviews > 0 && {
          label: `${pipelineSummary.interviews} interview${pipelineSummary.interviews === 1 ? '' : 's'} in your pipeline`,
          to: '/pipeline',
          urgent: false,
        },
      ].filter((t): t is { label: string; to: string; urgent: boolean } => Boolean(t))
    : []

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">
          No candidates yet. Create one via <code>POST /api/candidates</code> to see the dashboard.
        </p>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div
        className="relative overflow-hidden rounded-2xl p-8 text-white"
        style={{
          backgroundImage:
            'linear-gradient(135deg, var(--color-panel-from), var(--color-panel-via) 55%, var(--color-panel-to))',
        }}
      >
        <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
          <div className="max-w-xl">
            <p className="text-sm text-slate-300">Good to see you,</p>
            <h1 className="text-2xl font-bold">{selected.fullName}</h1>
            <p className="mt-1 text-slate-300">New opportunities are out there. Let&rsquo;s find your next chapter.</p>
            <div className="mt-5 flex flex-wrap gap-3">
              <Link to="/job-discovery">
                <Button icon={<Search size={16} />}>Find New Opportunities</Button>
              </Link>
              <Link to="/career-vault">
                <Button variant="secondary" className="bg-white/10 text-white border-white/20 hover:bg-white/20" icon={<Upload size={16} />}>
                  Upload / Update Resume
                </Button>
              </Link>
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-4 rounded-xl bg-white/10 p-4 backdrop-blur-sm">
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-200">
                <Sparkles size={12} /> Your Goals
              </div>
              <ul className="mt-2 space-y-1 text-sm text-slate-100">
                <li>{selected.location || 'Location not set'}</li>
                <li>${Math.round(selected.compFloorMinorUnits / 100_000)}k+ target comp</li>
                <li>{selected.workAuthorizations.length > 0 ? selected.workAuthorizations.join(', ') : 'Work authorization not set'}</li>
              </ul>
            </div>
            {vault && (
              <div className="shrink-0 border-l border-white/20 pl-4">
                <ScoreRing value={vault.completeness} size={72} strokeWidth={6} label="Profile" />
              </div>
            )}
          </div>
        </div>
      </div>

      {autopilot && autopilot.settings.status !== 'RUNNING' && (
        <Card className="flex items-center justify-between gap-4 border-blue-200 bg-blue-50/50">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600">
              <Bot size={18} />
            </span>
            <div>
              <p className="text-sm font-semibold text-slate-900">Get more interviews with AI-powered applications</p>
              <p className="text-xs text-slate-500">Auto-apply to matching jobs &middot; personalized tailoring &middot; track everything in one place</p>
            </div>
          </div>
          <Link to="/autopilot">
            <Button>Enable Autopilot</Button>
          </Link>
        </Card>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatCard icon={<Search size={18} />} iconTone="blue" value={jobPostings.length} label="Jobs Discovered" />
        <StatCard
          icon={<Star size={18} />}
          iconTone="green"
          value={strongMatches.length}
          label="Strong Matches"
          delta={strongMatchesThisWeek > 0 ? `+${strongMatchesThisWeek} this week` : undefined}
        />
        <StatCard
          icon={<Send size={18} />}
          iconTone="purple"
          value={tailoringJobs.length}
          label="Applications"
          delta={applicationsThisWeek > 0 ? `+${applicationsThisWeek} this week` : undefined}
        />
        <StatCard icon={<Users size={18} />} iconTone="amber" value={pipelineSummary?.interviews ?? 0} label="Interviews" />
        <StatCard icon={<Trophy size={18} />} iconTone="green" value={pipelineSummary?.offers ?? 0} label="Offers" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader title="Application Pipeline" subtitle="Candidates counted at each stage, most-recent scoring." />
          {pipelineChartData.length === 0 ? (
            <p className="text-sm text-slate-400">No pipeline activity yet.</p>
          ) : (
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={pipelineChartData} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#e2e8f0" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="stage"
                    tick={{ fontSize: 11, fill: '#64748b' }}
                    tickLine={false}
                    axisLine={{ stroke: '#e2e8f0' }}
                    interval={0}
                    angle={-20}
                    textAnchor="end"
                    height={40}
                  />
                  <Tooltip
                    cursor={{ fill: '#f1f5f9' }}
                    contentStyle={{ borderRadius: 8, borderColor: '#e2e8f0', fontSize: 12 }}
                  />
                  <Bar dataKey="value" fill="var(--color-brand-from)" radius={[4, 4, 0, 0]} maxBarSize={36} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>

        <Card>
          <CardHeader title="Match Quality" subtitle="This candidate's matches by composite score tier." />
          {!hasMatchTierData ? (
            <p className="text-sm text-slate-400">No matches scored yet.</p>
          ) : (
            <div className="flex items-center gap-4">
              <div className="relative h-40 w-40 shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={matchTierData}
                      dataKey="count"
                      nameKey="label"
                      innerRadius={48}
                      outerRadius={72}
                      paddingAngle={2}
                      stroke="#fff"
                      strokeWidth={2}
                    >
                      {matchTierData.map((tier) => (
                        <Cell key={tier.key} fill={tier.color} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ borderRadius: 8, borderColor: '#e2e8f0', fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="text-xl font-bold text-slate-900">{matches.length}</span>
                  <span className="text-[10px] text-slate-500">Matches</span>
                </div>
              </div>
              <ul className="min-w-0 flex-1 space-y-1.5 text-sm">
                {matchTierData.map((tier) => (
                  <li key={tier.key} className="flex items-center justify-between gap-2">
                    <span className="flex items-center gap-2 text-slate-600">
                      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: tier.color }} />
                      {tier.label}
                    </span>
                    <span className="font-medium text-slate-900">{tier.count}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Profile Completeness"
            subtitle="Career Vault sections populated."
            action={
              <Link to="/career-vault" className="text-sm font-medium text-blue-600 hover:underline">
                Edit &rarr;
              </Link>
            }
          />
          {vault ? (
            <div className="flex items-center gap-4">
              <ScoreRing value={vault.completeness} size={88} strokeWidth={8} />
              <ul className="min-w-0 flex-1 space-y-1.5 text-sm">
                {vaultChecklist.map((item) => (
                  <li key={item.label} className="flex items-center gap-2">
                    <CheckCircle2 size={14} className={item.done ? 'text-emerald-600' : 'text-slate-300'} />
                    <span className={item.done ? 'text-slate-700' : 'text-slate-400'}>{item.label}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="text-sm text-slate-400">Loading&hellip;</p>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <Card>
            <CardHeader
              title="Top Matches for You"
              subtitle="Ranked by composite score across skills, experience, semantic fit and domain."
              action={
                <Link to="/matches" className="text-sm font-medium text-blue-600 hover:underline">
                  View all matches &rarr;
                </Link>
              }
            />
            {loading && <p className="text-sm text-slate-400">Loading&hellip;</p>}
            {!loading && topMatches.length === 0 && (
              <p className="text-sm text-slate-500">
                No matches yet. Score this candidate against a job posting via Matches.
              </p>
            )}
            <div className="divide-y divide-slate-100">
              {topMatches.map((m) => (
                <div key={m.id} className="flex items-center gap-4 py-3">
                  <CompanyAvatar name={m.jobPosting.company} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-slate-900">{m.jobPosting.title}</p>
                    <p className="truncate text-sm text-slate-500">{m.jobPosting.company}</p>
                  </div>
                  <Badge tone={toneForScore(m.compositeScore)}>
                    {Math.round(m.compositeScore * 100)}% match
                  </Badge>
                  <span className="hidden w-28 text-sm text-slate-500 sm:block">
                    {m.jobPosting.remote ? 'Remote' : m.jobPosting.location}
                  </span>
                  <span className="hidden w-28 text-sm text-slate-500 md:block">
                    {formatCompRange(m.jobPosting.compMinMinorUnits, m.jobPosting.compMaxMinorUnits)}
                  </span>
                  <Link to={`/matches/${m.id}`}>
                    <Button variant="secondary">View Details</Button>
                  </Link>
                </div>
              ))}
            </div>
          </Card>
        </div>

        <Card>
          <CardHeader title="Recent Activity" subtitle="From the audit ledger, this candidate only." />
          <ul className="space-y-4">
            {activity.slice(0, 8).map((e) => {
              const iconSpec = EVENT_ICON[e.eventType] ?? EVENT_ICON.MATCH_SCORED
              const EventIcon = iconSpec.icon
              return (
                <li key={e.id} className="flex gap-3 text-sm">
                  <span className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${iconSpec.className}`}>
                    <EventIcon size={14} />
                  </span>
                  <div className="min-w-0">
                    <p className="font-medium text-slate-800">{EVENT_LABEL[e.eventType] ?? e.eventType}</p>
                    <p className="truncate text-slate-500">{e.details}</p>
                    <p className="text-xs text-slate-400">{formatRelativeTime(e.occurredAt)}</p>
                  </div>
                </li>
              )
            })}
            {activity.length === 0 && <p className="text-sm text-slate-400">No activity yet for this candidate.</p>}
          </ul>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Upcoming Tasks" subtitle="Deterministic checklist derived from your current pipeline state." />
          {upcomingTasks.length === 0 ? (
            <p className="text-sm text-slate-400">Nothing needs your attention right now.</p>
          ) : (
            <ul className="space-y-3">
              {upcomingTasks.map((task) => (
                <li key={task.label}>
                  <Link to={task.to} className="flex items-center justify-between gap-3 rounded-lg px-1 py-1 text-sm hover:bg-slate-50">
                    <span className="flex items-center gap-2">
                      <span className={`h-2 w-2 shrink-0 rounded-full ${task.urgent ? 'bg-red-500' : 'bg-slate-300'}`} />
                      <span className="text-slate-700">{task.label}</span>
                    </span>
                    <span className="text-blue-600">&rarr;</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="Your Impact" subtitle="What the platform has done for this candidate so far." />
          <div className="grid grid-cols-3 gap-4">
            <div className="flex items-center gap-2">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                <Send size={16} />
              </span>
              <div>
                <p className="text-lg font-bold text-slate-900">{tailoringJobs.length}</p>
                <p className="text-xs text-slate-500">Applications</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-violet-50 text-violet-600">
                <Users size={16} />
              </span>
              <div>
                <p className="text-lg font-bold text-slate-900">{pipelineSummary?.interviews ?? 0}</p>
                <p className="text-xs text-slate-500">Interviews</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <TrendingUp size={16} />
              </span>
              <div>
                <p className="text-lg font-bold text-slate-900">{approvedArtifacts}</p>
                <p className="text-xs text-slate-500">Ready to submit</p>
              </div>
            </div>
          </div>
        </Card>
      </div>

      {topMatches.length > 0 && (
        <Card className="bg-blue-50/40">
          <p className="text-sm text-slate-600">
            Top match: <span className="font-semibold text-slate-900">{topMatches[0].jobPosting.title}</span> at{' '}
            {topMatches[0].jobPosting.company} &mdash; {labelForScore(topMatches[0].compositeScore)}.
          </p>
        </Card>
      )}
    </div>
  )
}
