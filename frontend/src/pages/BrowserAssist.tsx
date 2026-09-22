import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { CheckCircle2, ChevronLeft, ChevronRight, Copy, Globe, Lock, ShieldCheck } from 'lucide-react'
import { api } from '../api/client'
import type { ArtifactReviewView } from '../api/types'
import { Card, CardHeader } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Stepper } from '../components/ui/Stepper'

const STEPS = [
  { label: 'Prepare', description: 'Review what Browser Assist will walk you through.' },
  { label: 'Open Application', description: 'The job posting, in a fake browser preview.' },
  { label: 'Pre-fill', description: 'Your tailored resume and cover letter, ready to paste.' },
  { label: 'Review & Edit', description: 'Read the final text before you use it.' },
  { label: 'Submit (You)', description: 'You submit yourself - we only log your confirmation.' },
]

export function BrowserAssist() {
  const { artifactId } = useParams<{ artifactId: string }>()
  const [view, setView] = useState<ArtifactReviewView | null>(null)
  const [loading, setLoading] = useState(true)
  const [step, setStep] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => {
    if (!artifactId) return
    setLoading(true)
    api.tailoring
      .getArtifact(artifactId)
      .then(setView)
      .finally(() => setLoading(false))
  }, [artifactId])

  if (loading) {
    return <p className="text-sm text-slate-400">Loading&hellip;</p>
  }

  if (!view) {
    return (
      <Card>
        <p className="text-sm text-slate-500">This artifact could not be found.</p>
      </Card>
    )
  }

  const { artifact } = view
  const company = artifact.jobPosting.company
  const title = artifact.jobPosting.title
  const fakeUrl = `${company.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.com/careers/apply/${artifact.jobPosting.id.slice(0, 8)}`

  const confirmSubmit = async () => {
    setSubmitting(true)
    try {
      await api.tailoring.markSubmitted(artifactId!)
      setSubmitted(true)
    } finally {
      setSubmitting(false)
    }
  }

  const copy = (text: string) => {
    navigator.clipboard?.writeText(text).catch(() => {})
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/applications" className="text-xs font-medium text-blue-600 hover:underline">
          &larr; Back to Applications
        </Link>
        <h1 className="mt-1 text-2xl font-bold text-slate-900">Browser Assist</h1>
        <p className="mt-1 text-sm text-slate-500">
          {artifact.candidate.fullName} &rarr; {title} at {company}
        </p>
      </div>

      <div className="grid grid-cols-4 gap-6">
        <div className="col-span-1">
          <Card>
            <Stepper steps={STEPS} currentIndex={step} />
          </Card>
        </div>

        <div className="col-span-3 space-y-4">
          {step === 0 && (
            <Card>
              <CardHeader
                title="Prepare"
                subtitle="Here's what Browser Assist will walk you through - nothing happens automatically."
              />
              <ul className="space-y-2 text-sm text-slate-600">
                <li>1. A preview of the job posting you're applying to.</li>
                <li>2. Your tailored resume and cover letter, pre-filled for you to copy.</li>
                <li>3. A chance to review and edit the final text before you use it.</li>
                <li>4. You open the real application yourself and submit it - we only record that you did.</li>
              </ul>
              <div className="mt-4 flex items-start gap-2 rounded-lg bg-blue-50 p-3 text-sm text-blue-700 ring-1 ring-inset ring-blue-600/20">
                <ShieldCheck size={16} className="mt-0.5 shrink-0" />
                <span>
                  Candidly never bypasses CAPTCHAs and never submits applications on your behalf. You're always the
                  one who clicks Submit, on the real site, in your own browser.
                </span>
              </div>
              <div className="mt-4 flex justify-end">
                <Button icon={<ChevronRight size={14} />} onClick={() => setStep(1)}>
                  Continue
                </Button>
              </div>
            </Card>
          )}

          {step === 1 && (
            <Card padded={false}>
              <div className="border-b border-slate-200 bg-slate-100 p-3">
                <div className="flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-1.5">
                  <Lock size={12} className="text-slate-400" />
                  <span className="truncate text-xs text-slate-600">{fakeUrl}</span>
                  <Globe size={12} className="ml-auto text-slate-300" />
                </div>
                <p className="mt-2 text-[11px] text-slate-400">
                  Illustrative preview only - this address bar doesn&apos;t navigate anywhere.
                </p>
              </div>
              <div className="p-5">
                <CardHeader title={title} subtitle={`${company} · ${artifact.jobPosting.location}`} />
                <p className="text-sm text-slate-600">
                  This is where the real careers site application form would be. Browser Assist doesn&apos;t open or
                  control that page for you - the next steps just get your materials ready to paste in yourself.
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  {artifact.jobPosting.mandatorySkillIds.slice(0, 6).map((s) => (
                    <Badge key={s} tone="slate">
                      {s}
                    </Badge>
                  ))}
                </div>
                <div className="mt-4 flex justify-between">
                  <Button variant="secondary" icon={<ChevronLeft size={14} />} onClick={() => setStep(0)}>
                    Back
                  </Button>
                  <Button icon={<ChevronRight size={14} />} onClick={() => setStep(2)}>
                    Continue
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {step === 2 && (
            <Card>
              <CardHeader
                title="Pre-fill"
                subtitle="Your tailored materials, shown as if already dropped into the application form."
              />
              <div className="space-y-4">
                <div>
                  <p className="mb-1 text-xs font-semibold uppercase text-slate-400">Resume</p>
                  <div className="rounded-lg border border-slate-300 bg-white p-3">
                    <div className="max-h-56 overflow-y-auto whitespace-pre-wrap rounded bg-slate-50 p-3 text-sm text-slate-700">
                      {artifact.content || '(no bullets survived grounding)'}
                    </div>
                  </div>
                </div>
                {artifact.coverLetterContent && (
                  <div>
                    <p className="mb-1 text-xs font-semibold uppercase text-slate-400">Cover Letter</p>
                    <div className="rounded-lg border border-slate-300 bg-white p-3">
                      <div className="max-h-56 overflow-y-auto whitespace-pre-wrap rounded bg-slate-50 p-3 text-sm text-slate-700">
                        {artifact.coverLetterContent}
                      </div>
                    </div>
                  </div>
                )}
              </div>
              <div className="mt-4 flex justify-between">
                <Button variant="secondary" icon={<ChevronLeft size={14} />} onClick={() => setStep(1)}>
                  Back
                </Button>
                <Button icon={<ChevronRight size={14} />} onClick={() => setStep(3)}>
                  Continue
                </Button>
              </div>
            </Card>
          )}

          {step === 3 && (
            <Card>
              <CardHeader title="Review & Edit" subtitle="Read the final text carefully - copy anything you need." />
              <div className="space-y-4">
                <div>
                  <div className="mb-1 flex items-center justify-between">
                    <p className="text-xs font-semibold uppercase text-slate-400">Resume</p>
                    <button
                      onClick={() => copy(artifact.content)}
                      className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline"
                    >
                      <Copy size={12} /> Copy
                    </button>
                  </div>
                  <div className="max-h-56 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                    {artifact.content || '(no bullets survived grounding)'}
                  </div>
                </div>
                {artifact.coverLetterContent && (
                  <div>
                    <div className="mb-1 flex items-center justify-between">
                      <p className="text-xs font-semibold uppercase text-slate-400">Cover Letter</p>
                      <button
                        onClick={() => copy(artifact.coverLetterContent ?? '')}
                        className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:underline"
                      >
                        <Copy size={12} /> Copy
                      </button>
                    </div>
                    <div className="max-h-56 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                      {artifact.coverLetterContent}
                    </div>
                  </div>
                )}
                {artifact.screeningAnswers.length > 0 && (
                  <div>
                    <p className="mb-1 text-xs font-semibold uppercase text-slate-400">Screening Answers</p>
                    <div className="space-y-2">
                      {artifact.screeningAnswers.map((entry, i) => {
                        const [question, answer] = entry.split(' || ')
                        return (
                          <div key={i} className="rounded-lg border border-slate-200 p-3">
                            <p className="text-xs font-semibold text-slate-500">{question}</p>
                            <p className="mt-1 text-sm text-slate-700">{answer}</p>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}
              </div>
              <div className="mt-4 flex justify-between">
                <Button variant="secondary" icon={<ChevronLeft size={14} />} onClick={() => setStep(2)}>
                  Back
                </Button>
                <Button icon={<ChevronRight size={14} />} onClick={() => setStep(4)}>
                  Continue
                </Button>
              </div>
            </Card>
          )}

          {step === 4 && (
            <Card>
              <CardHeader title="Submit (You)" subtitle="This is where you personally submit the application." />

              <div className="flex items-start gap-3 rounded-lg bg-emerald-50 p-4 text-sm text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                <ShieldCheck size={20} className="mt-0.5 shrink-0" />
                <div>
                  <p className="font-semibold">You are in control.</p>
                  <p className="mt-1">
                    Candidly never bypasses CAPTCHAs, never fills out a third-party form on your behalf, and never
                    submits an application for you. Open the real application yourself in a new tab, paste in the
                    materials from the previous step, and submit it there. When you're done, confirm below so we can
                    log it for your records - that's all this button does.
                  </p>
                </div>
              </div>

              {!submitted ? (
                <div className="mt-4 flex items-center justify-between">
                  <Button variant="secondary" icon={<ChevronLeft size={14} />} onClick={() => setStep(3)}>
                    Back
                  </Button>
                  <Button onClick={confirmSubmit} disabled={submitting}>
                    {submitting ? 'Recording...' : "I've submitted this myself"}
                  </Button>
                </div>
              ) : (
                <div className="mt-4 flex items-center gap-2 rounded-lg bg-blue-50 p-3 text-sm text-blue-700 ring-1 ring-inset ring-blue-600/20">
                  <CheckCircle2 size={16} />
                  <span>Confirmed - logged to the audit ledger. Good luck!</span>
                </div>
              )}
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
