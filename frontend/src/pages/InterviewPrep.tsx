import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, ClipboardList, Layers } from 'lucide-react'
import { api } from '../api/client'
import { useCandidate } from '../context/CandidateContext'
import type { AnswerFeedback, CareerLevel, InterviewCategory, InterviewQuestion, InterviewStage, JobPosting } from '../api/types'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge, toneForScore } from '../components/ui/Badge'
import { ProgressBar, StatCard } from '../components/ui/StatCard'
import { ScoreRing } from '../components/ui/ScoreRing'
import { Button } from '../components/ui/Button'
import { Tabs } from '../components/ui/Tabs'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'

const CATEGORIES: { key: InterviewCategory; label: string }[] = [
  { key: 'SYSTEM_DESIGN', label: 'System Design' },
  { key: 'CODING', label: 'Coding' },
  { key: 'BEHAVIORAL', label: 'Behavioral' },
  { key: 'DOMAIN', label: 'Domain' },
]

const CAREER_LEVELS: { key: CareerLevel; label: string }[] = [
  { key: 'ENTRY', label: 'Entry-level' },
  { key: 'MID', label: 'Mid-level' },
  { key: 'SENIOR', label: 'Senior' },
  { key: 'STAFF_PLUS', label: 'Staff+' },
]

const INTERVIEW_STAGES: { key: InterviewStage; label: string }[] = [
  { key: 'PHONE_SCREEN', label: 'Phone screen' },
  { key: 'TECHNICAL_DEEP_DIVE', label: 'Technical deep-dive' },
  { key: 'ONSITE_LOOP', label: 'Onsite loop' },
  { key: 'FINAL_BEHAVIORAL', label: 'Final / behavioral' },
]

// The Web Speech API has no standard TS lib typings (it's still non-standard/vendor-prefixed
// in most browsers), so recognition is handled through `any` rather than fighting the DOM lib.
function getSpeechRecognitionCtor(): any {
  return (window as any).SpeechRecognition ?? (window as any).webkitSpeechRecognition ?? null
}

