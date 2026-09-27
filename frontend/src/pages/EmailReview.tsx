import { useEffect, useState } from 'react'
import { CheckCircle2, Edit3, Clock, XCircle, MessageSquareDiff, History } from 'lucide-react'
import { api } from '../api/client'
import type { EmailIntakeRecord } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'

export function EmailReview() {
  const { selected } = useCandidate()
  const [records, setRecords] = useState<EmailIntakeRecord[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editedText, setEditedText] = useState('')
  const [suggestingId, setSuggestingId] = useState<string | null>(null)
  const [alternativeText, setAlternativeText] = useState('')
  const [note, setNote] = useState<Record<string, string>>({})
  const [busyId, setBusyId] = useState<string | null>(null)
  const [threadId, setThreadId] = useState<string | null>(null)
  const [thread, setThread] = useState<EmailIntakeRecord[]>([])

  const load = () => {
    if (!selected) return
    setLoadError(null)
    api.emailIntake.needsReview(selected.id).then(setRecords).catch((e) => setLoadError(describeError(e)))
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
      setSuggestingId(null)
      load()
    } finally {
      setBusyId(null)
    }
  }

  const toggleThread = (record: EmailIntakeRecord) => {
    if (threadId === record.id) {
      setThreadId(null)
      setThread([])
      return
    }
    setThreadId(record.id)
    if (!selected || !record.jobPosting) {
      setThread([])
      return
    }
    api.emailIntake
      .thread(selected.id, record.jobPosting.id)
      .then(setThread)
      .catch((e) => setLoadError(describeError(e)))
  }

  return (
    <div className="space-y-6">
      {loadError && <ErrorBanner message={`Couldn't load emails: ${loadError}`} />}
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
                    <dt className="inline text-slate-400">Type: </dt>
                    <dd className="inline">{r.emailType.replace(/_/g, ' ')}</dd>
                  </div>
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

              <div className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-3">
                <div className="lg:col-span-2">
                  <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                    {suggestingId === r.id ? 'Propose an Alternative' : 'Suggested Draft'}
                  </p>
                  {editingId === r.id ? (
                    <textarea
                      value={editedText}
                      onChange={(e) => setEditedText(e.target.value)}
                      rows={5}
                      className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
                    />
                  ) : suggestingId === r.id ? (
                    <textarea
                      value={alternativeText}
                      onChange={(e) => setAlternativeText(e.target.value)}
                      rows={5}
                      placeholder="e.g. Could we do Thursday at 2pm instead?"
                      className="w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
                    />
                  ) : (
                    <p className="whitespace-pre-wrap rounded-lg border border-slate-100 bg-white p-3 text-sm text-slate-700">
                      {r.draftReplyText}
                    </p>
                  )}
                </div>
                <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                  <div className="mb-1 flex items-center justify-between">
                    <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Context</p>
                    {r.jobPosting && (
                      <button
                        onClick={() => toggleThread(r)}
                        className="flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline"
                      >
                        <History size={12} /> Previous emails
                      </button>
                    )}
                  </div>
                  <dl className="space-y-1 text-xs text-slate-600">
                    <div>
                      <dt className="text-slate-400">Company</dt>
                      <dd className="font-medium text-slate-700">{r.jobPosting?.company ?? 'Unknown'}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-400">Location</dt>
                      <dd className="font-medium text-slate-700">{r.jobPosting?.location ?? 'Not stated'}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-400">Screening decision</dt>
                      <dd className="font-medium text-slate-700">{r.jobPosting?.screeningDecision ?? 'N/A'}</dd>
                    </div>
                  </dl>
                  {threadId === r.id && (
                    <div className="mt-2 space-y-1.5 border-t border-slate-200 pt-2">
                      {thread.map((t) => (
                        <div key={t.id} className="rounded border border-slate-200 bg-white p-1.5 text-xs">
                          <span className="font-medium text-slate-700">{t.emailType.replace(/_/g, ' ')}</span>
                          <span className="ml-1 text-slate-400">{new Date(t.classifiedAt).toLocaleDateString()}</span>
                        </div>
                      ))}
                      {thread.length === 0 && <p className="text-xs text-slate-400">No prior emails for this application.</p>}
                    </div>
                  )}
                </div>
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
                ) : suggestingId === r.id ? (
                  <>
                    <Button
                      disabled={busyId === r.id || !alternativeText.trim()}
                      onClick={() =>
                        act(() => api.emailIntake.suggestAlternative(selected.id, r.id, alternativeText, note[r.id]), r.id)
                      }
                      icon={<MessageSquareDiff size={14} />}
                    >
                      Save Suggestion
                    </Button>
                    <Button variant="secondary" onClick={() => setSuggestingId(null)}>
                      Cancel
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
                      onClick={() => {
                        setSuggestingId(r.id)
                        setAlternativeText('')
                      }}
                      icon={<MessageSquareDiff size={14} />}
                    >
                      Suggest Alternative
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
