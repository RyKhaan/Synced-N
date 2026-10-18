package com.example.syncedn

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/** Existing lyrics field with paste-specific formatting; normal typing is left alone. */
internal class ChordSheetInput(context: Context) : EditText(context) {
    init {
        typeface = Typeface.MONOSPACE
        setHorizontallyScrolling(true)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText) {
            return super.onTextContextMenuItem(id)
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return super.onTextContextMenuItem(id)
        val pasted = (0 until clip.itemCount).joinToString("\n") {
            clip.getItemAt(it).coerceToText(context).toString()
        }
        val formatted = SheetPasteFormatter.formatInsertion(pasted)
        val editable = text ?: return false
        val first = selectionStart.takeIf { it >= 0 } ?: editable.length
        val last = selectionEnd.takeIf { it >= 0 } ?: first
        val start = minOf(first, last)
        val end = maxOf(first, last)
        beginBatchEdit()
        try {
            editable.replace(start, end, formatted)
            setSelection((start + formatted.length).coerceAtMost(editable.length))
        } finally { endBatchEdit() }
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val original = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(original, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // Keyboard clipboard panels can deliver multi-line sheets without a Paste menu action.
                val value = text?.toString()
                return if (value != null && value.length > 1 && (value.contains('\n') || value.contains('\r'))) {
                    super.commitText(SheetPasteFormatter.formatInsertion(value), newCursorPosition)
                } else super.commitText(text, newCursorPosition)
            }
        }
    }
}
