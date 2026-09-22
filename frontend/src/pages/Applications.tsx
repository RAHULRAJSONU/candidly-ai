import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, ExternalLink, Plus, ShieldCheck, XCircle } from 'lucide-react'
import { api } from '../api/client'
import type { ArtifactReviewView, AtsScoreResult, ManualApplication, ManualApplicationStatus } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge, toneForScore } from '../components/ui/Badge'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { Tabs } from '../components/ui/Tabs'
import { Button } from '../components/ui/Button'
import { ProgressBar } from '../components/ui/StatCard'
import { formatRelativeTime } from '../lib/format'

const MANUAL_STATUS_TONE: Record<ManualApplicationStatus, 'blue' | 'green' | 'red' | 'slate' | 'amber'> = {
  APPLIED: 'blue',
  INTERVIEWING: 'amber',
  OFFER: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'slate',
}

export function Applications() {
  const { selected: candidate } = useCandidate()
  const [tab, setTab] = useState('pending')
  const [pending, setPending] = useState<ArtifactReviewView[]>([])
  const [history, setHistory] = useState<ArtifactReviewView[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [loading, setLoading] = useState(true)
  const [atsScore, setAtsScore] = useState<AtsScoreResult | null>(null)
  const [docTab, setDocTab] = useState('resume')

  const [manual, setManual] = useState<ManualApplication[]>([])
  const [showAddForm, setShowAddForm] = useState(false)
  const [expandedManualId, setExpandedManualId] = useState<string | null>(null)

  const load = () =>
    Promise.all([api.tailoring.pendingReview(), api.tailoring.history()]).then(([p, h]) => {
      setPending(p)
      setHistory(h)
    })

  const loadManual = () => {
    if (!candidate) return
    api.pipeline.manualApplications(candidate.id).then(setManual)
  }

  useEffect(() => {
    setLoading(true)
    load().finally(() => setLoading(false))
  }, [])

  useEffect(loadManual, [candidate])

  const list = tab === 'pending' ? pending : history
  const selected = list.find((v) => v.artifact.id === selectedId) ?? list[0] ?? null

  useEffect(() => {
    if (!selected) {
      setAtsScore(null)
      return
    }
    api.tailoring.atsScore(selected.artifact.id).then(setAtsScore)
  }, [selected?.artifact.id])

  useEffect(() => {
    setDocTab('resume')
  }, [selected?.artifact.id])

  const decide = async (action: 'approve' | 'reject') => {
    if (!selected) return
    if (action === 'approve') await api.tailoring.approve(selected.artifact.id, note)
    else await api.tailoring.reject(selected.artifact.id, note)
    setNote('')
    setSelectedId(null)
    await load()
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Applications</h1>
        <p className="mt-1 text-sm text-slate-500">
          Human-in-the-loop review queue (docs/01 §2 - default path, not the exception). No submission happens here or
          anywhere in this app; approval only clears the artifact to hand back to the candidate.
        </p>
      </div>

      <Tabs
        tabs={[
          { key: 'pending', label: 'Pending Review', count: pending.length },
          { key: 'history', label: 'History', count: history.length },
          { key: 'manual', label: 'My Applications', count: manual.length },
        ]}
        active={tab}
        onChange={(k) => {
          setTab(k)
          setSelectedId(null)
        }}
      />

      {tab === 'manual' ? (
        <ManualApplicationsPanel
          candidateId={candidate?.id ?? null}
          applications={manual}
          showAddForm={showAddForm}
          setShowAddForm={setShowAddForm}
          expandedId={expandedManualId}
          setExpandedId={setExpandedManualId}
          reload={loadManual}
        />
      ) : (
      <div className="grid grid-cols-4 gap-6">
        <div className="col-span-1 space-y-2">
          {loading && <p className="text-sm text-slate-400">Loading&hellip;</p>}
          {!loading && list.length === 0 && (
            <Card>
              <p className="text-sm text-slate-500">Nothing here yet.</p>
            </Card>
          )}
          {list.map((v) => (
            <button
              key={v.artifact.id}
              onClick={() => setSelectedId(v.artifact.id)}
              className={`flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-colors ${
                selected?.artifact.id === v.artifact.id ? 'border-blue-400 bg-blue-50/40' : 'border-slate-200 bg-white hover:border-slate-300'
              }`}
            >
              <CompanyAvatar name={v.artifact.jobPosting.company} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">{v.artifact.candidate.fullName}</p>
                <p className="truncate text-xs text-slate-500">
                  {v.artifact.jobPosting.title} &middot; {v.artifact.jobPosting.company}
                </p>
              </div>
              {v.artifact.status === 'APPROVED' && <Badge tone="green">Approved</Badge>}
              {v.artifact.status === 'REJECTED' && <Badge tone="red">Rejected</Badge>}
              {v.artifact.status === 'PENDING_APPROVAL' && <Badge tone="blue">Pending</Badge>}
              {v.artifact.status === 'NEEDS_HUMAN_REVIEW' && <Badge tone="amber">Needs review</Badge>}
            </button>
          ))}
        </div>

        <div className="col-span-2">
          {!selected ? (
            <Card>
              <p className="text-sm text-slate-500">Select an application to review.</p>
            </Card>
          ) : (
            <div className="space-y-4">
              <Card>
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-semibold text-slate-900">
                      {selected.artifact.candidate.fullName} &rarr; {selected.artifact.jobPosting.title}
                    </h3>
                    <p className="text-sm text-slate-500">{selected.artifact.jobPosting.company}</p>
                  </div>
                  {selected.artifact.groundingPassed ? (
                    <Badge tone="green">
                      <CheckCircle2 size={12} /> Grounding passed
                    </Badge>
                  ) : (
                    <Badge tone="red">
                      <XCircle size={12} /> Needs human review
                    </Badge>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {selected.artifact.criticLoopsUsed} critic loop(s) &middot; generated{' '}
                  {formatRelativeTime(selected.artifact.generatedAt)}
                </p>

                {selected.matchScorecard && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Badge tone={toneForScore(selected.matchScorecard.compositeScore)}>
                      {Math.round(selected.matchScorecard.compositeScore * 100)}% composite
                    </Badge>
                    <Badge tone="slate">Skill {Math.round(selected.matchScorecard.skillScore * 100)}%</Badge>
                    <Badge tone="slate">Experience {Math.round(selected.matchScorecard.experienceScore * 100)}%</Badge>
                    <Badge tone="slate">Semantic {Math.round(selected.matchScorecard.semanticScore * 100)}%</Badge>
                    <Badge tone="slate">Domain {Math.round(selected.matchScorecard.domainScore * 100)}%</Badge>
                  </div>
                )}
              </Card>

              <Card padded={false}>
                <div className="px-5 pt-4">
                  <Tabs
                    tabs={[
                      { key: 'resume', label: 'Resume' },
                      ...(selected.artifact.coverLetterContent
                        ? [{ key: 'coverLetter', label: 'Cover Letter' }]
                        : []),
                      ...(selected.artifact.screeningAnswers.length > 0
                        ? [{ key: 'screening', label: 'Screening Answers', count: selected.artifact.screeningAnswers.length }]
                        : []),
                      { key: 'ats', label: 'Additional Documents' },
                    ]}
                    active={docTab}
                    onChange={setDocTab}
                  />
                </div>

                <div className="p-5">
                  {docTab === 'resume' && (
                    <>
                      <div className="max-h-96 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
                        {selected.artifact.content || '(no bullets survived grounding)'}
                      </div>
                      {selected.artifact.rejectedClaims.length > 0 && (
                        <div className="mt-3">
                          <p className="mb-1 text-xs font-semibold uppercase text-amber-600">Rejected claims</p>
                          <ul className="list-inside list-disc space-y-1 text-sm text-amber-700">
                            {selected.artifact.rejectedClaims.map((c, i) => (
                              <li key={i}>{c}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </>
                  )}

                  {docTab === 'coverLetter' && (
                    <div className="max-h-96 space-y-3 overflow-y-auto rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
                      {(selected.artifact.coverLetterContent ?? '')
                        .split(/\n{2,}/)
                        .filter((p) => p.trim().length > 0)
                        .map((para, i) => (
                          <p key={i} className="whitespace-pre-wrap leading-relaxed">
                            {para}
                          </p>
                        ))}
                    </div>
                  )}

                  {docTab === 'screening' && (
                    <div className="max-h-96 space-y-3 overflow-y-auto">
                      {selected.artifact.screeningAnswers.map((entry, i) => {
                        const [question, answer] = entry.split(' || ')
                        return (
                          <div key={i} className="rounded-lg border border-slate-200 p-3">
                            <p className="text-xs font-semibold uppercase text-slate-400">{question}</p>
                            <p className="mt-1 text-sm text-slate-700">{answer}</p>
                          </div>
                        )
                      })}
                    </div>
                  )}

                  {docTab === 'ats' && (
                    <div>
                      {atsScore ? (
                        <>
                          <div className="mb-4 flex items-center gap-4">
                            <span
                              className={`text-3xl font-bold ${
                                atsScore.score >= 80
                                  ? 'text-emerald-600'
                                  : atsScore.score >= 60
                                    ? 'text-amber-600'
                                    : 'text-red-600'
                              }`}
                            >
                              {atsScore.score}
                              <span className="text-base font-medium text-slate-400">/100</span>
                            </span>
                            <div className="flex-1">
                              <ProgressBar
                                value={atsScore.score / 100}
                                tone={atsScore.score >= 80 ? 'green' : atsScore.score >= 60 ? 'amber' : 'blue'}
                              />
                            </div>
                          </div>
                          <p className="mb-3 text-xs text-slate-400">
                            Deterministic checks, not a model judgment - see AtsScoreService.
                          </p>
                          <ul className="space-y-1.5 text-sm">
                            {atsScore.checks.map((c) => (
                              <li key={c.label} className="flex items-center gap-2">
                                {c.passed ? (
                                  <CheckCircle2 size={14} className="text-emerald-600" />
                                ) : (
                                  <XCircle size={14} className="text-red-500" />
                                )}
                                <span className={c.passed ? 'text-slate-700' : 'text-red-700'}>{c.label}</span>
                              </li>
                            ))}
                          </ul>
                        </>
                      ) : (
                        <p className="text-sm text-slate-400">Loading ATS score&hellip;</p>
                      )}
                    </div>
                  )}
                </div>
              </Card>

              {tab === 'pending' ? (
                <Card>
                  <textarea
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Optional review note"
                    rows={2}
                    className="mb-3 w-full rounded-lg border border-slate-200 p-2 text-sm focus:border-blue-400 focus:outline-none"
                  />
                  <div className="flex gap-2">
                    <Button onClick={() => decide('approve')}>Approve</Button>
                    <Button variant="danger" onClick={() => decide('reject')}>
                      Reject
                    </Button>
                  </div>
                </Card>
              ) : (
                selected.artifact.reviewNote && (
                  <Card>
                    <p className="text-xs font-semibold uppercase text-slate-400">Reviewer note</p>
                    <p className="mt-1 text-sm text-slate-600">{selected.artifact.reviewNote}</p>
                  </Card>
                )
              )}
            </div>
          )}
        </div>

        <div className="col-span-1 space-y-4">
          {selected && (
            <>
              <Card>
                <CardHeader title="AI Verification" />
                {selected.artifact.groundingPassed && selected.artifact.rejectedClaims.length === 0 ? (
                  <div className="flex items-start gap-2 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                    <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
                    <span>All claims verified against your profile. Nothing was invented or embellished.</span>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <div className="flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-700 ring-1 ring-inset ring-amber-600/20">
                      <XCircle size={16} className="mt-0.5 shrink-0" />
                      <span>
                        {selected.artifact.groundingPassed
                          ? 'Grounding passed, but some claims were flagged and dropped:'
                          : 'This artifact needs human review before it can be trusted.'}
                      </span>
                    </div>
                    {selected.artifact.rejectedClaims.length > 0 && (
                      <ul className="list-inside list-disc space-y-1 text-xs text-slate-600">
                        {selected.artifact.rejectedClaims.map((c, i) => (
                          <li key={i}>{c}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </Card>

              <Card>
                <CardHeader title="Next Step" />
                {selected.artifact.status === 'APPROVED' ? (
                  <div className="space-y-3">
                    <p className="text-sm text-slate-600">
                      This artifact is approved. You submit it yourself, in your own browser session - Candidly never
                      submits on your behalf.
                    </p>
                    <Link to={`/browser-assist/${selected.artifact.id}`}>
                      <Button icon={<ExternalLink size={14} />} className="w-full">
                        Open Browser Assist
                      </Button>
                    </Link>
                  </div>
                ) : selected.artifact.status === 'PENDING_APPROVAL' ? (
                  <p className="text-sm text-slate-500">Awaiting your review below before this can move forward.</p>
                ) : selected.artifact.status === 'NEEDS_HUMAN_REVIEW' ? (
                  <p className="text-sm text-slate-500">
                    Grounding flagged this artifact - review the content tabs carefully before approving.
                  </p>
                ) : (
                  <p className="text-sm text-slate-500">This artifact was rejected and won&apos;t move forward.</p>
                )}
                <div className="mt-4 flex items-start gap-2 rounded-lg bg-blue-50 p-3 text-xs text-blue-700 ring-1 ring-inset ring-blue-600/20">
                  <ShieldCheck size={14} className="mt-0.5 shrink-0" />
                  <span>Human-in-the-loop by default - no application is ever submitted automatically.</span>
                </div>
              </Card>
            </>
          )}
        </div>
      </div>
      )}
    </div>
  )
}

function ManualApplicationsPanel({
  candidateId,
  applications,
  showAddForm,
  setShowAddForm,
  expandedId,
  setExpandedId,
  reload,
}: {
  candidateId: string | null
  applications: ManualApplication[]
  showAddForm: boolean
  setShowAddForm: (v: boolean) => void
  expandedId: string | null
  setExpandedId: (id: string | null) => void
  reload: () => void
}) {
  const [company, setCompany] = useState('')
  const [role, setRole] = useState('')
  const [appliedDate, setAppliedDate] = useState('')
  const [notes, setNotes] = useState('')
  const [noteDraft, setNoteDraft] = useState('')
  const [saving, setSaving] = useState(false)

  if (!candidateId) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  const addApplication = async () => {
    if (!company.trim() || !role.trim()) return
    setSaving(true)
    try {
      await api.pipeline.addManualApplication(candidateId, {
        company,
        role,
        appliedDate: appliedDate || undefined,
        notes: notes || undefined,
      })
      setCompany('')
      setRole('')
      setAppliedDate('')
      setNotes('')
      setShowAddForm(false)
      reload()
    } finally {
      setSaving(false)
    }
  }

  const addNote = async (applicationId: string) => {
    if (!noteDraft.trim()) return
    await api.pipeline.addManualApplicationTimelineNote(candidateId, applicationId, noteDraft)
    setNoteDraft('')
    reload()
  }

  return (
    <Card>
      <CardHeader
        title="Applications logged outside this platform"
        subtitle="Track something you applied to directly on a job board or company site."
        action={
          <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowAddForm(!showAddForm)}>
            Add Application
          </Button>
        }
      />
      {showAddForm && (
        <div className="mb-4 grid grid-cols-1 gap-2 rounded-lg border border-slate-200 p-3 sm:grid-cols-2">
          <input
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            placeholder="Company"
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
          />
          <input
            value={role}
            onChange={(e) => setRole(e.target.value)}
            placeholder="Role"
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
          />
          <input
            type="date"
            value={appliedDate}
            onChange={(e) => setAppliedDate(e.target.value)}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
          />
          <input
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Notes (optional)"
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
          />
          <Button className="sm:col-span-2" onClick={addApplication} disabled={saving || !company.trim() || !role.trim()}>
            {saving ? 'Saving...' : 'Save application'}
          </Button>
        </div>
      )}

      <div className="space-y-2">
        {applications.map((a) => (
          <div key={a.id} className="rounded-lg border border-slate-200 p-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-medium text-slate-900">
                  {a.role} &middot; {a.company}
                </p>
                <p className="text-xs text-slate-400">
                  {a.appliedDate ? `Applied ${a.appliedDate}` : formatRelativeTime(a.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <select
                  value={a.status}
                  onChange={(e) =>
                    api.pipeline
                      .updateManualApplicationStatus(candidateId, a.id, e.target.value as ManualApplicationStatus)
                      .then(reload)
                  }
                  className="rounded-lg border border-slate-200 px-2 py-1 text-xs focus:border-blue-400 focus:outline-none"
                >
                  {(['APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN'] as ManualApplicationStatus[]).map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
                <Badge tone={MANUAL_STATUS_TONE[a.status]}>{a.status}</Badge>
                <button
                  onClick={() => setExpandedId(expandedId === a.id ? null : a.id)}
                  className="text-xs font-medium text-blue-600 hover:underline"
                >
                  {expandedId === a.id ? 'Hide timeline' : 'Timeline'}
                </button>
              </div>
            </div>

            {expandedId === a.id && (
              <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
                {a.notes && <p className="text-sm text-slate-600">{a.notes}</p>}
                <ul className="space-y-1.5 text-xs text-slate-500">
                  {a.timeline.map((entry) => (
                    <li key={entry.id}>
                      <span className="text-slate-400">{formatRelativeTime(entry.occurredAt)}:</span> {entry.note}
                    </li>
                  ))}
                </ul>
                <div className="flex gap-2">
                  <input
                    value={noteDraft}
                    onChange={(e) => setNoteDraft(e.target.value)}
                    placeholder="Add a note..."
                    className="flex-1 rounded-lg border border-slate-200 px-2 py-1 text-xs focus:border-blue-400 focus:outline-none"
                  />
                  <Button variant="secondary" onClick={() => addNote(a.id)}>
                    Add
                  </Button>
                </div>
              </div>
            )}
          </div>
        ))}
        {applications.length === 0 && <p className="text-sm text-slate-400">No applications logged yet.</p>}
      </div>
    </Card>
  )
}
