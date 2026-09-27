import { useEffect, useState } from 'react'
import { AlertTriangle, Mail } from 'lucide-react'
import { api } from '../api/client'
import type { EmailIntakeResult, PipelineInterview, PipelineOffer, PipelineSummary } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import { formatRelativeTime } from '../lib/format'

const FUNNEL_STAGES: { key: keyof PipelineSummary; label: string; tone: string }[] = [
  { key: 'discovered', label: 'Discovered', tone: 'bg-slate-100 text-slate-700' },
  { key: 'filtered', label: 'Filtered', tone: 'bg-sky-50 text-sky-700' },
  { key: 'matched', label: 'Matched', tone: 'bg-blue-50 text-blue-700' },
  { key: 'shortlisted', label: 'Shortlisted', tone: 'bg-indigo-50 text-indigo-700' },
  { key: 'tailoring', label: 'Tailoring', tone: 'bg-violet-50 text-violet-700' },
  { key: 'pendingApproval', label: 'Pending Approval', tone: 'bg-amber-50 text-amber-700' },
  { key: 'approved', label: 'Approved', tone: 'bg-cyan-50 text-cyan-700' },
  { key: 'rejected', label: 'Not Selected', tone: 'bg-red-50 text-red-700' },
  { key: 'interviews', label: 'Interviews', tone: 'bg-teal-50 text-teal-700' },
  { key: 'offers', label: 'Offers', tone: 'bg-emerald-50 text-emerald-700' },
]

