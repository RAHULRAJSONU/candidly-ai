import { useEffect, useState } from 'react'
import { CheckCircle2, Edit3, Clock, XCircle } from 'lucide-react'
import { api } from '../api/client'
import type { EmailIntakeRecord } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'

export function EmailReview() {
  const { selected } = useCandidate()
  const [records, setRecords] = useState<EmailIntakeRecord[]>([])
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editedText, setEditedText] = useState('')
  const [note, setNote] = useState<Record<string, string>>({})
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = () => {
    if (!selected) return
    api.emailIntake.needsReview(selected.id).then(setRecords)
  }

  useEffect(load, [selected])

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  const act = async (action: () => Promise<unknown>, recordId: string) => {
    setBusyId(recordId)
    try {
      await action()
      setEditingId(null)
      load()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Human-in-the-Loop: Agent Emails</h1>
        <p className="mt-1 text-sm text-slate-500">
          {selected.fullName}&rsquo;s AI-drafted replies to interview invitations, awaiting your decision. Nothing is
          ever sent automatically - there is no outbound email integration in this app; approving here only records
          your decision.
        </p>
      </div>

      <Card>
        <CardHeader title={`Needs Review (${records.length})`} />
        <div className="space-y-4">
          {records.map((r) => (
            <div key={r.id} className="rounded-lg border border-slate-200 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    {r.jobPosting?.company} &middot; {r.jobPosting?.title}
                  </p>
                  <p className="text-xs text-slate-400">
                    {r.mode} interview{r.suggestedScheduledAt ? ` · suggested ${new Date(r.suggestedScheduledAt).toLocaleString()}` : ''}
                  </p>
                </div>
                <Badge tone="blue">{r.emailType.replace(/_/g, ' ')}</Badge>
              </div>

              <div className="mt-3 rounded-lg bg-slate-50 p-3">
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">AI Extracted Information</p>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-600">
                  <div>
                    <dt className="inline text-slate-400">Company: </dt>
                    <dd className="inline">{r.jobPosting?.company}</dd>
                  </div>
                  <div>
                    <dt className="inline text-slate-400">Role: </dt>
                    <dd className="inline">{r.jobPosting?.title}</dd>
                  </div>
                  <div>
                    <dt className="inline text-slate-400">Mode: </dt>
                    <dd className="inline">{r.mode}</dd>
                  </div>
                  <div>
                    <dt className="inline text-slate-400">Suggested time: </dt>
                    <dd className="inline">{r.suggestedScheduledAt ? new Date(r.suggestedScheduledAt).toLocaleString() : 'Not stated'}</dd>
                  </div>
                </dl>
              </div>

              <div className="mt-3">
                <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">Suggested Draft</p>
                {editingId === r.id ? (
                  <textarea
                    value={editedText}
                    onChange={(e) => setEditedText(e.target.value)}
                    rows={5}
                    className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
                  />
                ) : (
                  <p className="whitespace-pre-wrap rounded-lg border border-slate-100 bg-white p-3 text-sm text-slate-700">
                    {r.draftReplyText}
                  </p>
                )}
              </div>

              <input
                value={note[r.id] ?? ''}
                onChange={(e) => setNote({ ...note, [r.id]: e.target.value })}
                placeholder="Optional note..."
                className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-1.5 text-xs focus:border-blue-400 focus:outline-none"
              />

              <div className="mt-3 flex flex-wrap gap-2">
                {editingId === r.id ? (
                  <>
                    <Button
                      disabled={busyId === r.id}
                      onClick={() => act(() => api.emailIntake.editAndSend(selected.id, r.id, editedText, note[r.id]), r.id)}
                      icon={<CheckCircle2 size={14} />}
                    >
                      Save & Approve
                    </Button>
                    <Button variant="secondary" onClick={() => setEditingId(null)}>
                      Cancel edit
                    </Button>
                  </>
                ) : (
                  <>
                    <Button
                      disabled={busyId === r.id}
                      onClick={() => act(() => api.emailIntake.approveSend(selected.id, r.id, note[r.id]), r.id)}
                      icon={<CheckCircle2 size={14} />}
                    >
                      Approve & Send
                    </Button>
                    <Button
                      variant="secondary"
                      disabled={busyId === r.id}
                      onClick={() => {
                        setEditingId(r.id)
                        setEditedText(r.draftReplyText ?? '')
                      }}
                      icon={<Edit3 size={14} />}
                    >
                      Edit & Send
                    </Button>
                    <Button
                      variant="secondary"
                      disabled={busyId === r.id}
                      onClick={() => act(() => api.emailIntake.schedule(selected.id, r.id, note[r.id]), r.id)}
                      icon={<Clock size={14} />}
                    >
                      Schedule to Send
                    </Button>
                    <Button
                      variant="danger"
                      disabled={busyId === r.id}
                      onClick={() => act(() => api.emailIntake.cancel(selected.id, r.id, note[r.id]), r.id)}
                      icon={<XCircle size={14} />}
                    >
                      Cancel
                    </Button>
                  </>
                )}
              </div>
            </div>
          ))}
          {records.length === 0 && <p className="text-sm text-slate-400">Nothing needs review right now.</p>}
        </div>
      </Card>
    </div>
  )
}
