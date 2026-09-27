import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertTriangle,
  Bell,
  Bot,
  Camera,
  Crown,
  Lock,
  Moon,
  Plus,
  Shield,
  ShieldCheck,
  Sun,
  Trash2,
  Monitor as MonitorIcon,
} from 'lucide-react'
import { api, ApiError } from '../api/client'
import type { Candidate, CandidateSettings, CandidateSettingsUpdate } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Tabs } from '../components/ui/Tabs'
import { Button } from '../components/ui/Button'
import { Toggle } from '../components/ui/Toggle'
import { BrandIcon } from '../components/ui/BrandIcon'
import { IconPicker } from '../components/ui/IconPicker'
import { SkillChipInput } from '../components/ui/SkillChipInput'
import { CandidateAvatar } from '../components/ui/CandidateAvatar'
import { PhotoCropModal } from '../components/ui/PhotoCropModal'
import { ErrorBanner, describeError } from '../components/ui/ErrorBanner'
import type { ConnectedAccount } from '../lib/connectedAccounts'
import { loadConnectedAccounts, addConnectedAccount, removeConnectedAccount } from '../lib/connectedAccounts'
import { currencyOptions, fromMinorUnits, toMinorUnits, DEFAULT_CURRENCY } from '../lib/currency'

type Tab = 'account' | 'job-preferences' | 'ai-preferences' | 'notifications' | 'integrations' | 'privacy' | 'appearance' | 'billing'

const inputClass = 'w-full rounded-lg border border-slate-200 px-3 py-1.5 text-sm focus:border-blue-400 focus:outline-none'

const SETTINGS_INTEGRATION_DEFAULTS: ConnectedAccount[] = [
  { id: 'linkedin', iconId: 'linkedin', name: 'LinkedIn', builtin: true },
  { id: 'google-calendar', iconId: 'google', name: 'Google', builtin: true },
  { id: 'outlook', iconId: 'outlook', name: 'Outlook', builtin: true },
  { id: 'github', iconId: 'github', name: 'GitHub', builtin: true },
  { id: 'notion', iconId: 'notion', name: 'Notion', builtin: true },
]

const NOTICE_PERIOD_OPTIONS = ['Immediate', '0 - 15 days', '15 - 30 days', '30 - 60 days', '60 - 90 days', 'More than 90 days']
const EMPLOYMENT_TYPE_OPTIONS: { key: string; label: string }[] = [
  { key: 'FULL_TIME', label: 'Full-time' },
  { key: 'REMOTE', label: 'Remote' },
  { key: 'HYBRID', label: 'Hybrid' },
  { key: 'CONTRACT', label: 'Contract' },
]
const AI_MODEL_LABELS: Record<string, string> = {
  'openai/gpt-oss-120b': 'GPT-OSS-120B (Groq) — Recommended',
}

