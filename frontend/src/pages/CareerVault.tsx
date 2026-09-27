import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  Award,
  BadgeCheck,
  Briefcase,
  Building2,
  Camera,
  CheckCircle2,
  ChevronDown,
  Clock,
  DollarSign,
  Download,
  Eye,
  ExternalLink,
  FileText,
  FolderGit2,
  Globe,
  GraduationCap,
  Layers,
  Link2,
  Lock,
  Mail,
  MapPin,
  Pencil,
  Phone,
  Plus,
  ShieldCheck,
  Star,
  Tag,
  Trash2,
  Trophy,
  UploadCloud,
  User,
} from 'lucide-react'
import { api, ApiError } from '../api/client'
import type {
  Achievement,
  Candidate,
  CandidateExperience,
  CareerVaultView,
  Certification,
  Education,
  Project,
  ResumeMeta,
  SkillProficiencyLevel,
  SkillProfile,
} from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatCard, ProgressBar } from '../components/ui/StatCard'
import { Button } from '../components/ui/Button'
import { Tabs } from '../components/ui/Tabs'
import { Modal } from '../components/ui/Modal'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { CandidateAvatar } from '../components/ui/CandidateAvatar'
import { PhotoCropModal } from '../components/ui/PhotoCropModal'
import { BrandIcon } from '../components/ui/BrandIcon'
import { IconPicker } from '../components/ui/IconPicker'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import { mentionMatchesSkillIds } from '../lib/format'
import { currencyOptions, formatCompRange, formatMoney, fromMinorUnits, toMinorUnits, DEFAULT_CURRENCY } from '../lib/currency'
import type { ConnectedAccount } from '../lib/connectedAccounts'
import { CAREER_VAULT_DEFAULTS, loadConnectedAccounts, addConnectedAccount, removeConnectedAccount } from '../lib/connectedAccounts'

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'skills', label: 'Skills' },
  { key: 'experience', label: 'Experience' },
  { key: 'achievements', label: 'Achievements' },
  { key: 'education', label: 'Education' },
  { key: 'certifications', label: 'Certifications' },
  { key: 'projects', label: 'Projects' },
  { key: 'preferences', label: 'Preferences' },
]

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function csvToList(value: string): string[] {
  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

const inputClass = 'w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none'

/** Rough years-of-experience estimate from experience date ranges, for the header stat
 * strip. Not overlap-aware (roles that ran concurrently would double count) - a display
 * heuristic, not a claim used anywhere in scoring. */
function estimateYearsExperience(experiences: CandidateExperience[]): number {
  let totalMonths = 0
  for (const exp of experiences) {
    const start = Date.parse(exp.startDate)
    if (Number.isNaN(start)) continue
    const end = exp.endDate ? Date.parse(exp.endDate) : Date.now()
    if (Number.isNaN(end) || end < start) continue
    totalMonths += (end - start) / (1000 * 60 * 60 * 24 * 30.44)
  }
  return Math.max(0, Math.round(totalMonths / 12))
}

function formatDate(value: string | null | undefined): string {
  if (!value) return 'Undated'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

interface EvidenceItem {
  id: string
  kind: 'experience' | 'achievement' | 'education' | 'certification'
  title: string
  subtitle: string | null
  tags: string[]
  date: string | null
}

function buildEvidenceTimeline(
  experiences: CandidateExperience[],
  achievements: Achievement[],
  education: Education[],
  certifications: Certification[],
): EvidenceItem[] {
  const items: EvidenceItem[] = [
    ...experiences.map((e) => ({
      id: `experience-${e.id}`,
      kind: 'experience' as const,
      title: `${e.title} · ${e.employer}`,
      subtitle: e.narrative || null,
      tags: e.rawSkillMentions,
      date: e.startDate,
    })),
    ...achievements.map((a) => ({
      id: `achievement-${a.id}`,
      kind: 'achievement' as const,
      title: a.title,
      subtitle: a.description,
      tags: a.tags,
      date: a.occurredOn,
    })),
    ...education.map((e) => ({
      id: `education-${e.id}`,
      kind: 'education' as const,
      title: `${e.degree}${e.fieldOfStudy ? `, ${e.fieldOfStudy}` : ''}`,
      subtitle: e.institution,
      tags: [] as string[],
      date: e.endDate ?? e.startDate,
    })),
    ...certifications.map((c) => ({
      id: `certification-${c.id}`,
      kind: 'certification' as const,
      title: c.name,
      subtitle: c.issuer,
      tags: [] as string[],
      date: c.issuedOn,
    })),
  ]
  return items.sort((a, b) => {
    const bt = b.date ? Date.parse(b.date) : 0
    const at = a.date ? Date.parse(a.date) : 0
    return (Number.isNaN(bt) ? 0 : bt) - (Number.isNaN(at) ? 0 : at)
  })
}

const EVIDENCE_ICON: Record<EvidenceItem['kind'], { icon: typeof Award; bg: string; fg: string }> = {
  experience: { icon: Briefcase, bg: 'bg-sky-50', fg: 'text-sky-600' },
  achievement: { icon: Trophy, bg: 'bg-violet-50', fg: 'text-violet-600' },
  education: { icon: GraduationCap, bg: 'bg-blue-50', fg: 'text-blue-600' },
  certification: { icon: BadgeCheck, bg: 'bg-emerald-50', fg: 'text-emerald-600' },
}

function EvidenceCard({ item }: { item: EvidenceItem }) {
  const { icon: Icon, bg, fg } = EVIDENCE_ICON[item.kind]
  return (
    <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${bg} ${fg}`}>
        <Icon size={16} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium text-slate-900">{item.title}</p>
          <Badge tone="green">Verified</Badge>
        </div>
        {item.subtitle && <p className="mt-0.5 text-sm text-slate-500">{item.subtitle}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          {item.tags.map((t) => (
            <Badge key={t} tone="slate">
              {t}
            </Badge>
          ))}
          <span className="ml-auto text-xs text-slate-400">{formatDate(item.date)}</span>
        </div>
      </div>
    </div>
  )
}

interface SkillMentionCount {
  skill: string
  count: number
}

/** Every skill on file gets a "mention count": how many times it shows up in the
 * candidate's own experience/achievement records. This is an honest, derived signal
 * (not a fabricated proficiency level - there's no such field anywhere in the domain
 * model), used purely to rank/size the Skills Overview bars. */
function computeSkillMentionCounts(vault: CareerVaultView): SkillMentionCount[] {
  const counts = new Map<string, number>()
  for (const skill of vault.candidate.rawSkillMentions) counts.set(skill, 0)
  const bump = (mention: string) => {
    for (const skill of counts.keys()) {
      if (skill.toLowerCase() === mention.toLowerCase()) counts.set(skill, (counts.get(skill) ?? 0) + 1)
    }
  }
  for (const exp of vault.experiences) {
    for (const m of exp.rawSkillMentions) bump(m)
  }
  for (const a of vault.achievements) {
    for (const t of a.tags) bump(t)
  }
  return [...counts.entries()]
    .map(([skill, count]) => ({ skill, count }))
    .sort((a, b) => b.count - a.count || a.skill.localeCompare(b.skill))
}

/** The first tab worth sending someone to when their profile isn't "complete" yet,
 * mirroring the exact 5-section check CareerVaultService.completeness uses. */
function firstIncompleteTab(vault: CareerVaultView): string {
  if (vault.candidate.skillIds.length === 0) return 'skills'
  if (vault.experiences.length === 0) return 'experience'
  if (vault.achievements.length === 0) return 'achievements'
  if (vault.education.length === 0) return 'education'
  if (vault.certifications.length === 0) return 'certifications'
  return 'overview'
}

interface CompletenessSection {
  tab: string
  label: string
  done: boolean
  detail: string
}

/** The same 5 sections CareerVaultService.completeness counts, surfaced as a
 * checklist so "what's missing" is a fact, not a vague percentage. */
function buildCompletenessSections(vault: CareerVaultView): CompletenessSection[] {
  return [
    { tab: 'skills', label: 'Skills', done: vault.candidate.skillIds.length > 0, detail: `${vault.candidate.skillIds.length} on file` },
    { tab: 'experience', label: 'Experience', done: vault.experiences.length > 0, detail: `${vault.experiences.length} role(s)` },
    { tab: 'achievements', label: 'Achievements', done: vault.achievements.length > 0, detail: `${vault.achievements.length} logged` },
    { tab: 'education', label: 'Education', done: vault.education.length > 0, detail: `${vault.education.length} entry(ies)` },
    { tab: 'certifications', label: 'Certifications', done: vault.certifications.length > 0, detail: `${vault.certifications.length} on file` },
  ]
}

type EvidenceKind = 'experience' | 'achievement' | 'education' | 'certification' | 'project'

export function CareerVault() {
  const { selected } = useCandidate()
  const [vault, setVault] = useState<CareerVaultView | null>(null)
  const [tab, setTab] = useState('overview')
  const [showExperienceForm, setShowExperienceForm] = useState(false)
  const [showAchievementForm, setShowAchievementForm] = useState(false)
  const [showEducationForm, setShowEducationForm] = useState(false)
  const [showCertForm, setShowCertForm] = useState(false)
  const [showProjectForm, setShowProjectForm] = useState(false)
  const [showResumeModal, setShowResumeModal] = useState(false)
  const [showAddEvidenceMenu, setShowAddEvidenceMenu] = useState(false)
  const [showPrivacy, setShowPrivacy] = useState(false)
  const [resumeMeta, setResumeMeta] = useState<ResumeMeta | null>(null)
  const [resumeChecked, setResumeChecked] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = () => {
    if (!selected) return
    setLoadError(null)
    api.vault.get(selected.id).then(setVault).catch((e) => setLoadError(describeError(e)))
  }

  const loadResumeMeta = () => {
    if (!selected) return
    setResumeChecked(false)
    api.candidates
      .resumeMeta(selected.id)
      .then(setResumeMeta)
      .catch(() => setResumeMeta(null))
      .finally(() => setResumeChecked(true))
  }

  useEffect(load, [selected])
  useEffect(loadResumeMeta, [selected])

  const yearsExperience = useMemo(() => (vault ? estimateYearsExperience(vault.experiences) : 0), [vault])
  const evidenceTimeline = useMemo(
    () => (vault ? buildEvidenceTimeline(vault.experiences, vault.achievements, vault.education, vault.certifications) : []),
    [vault],
  )
  const skillCounts = useMemo(() => (vault ? computeSkillMentionCounts(vault) : []), [vault])
  const companiesCount = useMemo(() => (vault ? new Set(vault.experiences.map((e) => e.employer)).size : 0), [vault])

  const jumpToEvidenceForm = (kind: EvidenceKind) => {
    setShowAddEvidenceMenu(false)
    if (kind === 'experience') {
      setTab('experience')
      setShowExperienceForm(true)
    } else if (kind === 'achievement') {
      setTab('achievements')
      setShowAchievementForm(true)
    } else if (kind === 'education') {
      setTab('education')
      setShowEducationForm(true)
    } else if (kind === 'certification') {
      setTab('certifications')
      setShowCertForm(true)
    } else {
      setTab('projects')
      setShowProjectForm(true)
    }
  }

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  if (loadError && !vault) {
    return <ErrorBanner message={`Couldn't load your profile: ${loadError}`} />
  }

  if (!vault) {
    return (
      <Card>
        <p className="text-sm text-slate-400">Loading&hellip;</p>
      </Card>
    )
  }

  const complete = vault.completeness >= 1

  return (
    <div className="space-y-6">
      <div className="flex flex-col items-start justify-between gap-4 lg:flex-row lg:items-center">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Career Vault</h1>
          <p className="mt-1 text-sm text-slate-500">
            Your professional record - powering grounded AI tailoring and better job matches.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/career-vault/resume">
            <Button variant="secondary" icon={<ExternalLink size={14} />}>
              View Resume
            </Button>
          </Link>
          <Button variant="secondary" icon={<UploadCloud size={14} />} onClick={() => setShowResumeModal(true)}>
            Import Resume
          </Button>
          <div className="relative">
            <Button variant="primary" icon={<Plus size={14} />} onClick={() => setShowAddEvidenceMenu((v) => !v)}>
              Add Evidence
            </Button>
            {showAddEvidenceMenu && (
              <div className="absolute right-0 z-20 mt-1 w-48 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                {(
                  [
                    ['experience', 'Work Experience'],
                    ['achievement', 'Achievement'],
                    ['education', 'Education'],
                    ['certification', 'Certification'],
                    ['project', 'Project'],
                  ] as [EvidenceKind, string][]
                ).map(([kind, label]) => (
                  <button
                    key={kind}
                    onClick={() => jumpToEvidenceForm(kind)}
                    className="block w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <Card>
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-4">
            <EditableAvatar candidateId={vault.candidate.id} name={vault.candidate.fullName} size={80} />
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-xl font-bold text-slate-900">{vault.candidate.fullName}</h2>
                {complete ? (
                  <Badge tone="green">
                    <CheckCircle2 size={12} /> Verified Profile
                  </Badge>
                ) : (
                  <button onClick={() => setTab(firstIncompleteTab(vault))}>
                    <Badge tone="amber">{Math.round(vault.completeness * 100)}% complete</Badge>
                  </button>
                )}
              </div>
              {vault.candidate.headline && <p className="text-sm text-slate-600">{vault.candidate.headline}</p>}
              <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-400">
                <MapPin size={12} /> {vault.candidate.location}
              </p>
            </div>
          </div>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatCard icon={<Clock size={16} />} iconTone="blue" value={`${yearsExperience}+`} label="Years Experience" />
          <StatCard icon={<Layers size={16} />} iconTone="purple" value={vault.candidate.rawSkillMentions.length} label="Core Skills" />
          <StatCard icon={<Building2 size={16} />} iconTone="green" value={companiesCount} label="Companies" />
          <StatCard icon={<Trophy size={16} />} iconTone="amber" value={vault.achievements.length} label="Key Achievements" />
        </div>
        {!complete && (
          <p className="mt-3 text-xs text-slate-400">
            All information here is backed by the records you add - a &ldquo;Verified Profile&rdquo; badge appears once
            skills, experience, achievements, education, and certifications are all on file.
          </p>
        )}
      </Card>

      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <PersonalInfoCard candidate={vault.candidate} onSaved={load} />
            <ProfessionalSummaryCard candidate={vault.candidate} yearsExperience={yearsExperience} onSaved={load} />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <Card>
                <CardHeader
                  title="Recent Evidence"
                  subtitle="Your latest experience, achievements, education, and certifications - newest first"
                  action={
                    <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowAddEvidenceMenu((v) => !v)}>
                      Add
                    </Button>
                  }
                />
                <div className="space-y-2">
                  {evidenceTimeline.slice(0, 6).map((item) => (
                    <EvidenceCard key={item.id} item={item} />
                  ))}
                  {evidenceTimeline.length === 0 && (
                    <p className="text-sm text-slate-400">
                      No evidence on file yet - use Import Resume or Add Evidence above to get started.
                    </p>
                  )}
                </div>
              </Card>
            </div>
            <div className="space-y-6">
              <Card>
                <CardHeader title="Profile Completeness" subtitle="What's still missing, section by section" />
                <ProfileInsights vault={vault} onJump={setTab} />
              </Card>
              <Card>
                <CardHeader
                  title="Skills Overview"
                  action={
                    <button onClick={() => setTab('skills')} className="text-xs font-medium text-blue-600 hover:underline">
                      View All
                    </button>
                  }
                />
                <SkillsOverviewBars counts={skillCounts.slice(0, 5)} />
                {skillCounts.length === 0 && <p className="text-sm text-slate-400">No skills on file yet.</p>}
              </Card>
            </div>
          </div>
        </div>
      )}

      {tab === 'skills' && (
        <div className="space-y-6">
          <TopSkillsCard candidate={vault.candidate} onSaved={load} />
          <Card>
            <CardHeader title="Skill Mentions" subtitle="How often each skill shows up across your experience and achievements on file" />
            <SkillsOverviewBars counts={skillCounts} />
            {skillCounts.length === 0 && <p className="text-sm text-slate-400">No skills on file.</p>}
          </Card>
          <div>
            <h3 className="mb-3 text-sm font-semibold text-slate-700">Skill Details</h3>
            {vault.candidate.rawSkillMentions.length === 0 ? (
              <p className="text-sm text-slate-400">Add skills above to start rating your experience with each one.</p>
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {vault.candidate.rawSkillMentions.map((skillName) => {
                  const profile = vault.skillProfiles.find((p) => p.skillName.toLowerCase() === skillName.toLowerCase()) ?? null
                  const count = skillCounts.find((c) => c.skill === skillName)?.count ?? 0
                  return (
                    <SkillProfileCard
                      key={skillName}
                      candidateId={selected.id}
                      skillName={skillName}
                      profile={profile}
                      normalized={mentionMatchesSkillIds(skillName, vault.candidate.skillIds)}
                      mentionCount={count}
                      onSaved={load}
                    />
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {tab === 'experience' && (
        <Card>
          <CardHeader
            title="Experience Timeline"
            subtitle={`${vault.experiences.length} role(s) - click to expand`}
            action={
              <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowExperienceForm((v) => !v)}>
                Add
              </Button>
            }
          />
          {showExperienceForm && (
            <ExperienceForm
              candidateId={selected.id}
              onSaved={() => {
                setShowExperienceForm(false)
                load()
              }}
            />
          )}
          {vault.experiences.length === 0 ? (
            <p className="text-sm text-slate-400">
              No experience on file yet - it's pre-filled from your resume or LinkedIn profile at onboarding, or
              back-filled the next time you upload a resume with none on file. You can also add a role manually.
            </p>
          ) : (
            <ol className="space-y-4 border-l-2 border-slate-200 pl-5">
              {vault.experiences.map((exp) => (
                <ExperienceTimelineRow
                  key={exp.id}
                  exp={exp}
                  onDeleted={() => {
                    api.vault.deleteExperience(selected.id, exp.id).then(load)
                  }}
                />
              ))}
            </ol>
          )}
        </Card>
      )}

      {tab === 'achievements' && (
        <Card>
          <CardHeader
            title="Achievements"
            action={
              <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowAchievementForm((v) => !v)}>
                Add
              </Button>
            }
          />
          {showAchievementForm && (
            <AchievementForm
              candidateId={selected.id}
              onSaved={() => {
                setShowAchievementForm(false)
                load()
              }}
            />
          )}
          <div className="space-y-2">
            {vault.achievements.map((a) => (
              <EvidenceCard
                key={a.id}
                item={{ id: a.id, kind: 'achievement', title: a.title, subtitle: a.description, tags: a.tags, date: a.occurredOn }}
              />
            ))}
            {vault.achievements.length === 0 && <p className="text-sm text-slate-400">No achievements on file yet.</p>}
          </div>
        </Card>
      )}

      {tab === 'education' && (
        <Card>
          <CardHeader
            title="Education"
            action={
              <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowEducationForm((v) => !v)}>
                Add
              </Button>
            }
          />
          {showEducationForm && (
            <EducationForm
              candidateId={selected.id}
              onSaved={() => {
                setShowEducationForm(false)
                load()
              }}
            />
          )}
          <div className="space-y-2">
            {vault.education.map((e) => (
              <EvidenceCard
                key={e.id}
                item={{
                  id: e.id,
                  kind: 'education',
                  title: `${e.degree}${e.fieldOfStudy ? `, ${e.fieldOfStudy}` : ''}`,
                  subtitle: e.institution,
                  tags: [],
                  date: e.endDate ?? e.startDate,
                }}
              />
            ))}
            {vault.education.length === 0 && <p className="text-sm text-slate-400">No education on file yet.</p>}
          </div>
        </Card>
      )}

      {tab === 'certifications' && (
        <Card>
          <CardHeader
            title="Certifications"
            action={
              <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowCertForm((v) => !v)}>
                Add
              </Button>
            }
          />
          {showCertForm && (
            <CertificationForm
              candidateId={selected.id}
              onSaved={() => {
                setShowCertForm(false)
                load()
              }}
            />
          )}
          <div className="space-y-2">
            {vault.certifications.map((c) => (
              <EvidenceCard
                key={c.id}
                item={{ id: c.id, kind: 'certification', title: c.name, subtitle: c.issuer, tags: [], date: c.issuedOn }}
              />
            ))}
            {vault.certifications.length === 0 && <p className="text-sm text-slate-400">No certifications on file yet.</p>}
          </div>
        </Card>
      )}

      {tab === 'projects' && (
        <Card>
          <CardHeader
            title="Projects"
            subtitle="Side projects, open-source work, or portfolio pieces - self-reported, like achievements/education/certifications, not fed into resume-tailoring grounding."
            action={
              <Button variant="secondary" icon={<Plus size={14} />} onClick={() => setShowProjectForm((v) => !v)}>
                Add
              </Button>
            }
          />
          {showProjectForm && (
            <ProjectForm
              candidateId={selected.id}
              onSaved={() => {
                setShowProjectForm(false)
                load()
              }}
            />
          )}
          <div className="space-y-2">
            {vault.projects.map((p) => (
              <ProjectCard key={p.id} project={p} />
            ))}
            {vault.projects.length === 0 && <p className="text-sm text-slate-400">No projects on file yet.</p>}
          </div>
        </Card>
      )}

      {tab === 'preferences' && (
        <div className="space-y-6">
          <CareerInterestsCard candidate={vault.candidate} onSaved={load} />
          <JobPreferencesCard candidate={vault.candidate} onSaved={load} />
          <Card>
            <CardHeader
              title="Minimum Compensation"
              subtitle="There's no separate preferences record yet, so this reflects your profile field. Work authorization lives on the Overview tab's Personal Information card."
            />
            <div className="rounded-lg border border-slate-100 p-3 sm:w-1/2">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
                <Layers size={15} className="text-slate-400" />
                Minimum Compensation
              </div>
              <p className="text-sm text-slate-600">
                {formatMoney(vault.candidate.compFloorMinorUnits, vault.candidate.preferredCurrency || DEFAULT_CURRENCY)} annually
              </p>
            </div>
          </Card>
          <ConnectedAccountsCard candidateId={selected.id} />
          <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4">
            <div>
              <p className="text-sm font-medium text-slate-900">Manage my data</p>
              <p className="text-xs text-slate-500">Export or permanently erase this profile and every record tied to it (docs/02 §4.1).</p>
            </div>
            <Button variant="secondary" onClick={() => setShowPrivacy((v) => !v)}>
              {showPrivacy ? 'Hide' : 'Manage'}
            </Button>
          </div>
          {showPrivacy && <PrivacyCard candidate={vault.candidate} />}
        </div>
      )}

      {showResumeModal && (
        <Modal title="Resume" onClose={() => setShowResumeModal(false)} maxWidthClass="max-w-lg">
          <ResumeCard candidateId={selected.id} meta={resumeMeta} checked={resumeChecked} onChanged={loadResumeMeta} />
        </Modal>
      )}
    </div>
  )
}

function ProfileInsights({ vault, onJump }: { vault: CareerVaultView; onJump: (tab: string) => void }) {
  const sections = buildCompletenessSections(vault)
  const missing = sections.filter((s) => !s.done)
  return (
    <div className="space-y-2">
      {sections.map((s) => (
        <button
          key={s.tab}
          onClick={() => onJump(s.tab)}
          className="flex w-full items-center justify-between gap-2 rounded-lg p-1.5 text-left hover:bg-slate-50"
        >
          <span className="flex items-center gap-2">
            {s.done ? (
              <CheckCircle2 size={15} className="shrink-0 text-emerald-500" />
            ) : (
              <span className="h-3.5 w-3.5 shrink-0 rounded-full border-2 border-slate-300" />
            )}
            <span className={`text-sm ${s.done ? 'text-slate-700' : 'text-slate-500'}`}>{s.label}</span>
          </span>
          <span className="text-xs text-slate-400">{s.detail}</span>
        </button>
      ))}
      {missing.length === 0 ? (
        <p className="pt-1 text-xs text-emerald-600">All sections complete - your profile is fully verified.</p>
      ) : (
        <p className="pt-1 text-xs text-slate-400">
          Add {missing.map((s) => s.label.toLowerCase()).join(', ')} to reach a Verified Profile.
        </p>
      )}
    </div>
  )
}

function SkillsOverviewBars({ counts }: { counts: SkillMentionCount[] }) {
  const max = Math.max(1, ...counts.map((c) => c.count))
  return (
    <div className="space-y-3">
      {counts.map((c) => (
        <div key={c.skill}>
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="text-slate-700">{c.skill}</span>
            <span className="text-xs text-slate-400">{c.count}</span>
          </div>
          <ProgressBar value={c.count / max} tone="blue" />
        </div>
      ))}
    </div>
  )
}

const PROFICIENCY_LABEL: Record<SkillProficiencyLevel, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  EXPERT: 'Expert',
}

const PROFICIENCY_TONE: Record<SkillProficiencyLevel, 'slate' | 'blue' | 'green'> = {
  BEGINNER: 'slate',
  INTERMEDIATE: 'blue',
  EXPERT: 'green',
}

function ConfidenceStars({ value, onChange }: { value: number | null; onChange?: (v: number) => void }) {
  return (
    <span className="inline-flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          disabled={!onChange}
          onClick={() => onChange?.(n)}
          className={onChange ? 'cursor-pointer' : 'cursor-default'}
          aria-label={`${n} star`}
        >
          <Star size={14} className={value != null && n <= value ? 'fill-amber-400 text-amber-400' : 'text-slate-200'} />
        </button>
      ))}
    </span>
  )
}

/** One candidate-editable skill "profile" card - years of experience, a Beginner/
 * Intermediate/Expert level, a 1-5 confidence rating, and when/what version it was last
 * used. Backed by CandidateSkillProfile (upserted by exact skillName), separate from the
 * honest, purely-derived "mention count" bars above it - this is the candidate's own
 * self-rating, not something computed from their records. */
function SkillProfileCard({
  candidateId,
  skillName,
  profile,
  normalized,
  mentionCount,
  onSaved,
}: {
  candidateId: string
  skillName: string
  profile: SkillProfile | null
  normalized: boolean
  mentionCount: number
  onSaved: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [level, setLevel] = useState<SkillProficiencyLevel | ''>(profile?.proficiencyLevel ?? '')
  const [years, setYears] = useState(profile?.yearsOfExperience != null ? String(profile.yearsOfExperience) : '')
  const [confidence, setConfidence] = useState<number | null>(profile?.confidenceScore ?? null)
  const [lastUsedOn, setLastUsedOn] = useState(profile?.lastUsedOn ?? '')
  const [lastUsedVersion, setLastUsedVersion] = useState(profile?.lastUsedVersion ?? '')
  const [saving, setSaving] = useState(false)

  const startEdit = () => {
    setLevel(profile?.proficiencyLevel ?? '')
    setYears(profile?.yearsOfExperience != null ? String(profile.yearsOfExperience) : '')
    setConfidence(profile?.confidenceScore ?? null)
    setLastUsedOn(profile?.lastUsedOn ?? '')
    setLastUsedVersion(profile?.lastUsedVersion ?? '')
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.vault.upsertSkillProfile(candidateId, {
        skillName,
        proficiencyLevel: level || null,
        yearsOfExperience: years.trim() === '' ? null : Number(years),
        confidenceScore: confidence,
        lastUsedOn: lastUsedOn || null,
        lastUsedVersion: lastUsedVersion.trim() === '' ? null : lastUsedVersion.trim(),
      })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <Badge tone={normalized ? 'blue' : 'slate'}>{skillName}</Badge>
          {mentionCount > 0 && <span className="text-xs text-slate-400">{mentionCount} mention(s)</span>}
        </div>
        <button
          onClick={() => (editing ? setEditing(false) : startEdit())}
          className="text-xs font-medium text-blue-600 hover:underline"
        >
          {editing ? 'Cancel' : profile ? 'Edit' : 'Add details'}
        </button>
      </div>

      {!editing ? (
        <div className="mt-2 space-y-1.5 text-sm text-slate-600">
          <div className="flex flex-wrap items-center gap-2">
            {profile?.proficiencyLevel ? (
              <Badge tone={PROFICIENCY_TONE[profile.proficiencyLevel]}>{PROFICIENCY_LABEL[profile.proficiencyLevel]}</Badge>
            ) : (
              <span className="text-xs text-slate-400">No level set</span>
            )}
            {profile?.confidenceScore != null && <ConfidenceStars value={profile.confidenceScore} />}
          </div>
          {profile?.yearsOfExperience != null && (
            <p className="flex items-center gap-1.5 text-xs text-slate-500">
              <Clock size={12} /> {profile.yearsOfExperience} year(s) of experience
            </p>
          )}
          {(profile?.lastUsedOn || profile?.lastUsedVersion) && (
            <p className="flex items-center gap-1.5 text-xs text-slate-500">
              <Tag size={12} />
              Last used {profile.lastUsedOn ? formatDate(profile.lastUsedOn) : ''}
              {profile.lastUsedVersion ? ` · ${profile.lastUsedVersion}` : ''}
            </p>
          )}
          {!profile && <p className="text-xs text-slate-400">No years, level, or confidence rated yet.</p>}
        </div>
      ) : (
        <div className="mt-3 space-y-2">
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs text-slate-500">
              Level
              <select className={inputClass} value={level} onChange={(e) => setLevel(e.target.value as SkillProficiencyLevel | '')}>
                <option value="">Not set</option>
                <option value="BEGINNER">Beginner</option>
                <option value="INTERMEDIATE">Intermediate</option>
                <option value="EXPERT">Expert</option>
              </select>
            </label>
            <label className="text-xs text-slate-500">
              Years of experience
              <input
                className={inputClass}
                type="number"
                min="0"
                step="0.5"
                value={years}
                onChange={(e) => setYears(e.target.value)}
              />
            </label>
          </div>
          <div className="text-xs text-slate-500">
            Confidence
            <div className="mt-1">
              <ConfidenceStars value={confidence} onChange={setConfidence} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs text-slate-500">
              Last used
              <input className={inputClass} type="date" value={lastUsedOn} onChange={(e) => setLastUsedOn(e.target.value)} />
            </label>
            <label className="text-xs text-slate-500">
              Last version used
              <input
                className={inputClass}
                placeholder="e.g. React 19"
                value={lastUsedVersion}
                onChange={(e) => setLastUsedVersion(e.target.value)}
              />
            </label>
          </div>
          <div className="flex justify-end">
            <Button onClick={save} disabled={saving}>
              Save
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function ExperienceTimelineRow({ exp, onDeleted }: { exp: CandidateExperience; onDeleted: () => void }) {
  return (
    <li className="relative">
      <span className="absolute -left-[27px] top-1 flex h-4 w-4 items-center justify-center rounded-full border-2 border-white bg-blue-600 shadow" />
      <details className="group rounded-lg border border-slate-200 px-3 py-2 open:bg-slate-50/60">
        <summary className="flex cursor-pointer list-none items-center gap-3">
          <CompanyAvatar name={exp.employer} size={32} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-slate-900">
              {exp.title} &middot; {exp.employer}
            </p>
            <p className="text-xs text-slate-400">
              {exp.startDate} &ndash; {exp.endDate ?? 'Present'}
            </p>
          </div>
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              onDeleted()
            }}
            className="shrink-0 text-slate-400 hover:text-red-600"
            aria-label={`Remove ${exp.title} at ${exp.employer}`}
          >
            <Trash2 size={14} />
          </button>
          <ChevronDown className="shrink-0 text-slate-400 transition-transform group-open:rotate-180" size={16} />
        </summary>
        <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
          {exp.narrative && <p className="text-sm text-slate-600">{exp.narrative}</p>}
          {exp.verifiedMetrics.length > 0 && (
            <ul className="list-disc space-y-0.5 pl-4 text-sm text-slate-600">
              {exp.verifiedMetrics.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}
          {exp.rawSkillMentions.length > 0 && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {exp.rawSkillMentions.map((s) => (
                <Badge key={s} tone={mentionMatchesSkillIds(s, exp.skillIds) ? 'blue' : 'slate'}>
                  {s}
                </Badge>
              ))}
            </div>
          )}
        </div>
      </details>
    </li>
  )
}

function ExperienceForm({ candidateId, onSaved }: { candidateId: string; onSaved: () => void }) {
  const [employer, setEmployer] = useState('')
  const [title, setTitle] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [narrative, setNarrative] = useState('')
  const [skills, setSkills] = useState('')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!employer.trim() || !title.trim()) return
    setSaving(true)
    try {
      await api.vault.addExperience(candidateId, {
        employer: employer.trim(),
        title: title.trim(),
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        narrative: narrative || undefined,
        rawSkillMentions: csvToList(skills),
      })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mb-4 space-y-2 rounded-lg border border-slate-200 p-3">
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Job title (e.g. Senior Software Engineer)"
          className={inputClass}
        />
        <input
          value={employer}
          onChange={(e) => setEmployer(e.target.value)}
          placeholder="Company"
          className={inputClass}
        />
      </div>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <label className="block">
          <span className="text-xs font-medium text-slate-500">Start date</span>
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className={`${inputClass} mt-1`} />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-slate-500">End date (leave blank if current)</span>
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className={`${inputClass} mt-1`} />
        </label>
      </div>
      <textarea
        value={narrative}
        onChange={(e) => setNarrative(e.target.value)}
        placeholder="What did you do in this role?"
        rows={2}
        className={inputClass}
      />
      <input
        value={skills}
        onChange={(e) => setSkills(e.target.value)}
        placeholder="Skills used, comma-separated (e.g. React, Postgres)"
        className={inputClass}
      />
      <Button onClick={save} disabled={saving || !employer.trim() || !title.trim()}>
        {saving ? 'Saving...' : 'Save experience'}
      </Button>
    </div>
  )
}

function ProjectCard({ project }: { project: Project }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-indigo-600">
        <FolderGit2 size={16} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-medium text-slate-900">
            {project.url ? (
              <a href={project.url} target="_blank" rel="noreferrer" className="hover:underline">
                {project.title}
              </a>
            ) : (
              project.title
            )}
          </p>
          <span className="text-xs text-slate-400">
            {formatDate(project.startDate)}
            {project.endDate ? ` – ${formatDate(project.endDate)}` : ''}
          </span>
        </div>
        {project.description && <p className="mt-0.5 text-sm text-slate-500">{project.description}</p>}
        {project.technologies.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {project.technologies.map((t) => (
              <Badge key={t} tone="slate">
                {t}
              </Badge>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function ProjectForm({ candidateId, onSaved }: { candidateId: string; onSaved: () => void }) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [url, setUrl] = useState('')
  const [technologies, setTechnologies] = useState('')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!title.trim()) return
    setSaving(true)
    try {
      await api.vault.addProject(candidateId, {
        title,
        description,
        url: url || undefined,
        technologies: technologies
          .split(',')
          .map((t) => t.trim())
          .filter(Boolean),
      })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mb-4 space-y-2 rounded-lg border border-slate-200 p-3">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Project title"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Description"
        rows={2}
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <input
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        placeholder="Link (optional)"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <input
        value={technologies}
        onChange={(e) => setTechnologies(e.target.value)}
        placeholder="Technologies, comma-separated (e.g. React, Postgres)"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <Button onClick={save} disabled={saving}>
        {saving ? 'Saving...' : 'Save project'}
      </Button>
    </div>
  )
}

function AchievementForm({ candidateId, onSaved }: { candidateId: string; onSaved: () => void }) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!title.trim()) return
    setSaving(true)
    try {
      await api.vault.addAchievement(candidateId, { title, description })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mb-4 space-y-2 rounded-lg border border-slate-200 p-3">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Title (e.g. Reduced infra cost by 40%)"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="Description"
        rows={2}
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <Button onClick={save} disabled={saving}>
        {saving ? 'Saving...' : 'Save achievement'}
      </Button>
    </div>
  )
}

function EducationForm({ candidateId, onSaved }: { candidateId: string; onSaved: () => void }) {
  const [institution, setInstitution] = useState('')
  const [degree, setDegree] = useState('')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!institution.trim() || !degree.trim()) return
    setSaving(true)
    try {
      await api.vault.addEducation(candidateId, { institution, degree })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mb-4 space-y-2 rounded-lg border border-slate-200 p-3">
      <input
        value={degree}
        onChange={(e) => setDegree(e.target.value)}
        placeholder="Degree (e.g. B.Tech Computer Science)"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <input
        value={institution}
        onChange={(e) => setInstitution(e.target.value)}
        placeholder="Institution"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <Button onClick={save} disabled={saving}>
        {saving ? 'Saving...' : 'Save education'}
      </Button>
    </div>
  )
}

function CertificationForm({ candidateId, onSaved }: { candidateId: string; onSaved: () => void }) {
  const [name, setName] = useState('')
  const [issuer, setIssuer] = useState('')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    if (!name.trim() || !issuer.trim()) return
    setSaving(true)
    try {
      await api.vault.addCertification(candidateId, { name, issuer })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mb-4 space-y-2 rounded-lg border border-slate-200 p-3">
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Certification name"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <input
        value={issuer}
        onChange={(e) => setIssuer(e.target.value)}
        placeholder="Issuer"
        className="w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
      />
      <Button onClick={save} disabled={saving}>
        {saving ? 'Saving...' : 'Save certification'}
      </Button>
    </div>
  )
}

function Field({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 py-1.5">
      <div className="flex w-40 shrink-0 items-center gap-1.5 text-sm font-medium text-slate-400">
        {icon}
        {label}
      </div>
      <div className="min-w-0 flex-1 text-sm text-slate-800">{children}</div>
    </div>
  )
}

function LabeledInput({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  placeholder?: string
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-500">{label}</span>
      <input className={`${inputClass} mt-1`} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </label>
  )
}

function EditAction({
  editing,
  saving,
  onEdit,
  onCancel,
  onSave,
}: {
  editing: boolean
  saving: boolean
  onEdit: () => void
  onCancel: () => void
  onSave: () => void
}) {
  return editing ? (
    <div className="flex gap-2">
      <Button variant="secondary" onClick={onCancel} disabled={saving}>
        Cancel
      </Button>
      <Button onClick={onSave} disabled={saving}>
        {saving ? 'Saving...' : 'Save'}
      </Button>
    </div>
  ) : (
    <Button variant="secondary" icon={<Pencil size={14} />} onClick={onEdit}>
      Edit
    </Button>
  )
}

/** Owns the photo-upload affordance (camera button + crop modal) so exactly one
 * editable avatar exists on the page - the hero header's - rather than a second,
 * separate one duplicated inside Personal Information below it. */
function EditableAvatar({ candidateId, name, size }: { candidateId: string; name: string; size: number }) {
  const { bumpPhotoVersion } = useCandidate()
  const [pickedPhoto, setPickedPhoto] = useState<File | null>(null)
  const [uploadingPhoto, setUploadingPhoto] = useState(false)
  const photoInputRef = useRef<HTMLInputElement>(null)

  const savePhoto = async (blob: Blob) => {
    setUploadingPhoto(true)
    try {
      await api.candidates.uploadPhoto(candidateId, blob)
      bumpPhotoVersion()
    } finally {
      setUploadingPhoto(false)
      setPickedPhoto(null)
    }
  }

  return (
    <div className="relative shrink-0">
      <CandidateAvatar candidateId={candidateId} name={name} size={size} />
      <button
        type="button"
        onClick={() => photoInputRef.current?.click()}
        disabled={uploadingPhoto}
        aria-label="Change photo"
        className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-full bg-blue-600 text-white shadow ring-2 ring-white hover:bg-blue-700 disabled:opacity-60"
      >
        <Camera size={13} />
      </button>
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) setPickedPhoto(file)
          e.target.value = ''
        }}
      />
      {pickedPhoto && <PhotoCropModal file={pickedPhoto} onCancel={() => setPickedPhoto(null)} onCropped={savePhoto} />}
    </div>
  )
}

function PersonalInfoCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [fullName, setFullName] = useState(candidate.fullName)
  const [headline, setHeadline] = useState(candidate.headline ?? '')
  const [location, setLocation] = useState(candidate.location)
  const [phone, setPhone] = useState(candidate.phone ?? '')
  const [linkedinUrl, setLinkedinUrl] = useState(candidate.linkedinUrl ?? '')
  const [portfolioUrl, setPortfolioUrl] = useState(candidate.portfolioUrl ?? '')
  const [workAuthorizations, setWorkAuthorizations] = useState(candidate.workAuthorizations.join(', '))
  const [saving, setSaving] = useState(false)

  const startEdit = () => {
    setFullName(candidate.fullName)
    setHeadline(candidate.headline ?? '')
    setLocation(candidate.location)
    setPhone(candidate.phone ?? '')
    setLinkedinUrl(candidate.linkedinUrl ?? '')
    setPortfolioUrl(candidate.portfolioUrl ?? '')
    setWorkAuthorizations(candidate.workAuthorizations.join(', '))
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, {
        fullName,
        headline,
        location,
        phone,
        linkedinUrl,
        portfolioUrl,
        workAuthorizations: csvToList(workAuthorizations),
      })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <CardHeader
        title="Personal Information"
        subtitle="This information helps personalize your job search experience."
        action={<EditAction editing={editing} saving={saving} onEdit={startEdit} onCancel={() => setEditing(false)} onSave={save} />}
      />
      <div className="divide-y divide-slate-50">
        <Field icon={<User size={14} />} label="Name">
            {editing ? <input className={inputClass} value={fullName} onChange={(e) => setFullName(e.target.value)} /> : candidate.fullName}
          </Field>
          <Field icon={<Briefcase size={14} />} label="Headline">
            {editing ? (
              <input className={inputClass} value={headline} onChange={(e) => setHeadline(e.target.value)} placeholder="e.g. Staff Engineer" />
            ) : (
              candidate.headline || <span className="text-slate-400">Not set</span>
            )}
          </Field>
          <Field icon={<MapPin size={14} />} label="Location">
            {editing ? <input className={inputClass} value={location} onChange={(e) => setLocation(e.target.value)} /> : candidate.location}
          </Field>
          <Field icon={<Mail size={14} />} label="Email">
            <span className="text-slate-500">{candidate.email}</span>
          </Field>
          <Field icon={<Phone size={14} />} label="Phone">
            {editing ? (
              <input className={inputClass} value={phone} onChange={(e) => setPhone(e.target.value)} />
            ) : (
              candidate.phone || <span className="text-slate-400">Not set</span>
            )}
          </Field>
          <Field icon={<Link2 size={14} />} label="LinkedIn">
            {editing ? (
              <input className={inputClass} value={linkedinUrl} onChange={(e) => setLinkedinUrl(e.target.value)} placeholder="linkedin.com/in/..." />
            ) : candidate.linkedinUrl ? (
              <a href={candidate.linkedinUrl} target="_blank" rel="noreferrer" className="text-blue-600 hover:underline">
                {candidate.linkedinUrl}
              </a>
            ) : (
              <span className="text-slate-400">Not set</span>
            )}
          </Field>
          <Field icon={<Globe size={14} />} label="Portfolio / Website">
            {editing ? (
              <input className={inputClass} value={portfolioUrl} onChange={(e) => setPortfolioUrl(e.target.value)} placeholder="https://..." />
            ) : candidate.portfolioUrl ? (
              <a href={candidate.portfolioUrl} target="_blank" rel="noreferrer" className="text-blue-600 hover:underline">
                {candidate.portfolioUrl}
              </a>
            ) : (
              <span className="text-slate-400">Not set</span>
            )}
          </Field>
          <Field icon={<ShieldCheck size={14} />} label="Work Authorization">
            {editing ? (
              <input
                className={inputClass}
                value={workAuthorizations}
                onChange={(e) => setWorkAuthorizations(e.target.value)}
                placeholder="US, EU, UK"
              />
            ) : candidate.workAuthorizations.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {candidate.workAuthorizations.map((w) => (
                  <Badge key={w} tone="blue">
                    {w}
                  </Badge>
                ))}
              </div>
            ) : (
              <span className="text-slate-400">Not specified</span>
            )}
        </Field>
      </div>
    </Card>
  )
}

