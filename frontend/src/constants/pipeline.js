export const STAGES = {
  l1: { id: 'l1', label: 'Diff Sanitization', event: 'l1.complete' },
  l2: { id: 'l2', label: 'File Analysis', event: 'l2.complete' },
  l3: { id: 'l3', label: 'Global Aggregation', event: 'l3.complete' },
}

export const EVENT_TYPES = {
  TASK_STARTED: 'task.started',
  L1_COMPLETE: 'l1.complete',
  L2_FILE_START: 'l2.file.start',
  L2_FILE_CHUNK: 'l2.file.chunk',
  L2_FILE_COMPLETE: 'l2.file.complete',
  L2_COMPLETE: 'l2.complete',
  L3_START: 'l3.start',
  L3_COMPLETE: 'l3.complete',
  RESULT: 'result',
  ERROR: 'error',
}

export const INITIAL_STAGES = {
  l1: 'pending',
  l2: 'pending',
  l3: 'pending',
}