export function Pipeline() {
  const { selected } = useCandidate()
  const [summary, setSummary] = useState<PipelineSummary | null>(null)
  const [interviews, setInterviews] = useState<PipelineInterview[]>([])
  const [offers, setOffers] = useState<PipelineOffer[]>([])
  const [emailText, setEmailText] = useState('')
  const [emailResult, setEmailResult] = useState<EmailIntakeResult | null>(null)
  const [classifying, setClassifying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    if (!selected) return
    setError(null)
    api.pipeline.summary(selected.id).then(setSummary).catch((e) => setError(describeError(e)))
    api.pipeline.interviews(selected.id).then(setInterviews).catch((e) => setError(describeError(e)))
    api.pipeline.offers(selected.id).then(setOffers).catch((e) => setError(describeError(e)))
  }

  useEffect(load, [selected])

  const classify = async () => {
    if (!selected || !emailText.trim()) return
    setClassifying(true)
    setEmailResult(null)
    try {
      const result = await api.emailIntake.classify(selected.id, emailText)
      setEmailResult(result)
      if (result.createdInterviewId) {
        setEmailText('')
        load()
      }
    } finally {
      setClassifying(false)
    }
  }

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      {error && <ErrorBanner message={`Couldn't load pipeline data: ${error}`} />}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Pipeline</h1>
        <p className="mt-1 text-sm text-slate-500">
          {selected.fullName}&rsquo;s application funnel, plus interview and offer tracking. Nothing here submits or
          replies to anything - docs/04 FR-5 still ends at the candidate's own session.
        </p>
      </div>

      {summary && (
        <Card>
          <CardHeader title="Application Pipeline" />
          <div className="flex flex-wrap gap-3">
            {FUNNEL_STAGES.map((stage, i) => (
              <div key={stage.key} className="flex flex-1 items-center gap-2">
                <div className={`min-w-[110px] flex-1 rounded-lg p-3 text-center ${stage.tone}`}>
                  <p className="text-xl font-bold">{summary[stage.key]}</p>
                  <p className="text-xs opacity-80">{stage.label}</p>
                </div>
                {i < FUNNEL_STAGES.length - 1 && <span className="hidden shrink-0 text-slate-300 sm:inline">&rarr;</span>}
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card>
        <CardHeader
          title="Paste a recruiter email"
          subtitle="TypeSafe classifies it and links it to a known application; interview invitations auto-create a tracked interview."
          action={<Mail className="text-slate-400" size={18} />}
        />
        <textarea
          value={emailText}
          onChange={(e) => setEmailText(e.target.value)}
          rows={4}
          placeholder="Paste the email body here..."
          className="mb-3 w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
        />
        <Button onClick={classify} disabled={classifying || !emailText.trim()}>
          {classifying ? 'Classifying...' : 'Classify email'}
        </Button>

        {emailResult && (
          <div className="mt-4 rounded-lg bg-slate-50 p-4 text-sm">
            {emailResult.scamRisk && (
              <div className="mb-3 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-red-800">
                <AlertTriangle className="mt-0.5 shrink-0" size={16} />
                <p>
                  This email matches patterns common in job scams or phishing (requests for payment/banking/ID
                  details, unsolicited too-good offers, or pressure to move off-platform). Verify the sender and
                  company independently before responding.
                </p>
              </div>
            )}
            <p>
              Classified as <Badge tone="blue">{emailResult.emailType}</Badge>
            </p>
            {emailResult.createdInterviewId ? (
              <p className="mt-2 text-emerald-700">
                Interview created (mode: {emailResult.mode}
                {emailResult.suggestedScheduledAt ? `, suggested time: ${new Date(emailResult.suggestedScheduledAt).toLocaleString()}` : ''}
                ).
              </p>
            ) : (
              <p className="mt-2 text-slate-500">No interview record created for this email.</p>
            )}
          </div>
        )}
      </Card>

      <div className="grid grid-cols-2 gap-6">
        <Card>
          <CardHeader title="Interviews" />
          <div className="space-y-3">
            {interviews.map((iv) => (
              <div key={iv.id} className="rounded-lg border border-slate-200 p-3">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-slate-900">
                    {iv.jobPosting.title} &middot; {iv.jobPosting.company}
                  </p>
                  <Badge tone={iv.status === 'SCHEDULED' ? 'blue' : iv.status === 'COMPLETED' ? 'green' : 'red'}>
                    {iv.status}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {iv.mode} &middot; {iv.source === 'EMAIL_EXTRACTED' ? 'from email' : 'manual'}
                  {iv.scheduledAt ? ` · ${new Date(iv.scheduledAt).toLocaleString()}` : ''}
                </p>
                {iv.status === 'SCHEDULED' && (
                  <div className="mt-2 flex gap-2">
                    <Button
                      variant="secondary"
                      onClick={() => api.pipeline.updateInterviewStatus(selected.id, iv.id, 'COMPLETED').then(load)}
                    >
                      Mark completed
                    </Button>
                    <Button
                      variant="danger"
                      onClick={() => api.pipeline.updateInterviewStatus(selected.id, iv.id, 'CANCELLED').then(load)}
                    >
                      Cancel
                    </Button>
                  </div>
                )}
              </div>
            ))}
            {interviews.length === 0 && <p className="text-sm text-slate-400">No interviews tracked yet.</p>}
          </div>
        </Card>

        <Card>
          <CardHeader title="Offers" />
          <div className="space-y-3">
            {offers.map((o) => (
              <div key={o.id} className="rounded-lg border border-slate-200 p-3">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-slate-900">
                    {o.jobPosting.title} &middot; {o.jobPosting.company}
                  </p>
                  <Badge tone={o.status === 'ACCEPTED' ? 'green' : o.status === 'DECLINED' ? 'red' : 'blue'}>
                    {o.status}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-slate-400">{formatRelativeTime(o.receivedAt)}</p>
                {o.status === 'EXTENDED' && (
                  <div className="mt-2 flex gap-2">
                    <Button variant="secondary" onClick={() => api.pipeline.updateOfferStatus(selected.id, o.id, 'ACCEPTED').then(load)}>
                      Accept
                    </Button>
                    <Button variant="danger" onClick={() => api.pipeline.updateOfferStatus(selected.id, o.id, 'DECLINED').then(load)}>
                      Decline
                    </Button>
                  </div>
                )}
              </div>
            ))}
            {offers.length === 0 && <p className="text-sm text-slate-400">No offers tracked yet.</p>}
          </div>
        </Card>
      </div>
    </div>
  )
}