function ProfessionalSummaryCard({
  candidate,
  yearsExperience,
  onSaved,
}: {
  candidate: Candidate
  yearsExperience: number
  onSaved: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [summary, setSummary] = useState(candidate.professionalSummary ?? '')
  const [saving, setSaving] = useState(false)

  const startEdit = () => {
    setSummary(candidate.professionalSummary ?? '')
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, { professionalSummary: summary })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  const tags = [
    yearsExperience > 0 ? `${yearsExperience}+ years experience` : null,
    candidate.openToRelocation ? 'Open to relocation' : null,
    candidate.employmentTypes.length > 0 ? `Looking for ${candidate.employmentTypes.join(', ').toLowerCase()} opportunities` : null,
  ].filter((t): t is string => Boolean(t))

  return (
    <Card>
      <CardHeader
        title="Professional Summary"
        action={<EditAction editing={editing} saving={saving} onEdit={startEdit} onCancel={() => setEditing(false)} onSave={save} />}
      />
      {editing ? (
        <textarea
          className={inputClass}
          rows={4}
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
          placeholder="A few sentences about your experience and what you're looking for..."
        />
      ) : (
        <p className="text-sm text-slate-600">{candidate.professionalSummary || 'No summary yet - click Edit to add one.'}</p>
      )}
      {tags.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {tags.map((t) => (
            <Badge key={t} tone="slate">
              {t}
            </Badge>
          ))}
        </div>
      )}
    </Card>
  )
}

function CareerInterestsCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [roles, setRoles] = useState(candidate.preferredRoles.join(', '))
  const [industries, setIndustries] = useState(candidate.preferredIndustries.join(', '))
  const [locations, setLocations] = useState(candidate.preferredLocations.join(', '))
  const [employmentTypes, setEmploymentTypes] = useState(candidate.employmentTypes.join(', '))
  const [saving, setSaving] = useState(false)

  const startEdit = () => {
    setRoles(candidate.preferredRoles.join(', '))
    setIndustries(candidate.preferredIndustries.join(', '))
    setLocations(candidate.preferredLocations.join(', '))
    setEmploymentTypes(candidate.employmentTypes.join(', '))
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, {
        preferredRoles: csvToList(roles),
        preferredIndustries: csvToList(industries),
        preferredLocations: csvToList(locations),
        employmentTypes: csvToList(employmentTypes),
      })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  const columns: { icon: ReactNode; bg: string; label: string; value: string[]; text: string; onChange: (v: string) => void }[] = [
    { icon: <Briefcase size={16} className="text-emerald-600" />, bg: 'bg-emerald-50', label: 'Preferred Roles', value: candidate.preferredRoles, text: roles, onChange: setRoles },
    { icon: <Building2 size={16} className="text-violet-600" />, bg: 'bg-violet-50', label: 'Preferred Industries', value: candidate.preferredIndustries, text: industries, onChange: setIndustries },
    { icon: <MapPin size={16} className="text-blue-600" />, bg: 'bg-blue-50', label: 'Preferred Locations', value: candidate.preferredLocations, text: locations, onChange: setLocations },
    { icon: <BadgeCheck size={16} className="text-amber-600" />, bg: 'bg-amber-50', label: 'Employment Type', value: candidate.employmentTypes, text: employmentTypes, onChange: setEmploymentTypes },
  ]

  return (
    <Card>
      <CardHeader
        title="Career Interests"
        subtitle="Help us find the right opportunities for you."
        action={<EditAction editing={editing} saving={saving} onEdit={startEdit} onCancel={() => setEditing(false)} onSave={save} />}
      />
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {columns.map(({ icon, bg, label, value, text, onChange }) => (
          <div key={label}>
            <div className={`mb-1.5 flex h-9 w-9 items-center justify-center rounded-full ${bg}`}>{icon}</div>
            <p className="text-sm font-medium text-slate-700">{label}</p>
            {editing ? (
              <input className={`${inputClass} mt-1`} value={text} onChange={(e) => onChange(e.target.value)} placeholder="Comma-separated" />
            ) : value.length > 0 ? (
              <p className="mt-0.5 text-sm text-slate-500">{value.join(', ')}</p>
            ) : (
              <p className="mt-0.5 text-sm text-slate-400">Not specified</p>
            )}
          </div>
        ))}
      </div>
    </Card>
  )
}