export function Settings() {
  const { selected, refresh } = useCandidate()
  const [tab, setTab] = useState<Tab>('account')
  const [settings, setSettings] = useState<CandidateSettings | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const candidateId = selected?.id ?? null

  useEffect(() => {
    if (!candidateId) {
      setSettings(null)
      return
    }
    setLoadError(null)
    api.settings.get(candidateId).then(setSettings).catch((e) => setLoadError(describeError(e)))
  }, [candidateId])

  useEffect(() => {
    if (!settings) return
    document.documentElement.setAttribute('data-theme', settings.theme === 'SYSTEM' ? resolveSystemTheme() : settings.theme.toLowerCase())
  }, [settings?.theme])

  const patchSettings = async (patch: CandidateSettingsUpdate) => {
    if (!candidateId) return
    const updated = await api.settings.update(candidateId, patch)
    setSettings(updated)
  }

  if (!selected) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Settings</h1>
          <p className="mt-1 text-sm text-slate-500">Select a candidate first to manage their settings.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {loadError && <ErrorBanner message={`Couldn't load settings: ${loadError}`} />}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Settings</h1>
        <p className="mt-1 text-sm text-slate-500">Personalize your experience, manage your preferences, and keep your account secure.</p>
      </div>

      <Tabs
        tabs={[
          { key: 'account', label: 'Account' },
          { key: 'job-preferences', label: 'Job Preferences' },
          { key: 'ai-preferences', label: 'AI Preferences' },
          { key: 'notifications', label: 'Notifications' },
          { key: 'integrations', label: 'Integrations' },
          { key: 'privacy', label: 'Privacy & Security' },
          { key: 'appearance', label: 'Appearance' },
          { key: 'billing', label: 'Billing' },
        ]}
        active={tab}
        onChange={(k) => setTab(k as Tab)}
      />

      {tab === 'account' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <ProfileSettingsCard candidate={selected} onSaved={refresh} />
          <JobPreferencesCard candidate={selected} onSaved={refresh} />
          <AiPreferencesCard settings={settings} onPatch={patchSettings} />
          <NotificationsCard settings={settings} onPatch={patchSettings} />
          <IntegrationsCard candidateId={selected.id} />
          <PrivacySecurityCard candidate={selected} />
          <AppearanceCard settings={settings} onPatch={patchSettings} />
          <BillingCard settings={settings} />
          <DangerZoneCard candidate={selected} className="lg:col-span-3" />
        </div>
      )}

      {tab === 'job-preferences' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <JobPreferencesCard candidate={selected} onSaved={refresh} />
        </div>
      )}
      {tab === 'ai-preferences' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <AiPreferencesCard settings={settings} onPatch={patchSettings} />
        </div>
      )}
      {tab === 'notifications' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <NotificationsCard settings={settings} onPatch={patchSettings} />
        </div>
      )}
      {tab === 'integrations' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <IntegrationsCard candidateId={selected.id} />
        </div>
      )}
      {tab === 'privacy' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <PrivacySecurityCard candidate={selected} />
          <DangerZoneCard candidate={selected} />
        </div>
      )}
      {tab === 'appearance' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <AppearanceCard settings={settings} onPatch={patchSettings} />
        </div>
      )}
      {tab === 'billing' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <BillingCard settings={settings} />
        </div>
      )}
    </div>
  )
}

function resolveSystemTheme(): 'light' | 'dark' {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

function SavedStatus({ state }: { state: 'idle' | 'saving' | 'saved' | 'error' }) {
  if (state === 'saving') return <span className="text-xs text-slate-400">Saving…</span>
  if (state === 'saved') return <span className="text-xs text-emerald-600">Saved</span>
  if (state === 'error') return <span className="text-xs text-red-600">Couldn't save</span>
  return null
}

function useSaveStatus() {
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const run = async (fn: () => Promise<unknown>) => {
    setStatus('saving')
    try {
      await fn()
      setStatus('saved')
    } catch {
      setStatus('error')
    } finally {
      if (timer.current) clearTimeout(timer.current)
      timer.current = setTimeout(() => setStatus('idle'), 2000)
    }
  }
  return { status, run }
}

// --- Profile Settings ---

function ProfileSettingsCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const { bumpPhotoVersion } = useCandidate()
  const [editing, setEditing] = useState(false)
  const [fullName, setFullName] = useState(candidate.fullName)
  const [phone, setPhone] = useState(candidate.phone ?? '')
  const [location, setLocation] = useState(candidate.location)
  const [saving, setSaving] = useState(false)
  const [pickedPhoto, setPickedPhoto] = useState<File | null>(null)
  const [uploadingPhoto, setUploadingPhoto] = useState(false)
  const photoInputRef = useRef<HTMLInputElement>(null)

  const startEdit = () => {
    setFullName(candidate.fullName)
    setPhone(candidate.phone ?? '')
    setLocation(candidate.location)
    setEditing(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      await api.candidates.update(candidate.id, { fullName, phone, location })
      setEditing(false)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  const savePhoto = async (blob: Blob) => {
    setUploadingPhoto(true)
    try {
      await api.candidates.uploadPhoto(candidate.id, blob)
      bumpPhotoVersion()
    } finally {
      setUploadingPhoto(false)
      setPickedPhoto(null)
    }
  }

  return (
    <Card>
      <CardHeader
        title="Profile Settings"
        subtitle="Manage your personal information and account details."
        action={
          editing ? (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setEditing(false)}>
                Cancel
              </Button>
              <Button onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </Button>
            </div>
          ) : (
            <Button variant="secondary" onClick={startEdit}>
              Edit Profile
            </Button>
          )
        }
      />
      <div className="flex items-start gap-4">
        <div className="relative shrink-0">
          <CandidateAvatar candidateId={candidate.id} name={candidate.fullName} size={72} />
          <button
            type="button"
            onClick={() => photoInputRef.current?.click()}
            disabled={uploadingPhoto}
            aria-label="Change photo"
            className="absolute -bottom-1 -right-1 flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-white shadow ring-2 ring-white hover:bg-blue-700 disabled:opacity-60"
          >
            <Camera size={11} />
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
        <div className="flex-1 space-y-3">
          <LabeledField label="Full Name">
            {editing ? <input className={inputClass} value={fullName} onChange={(e) => setFullName(e.target.value)} /> : (
              <p className="text-sm font-medium text-slate-800">{candidate.fullName}</p>
            )}
          </LabeledField>
          <LabeledField label="Email">
            <input className={`${inputClass} bg-slate-50 text-slate-500`} value={candidate.email} disabled title="Email can't be changed - it's this candidate's identity key." />
          </LabeledField>
          <LabeledField label="Phone">
            {editing ? <input className={inputClass} value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+1 555 0100" /> : (
              <p className="text-sm text-slate-700">{candidate.phone || 'Not set'}</p>
            )}
          </LabeledField>
          <LabeledField label="Location">
            {editing ? <input className={inputClass} value={location} onChange={(e) => setLocation(e.target.value)} /> : (
              <p className="text-sm text-slate-700">{candidate.location}</p>
            )}
          </LabeledField>
        </div>
      </div>
    </Card>
  )
}

