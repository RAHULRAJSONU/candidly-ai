import { useRef, useState } from 'react'
import { CheckCircle2, Loader2, ShieldCheck, Upload, X } from 'lucide-react'
import { api } from '../../api/client'
import type { ProfileImportResult } from '../../api/types'

const MAX_SIZE_BYTES = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['.pdf', '.docx']

function isAccepted(file: File) {
  const name = file.name.toLowerCase()
  return ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext))
}

/** Drag-and-drop resume upload widget matching the onboarding mock's upload step, shared
 * between the onboarding wizard and Career Vault's "Import Resume" action. */
export function ResumeDropzone({ onExtracted }: { onExtracted: (result: ProfileImportResult) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleFile = async (candidate: File) => {
    setError(null)
    if (!isAccepted(candidate)) {
      setError('Unsupported file type - only PDF and DOCX are supported.')
      return
    }
    if (candidate.size > MAX_SIZE_BYTES) {
      setError('File is too large - max 10 MB.')
      return
    }
    setFile(candidate)
    setUploading(true)
    try {
      const result = await api.candidates.importResume(candidate)
      onExtracted(result)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not process this resume.')
      setFile(null)
    } finally {
      setUploading(false)
    }
  }

  const clear = () => {
    setFile(null)
    setError(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-3">
      <div
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          const dropped = e.dataTransfer.files[0]
          if (dropped) handleFile(dropped)
        }}
        className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-8 text-center transition-colors ${
          dragOver ? 'border-blue-400 bg-blue-50/40' : 'border-slate-300 bg-slate-50/60'
        }`}
      >
        <Upload className="mb-2 text-blue-500" size={28} />
        <p className="text-sm text-slate-700">
          Drag and drop your resume here
          <br />
          or{' '}
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="font-medium text-blue-600 hover:underline"
          >
            Browse files
          </button>
        </p>
        <p className="mt-1 text-xs text-slate-400">Supports PDF, DOCX (Max 10 MB)</p>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx"
          className="hidden"
          onChange={(e) => {
            const selected = e.target.files?.[0]
            if (selected) handleFile(selected)
          }}
        />
      </div>

      {file && (
        <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-3">
          <div className="flex items-center gap-2 text-sm text-slate-700">
            {uploading ? (
              <Loader2 className="animate-spin text-blue-500" size={16} />
            ) : (
              <CheckCircle2 className="text-emerald-600" size={16} />
            )}
            <span>{file.name}</span>
            <span className="text-slate-400">{(file.size / 1024 / 1024).toFixed(1)} MB</span>
          </div>
          <button onClick={clear} className="text-slate-400 hover:text-slate-600">
            <X size={14} />
          </button>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
        <ShieldCheck className="mt-0.5 shrink-0 text-slate-400" size={14} />
        <span>Your data is secure. We only use your resume to pre-fill your profile - you review and edit everything before it's saved.</span>
      </div>
    </div>
  )
}
