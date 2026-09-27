export function formatRelativeTime(iso: string): string {
  const date = new Date(iso)
  const diffMs = Date.now() - date.getTime()
  const minutes = Math.round(diffMs / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  return `${days}d ago`
}

export function skillLabel(skillId: string): string {
  return skillId.replace(/^skill\./, '').replace(/[-_]/g, ' ')
}

function normalizeForCompare(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]/g, '')
}

/** Loose match between a free-text skill mention and a set of canonical taxonomy skill
 * ids, for display purposes only (e.g. "Postgres" vs "skill.postgresql") - not a claim
 * of exact system-verified status, just a UI hint for which mentions also passed
 * taxonomy normalization. */
export function mentionMatchesSkillIds(mention: string, skillIds: string[]): boolean {
  const normalizedMention = normalizeForCompare(mention)
  if (!normalizedMention) return false
  return skillIds.some((id) => {
    const normalizedLabel = normalizeForCompare(skillLabel(id))
    return normalizedMention === normalizedLabel || normalizedMention.includes(normalizedLabel) || normalizedLabel.includes(normalizedMention)
  })
}
