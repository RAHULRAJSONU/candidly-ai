import { useEffect, useState } from 'react'
import { AlertTriangle, ShieldCheck, ShieldAlert, Trash2, Download, Lock, FileCheck } from 'lucide-react'
import { api } from '../api/client'
import type { AuditEvent, AuditEventType, AuditVerifyResult, BiasAuditReport, BiasGroupStat } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { ProgressBar, StatCard } from '../components/ui/StatCard'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { formatRelativeTime } from '../lib/format'

// This deployment's real, already-implemented compliance surfaces (audit ledger, GDPR
// export/erase, LL144 bias audit) map to these frameworks - not a claim of formal
// certification, just labeling what backs each control.
const FRAMEWORKS = [
  { label: 'GDPR', detail: 'Export & erasure (docs/02 §4.1)' },
  { label: 'CCPA', detail: 'Data-subject access & deletion' },
  { label: 'LL144', detail: 'Annual bias audit (docs/02 §4.3)' },
]

const ACTIVITY_EVENT_TYPES: AuditEventType[] = ['CANDIDATE_DATA_ERASED', 'TAILORED_ARTIFACT_REVIEWED', 'JOB_POSTING_SCREENED']

const ACTIVITY_LABEL: Record<string, string> = {
  CANDIDATE_DATA_ERASED: 'Data erasure request',
  TAILORED_ARTIFACT_REVIEWED: 'Application reviewed',
  JOB_POSTING_SCREENED: 'Job posting screened',
}

function GroupTable({ title, groups }: { title: string; groups: BiasGroupStat[] }) {
  return (
    <Card>
      <CardHeader title={title} />
      <div className="space-y-3">
        {groups.map((g) => {
          const flagged = g.impactRatio !== null && g.impactRatio < 0.8
          return (
            <div key={g.category}>
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="font-medium text-slate-700">{g.category}</span>
                <span className="flex items-center gap-1 text-slate-500">
                  {g.shortlisted}/{g.totalScored} shortlisted ({Math.round(g.shortlistRate * 100)}%)
                  {flagged && <AlertTriangle size={14} className="text-red-500" />}
                </span>
              </div>
              <ProgressBar value={g.shortlistRate} tone={flagged ? 'amber' : 'green'} />
              {g.impactRatio !== null && (
                <p className="mt-0.5 text-xs text-slate-400">
                  Impact ratio {g.impactRatio.toFixed(2)}
                  {flagged && ' - below the four-fifths (0.8) rule'}
                </p>
              )}
            </div>
          )
        })}
        {groups.length === 0 && <p className="text-sm text-slate-400">No scored candidates in this dimension yet.</p>}
      </div>
    </Card>
  )
}

function anyGroupFlagged(report: BiasAuditReport | null): boolean {
  if (!report) return false
  return [...report.byGender, ...report.byRaceEthnicity].some((g) => g.impactRatio !== null && g.impactRatio < 0.8)
}

