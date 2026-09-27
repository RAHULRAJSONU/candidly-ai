import { ApiError } from '../../api/client'

/** Turns any error caught from an `api.*` call into a readable message - network
 * failures/JSON parse errors are not `ApiError`s (see api/client.ts), so this covers
 * both cases instead of every call site reimplementing the same `instanceof` check. */
export function describeError(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong. Please try again.'
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: '12px 16px',
        borderRadius: 8,
        background: '#fef2f2',
        border: '1px solid #fecaca',
        color: '#991b1b',
        fontSize: 14,
      }}
      role="alert"
    >
      {message}
    </div>
  )
}
