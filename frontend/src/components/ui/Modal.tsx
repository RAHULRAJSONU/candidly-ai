import type { ReactNode } from 'react'
import { X } from 'lucide-react'

/** Minimal overlay + centered panel, following the same fixed-inset overlay pattern as
 * PhotoCropModal - used to host content (e.g. the resume manager) outside the tab flow. */
export function Modal({
  title,
  onClose,
  children,
  maxWidthClass = 'max-w-md',
}: {
  title: string
  onClose: () => void
  children: ReactNode
  maxWidthClass?: string
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
      <div className={`w-full ${maxWidthClass} rounded-2xl bg-white p-5 shadow-xl`}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-semibold text-slate-900">{title}</h3>
          <button onClick={onClose} className="rounded-full p-1 text-slate-400 hover:bg-slate-100" aria-label="Close">
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
