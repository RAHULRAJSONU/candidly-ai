import { useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { RotateCcw, X, ZoomIn } from 'lucide-react'
import { Button } from './Button'

const VIEWPORT = 240
const OUTPUT_SIZE = 480

/** Lightweight client-side square-crop UI for a profile photo - drag to reposition, slider
 * to zoom, no external cropping library. Renders the crop at export time onto an offscreen
 * canvas rather than during drag, so panning/zooming stays cheap (just CSS transforms). */
export function PhotoCropModal({
  file,
  onCancel,
  onCropped,
}: {
  file: File
  onCancel: () => void
  onCropped: (blob: Blob) => void
}) {
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [naturalSize, setNaturalSize] = useState<{ w: number; h: number } | null>(null)
  const [scale, setScale] = useState(1)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [saving, setSaving] = useState(false)
  const dragState = useRef<{ startX: number; startY: number; origin: { x: number; y: number } } | null>(null)
  const imgRef = useRef<HTMLImageElement>(null)

  useEffect(() => {
    const url = URL.createObjectURL(file)
    setImageUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const baseScale = naturalSize ? Math.max(VIEWPORT / naturalSize.w, VIEWPORT / naturalSize.h) : 1
  const displayedW = naturalSize ? naturalSize.w * baseScale * scale : VIEWPORT
  const displayedH = naturalSize ? naturalSize.h * baseScale * scale : VIEWPORT
  const boundX = Math.max(0, (displayedW - VIEWPORT) / 2)
  const boundY = Math.max(0, (displayedH - VIEWPORT) / 2)

  const clampOffset = (x: number, y: number) => ({
    x: Math.min(boundX, Math.max(-boundX, x)),
    y: Math.min(boundY, Math.max(-boundY, y)),
  })

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    dragState.current = { startX: e.clientX, startY: e.clientY, origin: offset }
  }
  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragState.current) return
    const dx = e.clientX - dragState.current.startX
    const dy = e.clientY - dragState.current.startY
    setOffset(clampOffset(dragState.current.origin.x + dx, dragState.current.origin.y + dy))
  }
  const onPointerUp = () => {
    dragState.current = null
  }

  const changeScale = (next: number) => {
    setScale(next)
    setOffset((prev) => clampOffset(prev.x, prev.y))
  }

  const save = () => {
    if (!naturalSize) return
    setSaving(true)
    const topLeftX = (VIEWPORT - displayedW) / 2 + offset.x
    const topLeftY = (VIEWPORT - displayedH) / 2 + offset.y
    const effectiveScale = baseScale * scale
    const sx = -topLeftX / effectiveScale
    const sy = -topLeftY / effectiveScale
    const sSize = VIEWPORT / effectiveScale

    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const ctx = canvas.getContext('2d')
    if (!ctx || !imgRef.current) {
      setSaving(false)
      return
    }
    ctx.drawImage(imgRef.current, sx, sy, sSize, sSize, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
    canvas.toBlob(
      (blob) => {
        setSaving(false)
        if (blob) onCropped(blob)
      },
      'image/png',
      0.92,
    )
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-900">Reposition photo</h3>
          <button onClick={onCancel} className="rounded-full p-1 text-slate-400 hover:bg-slate-100" aria-label="Close">
            <X size={16} />
          </button>
        </div>

        <div
          className="relative mx-auto touch-none overflow-hidden rounded-full bg-slate-100"
          style={{ width: VIEWPORT, height: VIEWPORT, cursor: 'grab' }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
        >
          {imageUrl && (
            <img
              ref={imgRef}
              src={imageUrl}
              alt="Selected profile"
              draggable={false}
              onLoad={(e) => setNaturalSize({ w: e.currentTarget.naturalWidth, h: e.currentTarget.naturalHeight })}
              className="pointer-events-none absolute left-1/2 top-1/2 max-w-none select-none"
              style={{
                width: displayedW,
                height: displayedH,
                transform: `translate(-50%, -50%) translate(${offset.x}px, ${offset.y}px)`,
              }}
            />
          )}
          <div className="pointer-events-none absolute inset-0 rounded-full ring-2 ring-white/80" />
        </div>

        <div className="mt-4 flex items-center gap-3">
          <ZoomIn size={14} className="text-slate-400" />
          <input
            type="range"
            min={1}
            max={3}
            step={0.05}
            value={scale}
            onChange={(e) => changeScale(parseFloat(e.target.value))}
            className="flex-1"
          />
          <button
            type="button"
            onClick={() => {
              setScale(1)
              setOffset({ x: 0, y: 0 })
            }}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100"
            aria-label="Reset"
          >
            <RotateCcw size={14} />
          </button>
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={save} disabled={saving || !naturalSize}>
            {saving ? 'Saving...' : 'Save photo'}
          </Button>
        </div>
      </div>
    </div>
  )
}
