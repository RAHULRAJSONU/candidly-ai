/** Money formatting shared by every page. Amounts are integer minor units (cents, paise)
 * tagged with an ISO 4217 code: a candidate's own amounts (comp floor, expected CTC,
 * Autopilot salary range) use Candidate.preferredCurrency - set in Settings > Job
 * Preferences - while a JobPosting or PipelineOffer carries its own `currency`. There's no
 * FX-rate source in this app, so an amount is always shown in the currency it was recorded
 * in and never converted (backend: CurrencyCodes). */

export const DEFAULT_CURRENCY = 'USD'

export const SUPPORTED_CURRENCIES: { code: string; name: string }[] = [
  { code: 'USD', name: 'US Dollar' },
  { code: 'INR', name: 'Indian Rupee' },
  { code: 'EUR', name: 'Euro' },
  { code: 'GBP', name: 'British Pound' },
  { code: 'CAD', name: 'Canadian Dollar' },
  { code: 'AUD', name: 'Australian Dollar' },
  { code: 'SGD', name: 'Singapore Dollar' },
  { code: 'AED', name: 'UAE Dirham' },
]

/** Dropdown options, keeping `current` selectable even if it was set via the API to a valid
 * ISO code outside the curated list above. */
export function currencyOptions(current?: string): { code: string; label: string }[] {
  const options = SUPPORTED_CURRENCIES.map((c) => ({ code: c.code, label: `${c.code} - ${c.name}` }))
  if (current && !options.some((o) => o.code === current)) options.push({ code: current, label: current })
  return options
}

// en-IN renders INR in lakh/crore grouping (₹57,00,000 / ₹57L), which is how INR salaries
// are read. en-US for everything else also disambiguates same-symbol currencies (CA$, A$).
function localeFor(currency: string): string {
  return currency === 'INR' ? 'en-IN' : 'en-US'
}

/** Null for a code Intl rejects (only possible for legacy rows stored before the backend
 * validated currency codes) - callers fall back to plain "<CODE> <number>". */
function numberFormat(currency: string, options: Intl.NumberFormatOptions): Intl.NumberFormat | null {
  try {
    return new Intl.NumberFormat(localeFor(currency), { style: 'currency', currency, ...options })
  } catch {
    return null
  }
}

function minorUnitFactor(currency: string): number {
  return 10 ** (numberFormat(currency, {})?.resolvedOptions().maximumFractionDigits ?? 2)
}

export function toMinorUnits(major: number, currency: string): number {
  return Math.round(major * minorUnitFactor(currency))
}

export function fromMinorUnits(minor: number, currency: string): number {
  return minor / minorUnitFactor(currency)
}

export function currencySymbol(currency: string): string {
  return numberFormat(currency, {})?.formatToParts(0).find((p) => p.type === 'currency')?.value ?? currency
}

/** `compact` gives list-view short forms ("$150K", "₹57L"); the default is the full amount
 * with no fractional part ("$150,000", "₹57,00,000"). */
export function formatMoney(minor: number, currency: string, opts: { compact?: boolean } = {}): string {
  const major = fromMinorUnits(minor, currency)
  const fmt = numberFormat(currency, opts.compact ? { notation: 'compact', maximumFractionDigits: 1 } : { maximumFractionDigits: 0 })
  return fmt ? fmt.format(major) : `${currency} ${Math.round(major).toLocaleString()}`
}

export function formatCompRange(
  minMinorUnits: number | null,
  maxMinorUnits: number | null,
  currency: string,
  opts: { compact?: boolean } = { compact: true },
): string {
  if (minMinorUnits == null && maxMinorUnits == null) return 'Not specified'
  if (minMinorUnits != null && maxMinorUnits != null) {
    return `${formatMoney(minMinorUnits, currency, opts)} - ${formatMoney(maxMinorUnits, currency, opts)}`
  }
  return formatMoney((minMinorUnits ?? maxMinorUnits)!, currency, opts)
}