export function Compliance() {
  const { selected } = useCandidate()
  const [verify, setVerify] = useState<AuditVerifyResult | null>(null)
  const [report, setReport] = useState<BiasAuditReport | null>(null)
  const [purgeDays, setPurgeDays] = useState(365)
  const [purgeResult, setPurgeResult] = useState<string | null>(null)
  const [gdprMessage, setGdprMessage] = useState<string | null>(null)
  const [activity, setActivity] = useState<AuditEvent[]>([])

  const load = () => {
    api.audit.verify().then(setVerify)
    api.audit.biasReport().then(setReport)
    api.audit.events().then((events) =>
      setActivity(
        events
          .filter((e) => ACTIVITY_EVENT_TYPES.includes(e.eventType))
          .slice()
          .reverse(),
      ),
    )
  }

  useEffect(load, [])

  const runPurge = async () => {
    setPurgeResult(null)
    try {
      const result = await api.audit.purge(purgeDays)
      setPurgeResult(`Purged ${result.purged} event(s) older than ${purgeDays} days.`)
      load()
    } catch (e) {
      setPurgeResult(e instanceof Error ? e.message : 'Purge failed')
    }
  }

  const exportData = async () => {
    if (!selected) return
    const data = await api.candidates.export(selected.id)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${selected.fullName.replace(/\s+/g, '_')}_export.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const eraseData = async () => {
    if (!selected) return
    if (!confirm(`Permanently erase all data for ${selected.fullName}? This cannot be undone.`)) return
    await api.candidates.erase(selected.id)
    setGdprMessage(`${selected.fullName} erased. Reload the candidate picker to see the change.`)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Compliance</h1>
        <p className="mt-1 text-sm text-slate-500">
          Audit ledger integrity, LL144 bias-audit reporting (docs/02 §4.3), and GDPR data-subject actions (docs/02
          §4.1).
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          {FRAMEWORKS.map((f) => (
            <Badge key={f.label} tone="green" className="gap-1.5">
              <ShieldCheck size={12} />
              {f.label} <span className="font-normal text-emerald-600/80">&middot; {f.detail}</span>
            </Badge>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          icon={<ShieldCheck size={18} />}
          iconTone={verify?.intact ? 'green' : 'amber'}
          value={verify === null ? 'Checking...' : verify.intact ? 'Intact' : 'Broken'}
          label="Audit ledger"
        />
        <StatCard
          icon={<FileCheck size={18} />}
          iconTone={anyGroupFlagged(report) ? 'amber' : 'green'}
          value={anyGroupFlagged(report) ? 'Review needed' : 'Compliant'}
          label="LL144 bias audit"
        />
        <StatCard icon={<Lock size={18} />} iconTone="blue" value="Ready" label="GDPR data rights" />
      </div>

      <Card padded={false}>
        <CardHeader title="Audit ledger & retention" subtitle="Hash-chained decision ledger, checkpointed on purge (docs/02 §2)." />
        <div className="border-t border-slate-100">
          <div className="flex items-center justify-between gap-4 px-5 py-3">
            <div className="flex items-center gap-3">
              {verify?.intact ? (
                <ShieldCheck className="text-emerald-600" size={22} />
              ) : (
                <ShieldAlert className="text-red-600" size={22} />
              )}
              <span className="text-sm text-slate-600">Chain status</span>
            </div>
            <span className="text-sm font-semibold text-slate-900">
              {verify === null ? 'Checking...' : verify.intact ? 'Intact' : `BROKEN (first: ${verify.firstBrokenEventId})`}
            </span>
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-slate-100 px-5 py-3">
            <span className="text-sm text-slate-600">Retention window</span>
            <div className="flex items-center gap-2">
              <input
                type="number"
                value={purgeDays}
                onChange={(e) => setPurgeDays(Number(e.target.value))}
                className="w-20 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
              />
              <span className="text-sm text-slate-500">days</span>
            </div>
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-slate-100 px-5 py-3">
            <span className="text-xs text-slate-400">{purgeResult ?? 'Purges events older than the window above.'}</span>
            <Button variant="secondary" onClick={runPurge}>
              Purge older events
            </Button>
          </div>
        </div>
      </Card>

      {report && (
        <div className="grid grid-cols-2 gap-6">
          <GroupTable title="Shortlist rate by gender" groups={report.byGender} />
          <GroupTable title="Shortlist rate by race/ethnicity" groups={report.byRaceEthnicity} />
        </div>
      )}

      <Card>
        <CardHeader title="Recent compliance activity" subtitle="Erasures, reviews, and screening decisions from the audit ledger" />
        <div className="max-h-72 space-y-2 overflow-y-auto">
          {activity.slice(0, 20).map((e) => (
            <div key={e.id} className="flex items-start justify-between gap-3 rounded-lg border border-slate-100 p-2.5 text-sm">
              <div>
                <p className="font-medium text-slate-800">{ACTIVITY_LABEL[e.eventType] ?? e.eventType}</p>
                <p className="mt-0.5 text-xs text-slate-400">{e.details}</p>
              </div>
              <span className="shrink-0 text-xs text-slate-400">{formatRelativeTime(e.occurredAt)}</span>
            </div>
          ))}
          {activity.length === 0 && <p className="text-sm text-slate-400">No compliance-relevant events recorded yet.</p>}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="GDPR data-subject actions"
          subtitle={selected ? `For ${selected.fullName}` : 'Select a candidate in the top bar'}
        />
        <div className="flex gap-3">
          <Button variant="secondary" icon={<Download size={16} />} onClick={exportData} disabled={!selected}>
            Export data
          </Button>
          <Button variant="danger" icon={<Trash2 size={16} />} onClick={eraseData} disabled={!selected}>
            Erase candidate
          </Button>
        </div>
        {gdprMessage && <p className="mt-2 text-sm text-slate-500">{gdprMessage}</p>}
        <p className="mt-3 text-xs text-slate-400">
          Erasure hard-deletes every table this candidate owns but deliberately leaves their audit-ledger rows in
          place, so the chain stays verifiable (see CandidateDataSubjectService's javadoc).
        </p>
      </Card>
    </div>
  )
}