export function InterviewPrep() {
  const { selected: candidate } = useCandidate()

  const [category, setCategory] = useState<InterviewCategory>('SYSTEM_DESIGN')
  const [questions, setQuestions] = useState<InterviewQuestion[]>([])
  const [jobPostings, setJobPostings] = useState<JobPosting[]>([])
  const [jobPostingId, setJobPostingId] = useState('')
  const [careerLevel, setCareerLevel] = useState<CareerLevel>('MID')
  const [stage, setStage] = useState<InterviewStage>('TECHNICAL_DEEP_DIVE')
  const [generating, setGenerating] = useState(false)

  const [selectedQuestion, setSelectedQuestion] = useState<InterviewQuestion | null>(null)
  const [answer, setAnswer] = useState('')
  const [feedback, setFeedback] = useState<AnswerFeedback | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Session-only practice history (not persisted server-side) - purely for the stat row/
  // per-category indicators below, derived from answers actually submitted this session.
  const [sessionHistory, setSessionHistory] = useState<{ category: InterviewCategory; feedback: AnswerFeedback }[]>([])

  const [loadError, setLoadError] = useState<string | null>(null)
  const [voiceMode, setVoiceMode] = useState(false)
  const [listening, setListening] = useState(false)
  const recognitionRef = useRef<any>(null)
  const voiceSupported =
    typeof window !== 'undefined' && 'speechSynthesis' in window && getSpeechRecognitionCtor() != null

  useEffect(() => {
    api.interviewPrep
      .questions({ category })
      .then((qs) => {
        setQuestions(qs)
        setSelectedQuestion(qs[0] ?? null)
        setFeedback(null)
        setAnswer('')
      })
      .catch((e) => setLoadError(describeError(e)))
  }, [category])

  useEffect(() => {
    api.jobPostings
      .list()
      .then((jobs) => {
        setJobPostings(jobs)
        if (jobs.length > 0) setJobPostingId(jobs[0].id)
      })
      .catch((e) => setLoadError(describeError(e)))
  }, [])

  useEffect(() => {
    if (!candidate) return
    api.interviewPrep
      .suggestedLevel(candidate.id)
      .then((res) => setCareerLevel(res.level))
      .catch((e) => setLoadError(describeError(e)))
  }, [candidate])

  // Auto-speak the active question in voice mode - the "feels like a real interviewer" part.
  useEffect(() => {
    if (!voiceMode || !voiceSupported || !selectedQuestion) return
    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(selectedQuestion.text))
  }, [selectedQuestion, voiceMode, voiceSupported])

  useEffect(() => {
    return () => {
      recognitionRef.current?.stop()
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
    }
  }, [])

  const generatePersonalizedQuestions = async () => {
    if (!candidate || !jobPostingId) return
    setGenerating(true)
    try {
      const qs = await api.interviewPrep.questions({ category, candidateId: candidate.id, jobPostingId, careerLevel, stage })
      setQuestions(qs)
      setSelectedQuestion(qs[0] ?? null)
      setFeedback(null)
      setAnswer('')
    } finally {
      setGenerating(false)
    }
  }

  const selectQuestion = (q: InterviewQuestion) => {
    setSelectedQuestion(q)
    setFeedback(null)
    setAnswer('')
  }

  const toggleListening = () => {
    if (listening) {
      recognitionRef.current?.stop()
      setListening(false)
      return
    }
    const Ctor = getSpeechRecognitionCtor()
    if (!Ctor) return
    const recognition = new Ctor()
    recognition.continuous = true
    recognition.interimResults = true
    recognition.lang = 'en-US'
    recognition.onresult = (event: any) => {
      let transcript = ''
      for (let i = 0; i < event.results.length; i++) {
        transcript += event.results[i][0].transcript
      }
      setAnswer(transcript)
    }
    recognition.onend = () => setListening(false)
    recognition.onerror = () => setListening(false)
    recognitionRef.current = recognition
    recognition.start()
    setListening(true)
  }

  const submit = async () => {
    if (!selectedQuestion || !jobPostingId || !answer.trim()) return
    recognitionRef.current?.stop()
    setSubmitting(true)
    try {
      const result = await api.interviewPrep.submitAnswer({
        questionId: selectedQuestion.generated ? undefined : selectedQuestion.id,
        questionText: selectedQuestion.generated ? selectedQuestion.text : undefined,
        answer,
        jobPostingId,
        candidateId: candidate?.id,
        careerLevel,
        interviewStage: stage,
      })
      setFeedback(result)
      setSessionHistory((prev) => [...prev, { category: selectedQuestion.category, feedback: result }])
    } finally {
      setSubmitting(false)
    }
  }

  const answerFollowUp = () => {
    if (!feedback?.followUpQuestion || !selectedQuestion) return
    selectQuestion({
      id: `followup-${Date.now()}`,
      category: selectedQuestion.category,
      text: feedback.followUpQuestion,
      generated: true,
    })
  }

  const answeredByCategory = sessionHistory.reduce<Partial<Record<InterviewCategory, number>>>((acc, entry) => {
    acc[entry.category] = (acc[entry.category] ?? 0) + 1
    return acc
  }, {})
  const avgScore =
    sessionHistory.length === 0
      ? 0
      : sessionHistory.reduce((sum, entry) => sum + (entry.feedback.clarity + entry.feedback.depth + entry.feedback.relevance) / 3, 0) /
        sessionHistory.length
  const categoriesPracticed = Object.keys(answeredByCategory).length

  return (
    <div className="space-y-6">
      {loadError && <ErrorBanner message={`Couldn't load interview prep data: ${loadError}`} />}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Interview Prep</h1>
        <p className="mt-1 text-sm text-slate-500">
          Practice against a question tailored to your profile, the target role, your career level, and the
          interview stage - with TypeSafe-judged feedback on clarity, depth, and relevance, and adaptive follow-ups.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
        <StatCard
          icon={<ClipboardList size={18} />}
          iconTone="blue"
          value={sessionHistory.length}
          label="Questions answered this session"
        />
        <StatCard
          icon={<CheckCircle2 size={18} />}
          iconTone="green"
          value={sessionHistory.length ? `${Math.round(avgScore * 100)}%` : '—'}
          label="Average feedback score"
        />
        <StatCard icon={<Layers size={18} />} iconTone="purple" value={`${categoriesPracticed}/4`} label="Categories practiced" />
        <Card className="flex items-center gap-4">
          <ScoreRing value={avgScore} size={56} strokeWidth={6} />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-slate-700">Session average</p>
            <p className="text-xs text-slate-400">Clarity, depth &amp; relevance combined</p>
          </div>
        </Card>
      </div>

      <Card>
        <CardHeader title="Set up your session" subtitle="Personalized questions use your profile plus the role's structured fields only." />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          <select
            value={jobPostingId}
            onChange={(e) => setJobPostingId(e.target.value)}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-blue-400 focus:outline-none"
          >
            {jobPostings.map((j) => (
              <option key={j.id} value={j.id}>
                {j.title} at {j.company}
              </option>
            ))}
          </select>
          <select
            value={careerLevel}
            onChange={(e) => setCareerLevel(e.target.value as CareerLevel)}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-blue-400 focus:outline-none"
          >
            {CAREER_LEVELS.map((l) => (
              <option key={l.key} value={l.key}>
                {l.label}
              </option>
            ))}
          </select>
          <select
            value={stage}
            onChange={(e) => setStage(e.target.value as InterviewStage)}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-blue-400 focus:outline-none"
          >
            {INTERVIEW_STAGES.map((s) => (
              <option key={s.key} value={s.key}>
                {s.label}
              </option>
            ))}
          </select>
          <Button onClick={generatePersonalizedQuestions} disabled={generating || !candidate || !jobPostingId}>
            {generating ? 'Generating...' : 'Generate personalized questions'}
          </Button>
        </div>

        <div className="mt-3 flex items-center gap-2 text-sm text-slate-600">
          <label className="inline-flex items-center gap-2">
            <input
              type="checkbox"
              checked={voiceMode}
              disabled={!voiceSupported}
              onChange={(e) => setVoiceMode(e.target.checked)}
            />
            Voice mode - questions read aloud, answer by speaking
          </label>
          {!voiceSupported && <span className="text-xs text-slate-400">(not supported in this browser)</span>}
        </div>
      </Card>

      <Card padded={false} className="p-2">
        <div className="px-3 pt-2">
          <Tabs
            tabs={CATEGORIES.map((c) => ({ key: c.key, label: c.label, count: answeredByCategory[c.key] ?? 0 }))}
            active={category}
            onChange={(k) => setCategory(k as InterviewCategory)}
          />
        </div>
        <div className="grid grid-cols-3 gap-4 p-4">
          {questions.map((q) => (
            <button
              key={q.id}
              onClick={() => selectQuestion(q)}
              className={`rounded-lg border p-3 text-left text-sm transition-colors ${
                selectedQuestion?.id === q.id ? 'border-blue-400 bg-blue-50/40' : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              {q.generated && <Badge tone="purple">Personalized</Badge>}
              <div className="mt-1">{q.text}</div>
            </button>
          ))}
        </div>
      </Card>

      {selectedQuestion && (
        <Card>
          <CardHeader
            title={selectedQuestion.text}
            subtitle="Target role for context (excludes raw job description - only structured fields reach the judgment)."
          />
          {voiceMode && voiceSupported && (
            <div className="mb-3 flex items-center gap-2">
              <Button
                variant={listening ? 'danger' : 'secondary'}
                onClick={toggleListening}
                type="button"
              >
                {listening ? 'Stop listening' : 'Speak your answer'}
              </Button>
              {listening && <span className="text-xs text-slate-500">Listening...</span>}
            </div>
          )}
          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            rows={6}
            placeholder="Type your answer, or use voice mode above..."
            className="mb-3 w-full rounded-lg border border-slate-200 p-3 text-sm focus:border-blue-400 focus:outline-none"
          />
          <Button onClick={submit} disabled={submitting || !answer.trim() || !jobPostingId}>
            {submitting ? 'Scoring...' : 'Submit answer'}
          </Button>

          {feedback && (
            <div className="mt-5 space-y-3 border-t border-slate-100 pt-4">
              {(['clarity', 'depth', 'relevance'] as const).map((dim) => (
                <div key={dim}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="capitalize text-slate-600">{dim}</span>
                    <Badge tone={toneForScore(feedback[dim])}>{Math.round(feedback[dim] * 100)}%</Badge>
                  </div>
                  <ProgressBar value={feedback[dim]} tone={dim === 'clarity' ? 'blue' : dim === 'depth' ? 'violet' : 'green'} />
                </div>
              ))}
              <p className="rounded-lg bg-blue-50/60 p-3 text-sm text-slate-700">{feedback.suggestion}</p>

              {feedback.followUpQuestion && (
                <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3 text-sm text-slate-700">
                  <p className="font-medium text-amber-800">Interviewer follow-up</p>
                  <p className="mt-1">{feedback.followUpQuestion}</p>
                  <Button variant="secondary" className="mt-2" onClick={answerFollowUp}>
                    Answer follow-up
                  </Button>
                </div>
              )}
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
