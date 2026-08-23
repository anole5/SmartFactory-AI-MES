// SSE 流式客户端（第 7 周）：裸 fetch + ReadableStream 手写 SSE 行解析，不走 axios
// （axios 会缓冲响应体，拿不到逐块事件；vite 代理 SSE 透传无缓冲）
//
// 后端事件协议（第 7 周 T2）：
//   event:meta  {intent, ...}   （意图先行，可带 exceptionId/reportDate 等扩展）
//   event:delta {content}       （×N，打字机正文）
//   event:done  {recordId,intent,answer,references,fallback,...}（answer === 全部 delta 拼接）
//   event:error {message}
// 非 200：后端统一 ApiResult JSON 错误体（401/403/400），取 message 交给 onError。

export interface StreamDone {
  recordId?: string
  intent?: string
  answer: string
  references?: { docId: string; docName: string }[]
  fallback?: boolean
  summary?: string
  reportDate?: string
  exceptionId?: string
}

export interface StreamHandlers {
  onMeta?: (meta: Record<string, unknown>) => void
  onDelta?: (content: string) => void
  onDone?: (done: StreamDone) => void
  onError?: (message: string) => void
}

/**
 * POST 一个 SSE 流式请求并逐事件回调。
 * - 停止按钮：传 AbortSignal，abort 后静默返回（不触发 onError）
 * - 首事件前失败：fetch 抛错/非 200/EOF 无 done 都会走 onError，
 *   调用方据"是否已收到事件"决定回退非流式接口
 */
export async function streamPost(
  path: string,
  body: Record<string, unknown>,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem('mes_token')
  let res: Response
  try {
    res = await fetch('/api' + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal,
    })
  } catch {
    if (signal?.aborted) return // 停止按钮断开：正常用户行为，静默
    handlers.onError?.('网络异常，请检查后端服务')
    return
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const parsed = JSON.parse(text) as { message?: string }
      if (parsed?.message) message = parsed.message
    } catch {
      /* 非 JSON 错误体，用默认文案 */
    }
    handlers.onError?.(message)
    return
  }
  if (!res.body) {
    handlers.onError?.('流式响应无内容')
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'message'
  const dataLines: string[] = []
  let doneReceived = false

  const flush = () => {
    const data = dataLines.join('\n').trim()
    dataLines.length = 0
    const eventName = currentEvent
    currentEvent = 'message'
    if (!data) return
    let parsed: Record<string, unknown> | null = null
    try {
      parsed = JSON.parse(data)
    } catch {
      return // 非 JSON 事件体：忽略（未知事件同此处理）
    }
    if (!parsed) return
    if (eventName === 'meta') handlers.onMeta?.(parsed)
    else if (eventName === 'delta') handlers.onDelta?.(String(parsed.content ?? ''))
    else if (eventName === 'done') {
      doneReceived = true
      handlers.onDone?.(parsed as unknown as StreamDone)
    } else if (eventName === 'error') handlers.onError?.(String(parsed.message ?? 'AI 服务异常'))
    // 未知事件名：忽略（协议向后兼容）
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let idx: number
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) currentEvent = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        flush()
      }
    }
    // EOF：没收到 done 属于连接中断（正常终止必有 done 收尾）
    if (!doneReceived && !signal?.aborted) {
      handlers.onError?.('连接中断，请重试')
    }
  } catch {
    if (signal?.aborted) return
    handlers.onError?.('流式读取中断，请重试')
  }
}
