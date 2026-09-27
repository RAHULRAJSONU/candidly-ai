import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, Inbox as InboxIcon, ArrowRight } from 'lucide-react'
import { api } from '../api/client'
import type { EmailIntakeRecord, EmailIntakeResult } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Tabs } from '../components/ui/Tabs'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import { formatRelativeTime } from '../lib/format'

const TYPE_TONE: Record<string, 'blue' | 'green' | 'red' | 'slate'> = {
  INTERVIEW_INVITATION: 'blue',
  FOLLOW_UP: 'green',
  REJECTION: 'red',
  OTHER: 'slate',
}

/** No real priority field exists on EmailIntakeRecord - this is a client-side display
 * heuristic derived from emailType, matching the mock's priority-pill treatment without
 * inventing new backend state. */
const TYPE_PRIORITY: Record<string, { label: string; tone: 'red' | 'amber' | 'slate' }> = {
  INTERVIEW_INVITATION: { label: 'High priority', tone: 'red' },
  FOLLOW_UP: { label: 'Medium priority', tone: 'amber' },
  REJECTION: { label: 'Low priority', tone: 'slate' },
  OTHER: { label: 'Low priority', tone: 'slate' },
}

export function EmailInbox() {
  const { selected } = useCandidate()
  const [tab, setTab] = useState<'inbox' | 'monitoring'>('inbox')
  const [history, setHistory] = useState<EmailIntakeRecord[]>([])
  const [needsReviewCount, setNeedsReviewCount] = useState(0)
  const [emailText, setEmailText] = useState('')
  const [result, setResult] = useState<EmailIntakeResult | null>(null)
  const [classifying, setClassifying] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = () => {
    if (!selected) return
    setLoadError(null)
    api.emailIntake.history(selected.id).then(setHistory).catch((e) => setLoadError(describeError(e)))
    api.emailIntake
      .needsReview(selected.id)
      .then((r) => setNeedsReviewCount(r.length))
      .catch((e) => setLoadError(describeError(e)))
  }

  useEffect(load, [selected])

  const classify = async () => {
    if (!selected || !emailText.trim()) return
    setClassifying(true)
    setResult(null)
    try {
      const r = await api.emailIntake.classify(selected.id, emailText)
      setResult(r)
      setEmailText('')
      load()
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
      {loadError && <ErrorBanner message={`Couldn't load email inbox: ${loadError}`} />}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <InboxIcon size={22} className="text-slate-700" />
            <h1 className="text-2xl font-bold text-slate-900">Email Inbox</h1>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {selected.fullName}&rsquo;s recruiter emails, classified by TypeSafe - pasted manually, not auto-read.
            There is no Gmail/Outlook OAuth integration in this build; connecting a real inbox is intentionally not
            simulated here.
          </p>
        </div>
        {needsReviewCount > 0 && (
          <Link to="/email-review">
            <Button icon={<ArrowRight size={16} />}>{needsReviewCount} reply draft(s) need review</Button>
          </Link>
        )}
      </div>

      <Tabs
        tabs={[
          { key: 'inbox', label: 'Inbox', count: history.length },
          { key: 'monitoring', label: 'Auto Monitoring' },
        ]}
        active={tab}
        onChange={(k) => setTab(k as 'inbox' | 'monitoring')}
      />

      {tab === 'inbox' && (
        <>
          <Card>
            <CardHeader
              title="Paste a recruiter email"
              subtitle="TypeSafe classifies it, links it to a known application, and - for interview invitations - drafts a reply for you to review."
              action={<Mail className="text-slate-400" size={18} />}
            />
            <textarea
              value={emailText}
              onChange={(e) => setEmailText(e.target.value)}
              rows={5}
              placeholder="Paste the email body here..."
              className="mb-3 w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
            />
            <Button onClick={classify} disabled={classifying || !emailText.trim()}>
              {classifying ? 'Classifying...' : 'Classify email'}
            </Button>

            {result && (
              <div className="mt-4 rounded-lg bg-slate-50 p-4 text-sm">
                <p>
                  Classified as <Badge tone={TYPE_TONE[result.emailType]}>{result.emailType.replace(/_/g, ' ')}</Badge>
                </p>
                {result.createdInterviewId && (
                  <p className="mt-2 text-emerald-700">
                    Interview record created (mode: {result.mode}
                    {result.suggestedScheduledAt ? `, suggested time: ${new Date(result.suggestedScheduledAt).toLocaleString()}` : ''}
                    ).
                  </p>
                )}
                {result.draftReplyText && (
                  <p className="mt-2 text-blue-700">
                    A reply draft is ready for your review - see the &ldquo;reply draft(s) need review&rdquo; link above.
                  </p>
                )}
              </div>
            )}
          </Card>

          <Card>
            <CardHeader title="History" />
            <div className="space-y-2">
              {history.map((r) => (
                <div key={r.id} className="rounded-lg border border-slate-100 p-3 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <Badge tone={TYPE_TONE[r.emailType]}>{r.emailType.replace(/_/g, ' ')}</Badge>
                      <Badge tone={TYPE_PRIORITY[r.emailType].tone}>{TYPE_PRIORITY[r.emailType].label}</Badge>
                      {r.jobPosting && (
                        <span className="text-slate-700">
                          {r.jobPosting.company} &middot; {r.jobPosting.title}
                        </span>
                      )}
                    </div>
                    <span className="text-xs text-slate-400">{formatRelativeTime(r.classifiedAt)}</span>
                  </div>
                  {r.reviewStatus !== 'NO_REPLY_NEEDED' && (
                    <p className="mt-1 text-xs text-slate-400">Reply status: {r.reviewStatus.replace(/_/g, ' ')}</p>
                  )}
                </div>
              ))}
              {history.length === 0 && <p className="text-sm text-slate-400">No emails classified yet.</p>}
            </div>
          </Card>
        </>
      )}

      {tab === 'monitoring' && (
        <Card>
          <CardHeader
            title="Monitoring rules"
            subtitle="What an automatic inbox connection would detect - shown for parity with the design; there's no live connection to run these against."
          />
          <div className="space-y-2 text-sm text-slate-600">
            {[
              'Detect interview invitations',
              'Detect application status updates',
              'Detect recruiter messages',
              'Detect job alerts',
              'Auto-categorize emails',
              'Daily summary email',
            ].map((rule) => (
              <label key={rule} className="flex items-center gap-2">
                <input type="checkbox" checked disabled className="h-4 w-4 rounded border-slate-300" />
                {rule}
              </label>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
