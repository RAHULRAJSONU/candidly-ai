import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  ArrowRight,
  Briefcase,
  Camera,
  Compass,
  DollarSign,
  FileText,
  Link2,
  MapPin,
  Newspaper,
  PenLine,
  Search,
  ShieldCheck,
  Target,
  Trash2,
  TrendingUp,
} from 'lucide-react'
import { api } from '../api/client'
import { useCandidate } from '../context/CandidateContext'
import type { ProfileImportResult } from '../api/types'
import { Card, CardHeader } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { ResumeDropzone } from '../components/ui/ResumeDropzone'
import { SkillChipInput } from '../components/ui/SkillChipInput'
import { Stepper } from '../components/ui/Stepper'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'

type CareerGoal = 'FIND_JOB' | 'EXPLORE' | 'STAY_INFORMED'

const CAREER_GOALS: { key: CareerGoal; icon: typeof Search; title: string; description: string }[] = [
  { key: 'FIND_JOB', icon: Search, title: 'Find a new job', description: 'Actively looking' },
  { key: 'EXPLORE', icon: Compass, title: 'Explore new opportunities', description: 'Open to the right offer' },
  { key: 'STAY_INFORMED', icon: Newspaper, title: 'Stay informed', description: 'Not actively looking' },
]

interface ExperienceDraft {
  employer: string
  title: string
  startDate: string
  endDate: string
  narrative: string
  skills: string
}

const EMPTY_EXPERIENCE: ExperienceDraft = { employer: '', title: '', startDate: '', endDate: '', narrative: '', skills: '' }

type Stage = 'welcome' | 'import' | 'import-resume' | 'import-linkedin' | 'about' | 'skills' | 'experience' | 'review'

const NUMBERED_STEPS = [
  { label: 'Import your profile', description: 'Resume, LinkedIn, or start fresh' },
  { label: 'About you', description: 'Contact & work eligibility' },
  { label: 'Skills', description: 'What you bring to the table' },
  { label: 'Experience', description: 'Your work history' },
  { label: 'Review', description: 'Confirm & create your profile' },
] as const
const STAGE_TO_STEP_INDEX: Record<Stage, number | null> = {
  welcome: null,
  import: 0,
  'import-resume': 0,
  'import-linkedin': 0,
  about: 1,
  skills: 2,
  experience: 3,
  review: 4,
}

const FORM_STAGES: Stage[] = ['about', 'skills', 'experience', 'review']

