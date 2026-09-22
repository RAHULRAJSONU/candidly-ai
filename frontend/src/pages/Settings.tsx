import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bot, Bell, Link2, Moon, Shield, User } from 'lucide-react'
import { api } from '../api/client'
import type { AiConfigView } from '../api/types'
import { useCandidate } from '../context/CandidateContext'
import { Card, CardHeader } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Tabs } from '../components/ui/Tabs'

type Tab = 'account' | 'ai' | 'notifications' | 'integrations' | 'appearance'

const THEME_STORAGE_KEY = 'candidly.theme'

function loadTheme(): 'light' | 'dark' {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

const INTEGRATIONS = ['LinkedIn', 'Google Calendar', 'Outlook', 'GitHub', 'Notion']

export function Settings() {
  const { selected } = useCandidate()
  const [tab, setTab] = useState<Tab>('account')
  const [aiConfig, setAiConfig] = useState<AiConfigView | null>(null)
  const [theme, setTheme] = useState<'light' | 'dark'>(loadTheme)

  useEffect(() => {
    api.aiConfig.get().then(setAiConfig)
  }, [])

  useEffect(() => {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme)
    } catch {
      // best-effort only
    }
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Settings</h1>
        <p className="mt-1 text-sm text-slate-500">Account, AI behavior, notifications, and integrations.</p>
      </div>

      <Tabs
        tabs={[
          { key: 'account', label: 'Account' },
          { key: 'ai', label: 'AI Preferences' },
          { key: 'notifications', label: 'Notifications' },
          { key: 'integrations', label: 'Integrations' },
          { key: 'appearance', label: 'Appearance' },
        ]}
        active={tab}
        onChange={(k) => setTab(k as Tab)}
      />

      {tab === 'account' && (
        <Card>
          <CardHeader title="Account" subtitle="Read-only here - profile fields are edited on Career Vault / New Candidate." action={<User className="text-slate-400" size={18} />} />
          {selected ? (
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
              <Row label="Name" value={selected.fullName} />
              <Row label="Email" value={selected.email} />
              <Row label="Location" value={selected.location} />
              <Row label="Work authorization" value={selected.workAuthorizations.join(', ') || 'Not specified'} />
            </dl>
          ) : (
            <p className="text-sm text-slate-500">Select a candidate first.</p>
          )}
          <div className="mt-4 flex gap-3 text-sm">
            <Link to="/career-vault" className="font-medium text-blue-600 hover:underline">
              Edit Career Vault
            </Link>
            <Link to="/onboarding" className="font-medium text-blue-600 hover:underline">
              Onboard a new candidate
            </Link>
          </div>
        </Card>
      )}

      {tab === 'ai' && (
        <Card>
          <CardHeader
            title="AI Preferences"
            subtitle="What's actually configured in this deployment - there is no per-candidate model switch in this build."
            action={<Bot className="text-slate-400" size={18} />}
          />
          {aiConfig ? (
            <div className="space-y-3">
              <ProviderRow label="Resume tailoring & email drafting" view={aiConfig.tailoringAndDrafting} />
              <ProviderRow label="Embeddings (semantic search)" view={aiConfig.embeddings} />
              <ProviderRow label="Judgments (scoring, classification)" view={aiConfig.judgments} />
            </div>
          ) : (
            <p className="text-sm text-slate-400">Loading...</p>
          )}
          <p className="mt-4 text-xs text-slate-400">
            Autopilot's own behavior toggles (auto-apply, auto-tailor, daily limits) live on the{' '}
            <Link to="/autopilot/settings" className="text-blue-600 hover:underline">
              Autopilot Settings
            </Link>{' '}
            page, not here.
          </p>
        </Card>
      )}

      {tab === 'notifications' && (
        <Card>
          <CardHeader
            title="Notifications"
            subtitle="Configured per-candidate on the Autopilot Settings screen, since notification triggers are tied to agent activity."
            action={<Bell className="text-slate-400" size={18} />}
          />
          <Link to="/autopilot/settings" className="text-sm font-medium text-blue-600 hover:underline">
            Go to Autopilot notification preferences &rarr;
          </Link>
        </Card>
      )}

      {tab === 'integrations' && (
        <Card>
          <CardHeader
            title="Integrations"
            subtitle="Not available in this build - there's no OAuth connector for any of these; connecting would need a real consent flow this slice doesn't implement."
            action={<Link2 className="text-slate-400" size={18} />}
          />
          <div className="space-y-2">
            {INTEGRATIONS.map((name) => (
              <div key={name} className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-sm">
                <span className="text-slate-700">{name}</span>
                <Badge tone="slate">Not connected</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      {tab === 'appearance' && (
        <Card>
          <CardHeader title="Appearance" action={<Moon className="text-slate-400" size={18} />} />
          <div className="flex gap-2">
            {(['light', 'dark'] as const).map((mode) => (
              <button
                key={mode}
                onClick={() => setTheme(mode)}
                className={`rounded-lg border px-4 py-2 text-sm font-medium capitalize ${
                  theme === mode ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                }`}
              >
                {mode}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Saved to this browser only. Dark mode styling isn't implemented across every page yet - this sets the
            preference so it's ready once it is.
          </p>
        </Card>
      )}

      <Card className="bg-slate-50/60">
        <CardHeader title="Privacy & data" action={<Shield className="text-slate-400" size={18} />} />
        <p className="text-sm text-slate-500">
          Audit-chain verification, retention purge, the LL144 bias report, and GDPR export/erase all live on{' '}
          <Link to="/compliance" className="font-medium text-blue-600 hover:underline">
            Compliance
          </Link>
          , not duplicated here.
        </p>
      </Card>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-400">{label}</dt>
      <dd className="font-medium text-slate-800">{value}</dd>
    </div>
  )
}

function ProviderRow({ label, view }: { label: string; view: import('../api/types').AiProviderView }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-sm">
      <div>
        <p className="font-medium text-slate-800">{label}</p>
        <p className="text-xs text-slate-400">
          {view.provider} &middot; {view.model}
        </p>
      </div>
      <Badge tone={view.configured ? 'green' : 'red'}>{view.configured ? 'API key set' : 'No API key'}</Badge>
    </div>
  )
}
