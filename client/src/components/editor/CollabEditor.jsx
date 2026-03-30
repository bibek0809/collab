import { useRef, useCallback, useEffect } from 'react';

/**
 * Collaborative contentEditable editor.
 *
 * Maintains a local `charMap` (array of {id, value}) that mirrors the CRDT
 * character sequence on the server. On every local keystroke it computes
 * the diff against the previous text, generates INSERT / DELETE operations
 * via `onLocalOp`, and updates the char-map.
 *
 * Remote operations received via WebSocket call `applyRemoteOp` from the
 * parent, which mutates the char-map and the DOM.
 */
export default function CollabEditor({
  initialContent,
  siteId,
  onLocalOp,
  onCursorMove,
  remoteOp,          // latest remote operation passed down
  remoteOpSeq,       // incrementing counter so we can react to each one
}) {
  const editorRef = useRef(null);
  const charMapRef = useRef([]);
  const clockRef = useRef(0);
  const suppressRef = useRef(false); // suppress onInput when we update DOM ourselves

  // ── initialise char-map from loaded content ────────
  useEffect(() => {
    if (initialContent == null) return;
    const chars = [];
    for (let i = 0; i < initialContent.length; i++) {
      chars.push({ id: `init:${i}`, value: initialContent[i] });
    }
    charMapRef.current = chars;

    if (editorRef.current) {
      suppressRef.current = true;
      editorRef.current.innerText = initialContent;
      suppressRef.current = false;
    }
  }, [initialContent]);

  // ── apply remote operation (INSERT / DELETE) ───────
  useEffect(() => {
    if (!remoteOp) return;
    const el = editorRef.current;
    if (!el) return;

    suppressRef.current = true;

    if (remoteOp.opType === 'INSERT' && remoteOp.characterValue) {
      const prevIdx = remoteOp.previousId
        ? charMapRef.current.findIndex((c) => c.id === remoteOp.previousId)
        : -1;
      const insertAt = prevIdx + 1;
      charMapRef.current.splice(insertAt, 0, {
        id: remoteOp.characterId,
        value: remoteOp.characterValue,
      });
    } else if (remoteOp.opType === 'DELETE') {
      const idx = charMapRef.current.findIndex((c) => c.id === remoteOp.characterId);
      if (idx !== -1) charMapRef.current.splice(idx, 1);
    }

    // rebuild text node — preserve cursor
    const sel = window.getSelection();
    let cursorPos = 0;
    if (sel.rangeCount > 0 && el.contains(sel.anchorNode)) {
      cursorPos = getCaretOffset(el);
    }
    el.innerText = charMapRef.current.map((c) => c.value).join('');
    restoreCaret(el, cursorPos);
    suppressRef.current = false;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remoteOpSeq]);

  // ── handle local input ─────────────────────────────
  const handleInput = useCallback(() => {
    if (suppressRef.current) return;
    const el = editorRef.current;
    if (!el) return;

    const newText = el.innerText;
    const oldChars = charMapRef.current;
    const oldText = oldChars.map((c) => c.value).join('');

    if (newText === oldText) return;

    // simple diff: find the first index of change and the tail overlap
    let prefixLen = 0;
    while (prefixLen < oldText.length && prefixLen < newText.length && oldText[prefixLen] === newText[prefixLen]) {
      prefixLen++;
    }
    let oldSuffixLen = 0;
    while (
      oldSuffixLen < (oldText.length - prefixLen) &&
      oldSuffixLen < (newText.length - prefixLen) &&
      oldText[oldText.length - 1 - oldSuffixLen] === newText[newText.length - 1 - oldSuffixLen]
    ) {
      oldSuffixLen++;
    }

    const deletedCount = oldText.length - prefixLen - oldSuffixLen;
    const insertedStr = newText.substring(prefixLen, newText.length - oldSuffixLen);

    // process deletions
    for (let i = 0; i < deletedCount; i++) {
      const delIdx = prefixLen; // always delete at prefixLen (chars shift)
      if (delIdx < charMapRef.current.length) {
        const ch = charMapRef.current[delIdx];
        clockRef.current++;
        onLocalOp?.({
          opType: 'DELETE',
          characterId: ch.id,
          siteId,
          logicalTimestamp: clockRef.current,
          versionVector: {},
        });
        charMapRef.current.splice(delIdx, 1);
      }
    }

    // process insertions
    for (let i = 0; i < insertedStr.length; i++) {
      const insertIdx = prefixLen + i;
      clockRef.current++;
      const charId = `${siteId}:${clockRef.current}`;
      const prevId =
        insertIdx > 0 && charMapRef.current[insertIdx - 1]
          ? charMapRef.current[insertIdx - 1].id
          : null;

      charMapRef.current.splice(insertIdx, 0, { id: charId, value: insertedStr[i] });

      onLocalOp?.({
        opType: 'INSERT',
        characterId: charId,
        characterValue: insertedStr[i],
        previousId: prevId,
        siteId,
        logicalTimestamp: clockRef.current,
        versionVector: {},
      });
    }
  }, [siteId, onLocalOp]);

  // ── cursor / selection tracking ────────────────────
  const handleSelect = useCallback(() => {
    const el = editorRef.current;
    if (!el) return;
    const pos = getCaretOffset(el);
    const sel = window.getSelection();
    const start = pos;
    const end = sel.rangeCount > 0 ? start + Math.abs(sel.getRangeAt(0).toString().length) : start;
    onCursorMove?.(pos, start, end);
  }, [onCursorMove]);

  return (
    <div className="bg-white rounded-2xl border border-ink-100 shadow-sm min-h-[480px] p-8 md:p-12">
      <div
        ref={editorRef}
        className="editor-area font-sans text-ink-800"
        contentEditable
        suppressContentEditableWarning
        data-placeholder="Start typing… your changes sync in real-time with other editors."
        onInput={handleInput}
        onSelect={handleSelect}
        onKeyUp={handleSelect}
        onClick={handleSelect}
        spellCheck
      />
    </div>
  );
}

// ── DOM helpers ──────────────────────────────────────

function getCaretOffset(el) {
  const sel = window.getSelection();
  if (!sel.rangeCount) return 0;
  const range = sel.getRangeAt(0).cloneRange();
  range.selectNodeContents(el);
  range.setEnd(sel.anchorNode, sel.anchorOffset);
  return range.toString().length;
}

function restoreCaret(el, offset) {
  try {
    const sel = window.getSelection();
    const range = document.createRange();

    let node = el.firstChild;
    if (!node) {
      range.selectNodeContents(el);
      range.collapse(true);
    } else {
      // Walk text nodes to find the right offset
      let remaining = offset;
      const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
      let current;
      while ((current = walker.nextNode())) {
        if (remaining <= current.textContent.length) {
          range.setStart(current, remaining);
          range.collapse(true);
          break;
        }
        remaining -= current.textContent.length;
      }
    }

    sel.removeAllRanges();
    sel.addRange(range);
  } catch {
    /* sometimes DOM is not ready */
  }
}
