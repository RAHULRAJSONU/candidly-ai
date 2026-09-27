import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/** Catches render-time throws anywhere in the tree below it so a bug in one page shows
 * a recovery screen instead of a blank white app (there was previously no boundary at
 * all - see CLAUDE.md's "review error handling" pass). Route changes reset it via `key`
 * in AppShell/App so navigating away from the broken page recovers without a reload. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 32, maxWidth: 560 }}>
          <h2 style={{ marginBottom: 8 }}>Something went wrong</h2>
          <p style={{ color: '#6b7280', marginBottom: 16 }}>
            {this.state.error.message || 'An unexpected error occurred while rendering this page.'}
          </p>
          <button onClick={() => this.setState({ error: null })}>Try again</button>
        </div>
      )
    }
    return this.props.children
  }
}
