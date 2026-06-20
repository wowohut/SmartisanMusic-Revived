package com.smartisanos.music.ui.shell

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.smartisanos.music.R
import smartisanos.app.MenuDialog

@Composable
internal fun LegacyPlaylistNameDialogOverlay(
    request: LegacyPlaylistNameDialogRequest?,
    onDismiss: () -> Unit,
    onConfirm: (LegacyPlaylistNameDialogRequest, String) -> Unit,
) {
    val context = LocalContext.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnConfirm by rememberUpdatedState(onConfirm)
    val title = when (request) {
        is LegacyPlaylistNameDialogRequest.Create -> stringResource(request.titleRes)
        is LegacyPlaylistNameDialogRequest.Rename -> stringResource(R.string.playlist_rename_title)
        null -> ""
    }
    val confirmText = when (request) {
        is LegacyPlaylistNameDialogRequest.Create -> stringResource(R.string.rename_continue)
        is LegacyPlaylistNameDialogRequest.Rename -> stringResource(R.string.save)
        null -> ""
    }
    DisposableEffect(request, title, confirmText) {
        val activeRequest = request ?: return@DisposableEffect onDispose { }
        val dialog = LegacyPlaylistNameDialog(
            context = context,
            title = title,
            initialName = activeRequest.initialName,
            confirmText = confirmText,
            onDismiss = latestOnDismiss,
            onConfirm = { name ->
                latestOnConfirm(activeRequest, name)
            },
        )
        dialog.show()
        onDispose {
            dialog.dismissIfShowing()
        }
    }
}

@Composable
internal fun LegacyPlaylistDeleteDialog(
    request: LegacyPlaylistDeleteRequest?,
    onDismiss: () -> Unit,
    onConfirm: (LegacyPlaylistDeleteRequest) -> Unit,
) {
    val context = LocalContext.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnConfirm by rememberUpdatedState(onConfirm)
    DisposableEffect(request) {
        val activeRequest = request ?: return@DisposableEffect onDispose { }
        val dialog = MenuDialog(context).apply {
            setTitle(
                when (activeRequest) {
                    LegacyPlaylistDeleteRequest.RootSelected -> R.string.playlist_delete_confirm
                    LegacyPlaylistDeleteRequest.DetailPlaylist -> R.string.playlist_delete_single_confirm
                    LegacyPlaylistDeleteRequest.NeteaseDetailPlaylist -> R.string.netease_playlist_delete_single_confirm
                    LegacyPlaylistDeleteRequest.DetailTracks -> R.string.playlist_remove_song_confirm
                },
            )
            setPositiveButton(R.string.dialog_delete_conform) {
                latestOnConfirm(activeRequest)
            }
            setNegativeButton(
                View.OnClickListener {
                    latestOnDismiss()
                },
            )
            setOnCancelListener {
                latestOnDismiss()
            }
        }
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}

private class LegacyPlaylistNameDialog(
    private val context: Context,
    title: String,
    initialName: String,
    confirmText: String,
    private val onDismiss: () -> Unit,
    private val onConfirm: (String) -> Unit,
) {
    private val dialog = Dialog(context, R.style.MmsDialogTheme)
    private val editText: EditText
    private val confirmButton: Button

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.revone_global_dialog_shape_background)
        }
        dialog.requestWindowFeature(1)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            onDismiss()
        }

        root.addView(
            TextView(context).apply {
                text = title
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.status_bar_color_dialog))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )

        val content = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.revone_global_dialog_message_background)
        }
        val editOuter = FrameLayout(context).apply {
            setPadding(context.dpPx(18), context.dpPx(18), context.dpPx(18), context.dpPx(18))
            minimumHeight = context.dpPx(44)
        }
        val editFrame = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.edit_text_bg)
        }
        editText = EditText(context).apply {
            setSingleLine(true)
            setText(initialName)
            selectAll()
            setTextColor(context.getColor(R.color.editor_text_color))
            setHintTextColor(context.getColor(R.color.editor_hint_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = null
            setPadding(0, 0, 0, 0)
        }
        editFrame.addView(
            editText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = context.dpPx(12)
                topMargin = context.dpPx(6)
                rightMargin = context.dpPx(12)
                bottomMargin = context.dpPx(6)
            },
        )
        editFrame.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.quick_icon_delete)
                setOnClickListener {
                    editText.text = null
                }
            },
            LinearLayout.LayoutParams(context.dpPx(32), context.dpPx(32)),
        )
        editOuter.addView(
            editFrame,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dpPx(40), Gravity.CENTER),
        )
        content.addView(editOuter, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val cancelButton = dialogButton(context, context.getString(android.R.string.cancel), R.drawable.btn_text_color_selector).apply {
            setBackgroundResource(R.drawable.revone_dialog_button_left_bg_selector)
            setOnClickListener {
                dialog.dismiss()
                onDismiss()
            }
        }
        confirmButton = dialogButton(context, confirmText, R.color.blue_btn_text_color_selector).apply {
            setBackgroundResource(R.drawable.revone_dialog_button_right_bg_selector)
            setOnClickListener {
                onConfirm(editText.text.toString())
            }
        }
        buttons.addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        buttons.addView(
            View(context).apply {
                setBackgroundResource(R.drawable.revone_button_dialog_vertical_divider)
            },
            LinearLayout.LayoutParams(context.dpPx(1), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        buttons.addView(confirmButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )

        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    confirmButton.isEnabled = !s.isNullOrBlank()
                    confirmButton.invalidate()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        confirmButton.isEnabled = editText.text?.isNotBlank() == true
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.54f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.revone_global_dialog_content_width), WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        editText.postDelayed(
            {
                editText.requestFocus()
                (dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(editText, 0)
            },
            300L,
        )
    }

    fun dismissIfShowing() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun dialogButton(context: Context, text: String, textColorSelector: Int): Button {
        return Button(context).apply {
            gravity = Gravity.CENTER
            this.text = text
            setTextColor(context.getColorStateList(textColorSelector))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }
}
