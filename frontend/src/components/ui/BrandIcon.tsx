import { getBrandIcon } from '../../lib/iconStore'

/** Renders one entry from the icon store as a rounded badge, matching the mock's colored
 * service-logo squares (e.g. LinkedIn's blue tile, GitHub's black tile). `boxSize` (the
 * badge's outer square, in px) defaults to a comfortable box around `size` (the glyph itself)
 * but can be overridden independently, e.g. to sit in a larger circular slot. */
export function BrandIcon({
  iconId,
  size = 18,
  boxSize,
  className = '',
}: {
  iconId: string
  size?: number
  boxSize?: number
  className?: string
}) {
  const { Icon, bg, fg, name } = getBrandIcon(iconId)
  const box = boxSize ?? Math.round(size * 1.6)
  return (
    <span
      title={name}
      style={{ width: box, height: box }}
      className={`flex shrink-0 items-center justify-center rounded-lg ${bg} ${fg} ${className}`}
    >
      <Icon size={size * 0.6} />
    </span>
  )
}
