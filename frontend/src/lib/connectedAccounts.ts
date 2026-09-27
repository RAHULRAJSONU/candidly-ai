export interface ConnectedAccount {
  id: string
  iconId: string
  name: string
  builtin?: boolean
}

/** The three accounts every candidate starts with on Career Vault, matching the mock - still
 * honestly "Not connected" everywhere they're rendered, since there's no real OAuth flow
 * behind any of them yet (see ConnectedAccountsCard). */
export const CAREER_VAULT_DEFAULTS: ConnectedAccount[] = [
  { id: 'linkedin', iconId: 'linkedin', name: 'LinkedIn', builtin: true },
  { id: 'github', iconId: 'github', name: 'GitHub', builtin: true },
  { id: 'gmail', iconId: 'gmail', name: 'Gmail', builtin: true },
]

/** Settings > Integrations starts with a different, broader default set (calendar/notes
 * tooling, not just social/dev accounts). */
export const SETTINGS_INTEGRATION_DEFAULTS: ConnectedAccount[] = [
  { id: 'linkedin', iconId: 'linkedin', name: 'LinkedIn', builtin: true },
  { id: 'google-calendar', iconId: 'google', name: 'Google Calendar', builtin: true },
  { id: 'github', iconId: 'github', name: 'GitHub', builtin: true },
  { id: 'notion', iconId: 'notion', name: 'Notion', builtin: true },
]

function storageKey(scope: string, ownerId: string): string {
  return `candidly.connectedAccounts.${scope}.${ownerId}`
}

function readCustom(scope: string, ownerId: string): ConnectedAccount[] {
  try {
    const raw = localStorage.getItem(storageKey(scope, ownerId))
    return raw ? (JSON.parse(raw) as ConnectedAccount[]) : []
  } catch {
    return []
  }
}

function writeCustom(scope: string, ownerId: string, accounts: ConnectedAccount[]): void {
  try {
    localStorage.setItem(storageKey(scope, ownerId), JSON.stringify(accounts))
  } catch {
    // best-effort only - this list is a per-browser convenience, not a source of truth
  }
}

/** Custom "connect a service" entries are saved to this browser's localStorage only, scoped
 * per (scope, ownerId) pair - there's no backend model for arbitrary connected-account
 * records, and building real OAuth for an open-ended set of services is out of scope here.
 * This mirrors the same local-only pattern Settings.tsx already uses for the theme preference. */
export function loadConnectedAccounts(scope: string, ownerId: string, defaults: ConnectedAccount[]): ConnectedAccount[] {
  return [...defaults, ...readCustom(scope, ownerId)]
}

export function addConnectedAccount(
  scope: string,
  ownerId: string,
  defaults: ConnectedAccount[],
  account: { name: string; iconId: string },
): ConnectedAccount[] {
  const custom = readCustom(scope, ownerId)
  const updated = [...custom, { id: `custom-${Date.now()}`, iconId: account.iconId, name: account.name }]
  writeCustom(scope, ownerId, updated)
  return [...defaults, ...updated]
}

export function removeConnectedAccount(scope: string, ownerId: string, defaults: ConnectedAccount[], id: string): ConnectedAccount[] {
  const updated = readCustom(scope, ownerId).filter((a) => a.id !== id)
  writeCustom(scope, ownerId, updated)
  return [...defaults, ...updated]
}
