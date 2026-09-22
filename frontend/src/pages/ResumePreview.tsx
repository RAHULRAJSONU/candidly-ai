import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, Printer } from 'lucide-react'
import { api } from '../api/client'
import type { CareerVaultView } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'

type Template = 'modern' | 'minimal' | 'executive' | 'technical'

const TEMPLATES: { key: Template; label: string }[] = [
  { key: 'modern', label: 'Modern' },
  { key: 'minimal', label: 'Minimal' },
  { key: 'executive', label: 'Executive' },
  { key: 'technical', label: 'Technical' },
]

/** Purely a visual layout choice over the same real content below - never changes what
 * data is shown, only how. Persisted to localStorage as a per-viewer convenience only
 * (not read back by Claude, not shared between viewers). */
const TEMPLATE_STORAGE_KEY = 'candidly.resumeTemplate'

function loadTemplate(): Template {
  try {
    const stored = localStorage.getItem(TEMPLATE_STORAGE_KEY)
    return (TEMPLATES.some((t) => t.key === stored) ? stored : 'modern') as Template
  } catch {
    return 'modern'
  }
}

const TEMPLATE_CLASSES: Record<Template, { header: string; name: string; section: string; accent: string }> = {
  modern: {
    header: 'border-b border-slate-200 pb-4',
    name: 'text-2xl font-bold text-slate-900',
    section: 'text-xs font-semibold uppercase tracking-wide text-slate-400',
    accent: 'text-blue-600',
  },
  minimal: {
    header: 'pb-3',
    name: 'text-xl font-medium text-slate-900',
    section: 'text-xs font-medium text-slate-400',
    accent: 'text-slate-700',
  },
  executive: {
    header: 'border-b-2 border-slate-900 pb-4 text-center',
    name: 'text-3xl font-serif font-bold uppercase tracking-wide text-slate-900',
    section: 'text-xs font-bold uppercase tracking-widest text-slate-600',
    accent: 'text-slate-900',
  },
  technical: {
    header: 'border-b border-dashed border-slate-300 pb-4 font-mono',
    name: 'text-xl font-bold text-slate-900 font-mono',
    section: 'text-xs font-semibold uppercase tracking-wide text-emerald-700 font-mono',
    accent: 'text-emerald-700',
  },
}

/** Deterministic profile -> resume rendering (the reverse of profileimport's resume ->
 * profile extraction), "similar to LinkedIn"'s Save-to-PDF: reformats already-verified
 * Career Vault data into a resume layout, nothing invented or model-generated. Printing
 * uses the browser's native print-to-PDF (Tailwind's `print:` variant hides the app
 * chrome - see Sidebar/TopBar/AppShell) rather than a server-side PDF library. */
export function ResumePreview() {
  const { selected } = useCandidate()
  const [vault, setVault] = useState<CareerVaultView | null>(null)
  const [template, setTemplate] = useState<Template>(loadTemplate)

  useEffect(() => {
    try {
      localStorage.setItem(TEMPLATE_STORAGE_KEY, template)
    } catch {
      // best-effort only
    }
  }, [template])

  useEffect(() => {
    if (!selected) return
    api.vault.get(selected.id).then(setVault)
  }, [selected])

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

  const { candidate, experiences, achievements, education, certifications } = vault
  const sortedExperiences = experiences
    .slice()
    .sort((a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''))
  const t = TEMPLATE_CLASSES[template]

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link to="/career-vault" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700">
          <ArrowLeft size={14} />
          Back to Career Vault
        </Link>
        <div className="flex items-center gap-2">
          <div className="flex gap-1 rounded-lg border border-slate-200 p-1">
            {TEMPLATES.map((tpl) => (
              <button
                key={tpl.key}
                onClick={() => setTemplate(tpl.key)}
                className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                  template === tpl.key ? 'bg-blue-600 text-white' : 'text-slate-500 hover:bg-slate-100'
                }`}
              >
                {tpl.label}
              </button>
            ))}
          </div>
          <Button icon={<Printer size={14} />} onClick={() => window.print()}>
            Print / Save as PDF
          </Button>
        </div>
      </div>

      <Card className="print:rounded-none print:border-none print:p-0 print:shadow-none">
        <header className={t.header}>
          <h1 className={t.name}>{candidate.fullName}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {candidate.location} &middot; {candidate.email}
          </p>
        </header>

        {candidate.rawSkillMentions.length > 0 && (
          <section className="mt-5">
            <h2 className={t.section}>Skills</h2>
            <p className="mt-1.5 text-sm text-slate-700">{candidate.rawSkillMentions.join(', ')}</p>
          </section>
        )}

        {sortedExperiences.length > 0 && (
          <section className="mt-5">
            <h2 className={t.section}>Experience</h2>
            <div className="mt-2 space-y-4">
              {sortedExperiences.map((exp) => (
                <div key={exp.id} className="break-inside-avoid">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="text-sm font-semibold text-slate-900">
                      {exp.title} &middot; {exp.employer}
                    </p>
                    <p className="shrink-0 text-xs text-slate-400">
                      {exp.startDate} &ndash; {exp.endDate ?? 'Present'}
                    </p>
                  </div>
                  {exp.narrative && <p className="mt-1 text-sm text-slate-600">{exp.narrative}</p>}
                  {exp.verifiedMetrics.length > 0 && (
                    <ul className="mt-1 list-disc space-y-0.5 pl-4 text-sm text-slate-600">
                      {exp.verifiedMetrics.map((m) => (
                        <li key={m}>{m}</li>
                      ))}
                    </ul>
                  )}
                  {exp.rawSkillMentions.length > 0 && (
                    <p className="mt-1 text-xs text-slate-400">{exp.rawSkillMentions.join(', ')}</p>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

        {education.length > 0 && (
          <section className="mt-5">
            <h2 className={t.section}>Education</h2>
            <div className="mt-2 space-y-2">
              {education.map((e) => (
                <div key={e.id}>
                  <p className="text-sm font-medium text-slate-900">
                    {e.degree}
                    {e.fieldOfStudy ? `, ${e.fieldOfStudy}` : ''}
                  </p>
                  <p className="text-sm text-slate-500">
                    {e.institution}
                    {e.startDate ? ` · ${e.startDate} – ${e.endDate ?? 'Present'}` : ''}
                  </p>
                </div>
              ))}
            </div>
          </section>
        )}

        {certifications.length > 0 && (
          <section className="mt-5">
            <h2 className={t.section}>Certifications</h2>
            <div className="mt-2 space-y-1">
              {certifications.map((c) => (
                <p key={c.id} className="text-sm text-slate-700">
                  {c.name} <span className="text-slate-400">&middot; {c.issuer}</span>
                </p>
              ))}
            </div>
          </section>
        )}

        {achievements.length > 0 && (
          <section className="mt-5">
            <h2 className={t.section}>Achievements</h2>
            <ul className="mt-2 list-disc space-y-1 pl-4">
              {achievements.map((a) => (
                <li key={a.id} className="text-sm text-slate-700">
                  <span className="font-medium">{a.title}</span>
                  {a.description ? ` – ${a.description}` : ''}
                </li>
              ))}
            </ul>
          </section>
        )}
      </Card>
    </div>
  )
}
