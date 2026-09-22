import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, AlertTriangle, Lightbulb, Minus, Plus, RotateCcw } from 'lucide-react'
import { api } from '../api/client'
import type { CareerVaultView, JobPosting, MatchScorecard } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge, toneForScore } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Tabs } from '../components/ui/Tabs'
import { skillLabel } from '../lib/format'

interface RequirementEvidence {
  skillId: string
  mandatory: boolean
  matched: boolean
  sources: string[]
  /** Deterministic display score - see comment on `requirements` below for how it's derived. */
  score: number
}

/** Same score->color bands as ScoreRing/Badge (`toneForScore`), mapped to the CSS custom properties directly since SVG `stroke`/`fill` can't take Tailwind classes. */
const NODE_COLORS: Record<string, string> = {
  green: 'var(--color-score-excellent)',
  blue: 'var(--color-score-strong)',
  amber: 'var(--color-score-good)',
  red: 'var(--color-score-weak)',
}

const CENTER = { x: 460, y: 320 }
const SKILL_RADIUS = 190
const LEAF_RADIUS = 300
const VIEW_W = 920
const VIEW_H = 620

interface SkillNode extends RequirementEvidence {
  x: number
  y: number
  angle: number
  color: string
}

interface LeafNode {
  id: string
  skillId: string
  label: string
  x: number
  y: number
  color: string
}

/**
 * Requirement <-> evidence traceability for one job posting, computed entirely from data
 * already on file (Career Vault + the job's own mandatory/preferred skills) - no new
 * backend endpoint, no model call. Rendered as a hand-rolled radial node-link graph (no
 * d3-force / graph library dependency - plain trigonometry) per the mock's "Evidence
 * Graph" concept: job title at the center, requirement/skill nodes on a middle ring,
 * evidence-document leaf nodes on an outer ring near their parent skill. Per-skill
 * percentages aren't a real backend metric (only matched/mandatory booleans and free-text
 * evidence sources are on file), so they're a deterministic display score derived from
 * those two booleans (matched+mandatory highest, unmatched+mandatory lowest) - close
 * enough to the mock's look without inventing a fake model judgment.
 */
