import type { ComponentType } from 'react'
import {
  FaLinkedin,
  FaGithub,
  FaGoogle,
  FaGoogleDrive,
  FaSlack,
  FaXTwitter,
  FaDiscord,
  FaDropbox,
  FaFacebook,
  FaFigma,
  FaMicrosoft,
  FaInstagram,
  FaMedium,
  FaTelegram,
  FaTrello,
  FaWhatsapp,
  FaYoutube,
} from 'react-icons/fa6'
import {
  SiGmail,
  SiNotion,
  SiZoom,
  SiCalendly,
  SiAsana,
  SiJira,
  SiGooglemeet,
  SiIndeed,
  SiGlassdoor,
  SiUpwork,
  SiStackoverflow,
  SiBehance,
  SiDribbble,
  SiGreenhouse,
} from 'react-icons/si'
import { Globe, PenTool, Layers, Building2, FileJson } from 'lucide-react'

export interface BrandIconDef {
  id: string
  name: string
  Icon: ComponentType<{ size?: number; className?: string }>
  /** Brand-accurate background (mostly the brand's own color, one flat square/circle per each
   * service's real style guide - Gmail keeps a white tile since its mark is multicolor on white). */
  bg: string
  fg: string
}

/** The "icon store" - a curated, searchable registry of real brand marks (via react-icons'
 * Font Awesome 6 + Simple Icons sets) used anywhere the app shows a connected/integratable
 * external service, plus a `custom` fallback for anything not in the catalog yet. This is a
 * static icon catalog, not a connection status source - whether an account is actually
 * connected is tracked separately (see ConnectedAccountsStore) and always defaults to
 * "Not connected" since this app has no real OAuth flow. */
export const ICON_STORE: BrandIconDef[] = [
  { id: 'linkedin', name: 'LinkedIn', Icon: FaLinkedin, bg: 'bg-[#0A66C2]', fg: 'text-white' },
  { id: 'github', name: 'GitHub', Icon: FaGithub, bg: 'bg-slate-900', fg: 'text-white' },
  { id: 'gmail', name: 'Gmail', Icon: SiGmail, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#EA4335]' },
  { id: 'google', name: 'Google', Icon: FaGoogle, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#4285F4]' },
  { id: 'google-drive', name: 'Google Drive', Icon: FaGoogleDrive, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#0F9D58]' },
  { id: 'google-meet', name: 'Google Meet', Icon: SiGooglemeet, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#00897B]' },
  { id: 'outlook', name: 'Outlook', Icon: FaMicrosoft, bg: 'bg-[#0078D4]', fg: 'text-white' },
  { id: 'slack', name: 'Slack', Icon: FaSlack, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#4A154B]' },
  { id: 'notion', name: 'Notion', Icon: SiNotion, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-slate-900' },
  { id: 'x', name: 'X (Twitter)', Icon: FaXTwitter, bg: 'bg-black', fg: 'text-white' },
  { id: 'discord', name: 'Discord', Icon: FaDiscord, bg: 'bg-[#5865F2]', fg: 'text-white' },
  { id: 'dropbox', name: 'Dropbox', Icon: FaDropbox, bg: 'bg-[#0061FF]', fg: 'text-white' },
  { id: 'facebook', name: 'Facebook', Icon: FaFacebook, bg: 'bg-[#1877F2]', fg: 'text-white' },
  { id: 'figma', name: 'Figma', Icon: FaFigma, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-slate-800' },
  { id: 'instagram', name: 'Instagram', Icon: FaInstagram, bg: 'bg-gradient-to-br from-[#F58529] via-[#DD2A7B] to-[#8134AF]', fg: 'text-white' },
  { id: 'medium', name: 'Medium', Icon: FaMedium, bg: 'bg-black', fg: 'text-white' },
  { id: 'telegram', name: 'Telegram', Icon: FaTelegram, bg: 'bg-[#26A5E4]', fg: 'text-white' },
  { id: 'trello', name: 'Trello', Icon: FaTrello, bg: 'bg-[#0052CC]', fg: 'text-white' },
  { id: 'whatsapp', name: 'WhatsApp', Icon: FaWhatsapp, bg: 'bg-[#25D366]', fg: 'text-white' },
  { id: 'youtube', name: 'YouTube', Icon: FaYoutube, bg: 'bg-[#FF0000]', fg: 'text-white' },
  { id: 'zoom', name: 'Zoom', Icon: SiZoom, bg: 'bg-[#0B5CFF]', fg: 'text-white' },
  { id: 'calendly', name: 'Calendly', Icon: SiCalendly, bg: 'bg-[#006BFF]', fg: 'text-white' },
  { id: 'asana', name: 'Asana', Icon: SiAsana, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#F06A6A]' },
  { id: 'jira', name: 'Jira', Icon: SiJira, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#0052CC]' },
  { id: 'indeed', name: 'Indeed', Icon: SiIndeed, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#2164F3]' },
  { id: 'glassdoor', name: 'Glassdoor', Icon: SiGlassdoor, bg: 'bg-[#0CAA41]', fg: 'text-white' },
  { id: 'upwork', name: 'Upwork', Icon: SiUpwork, bg: 'bg-white ring-1 ring-slate-200', fg: 'text-[#14A800]' },
  { id: 'stackoverflow', name: 'Stack Overflow', Icon: SiStackoverflow, bg: 'bg-[#F58025]', fg: 'text-white' },
  { id: 'behance', name: 'Behance', Icon: SiBehance, bg: 'bg-[#1769FF]', fg: 'text-white' },
  { id: 'dribbble', name: 'Dribbble', Icon: SiDribbble, bg: 'bg-[#EA4C89]', fg: 'text-white' },
  { id: 'greenhouse', name: 'Greenhouse', Icon: SiGreenhouse, bg: 'bg-[#24A47F]', fg: 'text-white' },
  // No Lever mark in the Simple Icons set this app draws from - PenTool is a generic
  // stand-in, not a claim of brand accuracy (same idea as CUSTOM_ICON below).
  { id: 'lever', name: 'Lever', Icon: PenTool, bg: 'bg-slate-900', fg: 'text-white' },
  // No Ashby/Workday marks in the Simple Icons set either - same generic-stand-in
  // rationale as Lever above, not a claim of brand accuracy.
  { id: 'ashby', name: 'Ashby', Icon: Layers, bg: 'bg-[#1a56db]', fg: 'text-white' },
  { id: 'workday', name: 'Workday', Icon: Building2, bg: 'bg-[#f89728]', fg: 'text-white' },
  // Not a brand mark at all - this source is any company's own career page (identified
  // by embedded Schema.org JobPosting structured data), not one named vendor.
  { id: 'jsonld', name: 'Company career page', Icon: FileJson, bg: 'bg-slate-700', fg: 'text-white' },
]

export const CUSTOM_ICON: BrandIconDef = {
  id: 'custom',
  name: 'Custom',
  Icon: Globe,
  bg: 'bg-slate-100',
  fg: 'text-slate-500',
}

export function getBrandIcon(id: string): BrandIconDef {
  return ICON_STORE.find((entry) => entry.id === id) ?? CUSTOM_ICON
}
