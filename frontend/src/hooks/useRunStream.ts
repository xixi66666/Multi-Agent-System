import { useEffect } from 'react'
import type { RunEvent } from '../types'

export function useRunStream(runId: string | null, onEvent: (event: RunEvent) => void) {
  useEffect(() => {
    if (!runId) return

    const source = new EventSource(`/api/runs/${runId}/events/stream`)
    const listener = (message: MessageEvent<string>) => {
      onEvent(JSON.parse(message.data) as RunEvent)
    }
    source.addEventListener('run-event', listener as EventListener)
    return () => {
      source.removeEventListener('run-event', listener as EventListener)
      source.close()
    }
  }, [runId, onEvent])
}
