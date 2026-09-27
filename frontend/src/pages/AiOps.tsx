import { useEffect, useState } from 'react'
import { Cpu, ShieldCheck, RotateCcw, FlaskConical } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { api } from '../api/client'
import type { AiConfigView, AiOpsSummary } from '../api/types'
import { Card, CardHeader } from '../components/ui/Card'
import { StatCard } from '../components/ui/StatCard'
import { ScoreRing } from '../components/ui/ScoreRing'
import { Badge } from '../components/ui/Badge'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import { LivePipelineFeed } from '../components/pipeline/LivePipelineFeed'
import { AutopilotInsights } from '../components/pipeline/AutopilotInsights'

const SCREENING_COLORS: Record<string, string> = {
  PASS: 'var(--color-score-excellent)',
  REVIEW: 'var(--color-score-good)',
  BLOCK: 'var(--color-score-weak)',
}

const STATUS_COLORS: Record<string, string> = {
  PENDING_APPROVAL: 'var(--color-score-good)',
  NEEDS_HUMAN_REVIEW: 'var(--color-score-weak)',
  APPROVED: 'var(--color-score-excellent)',
  REJECTED: '#64748b',
}

export function AiOps() {
  const [summary, setSummary] = useState<AiOpsSummary | null>(null)
  const [aiConfig, setAiConfig] = useState<AiConfigView | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.aiOps.summary().then(setSummary).catch((e) => setError(describeError(e)))
    api.aiConfig.get().then(setAiConfig).catch((e) => setError(describeError(e)))
  }, [])

  if (error && !summary) {
    return <ErrorBanner message={`Couldn't load AI ops summary: ${error}`} />
  }

  if (!summary) {
    return (
      <Card>
        <p className="text-sm text-slate-400">Loading&hellip;</p>
      </Card>
    )
  }

  const screeningData = Object.entries(summary.screeningDecisionCounts).map(([name, value]) => ({ name, value }))
  const statusData = Object.entries(summary.artifactStatusCounts).map(([name, value]) => ({
    name: name.replace(/_/g, ' '),
    key: name,
    value,
  }))

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2">
          <Cpu size={22} className="text-slate-700" />
          <h1 className="text-2xl font-bold text-slate-900">AI Ops</h1>
        </div>
        <p className="mt-1 text-sm text-slate-500">
          Real, aggregated numbers from this deployment's own generation, grounding, and scoring pipeline - not a
          multi-model leaderboard. This stack only calls Groq (tailoring/drafting), Jina (embeddings), and TypeSafe
          (judgments); a GPT-4o/Claude/Llama/Gemini comparison would have nothing real behind it, so it isn't shown
          here.
        </p>
      </div>

      <LivePipelineFeed />

      <AutopilotInsights />

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard icon={<FlaskConical size={18} />} value={summary.totalArtifactsGenerated} label="Artifacts Generated" />
        <StatCard
          icon={<ShieldCheck size={18} />}
          iconTone="green"
          value={`${Math.round(summary.groundingPassRate * 100)}%`}
          label="Grounding Pass Rate"
        />
        <StatCard
          icon={<RotateCcw size={18} />}
          iconTone="amber"
          value={summary.avgCriticLoopsUsed.toFixed(1)}
          label="Avg Critic Loops"
        />
        <StatCard iconTone="purple" icon={<Cpu size={18} />} value={summary.totalMatchesScored} label="Matches Scored" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="flex flex-col items-center justify-center gap-2">
          <p className="text-sm font-medium text-slate-700">Grounding Pass Rate</p>
          <ScoreRing value={summary.groundingPassRate} label="grounded" />
          <p className="text-center text-xs text-slate-400">
            Share of generated resume bullets that survived DeterministicGroundingVerifier unchanged - the actual
            invariant, not an LLM self-report.
          </p>
        </Card>

        <Card>
          <CardHeader title="Job Screening Decisions" subtitle="Prompt-injection / guardrail screen outcomes" />
          <ResponsiveContainer width="100%" height={180}>
            <BarChart data={screeningData} layout="vertical" margin={{ left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={60} />
              <Tooltip />
              <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                {screeningData.map((d) => (
                  <Cell key={d.name} fill={SCREENING_COLORS[d.name] ?? '#64748b'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <CardHeader title="Tailored Artifact Status" subtitle="Where generated artifacts sit right now" />
          <ResponsiveContainer width="100%" height={180}>
            <BarChart data={statusData} layout="vertical" margin={{ left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} width={90} />
              <Tooltip />
              <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                {statusData.map((d) => (
                  <Cell key={d.key} fill={STATUS_COLORS[d.key] ?? '#64748b'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>

      <Card>
        <CardHeader title="Configured Providers" subtitle="What's actually wired in this deployment" />
        {aiConfig ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {[
              { label: 'Tailoring & drafting', view: aiConfig.tailoringAndDrafting },
              { label: 'Embeddings', view: aiConfig.embeddings },
              { label: 'Judgments', view: aiConfig.judgments },
            ].map(({ label, view }) => (
              <div key={label} className="rounded-lg border border-slate-100 p-3 text-sm">
                <p className="font-medium text-slate-800">{label}</p>
                <p className="text-xs text-slate-400">
                  {view.provider} &middot; {view.model}
                </p>
                <Badge tone={view.configured ? 'green' : 'red'} className="mt-2">
                  {view.configured ? 'API key set' : 'No API key'}
                </Badge>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-400">Loading&hellip;</p>
        )}
        <p className="mt-3 text-xs text-slate-400">
          Per-pair scoring consistency (how much a composite score varies across repeated real judgment calls) is
          measured on demand via the Calibration tool (<code>POST /api/calibration/consistency</code>), capped at 20
          runs since each run is a real billed call - not run automatically here.
        </p>
      </Card>
    </div>
  )
}
