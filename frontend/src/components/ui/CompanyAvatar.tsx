const PALETTE = ['#7c3aed', '#0f172a', '#16a34a', '#dc2626', '#2563eb', '#ea580c', '#0891b2', '#db2777']

function colorFor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

export function CompanyAvatar({ name, size = 40 }: { name: string; size?: number }) {
  return (
    <div
      className="flex shrink-0 items-center justify-center rounded-lg text-sm font-bold text-white"
      style={{ width: size, height: size, backgroundColor: colorFor(name || '?'), fontSize: size * 0.4 }}
    >
      {(name || '?').charAt(0).toUpperCase()}
    </div>
  )
}