export function EvidenceGraph() {
  const { jobPostingId } = useParams<{ jobPostingId: string }>()
  const { selected } = useCandidate()
  const [job, setJob] = useState<JobPosting | null>(null)
  const [vault, setVault] = useState<CareerVaultView | null>(null)
  const [scorecard, setScorecard] = useState<MatchScorecard | null>(null)
  const [tab, setTab] = useState<'graph' | 'evidence' | 'missing'>('graph')
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null)
  const [zoom, setZoom] = useState(1)

  useEffect(() => {
    if (!selected || !jobPostingId) return
    api.jobPostings.get(jobPostingId).then(setJob)
    api.vault.get(selected.id).then(setVault)
    api.matches.listForCandidate(selected.id).then((matches) => {
      setScorecard(matches.find((m) => m.jobPosting.id === jobPostingId) ?? null)
    })
  }, [selected, jobPostingId])

  const requirements = useMemo<RequirementEvidence[]>(() => {
    if (!job || !vault) return []
    const all = [
      ...job.mandatorySkillIds.map((id) => ({ skillId: id, mandatory: true })),
      ...job.preferredSkillIds.map((id) => ({ skillId: id, mandatory: false })),
    ]
    return all.map(({ skillId, mandatory }) => {
      const matched = vault.candidate.skillIds.includes(skillId)
      const sources = vault.experiences.filter((e) => e.skillIds.includes(skillId)).map((e) => `${e.title} @ ${e.employer}`)
      const score = matched ? (mandatory ? 0.95 : 0.85) : mandatory ? 0.35 : 0.55
      return { skillId, mandatory, matched, sources, score }
    })
  }, [job, vault])

  const { skillNodes, leafNodes } = useMemo(() => {
    const n = requirements.length
    const skills: SkillNode[] = []
    const leaves: LeafNode[] = []
    requirements.forEach((r, i) => {
      const angle = n > 0 ? (i / n) * 2 * Math.PI - Math.PI / 2 : 0
      const x = CENTER.x + SKILL_RADIUS * Math.cos(angle)
      const y = CENTER.y + SKILL_RADIUS * Math.sin(angle)
      const color = NODE_COLORS[toneForScore(r.score)]
      skills.push({ ...r, x, y, angle, color })

      const capped = r.sources.slice(0, 3)
      const spread = Math.min(0.5, (2 * Math.PI) / Math.max(n, 6) / 1.5)
      capped.forEach((source, j) => {
        const leafAngle = capped.length === 1 ? angle : angle - spread / 2 + (spread * j) / (capped.length - 1)
        leaves.push({
          id: `${r.skillId}__${j}`,
          skillId: r.skillId,
          label: source,
          x: CENTER.x + LEAF_RADIUS * Math.cos(leafAngle),
          y: CENTER.y + LEAF_RADIUS * Math.sin(leafAngle),
          color,
        })
      })
    })
    return { skillNodes: skills, leafNodes: leaves }
  }, [requirements])

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  if (!job || !vault) {
    return (
      <Card>
        <p className="text-sm text-slate-400">Loading&hellip;</p>
      </Card>
    )
  }

  const missing = requirements.filter((r) => !r.matched)
  const matchedCount = requirements.length - missing.length
  const selectedSkill = skillNodes.find((s) => s.skillId === selectedSkillId) ?? null
  const strongest = [...skillNodes].sort((a, b) => b.score - a.score)[0]
  const weakest = [...skillNodes].filter((s) => !s.matched).sort((a, b) => a.score - b.score)

  const insight =
    requirements.length === 0
      ? 'This posting lists no structured skill requirements to graph.'
      : weakest.length === 0
        ? `You have verified evidence for all ${requirements.length} requirement${requirements.length === 1 ? '' : 's'}. Strongest signal: ${skillLabel(strongest.skillId)}.`
        : `You have strong evidence for ${matchedCount}/${requirements.length} requirements. Add evidence for ${weakest
            .slice(0, 2)
            .map((w) => skillLabel(w.skillId))
            .join(' and ')} to strengthen your match.`

  return (
    <div className="space-y-6">
      <div>
        <Link to="/career-vault" className="mb-1 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
          <ArrowLeft size={14} /> Back to Career Vault
        </Link>
        <h1 className="text-2xl font-bold text-slate-900">Evidence Graph</h1>
        <p className="mt-1 text-sm text-slate-500">
          How {selected.fullName}&rsquo;s verified record backs up each requirement of {job.title} at {job.company}.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="text-center">
          <p className="text-2xl font-bold text-slate-900">
            {matchedCount}/{requirements.length}
          </p>
          <p className="text-xs text-slate-500">Requirements with evidence</p>
        </Card>
        <Card className="text-center">
          <p className="text-2xl font-bold text-slate-900">{scorecard ? `${Math.round(scorecard.compositeScore * 100)}%` : '—'}</p>
          <p className="text-xs text-slate-500">Composite match score</p>
        </Card>
        <Card className="text-center">
          <p className="text-2xl font-bold text-slate-900">{missing.length}</p>
          <p className="text-xs text-slate-500">Missing pieces</p>
        </Card>
      </div>

      <Tabs
        tabs={[
          { key: 'graph', label: 'Graph View' },
          { key: 'evidence', label: 'Evidence List', count: requirements.length },
          { key: 'missing', label: 'Missing Pieces', count: missing.length },
        ]}
        active={tab}
        onChange={(k) => setTab(k as 'graph' | 'evidence' | 'missing')}
      />

      {tab === 'graph' && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500">
                <span className="flex items-center gap-1.5">
                  <span className="inline-block h-2.5 w-2.5 rounded-full bg-slate-800" /> Job Requirement
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: NODE_COLORS.green }} /> Your Skill /
                  Experience
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="inline-block h-2.5 w-2.5 rounded-full bg-violet-500" /> Evidence (Document)
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="inline-block h-0.5 w-4 bg-emerald-500" /> Strong Match
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="inline-block h-0.5 w-4 border-t border-dashed border-slate-400" /> Missing
                </span>
              </div>
              <div className="flex items-center gap-1">
                <Button variant="secondary" className="!px-2 !py-1" onClick={() => setZoom((z) => Math.max(0.6, z - 0.2))}>
                  <Minus size={14} />
                </Button>
                <Button variant="secondary" className="!px-2 !py-1" onClick={() => setZoom(1)}>
                  <RotateCcw size={14} />
                </Button>
                <Button variant="secondary" className="!px-2 !py-1" onClick={() => setZoom((z) => Math.min(2, z + 0.2))}>
                  <Plus size={14} />
                </Button>
              </div>
            </div>

            {requirements.length === 0 ? (
              <p className="text-sm text-slate-400">This posting lists no structured skill requirements.</p>
            ) : (
              <div className="overflow-hidden rounded-lg border border-slate-100 bg-slate-50/50">
                <div style={{ transform: `scale(${zoom})`, transformOrigin: 'center center', transition: 'transform 150ms ease' }}>
                  <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} width="100%" role="img" aria-label="Evidence graph">
                    {skillNodes.map((s) => (
                      <line
                        key={`edge-${s.skillId}`}
                        x1={CENTER.x}
                        y1={CENTER.y}
                        x2={s.x}
                        y2={s.y}
                        stroke={s.matched ? s.color : '#cbd5e1'}
                        strokeWidth={selectedSkillId === s.skillId ? 3.5 : s.matched ? 2 : 1.5}
                        strokeDasharray={s.matched ? undefined : '4 4'}
                        opacity={selectedSkillId && selectedSkillId !== s.skillId ? 0.25 : 0.9}
                      />
                    ))}
                    {leafNodes.map((l) => {
                      const skill = skillNodes.find((s) => s.skillId === l.skillId)!
                      return (
                        <line
                          key={`leaf-edge-${l.id}`}
                          x1={skill.x}
                          y1={skill.y}
                          x2={l.x}
                          y2={l.y}
                          stroke={l.color}
                          strokeWidth={selectedSkillId === l.skillId ? 2.5 : 1.25}
                          opacity={selectedSkillId && selectedSkillId !== l.skillId ? 0.15 : 0.6}
                        />
                      )
                    })}

                    {/* Center node */}
                    <g>
                      <rect x={CENTER.x - 90} y={CENTER.y - 32} width={180} height={64} rx={12} fill="#0f172a" />
                      <text x={CENTER.x} y={CENTER.y - 4} textAnchor="middle" fontSize={13} fontWeight={700} fill="#fff">
                        {job.title.length > 24 ? `${job.title.slice(0, 22)}…` : job.title}
                      </text>
                      <text x={CENTER.x} y={CENTER.y + 16} textAnchor="middle" fontSize={11} fill="#94a3b8">
                        {job.company}
                      </text>
                    </g>

                    {/* Evidence leaf nodes */}
                    {leafNodes.map((l) => (
                      <g key={l.id} opacity={selectedSkillId && selectedSkillId !== l.skillId ? 0.35 : 1}>
                        <rect
                          x={l.x - 55}
                          y={l.y - 14}
                          width={110}
                          height={28}
                          rx={14}
                          fill="#f5f3ff"
                          stroke="#c4b5fd"
                          strokeWidth={1}
                        />
                        <text x={l.x} y={l.y + 4} textAnchor="middle" fontSize={9} fill="#6d28d9">
                          {l.label.length > 20 ? `${l.label.slice(0, 18)}…` : l.label}
                        </text>
                      </g>
                    ))}

                    {/* Skill/requirement nodes */}
                    {skillNodes.map((s) => (
                      <g
                        key={s.skillId}
                        className="cursor-pointer"
                        onClick={() => setSelectedSkillId((cur) => (cur === s.skillId ? null : s.skillId))}
                        opacity={selectedSkillId && selectedSkillId !== s.skillId ? 0.45 : 1}
                      >
                        <circle
                          cx={s.x}
                          cy={s.y}
                          r={selectedSkillId === s.skillId ? 34 : 30}
                          fill="#fff"
                          stroke={s.color}
                          strokeWidth={selectedSkillId === s.skillId ? 3 : 2}
                        />
                        <text x={s.x} y={s.y - 4} textAnchor="middle" fontSize={10} fontWeight={600} fill="#0f172a">
                          {skillLabel(s.skillId).length > 14 ? `${skillLabel(s.skillId).slice(0, 12)}…` : skillLabel(s.skillId)}
                        </text>
                        <text x={s.x} y={s.y + 12} textAnchor="middle" fontSize={11} fontWeight={700} fill={s.color}>
                          {Math.round(s.score * 100)}%
                        </text>
                      </g>
                    ))}
                  </svg>
                </div>
              </div>
            )}
          </Card>

          <div className="space-y-4">
            <Card>
              <CardHeader title={selectedSkill ? skillLabel(selectedSkill.skillId) : 'Requirements'} />
              {selectedSkill ? (
                <div className="space-y-3">
                  <div className="flex items-center gap-2">
                    <Badge tone={selectedSkill.mandatory ? 'blue' : 'slate'}>{selectedSkill.mandatory ? 'Mandatory' : 'Preferred'}</Badge>
                    <Badge tone={toneForScore(selectedSkill.score)}>{Math.round(selectedSkill.score * 100)}% evidenced</Badge>
                  </div>
                  {selectedSkill.sources.length > 0 ? (
                    <div>
                      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">Backed by</p>
                      <ul className="space-y-1 text-sm text-slate-600">
                        {selectedSkill.sources.map((s) => (
                          <li key={s} className="rounded-md bg-slate-50 px-2 py-1">
                            {s}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : (
                    <p className="text-sm text-amber-600">No Career Vault evidence backs this requirement yet.</p>
                  )}
                  <button className="text-xs text-slate-400 hover:text-slate-600" onClick={() => setSelectedSkillId(null)}>
                    Clear selection
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  {skillNodes.map((s) => (
                    <button
                      key={s.skillId}
                      onClick={() => setSelectedSkillId(s.skillId)}
                      className="flex w-full items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-slate-50"
                    >
                      <span className="text-sm text-slate-700">{skillLabel(s.skillId)}</span>
                      <span className="flex items-center gap-2">
                        <span className="h-1.5 w-16 overflow-hidden rounded-full bg-slate-100">
                          <span className="block h-full rounded-full" style={{ width: `${s.score * 100}%`, background: s.color }} />
                        </span>
                        <span className="w-9 text-right text-xs font-semibold" style={{ color: s.color }}>
                          {Math.round(s.score * 100)}%
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </Card>

            <Card className="border-violet-100 bg-violet-50/60">
              <div className="flex items-start gap-2">
                <Lightbulb size={16} className="mt-0.5 shrink-0 text-violet-600" />
                <div>
                  <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-violet-700">AI Insight</p>
                  <p className="text-sm text-violet-700">{insight}</p>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}

      {tab === 'evidence' && (
        <Card>
          <CardHeader title="Job requirement &rarr; verified evidence" />
          <div className="space-y-2">
            {requirements.map((r) => (
              <div key={r.skillId} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
                <div className="flex items-center gap-2">
                  {r.matched ? (
                    <CheckCircle2 size={16} className="shrink-0 text-emerald-500" />
                  ) : (
                    <AlertTriangle size={16} className="shrink-0 text-amber-500" />
                  )}
                  <div>
                    <p className="text-sm font-medium text-slate-900">{skillLabel(r.skillId)}</p>
                    {r.sources.length > 0 && <p className="text-xs text-slate-400">Evidenced by: {r.sources.join('; ')}</p>}
                  </div>
                </div>
                <Badge tone={r.mandatory ? 'blue' : 'slate'}>{r.mandatory ? 'Mandatory' : 'Preferred'}</Badge>
              </div>
            ))}
            {requirements.length === 0 && <p className="text-sm text-slate-400">This posting lists no structured skill requirements.</p>}
          </div>
        </Card>
      )}

      {tab === 'missing' && (
        <Card>
          <CardHeader title="Requirements with no matching evidence on file" />
          <div className="space-y-2">
            {missing.map((r) => (
              <div key={r.skillId} className="flex items-center justify-between gap-3 rounded-lg border border-amber-100 bg-amber-50/50 p-3">
                <p className="text-sm font-medium text-slate-900">{skillLabel(r.skillId)}</p>
                <Badge tone={r.mandatory ? 'red' : 'amber'}>{r.mandatory ? 'Mandatory - gap' : 'Preferred - gap'}</Badge>
              </div>
            ))}
            {missing.length === 0 && <p className="text-sm text-slate-400">No gaps - every listed requirement has matching evidence.</p>}
          </div>
          {scorecard && scorecard.reasonCodes.length > 0 && (
            <div className="mt-4 rounded-lg bg-slate-50 p-3">
              <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">Scoring reason codes</p>
              <ul className="space-y-0.5 text-xs text-slate-500">
                {scorecard.reasonCodes.map((rc) => (
                  <li key={rc}>{rc}</li>
                ))}
              </ul>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