function LabeledField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label className="text-xs font-medium text-slate-400">{label}</label>
      <div className="mt-1">{children}</div>
    </div>
  )
}

// --- Job Preferences ---

function JobPreferencesCard({ candidate, onSaved }: { candidate: Candidate; onSaved: () => void }) {
  const { status, run } = useSaveStatus()
  const [locations, setLocations] = useState(candidate.preferredLocations.join(', '))
  const [roles, setRoles] = useState(candidate.preferredRoles.join(', '))
  const currency = candidate.preferredCurrency || DEFAULT_CURRENCY
  const [ctcMin, setCtcMin] = useState(candidate.expectedCompMinMinorUnits ? String(fromMinorUnits(candidate.expectedCompMinMinorUnits, currency)) : '')
  const [ctcMax, setCtcMax] = useState(candidate.expectedCompMaxMinorUnits ? String(fromMinorUnits(candidate.expectedCompMaxMinorUnits, currency)) : '')
  const [noticePeriod, setNoticePeriod] = useState(candidate.noticePeriod ?? NOTICE_PERIOD_OPTIONS[1])

  const save = (patch: Parameters<typeof api.candidates.update>[1]) =>
    run(async () => {
      await api.candidates.update(candidate.id, patch)
      onSaved()
    })

  const toggleEmploymentType = (key: string) => {
    const current = new Set(candidate.employmentTypes)
    if (current.has(key)) current.delete(key)
    else current.add(key)
    save({ employmentTypes: Array.from(current) })
  }

  return (
    <Card>
      <CardHeader
        title="Job Preferences"
        subtitle="Set your career preferences to get better matches."
        action={<SavedStatus state={status} />}
      />
      <div className="space-y-4">
        <LabeledField label="Preferred Job Locations">
          <SkillChipInput
            value={locations}
            onChange={(v) => {
              setLocations(v)
              save({ preferredLocations: v.split(',').map((s) => s.trim()).filter(Boolean) })
            }}
            placeholder="Add a location and press Enter"
          />
        </LabeledField>

        <div>
          <label className="text-xs font-medium text-slate-400">Preferred Job Types</label>
          <div className="mt-1.5 flex flex-wrap gap-4">
            {EMPLOYMENT_TYPE_OPTIONS.map((opt) => (
              <label key={opt.key} className="flex items-center gap-1.5 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-400"
                  checked={candidate.employmentTypes.includes(opt.key)}
                  onChange={() => toggleEmploymentType(opt.key)}
                />
                {opt.label}
              </label>
            ))}
          </div>
        </div>

        <LabeledField label="Target Roles">
          <SkillChipInput
            value={roles}
            onChange={(v) => {
              setRoles(v)
              save({ preferredRoles: v.split(',').map((s) => s.trim()).filter(Boolean) })
            }}
            placeholder="Add a role and press Enter"
          />
        </LabeledField>

        <LabeledField label="Currency">
          <select
            className={inputClass}
            value={currency}
            onChange={(e) => save({ preferredCurrency: e.target.value })}
          >
            {currencyOptions(currency).map((c) => (
              <option key={c.code} value={c.code}>
                {c.label}
              </option>
            ))}
          </select>
        </LabeledField>

        <div className="grid grid-cols-2 gap-3">
          <LabeledField label={`Expected CTC (min, ${currency})`}>
            <input
              type="number"
              min={0}
              className={inputClass}
              value={ctcMin}
              onChange={(e) => setCtcMin(e.target.value)}
              onBlur={() => save({ expectedCompMinMinorUnits: ctcMin ? toMinorUnits(Number(ctcMin), currency) : undefined })}
              placeholder="e.g. 7500000"
            />
          </LabeledField>
          <LabeledField label={`Expected CTC (max, ${currency})`}>
            <input
              type="number"
              min={0}
              className={inputClass}
              value={ctcMax}
              onChange={(e) => setCtcMax(e.target.value)}
              onBlur={() => save({ expectedCompMaxMinorUnits: ctcMax ? toMinorUnits(Number(ctcMax), currency) : undefined })}
              placeholder="e.g. 8000000"
            />
          </LabeledField>
        </div>

        <LabeledField label="Notice Period">
          <select
            className={inputClass}
            value={noticePeriod}
            onChange={(e) => {
              setNoticePeriod(e.target.value)
              save({ noticePeriod: e.target.value })
            }}
          >
            {NOTICE_PERIOD_OPTIONS.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
        </LabeledField>
      </div>
    </Card>
  )
}

// --- AI Preferences ---

function AiPreferencesCard({
  settings,
  onPatch,
}: {
  settings: CandidateSettings | null
  onPatch: (patch: CandidateSettingsUpdate) => Promise<void>
}) {
  const { status, run } = useSaveStatus()
  if (!settings) {
    return (
      <Card>
        <CardHeader title="AI Preferences" subtitle="Customize how the AI helps you." action={<Bot className="text-slate-400" size={18} />} />
        <p className="text-sm text-slate-400">Loading…</p>
      </Card>
    )
  }

  const patch = (p: CandidateSettingsUpdate) => run(() => onPatch(p))

  return (
    <Card>
      <CardHeader
        title="AI Preferences"
        subtitle="Customize how the AI helps you."
        action={<SavedStatus state={status} />}
      />
      <div className="space-y-3">
        <LabeledField label="Preferred AI Model">
          <select className={inputClass} value={settings.preferredAiModel} onChange={(e) => patch({ preferredAiModel: e.target.value })}>
            {Object.entries(AI_MODEL_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-400">This deployment has one configured chat model (Groq) - there's no real per-candidate model routing yet.</p>
        </LabeledField>
        <LabeledField label="Response Style">
          <select
            className={inputClass}
            value={settings.responseStyle}
            onChange={(e) => patch({ responseStyle: e.target.value as CandidateSettings['responseStyle'] })}
          >
            <option value="CONCISE">Concise</option>
            <option value="BALANCED">Balanced</option>
            <option value="DETAILED">Detailed</option>
          </select>
        </LabeledField>
        <LabeledField label="Level of Detail">
          <select
            className={inputClass}
            value={settings.levelOfDetail}
            onChange={(e) => patch({ levelOfDetail: e.target.value as CandidateSettings['levelOfDetail'] })}
          >
            <option value="BRIEF">Brief</option>
            <option value="STANDARD">Standard</option>
            <option value="COMPREHENSIVE">Comprehensive</option>
          </select>
        </LabeledField>
        <p className="pt-1 text-xs text-slate-400">Response style and level of detail genuinely shape resume/cover-letter prompts below - they're not decorative.</p>

        <ToggleRow
          label="Personalized job recommendations"
          checked={settings.personalizedRecommendations}
          onChange={(v) => patch({ personalizedRecommendations: v })}
        />
        <ToggleRow label="AI resume tailoring" checked={settings.aiResumeTailoring} onChange={(v) => patch({ aiResumeTailoring: v })} />
        <ToggleRow
          label="AI cover letter generation"
          checked={settings.aiCoverLetterGeneration}
          onChange={(v) => patch({ aiCoverLetterGeneration: v })}
        />
        <ToggleRow label="AI interview preparation" checked={settings.aiInterviewPrep} onChange={(v) => patch({ aiInterviewPrep: v })} />
        <ToggleRow
          label="Use my data for model improvement"
          checked={settings.useDataForModelImprovement}
          onChange={(v) => patch({ useDataForModelImprovement: v })}
          hint="No training pipeline reads this yet - it's stored for when one exists."
        />
      </div>
    </Card>
  )
}

function ToggleRow({
  label,
  checked,
  onChange,
  hint,
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
  hint?: string
}) {
  return (
    <div className="flex items-center justify-between py-1">
      <div>
        <p className="text-sm text-slate-700">{label}</p>
        {hint && <p className="text-xs text-slate-400">{hint}</p>}
      </div>
      <Toggle checked={checked} onChange={onChange} label={label} />
    </div>
  )
}

// --- Notifications ---

const NOTIFICATION_ROWS: { key: keyof CandidateSettings; label: string; subtitle: string; icon: ReactNode }[] = [
  { key: 'notifyJobMatches', label: 'Job matches', subtitle: 'New job recommendations', icon: <Bell size={16} /> },
  { key: 'notifyApplicationUpdates', label: 'Application updates', subtitle: 'Status changes and recruiter responses', icon: <Bell size={16} /> },
  { key: 'notifyInterviewReminders', label: 'Interview reminders', subtitle: 'Upcoming interviews and preparation tips', icon: <Bell size={16} /> },
  { key: 'notifyWeeklyDigest', label: 'Weekly digest', subtitle: 'Insights, analytics and suggestions', icon: <Bell size={16} /> },
  { key: 'notifyProductUpdates', label: 'Product updates', subtitle: 'New features and announcements', icon: <Bell size={16} /> },
]

function NotificationsCard({
  settings,
  onPatch,
}: {
  settings: CandidateSettings | null
  onPatch: (patch: CandidateSettingsUpdate) => Promise<void>
}) {
  const { status, run } = useSaveStatus()
  return (
    <Card>
      <CardHeader title="Notifications" subtitle="Choose what updates you want to receive." action={<SavedStatus state={status} />} />
      {!settings ? (
        <p className="text-sm text-slate-400">Loading…</p>
      ) : (
        <div className="space-y-3">
          {NOTIFICATION_ROWS.map((row) => (
            <div key={row.key} className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-500">{row.icon}</span>
                <div>
                  <p className="text-sm font-medium text-slate-700">{row.label}</p>
                  <p className="text-xs text-slate-400">{row.subtitle}</p>
                </div>
              </div>
              <Toggle
                checked={Boolean(settings[row.key])}
                onChange={(v) => run(() => onPatch({ [row.key]: v } as CandidateSettingsUpdate))}
                label={row.label}
              />
            </div>
          ))}
          <p className="pt-1 text-xs text-slate-400">
            These preferences are saved, but this build has no email/push delivery channel to act on them yet.
          </p>
        </div>
      )}
    </Card>
  )
}

// --- Integrations ---

function IntegrationsCard({ candidateId }: { candidateId: string }) {
  const [integrations, setIntegrations] = useState<ConnectedAccount[]>(() =>
    loadConnectedAccounts('settings', candidateId, SETTINGS_INTEGRATION_DEFAULTS),
  )
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [iconId, setIconId] = useState<string | null>(null)

  useEffect(() => setIntegrations(loadConnectedAccounts('settings', candidateId, SETTINGS_INTEGRATION_DEFAULTS)), [candidateId])

  const confirmAdd = () => {
    if (!name.trim() || !iconId) return
    setIntegrations(addConnectedAccount('settings', candidateId, SETTINGS_INTEGRATION_DEFAULTS, { name: name.trim(), iconId }))
    setAdding(false)
  }

  const remove = (id: string) => setIntegrations(removeConnectedAccount('settings', candidateId, SETTINGS_INTEGRATION_DEFAULTS, id))

  return (
    <Card>
      <CardHeader
        title="Integrations"
        subtitle="Connect your favorite tools and services."
        action={
          <Button variant="secondary" icon={<Plus size={14} />} onClick={() => { setName(''); setIconId(null); setAdding(true) }}>
            Add
          </Button>
        }
      />
      {adding && (
        <div className="mb-4 space-y-3 rounded-lg border border-slate-200 p-3">
          <label className="block">
            <span className="text-xs font-medium text-slate-500">Service or app name</span>
            <input className={`mt-1 ${inputClass}`} value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Slack, Zoom" />
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
        {integrations.map((integration) => (
          <div key={integration.id} className="flex items-center justify-between rounded-lg border border-slate-100 p-2.5 text-sm">
            <span className="flex items-center gap-2.5 text-slate-700">
              <BrandIcon iconId={integration.iconId} />
              {integration.name}
            </span>
            <div className="flex items-center gap-2">
              <Badge tone="slate">Not connected</Badge>
              {!integration.builtin && (
                <button onClick={() => remove(integration.id)} className="text-slate-400 hover:text-red-600" aria-label={`Remove ${integration.name}`}>
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-slate-400">
        No real OAuth flow is wired up in this build - every entry honestly shows "Not connected". Custom entries remember
        your icon choice in this browser only.
      </p>
    </Card>
  )
}

// --- Privacy & Security ---

function PrivacySecurityCard({ candidate }: { candidate: Candidate }) {
  const navigate = useNavigate()
  const [openInfo, setOpenInfo] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const rows: { key: string; icon: ReactNode; label: string; subtitle: string; action: () => void; badge?: ReactNode }[] = [
    {
      key: 'password',
      icon: <Lock size={16} className="text-blue-500" />,
      label: 'Change Password',
      subtitle: 'Update your password regularly.',
      action: () => setOpenInfo((k) => (k === 'password' ? null : 'password')),
    },
    {
      key: '2fa',
      icon: <ShieldCheck size={16} className="text-emerald-500" />,
      label: 'Two-Factor Authentication',
      subtitle: 'Add an extra layer of security.',
      action: () => setOpenInfo((k) => (k === '2fa' ? null : '2fa')),
    },
    {
      key: 'privacy',
      icon: <Shield size={16} className="text-violet-500" />,
      label: 'Data & Privacy Controls',
      subtitle: 'Manage your data, download or delete it.',
      action: () => navigate('/compliance'),
    },
    {
      key: 'sessions',
      icon: <MonitorIcon size={16} className="text-slate-500" />,
      label: 'Active Sessions',
      subtitle: 'View and manage your active sessions.',
      action: () => setOpenInfo((k) => (k === 'sessions' ? null : 'sessions')),
    },
  ]

  return (
    <Card>
      <CardHeader title="Privacy & Security" subtitle="Manage your data and keep your account safe." action={<Shield className="text-slate-400" size={18} />} />
      <div className="divide-y divide-slate-50">
        {rows.map((row) => (
          <div key={row.key}>
            <button onClick={row.action} className="flex w-full items-center justify-between py-2.5 text-left hover:bg-slate-50 rounded-lg px-1 -mx-1">
              <span className="flex items-center gap-3">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100">{row.icon}</span>
                <span>
                  <span className="block text-sm font-medium text-slate-700">{row.label}</span>
                  <span className="block text-xs text-slate-400">{row.subtitle}</span>
                </span>
              </span>
              <span className="text-slate-300">›</span>
            </button>
            {openInfo === row.key && (
              <p className="mb-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
                This build has no authentication system, so there's no real {row.label.toLowerCase()} to manage yet.
              </p>
            )}
          </div>
        ))}
        <button
          onClick={() => setConfirmDelete(true)}
          className="flex w-full items-center justify-between py-2.5 text-left hover:bg-red-50 rounded-lg px-1 -mx-1"
        >
          <span className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-50 text-red-600">
              <Trash2 size={16} />
            </span>
            <span>
              <span className="block text-sm font-medium text-red-600">Delete Account</span>
              <span className="block text-xs text-slate-400">Permanently delete your account and data.</span>
            </span>
          </span>
          <span className="text-slate-300">›</span>
        </button>
      </div>
      {confirmDelete && <DeleteAccountModal candidate={candidate} onCancel={() => setConfirmDelete(false)} />}
    </Card>
  )
}

function DeleteAccountModal({ candidate, onCancel }: { candidate: Candidate; onCancel: () => void }) {
  const { refresh } = useCandidate()
  const navigate = useNavigate()
  const [deleting, setDeleting] = useState(false)
  const [confirmText, setConfirmText] = useState('')
  const [error, setError] = useState<string | null>(null)

  const doDelete = async () => {
    setDeleting(true)
    setError(null)
    try {
      await api.candidates.erase(candidate.id)
      refresh()
      navigate('/onboarding')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to delete account')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl">
        <div className="flex items-center gap-2 text-red-600">
          <AlertTriangle size={18} />
          <h3 className="text-base font-semibold">Delete account?</h3>
        </div>
        <p className="mt-2 text-sm text-slate-600">
          This permanently deletes {candidate.fullName}'s profile, experience, matches, tailored artifacts, and everything
          else owned by this candidate (docs/02 §4.1 GDPR erasure - see Compliance for the full list). This can't be undone.
        </p>
        <label className="mt-3 block text-xs font-medium text-slate-500">
          Type <span className="font-semibold text-slate-700">{candidate.fullName}</span> to confirm
        </label>
        <input className={`mt-1 ${inputClass}`} value={confirmText} onChange={(e) => setConfirmText(e.target.value)} />
        {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button variant="danger" onClick={doDelete} disabled={deleting || confirmText !== candidate.fullName}>
            {deleting ? 'Deleting…' : 'Delete permanently'}
          </Button>
        </div>
      </div>
    </div>
  )
}

// --- Appearance ---

function AppearanceCard({
  settings,
  onPatch,
}: {
  settings: CandidateSettings | null
  onPatch: (patch: CandidateSettingsUpdate) => Promise<void>
}) {
  const { status, run } = useSaveStatus()
  const modes: { key: CandidateSettings['theme']; label: string; icon: ReactNode }[] = [
    { key: 'LIGHT', label: 'Light', icon: <Sun size={16} /> },
    { key: 'DARK', label: 'Dark', icon: <Moon size={16} /> },
    { key: 'SYSTEM', label: 'System', icon: <MonitorIcon size={16} /> },
  ]
  return (
    <Card>
      <CardHeader title="Appearance" subtitle="Customize how the app looks." action={<SavedStatus state={status} />} />
      <div className="flex gap-2">
        {modes.map((mode) => (
          <button
            key={mode.key}
            onClick={() => run(() => onPatch({ theme: mode.key }))}
            className={`flex flex-1 flex-col items-center gap-1.5 rounded-lg border px-4 py-3 text-sm font-medium ${
              settings?.theme === mode.key ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {mode.icon}
            {mode.label}
          </button>
        ))}
      </div>
      <p className="mt-2 text-xs text-slate-400">
        Saved to this candidate's settings. Dark mode styling isn't implemented across every page yet - this sets the
        preference so it's ready once it is.
      </p>
    </Card>
  )
}

// --- Billing ---

function BillingCard({ settings }: { settings: CandidateSettings | null }) {
  const [showModal, setShowModal] = useState(false)
  return (
    <Card>
      <CardHeader title="Billing & Plan" subtitle="Manage your subscription and billing details." action={<Crown className="text-amber-400" size={18} />} />
      <div className="flex items-center justify-between rounded-lg border border-amber-100 bg-amber-50/60 p-3">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-100 text-violet-600">
            <Crown size={16} />
          </span>
          <div>
            <p className="text-sm font-semibold text-slate-800">{settings?.planTier === 'PRO' ? 'Pro Plan' : settings?.planTier ?? 'Pro Plan'}</p>
            <p className="text-xs text-slate-500">Get the most out of CareerIntelligence</p>
          </div>
        </div>
        <Button variant="secondary" onClick={() => setShowModal(true)}>
          Manage Subscription
        </Button>
      </div>
      <ul className="mt-3 space-y-1 text-sm text-slate-600">
        <li>✓ Unlimited job applications</li>
        <li>✓ Advanced AI features</li>
        <li>✓ Priority support</li>
      </ul>
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl">
            <h3 className="text-base font-semibold text-slate-900">No billing processor wired up</h3>
            <p className="mt-2 text-sm text-slate-600">
              This build doesn't integrate a real payment processor (Stripe or otherwise) - the plan shown here is a
              stored display value, not a live subscription. There's nothing to change yet.
            </p>
            <div className="mt-4 flex justify-end">
              <Button onClick={() => setShowModal(false)}>Close</Button>
            </div>
          </div>
        </div>
      )}
    </Card>
  )
}

// --- Danger Zone ---

function DangerZoneCard({ candidate, className }: { candidate: Candidate; className?: string }) {
  const [confirmDelete, setConfirmDelete] = useState(false)
  return (
    <Card className={`border-red-200 bg-red-50/40 ${className ?? ''}`}>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h3 className="flex items-center gap-2 text-base font-semibold text-red-700">
            <AlertTriangle size={16} />
            Danger Zone
          </h3>
          <p className="mt-0.5 text-sm text-red-600/80">Permanently delete your account and all associated data.</p>
        </div>
        <Button variant="danger" icon={<Trash2 size={14} />} onClick={() => setConfirmDelete(true)}>
          Delete My Account
        </Button>
      </div>
      {confirmDelete && <DeleteAccountModal candidate={candidate} onCancel={() => setConfirmDelete(false)} />}
    </Card>
  )
}
