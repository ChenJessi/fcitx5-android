package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.Prefs
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.preedit.PreeditContent
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.mechdancer.dependency.manager.must
import splitties.resources.str
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(),
    InputBroadcastReceiver {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()

    private var _currentIme: InputMethodEntry? = null

    val currentIme
        get() = _currentIme ?: InputMethodEntry(context.str(R.string._not_available_))

    override val view by lazy { context.frameLayout(R.id.keyboard_view) }

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context),
            NumberKeyboard.Name to NumberKeyboard(context),
            NumSymKeyboard.Name to NumSymKeyboard(context),
            SymbolKeyboard.Name to SymbolKeyboard(context)
        )
    }
    private var currentKeyboardName = ""
    private var lastSymbolType: String by Prefs.getInstance().lastSymbolLayout

    private val currentKeyboard: BaseKeyboard get() = keyboards.getValue(currentKeyboardName)

    private val keyActionListener = BaseKeyboard.KeyActionListener {
        if (it is KeyAction.LayoutSwitchAction)
            switchLayout(it.act)
        else
            commonKeyActionListener.listener.onKeyAction(it)
    }

    private fun switchLayout(to: String) {
        if (to == currentKeyboardName) return
        keyboards[currentKeyboardName]?.let {
            it.onDetach()
            view.removeView(it)
            it.keyActionListener = null
        }
        if (to.isEmpty()) {
            currentKeyboardName = lastSymbolType
        } else {
            currentKeyboardName = to
            if (to != TextKeyboard.Name && to != lastSymbolType) {
                lastSymbolType = to
            }
        }
        view.apply { add(currentKeyboard, lParams(matchParent, matchParent)) }
        currentKeyboard.keyActionListener = keyActionListener
        currentKeyboard.onAttach(service.editorInfo)
        currentKeyboard.onInputMethodChange(currentIme)
    }

    override fun onEditorInfoUpdate(info: EditorInfo?) {
        switchLayout(service.editorInfo?.inputType?.let {
            when (it and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
                InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
                else -> TextKeyboard.Name
            }
        } ?: TextKeyboard.Name)
        currentKeyboard.onEditorInfoChange(info)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        _currentIme = ime
        currentKeyboard.onInputMethodChange(currentIme)
    }

    override fun onPreeditUpdate(content: PreeditContent) {
        currentKeyboard.onPreeditChange(service.editorInfo, content)
    }

    override fun onAttached() {
        if (currentKeyboardName.isEmpty()) {
            currentKeyboardName = TextKeyboard.Name
            view.apply { add(currentKeyboard, lParams(matchParent, matchParent)) }
        }
        currentKeyboard.keyActionListener = keyActionListener
    }

    override fun onDetached() {
        currentKeyboard.keyActionListener = null
    }

}