import { NavLink, useLocation } from 'react-router-dom'
import clsx from 'clsx'
import {
  LayoutDashboard,
  Search,
  Sparkles,
  FolderKanban,
  ShieldCheck,
  Briefcase,
  MessageSquareText,
  GitBranch,
  UserPlus,
  Bot,
  Inbox,
  UserCheck,
  Settings as SettingsIcon,
} from 'lucide-react'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/job-discovery', label: 'Job Discovery', icon: Search },
  { to: '/matches', label: 'Matches', icon: Sparkles },
  { to: '/applications', label: 'Applications', icon: FolderKanban },
  { to: '/autopilot', label: 'Autopilot', icon: Bot },
  { to: '/email-inbox', label: 'Email Inbox', icon: Inbox },
  { to: '/email-review', label: 'Agent Email Review', icon: UserCheck },
  { to: '/career-vault', label: 'Career Vault', icon: Briefcase },
  { to: '/interview-prep', label: 'Interview Prep', icon: MessageSquareText },
  { to: '/pipeline', label: 'Pipeline', icon: GitBranch },
  { to: '/compliance', label: 'Compliance', icon: ShieldCheck },
  { to: '/onboarding', label: 'New Candidate', icon: UserPlus },
  { to: '/settings', label: 'Settings', icon: SettingsIcon },
]

// Per-route copy for the bottom panel, matching the mock's varying quote per screen.
const PANEL_COPY: Record<string, { heading: string; quote: string }> = {
  '/': { heading: 'A better career is a brighter you.', quote: 'Progress today, opportunities tomorrow.' },
  '/job-discovery': { heading: 'Explore opportunities. Build what’s next.', quote: 'Better tools. Brighter possibilities.' },
  '/matches': { heading: 'Dream. Build. Advance.', quote: 'Same skills. Bigger possibilities.' },
  '/applications': { heading: 'Track. Learn. Grow.', quote: 'Every application is a step forward.' },
  '/autopilot': { heading: 'Let the agent work while you focus on what matters.', quote: 'Autonomy, with a human still holding the line.' },
  '/career-vault': { heading: 'Your experience has value.', quote: 'Verified today. Greater opportunities tomorrow.' },
  '/interview-prep': { heading: 'Prepare today. Perform tomorrow.', quote: 'Confidence creates opportunity.' },
  '/pipeline': { heading: 'Consistent actions create extraordinary opportunities.', quote: 'You’re not just applying. You’re building a future.' },
  '/compliance': { heading: 'Trust. Transparency. Better opportunities.', quote: 'Ethical AI for a fairer career journey.' },
  '/onboarding': { heading: 'Better opportunities start with a truer you.', quote: 'AI amplifies what’s real. It doesn’t replace you.' },
}
const DEFAULT_PANEL_COPY = { heading: 'Smarter opportunities. A brighter you.', quote: 'AI that works for you, with you — not instead of you.' }

export function Sidebar() {
  const location = useLocation()
  const panel = PANEL_COPY[location.pathname] ?? DEFAULT_PANEL_COPY

  return (
    <aside className="flex h-screen w-60 shrink-0 flex-col bg-[var(--color-sidebar)] print:hidden">
      <div className="flex items-center gap-2 px-5 py-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-[var(--color-brand-from)] to-[var(--color-brand-to)] text-sm font-bold text-white">
          A
        </div>
        <div>
          <p className="text-sm font-semibold leading-tight text-white">CareerIntelligence</p>
          <p className="text-[11px] leading-tight text-slate-400">Your story. Real opportunities.</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-[var(--color-sidebar-active)] text-white'
                  : 'text-slate-300 hover:bg-[var(--color-sidebar-hover)] hover:text-white',
              )
            }
          >
            <Icon size={18} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div
        className="relative m-3 overflow-hidden rounded-xl p-4 text-slate-200"
        style={{
          backgroundImage:
            'linear-gradient(165deg, var(--color-panel-from) 0%, var(--color-panel-via) 55%, var(--color-panel-to) 100%)',
        }}
      >
        <p className="text-sm font-semibold text-white">{panel.heading}</p>
        <p className="mt-3 text-xs italic text-slate-300">&ldquo;{panel.quote}&rdquo;</p>
        <div className="mt-3 h-1 w-10 rounded-full bg-gradient-to-r from-[var(--color-brand-from)] to-[var(--color-brand-to)]" />
      </div>
    </aside>
  )
}