function ResumeCard({
  candidateId,
  meta,
  checked,
  onChanged,
}: {
  candidateId: string
  meta: ResumeMeta | null
  checked: boolean
  onChanged: () => void
}) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleFile = async (file: File | undefined) => {
    if (!file) return
    setUploading(true)
    setError(null)
    try {
      await api.candidates.uploadResume(candidateId, file)
      onChanged()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  const remove = async () => {
    setUploading(true)
    try {
      await api.candidates.deleteResume(candidateId)
      onChanged()
    } finally {
      setUploading(false)
    }
  }

  return (
    <div>
      {!checked ? (
        <p className="text-sm text-slate-400">Loading&hellip;</p>
      ) : meta ? (
        <div className="space-y-3 rounded-lg border border-slate-100 p-3">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
              <FileText size={16} />
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{meta.filename}</p>
              <p className="text-xs text-slate-400">
                Updated {new Date(meta.uploadedAt).toLocaleDateString()} &middot; {formatBytes(meta.sizeBytes)}
              </p>
            </div>
          </div>
          <div className="flex gap-1.5">
            <a className="flex-1" href={api.candidates.resumePreviewUrl(candidateId)} target="_blank" rel="noreferrer">
              <Button variant="primary" icon={<Eye size={14} />} className="w-full">
                Preview
              </Button>
            </a>
            <label className="flex-1 cursor-pointer">
              <span className="flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                <UploadCloud size={14} />
                Replace
              </span>
              <input type="file" accept=".pdf,.doc,.docx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0])} disabled={uploading} />
            </label>
            <a href={api.candidates.resumeDownloadUrl(candidateId)} target="_blank" rel="noreferrer">
              <Button variant="secondary" icon={<Download size={14} />} />
            </a>
            <Button variant="danger" icon={<Trash2 size={14} />} onClick={remove} disabled={uploading} />
          </div>
        </div>
      ) : (
        <label className="flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed border-slate-200 p-6 text-center hover:border-blue-300">
          <UploadCloud className="text-slate-400" size={24} />
          <span className="text-sm text-slate-500">
            {uploading ? 'Uploading...' : 'No resume on file - click to upload (PDF or Word, up to 10MB)'}
          </span>
          <input type="file" accept=".pdf,.doc,.docx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0])} disabled={uploading} />
        </label>
      )}
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
    </div>
  )
}

function TopSkillsCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [newSkill, setNewSkill] = useState('')
  const [saving, setSaving] = useState(false)

  const addSkill = async () => {
    const skill = newSkill.trim()
    if (!skill) return
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, { rawSkillMentions: [...candidate.rawSkillMentions, skill] })
      setNewSkill('')
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  const removeSkill = async (skill: string) => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, { rawSkillMentions: candidate.rawSkillMentions.filter((s) => s !== skill) })
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <CardHeader
        title="Top Skills"
        action={
          <Button variant="secondary" icon={<Pencil size={14} />} onClick={() => setEditing((v) => !v)}>
            {editing ? 'Done' : 'Edit'}
          </Button>
        }
      />
      <div className="flex flex-wrap gap-1.5">
        {candidate.rawSkillMentions.map((mention) => (
          <Badge key={mention} tone={mentionMatchesSkillIds(mention, candidate.skillIds) ? 'blue' : 'slate'}>
            {mention}
            {editing && (
              <button onClick={() => removeSkill(mention)} className="ml-1 text-slate-400 hover:text-red-600" disabled={saving}>
                &times;
              </button>
            )}
          </Badge>
        ))}
        {candidate.rawSkillMentions.length === 0 && <p className="text-sm text-slate-400">No skills on file.</p>}
      </div>
      {editing && (
        <div className="mt-3 flex gap-2">
          <input
            className={inputClass}
            value={newSkill}
            onChange={(e) => setNewSkill(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addSkill()}
            placeholder="Add a skill"
          />
          <Button onClick={addSkill} disabled={saving}>
            Add
          </Button>
        </div>
      )}
    </Card>
  )
}

function JobPreferencesCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [locations, setLocations] = useState(candidate.preferredLocations.join(', '))
  const [compMin, setCompMin] = useState(candidate.expectedCompMinMinorUnits != null ? String(fromMinorUnits(candidate.expectedCompMinMinorUnits, candidate.preferredCurrency || DEFAULT_CURRENCY)) : '')
  const [compMax, setCompMax] = useState(candidate.expectedCompMaxMinorUnits != null ? String(fromMinorUnits(candidate.expectedCompMaxMinorUnits, candidate.preferredCurrency || DEFAULT_CURRENCY)) : '')
  const [currency, setCurrency] = useState(candidate.preferredCurrency || DEFAULT_CURRENCY)
  const [workMode, setWorkMode] = useState(candidate.workMode ?? '')
  const [noticePeriod, setNoticePeriod] = useState(candidate.noticePeriod ?? '')
  const [openToRelocation, setOpenToRelocation] = useState(candidate.openToRelocation)
  const [saving, setSaving] = useState(false)

  const startEdit = () => {
    setLocations(candidate.preferredLocations.join(', '))
    setCompMin(candidate.expectedCompMinMinorUnits != null ? String(fromMinorUnits(candidate.expectedCompMinMinorUnits, candidate.preferredCurrency || DEFAULT_CURRENCY)) : '')
    setCompMax(candidate.expectedCompMaxMinorUnits != null ? String(fromMinorUnits(candidate.expectedCompMaxMinorUnits, candidate.preferredCurrency || DEFAULT_CURRENCY)) : '')
    setCurrency(candidate.preferredCurrency || DEFAULT_CURRENCY)
    setWorkMode(candidate.workMode ?? '')
    setNoticePeriod(candidate.noticePeriod ?? '')
    setOpenToRelocation(candidate.openToRelocation)
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, {
        preferredLocations: csvToList(locations),
        expectedCompMinMinorUnits: compMin ? toMinorUnits(parseFloat(compMin), currency) : undefined,
        expectedCompMaxMinorUnits: compMax ? toMinorUnits(parseFloat(compMax), currency) : undefined,
        preferredCurrency: currency,
        workMode: workMode || undefined,
        noticePeriod: noticePeriod || undefined,
        openToRelocation,
      })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  const rows: { icon: ReactNode; label: string; value: string }[] = [
    { icon: <MapPin size={14} />, label: 'Location Preference', value: candidate.preferredLocations.join(', ') || 'Not specified' },
    {
      icon: <DollarSign size={14} />,
      label: 'Expected CTC',
      value: formatCompRange(candidate.expectedCompMinMinorUnits, candidate.expectedCompMaxMinorUnits, candidate.preferredCurrency || DEFAULT_CURRENCY, { compact: false }),
    },
    { icon: <Building2 size={14} />, label: 'Work Mode', value: candidate.workMode || 'Not specified' },
    { icon: <Clock size={14} />, label: 'Notice Period', value: candidate.noticePeriod || 'Not specified' },
    { icon: <Briefcase size={14} />, label: 'Relocation', value: candidate.openToRelocation ? 'Open to relocation' : 'Not open to relocation' },
  ]

  return (
    <Card>
      <CardHeader
        title="Job Preferences"
        action={<EditAction editing={editing} saving={saving} onEdit={startEdit} onCancel={() => setEditing(false)} onSave={save} />}
      />
      {editing ? (
        <div className="space-y-3">
          <LabeledInput label="Location preference (comma-separated)" value={locations} onChange={setLocations} />
          <label className="block">
            <span className="text-xs font-medium text-slate-500">Currency</span>
            <select value={currency} onChange={(e) => setCurrency(e.target.value)} className={`${inputClass} mt-1`}>
              {currencyOptions(currency).map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label}
                </option>
              ))}
            </select>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <LabeledInput label={`Expected CTC min (${currency})`} value={compMin} onChange={setCompMin} placeholder="e.g. 5700000" />
            <LabeledInput label={`Expected CTC max (${currency})`} value={compMax} onChange={setCompMax} placeholder="e.g. 6000000" />
          </div>
          <LabeledInput label="Work mode" value={workMode} onChange={setWorkMode} placeholder="Remote / Hybrid / Onsite / Flexible" />
          <LabeledInput label="Notice period" value={noticePeriod} onChange={setNoticePeriod} placeholder="e.g. Immediately / Short notice" />
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={openToRelocation} onChange={(e) => setOpenToRelocation(e.target.checked)} />
            Open to relocation
          </label>
        </div>
      ) : (
        <div className="space-y-3">
          {rows.map((r) => (
            <div key={r.label} className="flex items-start gap-2.5 text-sm">
              <span className="mt-0.5 text-slate-400">{r.icon}</span>
              <div>
                <p className="text-xs text-slate-400">{r.label}</p>
                <p className="text-slate-700">{r.value}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}

function PrivacyCard({ candidate }: { candidate: Candidate }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const exportData = async () => {
    setBusy(true)
    setMessage(null)
    try {
      const data = await api.candidates.export(candidate.id)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${candidate.fullName.replace(/\s+/g, '_')}_export.json`
      a.click()
      URL.revokeObjectURL(url)
    } finally {
      setBusy(false)
    }
  }

  const eraseData = async () => {
    if (!confirm(`Permanently erase all data for ${candidate.fullName}? This cannot be undone.`)) return
    setBusy(true)
    setMessage(null)
    try {
      await api.candidates.erase(candidate.id)
      setMessage(`${candidate.fullName} erased. Switch candidates from the top bar to see the change.`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader
        title="Privacy & Data"
        subtitle="Your data-subject rights over this profile (docs/02 §4.1) - the same export/erase endpoints as the Compliance page, scoped to your own record."
      />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
            <Download size={16} />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">Export my data</p>
            <p className="mt-0.5 text-sm text-slate-500">Download every record on file for this profile as JSON.</p>
            <Button className="mt-2" variant="secondary" onClick={exportData} disabled={busy}>
              Export
            </Button>
          </div>
        </div>
        <div className="flex items-start gap-3 rounded-lg border border-slate-100 p-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-600">
            <Trash2 size={16} />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">Delete my data</p>
            <p className="mt-0.5 text-sm text-slate-500">Permanently erase this profile and every record tied to it.</p>
            <Button className="mt-2" variant="danger" onClick={eraseData} disabled={busy}>
              Delete
            </Button>
          </div>
        </div>
      </div>
      <div className="mt-4 flex items-start gap-2 rounded-lg border border-slate-100 p-3 text-sm text-slate-500">
        <Lock size={14} className="mt-0.5 shrink-0 text-slate-400" />
        Decision-ledger entries about this profile (screening, scoring, review actions) are kept after erasure for
        audit integrity - they reference this profile only by an opaque id, never by name or contact details.
      </div>
      {message && <p className="mt-3 text-xs text-emerald-600">{message}</p>}
    </Card>
  )
}

function ConnectedAccountsCard({ candidateId }: { candidateId: string }) {
  const [accounts, setAccounts] = useState<ConnectedAccount[]>(() => loadConnectedAccounts('career-vault', candidateId, CAREER_VAULT_DEFAULTS))
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [iconId, setIconId] = useState<string | null>(null)

  useEffect(() => setAccounts(loadConnectedAccounts('career-vault', candidateId, CAREER_VAULT_DEFAULTS)), [candidateId])

  const startAdd = () => {
    setName('')
    setIconId(null)
    setAdding(true)
  }

  const confirmAdd = () => {
    if (!name.trim() || !iconId) return
    setAccounts(addConnectedAccount('career-vault', candidateId, CAREER_VAULT_DEFAULTS, { name: name.trim(), iconId }))
    setAdding(false)
  }

  const remove = (id: string) => setAccounts(removeConnectedAccount('career-vault', candidateId, CAREER_VAULT_DEFAULTS, id))

  return (
    <Card>
      <CardHeader
        title="Connected Accounts"
        subtitle="Not available in this build - there's no OAuth connector for any of these yet, custom entries just remember your icon choice in this browser."
        action={
          <Button variant="secondary" icon={<Plus size={14} />} onClick={startAdd}>
            Connect More
          </Button>
        }
      />
      {adding && (
        <div className="mb-4 space-y-3 rounded-lg border border-slate-200 p-3">
          <label className="block">
            <span className="text-xs font-medium text-slate-500">Service or app name</span>
            <input
              className={`${inputClass} mt-1`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Notion, Zoom, or your own tool"
            />
          </label>
          <IconPicker value={iconId} onChange={setIconId} />
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setAdding(false)}>
              Cancel
            </Button>
            <Button onClick={confirmAdd} disabled={!name.trim() || !iconId}>
              Add
            </Button>
          </div>
        </div>
      )}
      <div className="space-y-2">
        {accounts.map((account) => (
          <div key={account.id} className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-sm">
            <span className="flex items-center gap-2.5 text-slate-700">
              <BrandIcon iconId={account.iconId} />
              {account.name}
            </span>
            <div className="flex items-center gap-2">
              <Badge tone="slate">Not connected</Badge>
              {!account.builtin && (
                <button onClick={() => remove(account.id)} className="text-slate-400 hover:text-red-600" aria-label={`Remove ${account.name}`}>
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </Card>
  )
}