export function Onboarding() {
  const navigate = useNavigate()
  const { setSelectedId, refresh } = useCandidate()
  const [stage, setStage] = useState<Stage>('welcome')

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [location, setLocation] = useState('')
  const [workAuth, setWorkAuth] = useState('US')
  const [compFloor, setCompFloor] = useState(100000)
  const [skills, setSkills] = useState('')
  const [experiences, setExperiences] = useState<ExperienceDraft[]>([EMPTY_EXPERIENCE])
  const [linkedInText, setLinkedInText] = useState('')
  const [importing, setImporting] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState<string | null>(null)
  const [careerGoal, setCareerGoal] = useState<CareerGoal | null>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)

  // Cosmetic only - never uploaded or persisted; there's no avatar-storage endpoint on
  // this backend. Revoke the object URL on change/unmount to avoid leaking blob memory.
  useEffect(() => {
    return () => {
      if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl)
    }
  }, [photoPreviewUrl])

  const pickPhoto = (file: File | undefined) => {
    if (!file) return
    setPhotoPreviewUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return URL.createObjectURL(file)
    })
  }

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const updateExperience = (index: number, patch: Partial<ExperienceDraft>) => {
    setExperiences((prev) => prev.map((e, i) => (i === index ? { ...e, ...patch } : e)))
  }

  const addExperience = () => setExperiences((prev) => [...prev, EMPTY_EXPERIENCE])

  const removeExperience = (index: number) => setExperiences((prev) => prev.filter((_, i) => i !== index))

  const applyImportResult = (result: ProfileImportResult) => {
    if (result.fullName) setFullName(result.fullName)
    if (result.email) setEmail(result.email)
    if (result.location) setLocation(result.location)
    if (result.rawSkillMentions.length > 0) setSkills(result.rawSkillMentions.join(', '))
    if (result.experiences.length > 0) {
      setExperiences(
        result.experiences.map((e) => ({
          employer: e.employer ?? '',
          title: e.title ?? '',
          startDate: e.startDate ?? '',
          endDate: e.endDate ?? '',
          narrative: e.narrative ?? '',
          skills: e.rawSkillMentions.join(', '),
        })),
      )
    }
    setStage('about')
  }

  const extractFromLinkedIn = async () => {
    if (!linkedInText.trim()) return
    setImporting(true)
    setImportError(null)
    try {
      const result = await api.candidates.importLinkedInText(linkedInText)
      applyImportResult(result)
    } catch (e) {
      setImportError(e instanceof Error ? e.message : 'Could not extract profile data from that text.')
    } finally {
      setImporting(false)
    }
  }

  const submit = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const candidate = await api.candidates.create({
        fullName,
        email,
        location,
        workAuthorizations: workAuth.split(',').map((s) => s.trim()).filter(Boolean),
        compFloorMinorUnits: Math.round(compFloor * 100),
        rawSkillMentions: skills.split(',').map((s) => s.trim()).filter(Boolean),
        experiences: experiences
          .filter((e) => e.employer.trim() && e.title.trim())
          .map((e) => ({
            employer: e.employer,
            title: e.title,
            startDate: e.startDate || '2020-01-01',
            endDate: e.endDate || null,
            narrative: e.narrative || `${e.title} at ${e.employer}.`,
            verifiedMetrics: [],
            rawSkillMentions: e.skills.split(',').map((s) => s.trim()).filter(Boolean),
          })),
      })
      refresh()
      setSelectedId(candidate.id)
      navigate('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create candidate')
    } finally {
      setSubmitting(false)
    }
  }

  if (stage === 'welcome') {
    return (
      <div className="mx-auto flex max-w-3xl flex-col items-center gap-6 py-6">
        <div className="w-full rounded-2xl bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-10 text-center text-white">
          <h1 className="text-3xl font-bold">Find better opportunities with the power of AI</h1>
          <p className="mx-auto mt-2 max-w-xl text-sm text-slate-300">
            We&rsquo;ll help you discover, apply, and track the right jobs - so you can focus on what matters.
          </p>
        </div>
        <div className="grid w-full grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { icon: Search, label: 'Discover personalized jobs', tone: 'blue' },
            { icon: Target, label: 'Verified, truthful applications', tone: 'green' },
            { icon: Briefcase, label: 'Track your progress', tone: 'purple' },
            { icon: TrendingUp, label: 'Get insights to grow', tone: 'amber' },
          ].map(({ icon: Icon, label, tone }) => (
            <Card key={label} className="flex flex-col items-start gap-2 text-left">
              <span className={`flex h-9 w-9 items-center justify-center rounded-full ${iconTone[tone]}`}>
                <Icon size={17} />
              </span>
              <p className="text-sm font-medium text-slate-700">{label}</p>
            </Card>
          ))}
        </div>
        <Button icon={<ArrowRight size={14} />} onClick={() => setStage('import')}>
          Get Started
        </Button>
      </div>
    )
  }

  const stepIndex = STAGE_TO_STEP_INDEX[stage]
  const isFormStage = FORM_STAGES.includes(stage)

  const body = (
    <div key={stage} className="animate-step-fade-in">
      {stage === 'import' && (
        <div className="space-y-3">
          <p className="text-sm text-slate-500">
            Import your profile to save time, or enter everything yourself - you can edit anything before saving.
          </p>
          <ImportOption
            icon={FileText}
            title="Upload Resume"
            description="PDF or DOCX - we'll extract your details automatically."
            highlight="Fastest"
            onClick={() => setStage('import-resume')}
          />
          <ImportOption
            icon={Link2}
            title="Paste LinkedIn Profile"
            description="Paste your profile URL or exported profile text."
            onClick={() => setStage('import-linkedin')}
          />
          <ImportOption
            icon={PenLine}
            title="Enter Manually"
            description="Skip import and fill in your profile yourself."
            onClick={() => setStage('about')}
          />
        </div>
      )}

      {stage === 'import-resume' && (
        <div className="space-y-4">
          <p className="text-sm text-slate-500">We'll scan your resume to understand your skills, experience, and career profile.</p>
          <ResumeDropzone onExtracted={applyImportResult} />
        </div>
      )}

      {stage === 'import-linkedin' && (
        <div className="space-y-3">
          <Field label="LinkedIn profile URL or exported profile text">
            <textarea
              value={linkedInText}
              onChange={(e) => setLinkedInText(e.target.value)}
              rows={8}
              className={inputClass}
              placeholder="https://www.linkedin.com/in/your-profile or paste your profile's exported text here..."
            />
          </Field>
          {importError && <p className="text-sm text-red-600">{importError}</p>}
          <Button onClick={extractFromLinkedIn} disabled={importing || !linkedInText.trim()}>
            {importing ? 'Extracting...' : 'Extract profile'}
          </Button>
        </div>
      )}

      {stage === 'about' && (
        <div className="space-y-6">
          <div className="flex flex-col items-center gap-2">
            <div className="relative">
              {photoPreviewUrl ? (
                <img
                  src={photoPreviewUrl}
                  alt="Profile preview"
                  className="h-20 w-20 rounded-full object-cover ring-1 ring-slate-200"
                />
              ) : (
                <CompanyAvatar name={fullName || '?'} size={80} />
              )}
              <button
                type="button"
                onClick={() => photoInputRef.current?.click()}
                aria-label="Add photo"
                className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-full bg-blue-600 text-white shadow ring-2 ring-white hover:bg-blue-700"
              >
                <Camera size={13} />
              </button>
              <input
                ref={photoInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => pickPhoto(e.target.files?.[0])}
              />
            </div>
            <p className="text-xs text-slate-400">Add Photo (optional) - shown here only, not saved</p>
          </div>

          <div className="space-y-3">
            <SectionLabel>Contact</SectionLabel>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Full name">
                <input value={fullName} onChange={(e) => setFullName(e.target.value)} className={inputClass} placeholder="Rahul Raj" />
              </Field>
              <Field label="Email">
                <input value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} placeholder="rahul@example.com" />
              </Field>
            </div>
            <Field label="Location">
              <div className="relative">
                <MapPin className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
                <input
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  className={`${inputClass} pl-9`}
                  placeholder="Remote"
                />
              </div>
            </Field>
          </div>

          <div className="space-y-3">
            <SectionLabel>Work eligibility &amp; compensation</SectionLabel>
            <Field label="Work authorizations (comma-separated)">
              <input value={workAuth} onChange={(e) => setWorkAuth(e.target.value)} className={inputClass} placeholder="US, EU" />
            </Field>
            <Field label="Minimum acceptable annual compensation (USD)">
              <div className="relative">
                <DollarSign className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
                <input
                  type="number"
                  value={compFloor}
                  onChange={(e) => setCompFloor(Number(e.target.value))}
                  className={`${inputClass} pl-9`}
                />
              </div>
            </Field>
          </div>
        </div>
      )}

      {stage === 'skills' && (
        <div className="space-y-6">
          <div className="space-y-2">
            <Field label="Skills">
              <SkillChipInput value={skills} onChange={setSkills} placeholder="e.g. Java, Spring, AWS - press Enter to add" />
            </Field>
            <p className="text-xs text-slate-400">
              {skills.split(',').map((s) => s.trim()).filter(Boolean).length} skill(s) added. Add anything relevant - languages,
              frameworks, tools, domains.
            </p>
          </div>

          <div className="space-y-2">
            <SectionLabel>Career Goals</SectionLabel>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              {CAREER_GOALS.map(({ key, icon: Icon, title, description }) => {
                const isActive = careerGoal === key
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setCareerGoal((prev) => (prev === key ? null : key))}
                    className={`flex flex-col items-start gap-2 rounded-xl border p-4 text-left transition ${
                      isActive ? 'border-blue-500 bg-blue-50/40 ring-1 ring-blue-500' : 'border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-full ${
                        isActive ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      <Icon size={16} />
                    </span>
                    <div>
                      <p className="text-sm font-medium text-slate-800">{title}</p>
                      <p className="text-xs text-slate-500">{description}</p>
                    </div>
                  </button>
                )
              })}
            </div>
            <p className="text-xs text-slate-400">Optional - helps us tailor your experience. Not saved to your profile yet.</p>
          </div>
        </div>
      )}

      {stage === 'experience' && (
        <div className="space-y-4">
          {experiences.map((exp, i) => (
            <div key={i} className="space-y-3 rounded-lg border border-slate-200 p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-600">
                    {i + 1}
                  </span>
                  <p className="text-sm font-medium text-slate-700">
                    {exp.title || exp.employer ? [exp.title, exp.employer].filter(Boolean).join(' at ') : `Role ${i + 1}`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => removeExperience(i)}
                  disabled={experiences.length === 1}
                  className="text-slate-400 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-30"
                  aria-label="Remove this role"
                >
                  <Trash2 size={15} />
                </button>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <input
                  value={exp.employer}
                  onChange={(e) => updateExperience(i, { employer: e.target.value })}
                  className={inputClass}
                  placeholder="Employer"
                />
                <input
                  value={exp.title}
                  onChange={(e) => updateExperience(i, { title: e.target.value })}
                  className={inputClass}
                  placeholder="Title"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="date"
                  value={exp.startDate}
                  onChange={(e) => updateExperience(i, { startDate: e.target.value })}
                  className={inputClass}
                />
                <input
                  type="date"
                  value={exp.endDate}
                  onChange={(e) => updateExperience(i, { endDate: e.target.value })}
                  className={inputClass}
                  placeholder="Leave blank if current"
                />
              </div>
              <textarea
                value={exp.narrative}
                onChange={(e) => updateExperience(i, { narrative: e.target.value })}
                rows={2}
                className={inputClass}
                placeholder="What did you do in this role?"
              />
              <SkillChipInput
                value={exp.skills}
                onChange={(v) => updateExperience(i, { skills: v })}
                placeholder="Skills used - press Enter to add"
              />
            </div>
          ))}
          <Button variant="secondary" onClick={addExperience}>
            + Add another role
          </Button>
        </div>
      )}

      {stage === 'review' && (
        <div className="space-y-4">
          <ReviewSection title="Profile" onEdit={() => setStage('about')}>
            <p className="text-sm font-medium text-slate-900">{fullName || '(no name)'}</p>
            <p className="text-sm text-slate-500">{email || '(no email)'}</p>
            <p className="text-sm text-slate-500">{location || '(no location)'}</p>
          </ReviewSection>

          <ReviewSection title="Work eligibility & compensation" onEdit={() => setStage('about')}>
            <p className="text-sm text-slate-700">Authorized to work in: {workAuth || '(none)'}</p>
            <p className="text-sm text-slate-700">Minimum comp: ${compFloor.toLocaleString()}</p>
          </ReviewSection>

          <ReviewSection title="Skills" onEdit={() => setStage('skills')}>
            {skills.trim() ? (
              <div className="flex flex-wrap gap-1.5">
                {skills.split(',').map((s) => s.trim()).filter(Boolean).map((s) => (
                  <span key={s} className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20">
                    {s}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-400">(none)</p>
            )}
          </ReviewSection>

          <ReviewSection title="Experience" onEdit={() => setStage('experience')}>
            {experiences.filter((e) => e.employer).length === 0 && <p className="text-sm text-slate-400">(none)</p>}
            <div className="space-y-2">
              {experiences
                .filter((e) => e.employer)
                .map((e, i) => (
                  <div key={i} className="rounded-lg bg-slate-50 p-3">
                    <p className="text-sm font-medium text-slate-800">
                      {e.title} at {e.employer}
                    </p>
                    <p className="text-xs text-slate-500">
                      {e.startDate || 'unknown start'} &ndash; {e.endDate || 'present'}
                    </p>
                  </div>
                ))}
            </div>
          </ReviewSection>

          {error && <p className="text-sm text-red-600">{error}</p>}
        </div>
      )}

      {stage !== 'import-resume' && stage !== 'import-linkedin' && (
        <div className="mt-6 flex justify-between border-t border-slate-100 pt-5">
          <Button variant="secondary" icon={<ArrowLeft size={14} />} onClick={() => goBack(stage, setStage)} disabled={stage === 'import'}>
            Back
          </Button>
          {stage !== 'review' ? (
            <Button icon={<ArrowRight size={14} />} onClick={() => goNext(stage, setStage)}>
              Next
            </Button>
          ) : (
            <Button onClick={submit} disabled={submitting || !fullName || !email}>
              {submitting ? 'Creating...' : 'Complete Setup'}
            </Button>
          )}
        </div>
      )}
    </div>
  )

  if (!isFormStage) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Create your profile</h1>
          {stepIndex !== null && (
            <>
              <p className="mt-1 text-sm text-slate-500">
                Step {stepIndex + 1} of {NUMBERED_STEPS.length}: {NUMBERED_STEPS[stepIndex].label}
              </p>
              <div className="mt-3 flex gap-1.5">
                {NUMBERED_STEPS.map((s, i) => (
                  <div key={s.label} className={`h-1.5 flex-1 rounded-full ${i <= stepIndex ? 'bg-blue-600' : 'bg-slate-200'}`} />
                ))}
              </div>
            </>
          )}
        </div>
        <Card>{body}</Card>
      </div>
    )
  }

  return (
    <div className="mx-auto grid max-w-4xl grid-cols-[220px_1fr] gap-8">
      <aside className="pt-2">
        <h1 className="mb-6 text-xl font-bold text-slate-900">Create your profile</h1>
        <Stepper steps={NUMBERED_STEPS.map((s) => ({ label: s.label, description: s.description }))} currentIndex={stepIndex ?? 0} />
        <div className="mt-8 flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
          <ShieldCheck className="mt-0.5 shrink-0 text-slate-400" size={14} />
          <span>You can edit every field before your profile is created.</span>
        </div>
      </aside>
      <Card>
        <CardHeader title={NUMBERED_STEPS[stepIndex ?? 1].label} subtitle={NUMBERED_STEPS[stepIndex ?? 1].description} />
        {body}
      </Card>
    </div>
  )
}

const FORWARD_ORDER: Stage[] = ['import', 'about', 'skills', 'experience', 'review']

function goNext(stage: Stage, setStage: (s: Stage) => void) {
  const i = FORWARD_ORDER.indexOf(stage)
  setStage(FORWARD_ORDER[Math.min(FORWARD_ORDER.length - 1, i + 1)])
}

function goBack(stage: Stage, setStage: (s: Stage) => void) {
  const i = FORWARD_ORDER.indexOf(stage)
  setStage(FORWARD_ORDER[Math.max(0, i - 1)])
}

const inputClass = 'w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-blue-400 focus:outline-none'

const iconTone: Record<string, string> = {
  blue: 'bg-blue-50 text-blue-600',
  green: 'bg-emerald-50 text-emerald-600',
  purple: 'bg-violet-50 text-violet-600',
  amber: 'bg-amber-50 text-amber-600',
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-slate-500">{label}</span>
      {children}
    </label>
  )
}

function SectionLabel({ children }: { children: ReactNode }) {
  return <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-400">{children}</h4>
}

function ImportOption({
  icon: Icon,
  title,
  description,
  highlight,
  onClick,
}: {
  icon: typeof FileText
  title: string
  description: string
  highlight?: string
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-4 rounded-xl border border-slate-200 p-4 text-left transition hover:-translate-y-0.5 hover:border-blue-300 hover:bg-blue-50/30 hover:shadow-md"
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
        <Icon size={20} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-slate-800">{title}</p>
          {highlight && (
            <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
              {highlight}
            </span>
          )}
        </div>
        <p className="text-xs text-slate-500">{description}</p>
      </div>
      <ArrowRight className="shrink-0 text-slate-300" size={16} />
    </button>
  )
}

function ReviewSection({ title, onEdit, children }: { title: string; onEdit: () => void; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-200 p-4">
      <div className="mb-2 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-slate-800">{title}</h4>
        <button type="button" onClick={onEdit} className="text-xs font-medium text-blue-600 hover:underline">
          Edit
        </button>
      </div>
      <div className="space-y-1">{children}</div>
    </div>
  )
}
