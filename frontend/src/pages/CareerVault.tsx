import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Award,
  BadgeCheck,
  Briefcase,
  ChevronDown,
  FileText,
  GraduationCap,
  Layers,
  Plus,
  ShieldCheck,
  Trophy,
} from 'lucide-react'
import { api } from '../api/client'
import type { Achievement, CandidateExperience, CareerVaultView, Certification, Education } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { ProgressBar, StatCard } from '../components/ui/StatCard'
import { Button } from '../components/ui/Button'
import { Tabs } from '../components/ui/Tabs'
import { CompanyAvatar } from '../components/ui/CompanyAvatar'
import { mentionMatchesSkillIds } from '../lib/format'

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'skills', label: 'Skills' },
  { key: 'experience', label: 'Experience' },
  { key: 'achievements', label: 'Achievements' },
  { key: 'education', label: 'Education' },
  { key: 'certifications', label: 'Certifications' },
  { key: 'preferences', label: 'Preferences' },
]

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
  kind: 'achievement' | 'education' | 'certification'
  title: string
  subtitle: string | null
  tags: string[]
  date: string | null
}

function buildEvidenceTimeline(achievements: Achievement[], education: Education[], certifications: Certification[]): EvidenceItem[] {
  const items: EvidenceItem[] = [
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

export function CareerVault() {
  const { selected } = useCandidate()
  const [vault, setVault] = useState<CareerVaultView | null>(null)
  const [tab, setTab] = useState('overview')
  const [showAchievementForm, setShowAchievementForm] = useState(false)
  const [showEducationForm, setShowEducationForm] = useState(false)
  const [showCertForm, setShowCertForm] = useState(false)

  const load = () => {
    if (!selected) return
    api.vault.get(selected.id).then(setVault)
  }

  useEffect(load, [selected])

  const yearsExperience = useMemo(() => (vault ? estimateYearsExperience(vault.experiences) : 0), [vault])
  const companyCount = useMemo(
    () => (vault ? new Set(vault.experiences.map((e) => e.employer.trim().toLowerCase()).filter(Boolean)).size : 0),
    [vault],
  )
  const evidenceTimeline = useMemo(
    () => (vault ? buildEvidenceTimeline(vault.achievements, vault.education, vault.certifications) : []),
    [vault],
  )

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    )
  }

  if (!vault) {
    return (
      <Card>
        <p className="text-sm text-slate-400">Loading&hellip;</p>
      </Card>
    )
  }

  const isVerified = vault.completeness >= 0.8

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Career Vault</h1>
          <p className="mt-1 text-sm text-slate-500">
            Your verified professional record - every tailored artifact is grounded against exactly these records
            (docs/01 §2, docs/02 §1.4 C-8).
          </p>
        </div>
        <Link to="/career-vault/resume">
          <Button variant="secondary" icon={<FileText size={14} />}>
            Preview / Print Resume
          </Button>
        </Link>
      </div>

      <Card className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-4">
          <CompanyAvatar name={vault.candidate.fullName} size={56} />
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{vault.candidate.fullName}</h2>
              {isVerified && <Badge tone="green">Verified</Badge>}
            </div>
            <p className="text-sm text-slate-500">{vault.candidate.location || 'Location not set'}</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatCard icon={<Briefcase size={16} />} iconTone="blue" value={`${yearsExperience}+`} label="Years Experience" />
          <StatCard icon={<Layers size={16} />} iconTone="purple" value={vault.candidate.skillIds.length} label="Core Skills" />
          <StatCard icon={<BadgeCheck size={16} />} iconTone="green" value={companyCount} label="Companies" />
          <StatCard icon={<Trophy size={16} />} iconTone="amber" value={vault.achievements.length} label="Key Achievements" />
        </div>
      </Card>

      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="space-y-6">
          <Card>
            <CardHeader
              title="Profile Completeness"
              action={<span className="text-2xl font-bold text-slate-900">{Math.round(vault.completeness * 100)}%</span>}
            />
            <ProgressBar value={vault.completeness} tone="green" />
            <p className="mt-2 text-xs text-slate-400">
              Based on skills, experience, achievements, education, and certifications on file.
            </p>
          </Card>

          <Card>
            <CardHeader title="Recent Evidence" subtitle="Your latest verified records, newest first" />
            <div className="space-y-2">
              {evidenceTimeline.slice(0, 6).map((item) => (
                <EvidenceCard key={item.id} item={item} />
              ))}
              {evidenceTimeline.length === 0 && <p className="text-sm text-slate-400">No evidence on file yet.</p>}
            </div>
          </Card>
        </div>
      )}

      {tab === 'skills' && (
        <Card>
          <CardHeader title="Skills" subtitle={`${vault.candidate.rawSkillMentions.length} mention(s) on file`} />
          <div className="flex flex-wrap gap-1.5">
            {vault.candidate.rawSkillMentions.map((mention) => (
              <Badge key={mention} tone={mentionMatchesSkillIds(mention, vault.candidate.skillIds) ? 'blue' : 'slate'}>
                {mention}
              </Badge>
            ))}
            {vault.candidate.rawSkillMentions.length === 0 && <p className="text-sm text-slate-400">No skills on file.</p>}
          </div>
          {vault.candidate.rawSkillMentions.length > 0 && (
            <p className="mt-3 text-xs text-slate-400">
              <span className="mr-1 inline-block h-2 w-2 rounded-full bg-blue-500 align-middle" /> Verified against our
              skills taxonomy and used in match scoring &middot;{' '}
              <span className="mr-1 inline-block h-2 w-2 rounded-full bg-slate-300 align-middle" /> self-reported only
            </p>
          )}
        </Card>
      )}

      {tab === 'experience' && (
        <Card>
          <CardHeader title="Experience Timeline" subtitle={`${vault.experiences.length} role(s) - click to expand`} />
          {vault.experiences.length === 0 ? (
            <p className="text-sm text-slate-400">No experience on file.</p>
          ) : (
            <ol className="space-y-4 border-l-2 border-slate-200 pl-5">
              {vault.experiences.map((exp) => (
                <ExperienceTimelineRow key={exp.id} exp={exp} />
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

      {tab === 'preferences' && (
        <Card>
          <CardHeader
            title="Preferences"
            subtitle="Work eligibility and compensation on file - there's no separate preferences record yet, so this reflects your profile fields."
          />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-slate-100 p-3">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
                <ShieldCheck size={15} className="text-slate-400" />
                Work Authorization
              </div>
              <div className="flex flex-wrap gap-1.5">
                {vault.candidate.workAuthorizations.length > 0 ? (
                  vault.candidate.workAuthorizations.map((w) => (
                    <Badge key={w} tone="blue">
                      {w}
                    </Badge>
                  ))
                ) : (
                  <p className="text-sm text-slate-400">Not specified.</p>
                )}
              </div>
            </div>
            <div className="rounded-lg border border-slate-100 p-3">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-700">
                <Layers size={15} className="text-slate-400" />
                Minimum Compensation
              </div>
              <p className="text-sm text-slate-600">
                ${(vault.candidate.compFloorMinorUnits / 100).toLocaleString()} annually
              </p>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}

function ExperienceTimelineRow({ exp }: { exp: CandidateExperience }) {
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
