export default function StatusBar({ connected, charCount, documentId, onlineCount }) {
  return (
    <div className="flex items-center justify-between mt-4 px-2 text-[11px] text-ink-400 select-none">
      <div className="flex items-center gap-3">
        <span className="flex items-center gap-1">
          <span className={`w-1.5 h-1.5 rounded-full ${connected ? 'bg-emerald-500' : 'bg-red-400'}`} />
          {connected ? 'Connected' : 'Reconnecting…'}
        </span>
        <span>{charCount.toLocaleString()} chars</span>
      </div>
      <div className="flex items-center gap-3">
        <span className="font-mono">{documentId}</span>
        <span>{onlineCount} online</span>
      </div>
    </div>
  );
}
