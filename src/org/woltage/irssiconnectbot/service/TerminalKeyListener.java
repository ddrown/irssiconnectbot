/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2010 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.woltage.irssiconnectbot.service;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import org.woltage.irssiconnectbot.R;
import org.woltage.irssiconnectbot.TerminalView;
import org.woltage.irssiconnectbot.bean.SelectionArea;
import org.woltage.irssiconnectbot.util.PreferenceConstants;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.method.CharacterPickerDialog;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.vt320;

/**
 * @author kenny
 *
 */
@SuppressWarnings("deprecation") // for ClipboardManager
public class TerminalKeyListener implements OnKeyListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "ConnectBot.OnKeyListener";

    public final static int META_CTRL_ON = 0x01;
    public final static int META_CTRL_LOCK = 0x02;
    public final static int META_ALT_ON = 0x04;
    public final static int META_ALT_LOCK = 0x08;
    public final static int META_SHIFT_ON = 0x10;
    public final static int META_SHIFT_LOCK = 0x20;
    public final static int META_SLASH = 0x40;
    public final static int META_TAB = 0x80;

    // The bit mask of momentary and lock states for each
    public final static int META_CTRL_MASK = META_CTRL_ON | META_CTRL_LOCK;
    public final static int META_ALT_MASK = META_ALT_ON | META_ALT_LOCK;
    public final static int META_SHIFT_MASK = META_SHIFT_ON | META_SHIFT_LOCK;

    // backport constants from api level 11
    public final static int KEYCODE_ESCAPE = 111;
    public final static int HC_META_CTRL_ON = 4096;
    public final static int KEYCODE_PAGE_UP = 92;
    public final static int KEYCODE_PAGE_DOWN = 93;

    // All the transient key codes
    public final static int META_TRANSIENT = META_CTRL_ON | META_ALT_ON
            | META_SHIFT_ON;

    private final TerminalManager manager;
    private final TerminalBridge bridge;
    private final VDUBuffer buffer;

    private String camera;
    private boolean delOnShiftBackspace;
    private String desireZSkandinavianFixes;
    private boolean dPadEscape;
    private boolean fKeysUsingShiftNumbers;
    private String keymode;
    private String searchbutton;
    private boolean xperiaProFixes;

    private boolean hardKeyboard = false;

    private int metaState = 0;

    private int mDeadKey = 0;

    // TODO add support for the new API.
    private ClipboardManager clipboard = null;

    private boolean selectingForCopy = false;
    private final SelectionArea selectionArea;

    private String encoding;

    private final SharedPreferences prefs;

    public TerminalKeyListener(TerminalManager manager,
            TerminalBridge bridge,
            VDUBuffer buffer,
            String encoding) {
        this.manager = manager;
        this.bridge = bridge;
        this.buffer = buffer;
        this.encoding = encoding;

        selectionArea = bridge.getSelectionArea();

        prefs = PreferenceManager.getDefaultSharedPreferences(manager);
        prefs.registerOnSharedPreferenceChangeListener(this);

        hardKeyboard = (manager.res.getConfiguration().keyboard
                == Configuration.KEYBOARD_QWERTY);

        hardKeyboard = hardKeyboard && !Build.MODEL.contains("Transformer");

        updateSettings();
    }

    private static SparseArray<String> PICKER_SETS =
        new SparseArray<String>();
    static {
        PICKER_SETS.put(KeyCharacterMap.PICKER_DIALOG_INPUT,
                "!@#$%&*?/:_\"'()-+;,.€¥£~=\\^[]¡¿{}<>|Þþ§©®±÷ÖöÄäÅåØøÆæÜüẞß¶µ");
    };

    /**
     * Handle onKey() events coming down from a {@link TerminalView} above us.
     * Modify the keys to make more sense to a host then pass it to the transport.
     */
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        try {
            // skip keys if we aren't connected yet or have been disconnected
            if (bridge.isDisconnected() || bridge.transport == null)
                return false;

            final boolean hardKeyboardHidden = manager.hardKeyboardHidden;
            // Ignore all key-up events except for the special keys
            if (event.getAction() == KeyEvent.ACTION_UP) {
                // Volume key beeps are fired on key up events. Consume them to mute the beep when resizing
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    return true;
                }

                // There's nothing here for virtual keyboard users.
                if (!hardKeyboard || (hardKeyboard && hardKeyboardHidden))
                    return false;

                if (PreferenceConstants.KEYBOARDFIX_KEYMODE_RIGHT.equals(keymode)) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                            && (metaState & META_SLASH) != 0) {
                        metaState &= ~(META_SLASH | META_TRANSIENT);
                        bridge.transport.write('/');
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
                            && (metaState & META_TAB) != 0) {
                        metaState &= ~(META_TAB | META_TRANSIENT);
                        sendTab();
                        return true;
                    }
                } else if (PreferenceConstants.KEYBOARDFIX_KEYMODE_LEFT.equals(keymode)) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_LEFT
                            && (metaState & META_SLASH) != 0) {
                        metaState &= ~(META_SLASH | META_TRANSIENT);
                        bridge.transport.write('/');
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
                            && (metaState & META_TAB) != 0) {
                        metaState &= ~(META_TAB | META_TRANSIENT);
                        sendTab();
                        return true;
                    }
                }

                return false;
            }

            // check for terminal resizing keys
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                bridge.increaseFontSize();
                return true;
            } else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                bridge.decreaseFontSize();
                return true;
            }

            bridge.resetScrollPosition();

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN &&
                    event.getAction() == KeyEvent.ACTION_MULTIPLE) {
                byte[] input = event.getCharacters().getBytes(encoding);
                bridge.transport.write(input);
                return true;
            }

            int curMetaState = event.getMetaState();
            final int orgMetaState = curMetaState;

            if ((metaState & META_SHIFT_MASK) != 0) {
                curMetaState |= KeyEvent.META_SHIFT_ON;
            }

            if ((metaState & META_ALT_MASK) != 0) {
                curMetaState |= KeyEvent.META_ALT_ON;
            }

            int uchar = event.getUnicodeChar(curMetaState);

            if ((uchar & KeyCharacterMap.COMBINING_ACCENT) != 0) {
                mDeadKey = uchar & KeyCharacterMap.COMBINING_ACCENT_MASK;
                return true;
            }

            if (mDeadKey != 0) {
                uchar = KeyCharacterMap.getDeadChar(mDeadKey, keyCode);
                mDeadKey = 0;
            }

            //Show up the CharacterPickerDialog when the SYM key is pressed
            if( (keyCode == KeyEvent.KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_PICTSYMBOLS ||
                    uchar == KeyCharacterMap.PICKER_DIALOG_INPUT) && v != null) {
                showCharPickerDialog(v);
                if ((metaState & META_ALT_ON) != 0) { // reset fn-key state
                    metaState &= ~META_ALT_ON;
                    bridge.redraw();
                }
                return true;
            }

            // Android keyboard kcm files map ENTER to '\n' (0x0A), which is incorrect.
            // A keyboard needs to send '\r' (0x0D) when the ENTER button is pressed.
            // It may be converted to a different end-of-line sequence by a terminal program.
            // In case KEYCODE_ENTER is sent as-is, many programs will misinterpret the key
            // as ^J instead of RETURN.
            // To prevent this, only handle printing characters >= 0x20 (space) here.
            // KEYCODE_ENTER (0x0A), KEYCODE_SEARCH (0x00), KEYCODE_TAB (0x09) are handled
            // lateron below, the rest of the escape sequences have already been handled
            // above as KEYCODE_UNKNOWN.
            if (uchar >= 0x20) {
                metaState &= ~(META_SLASH | META_TAB);

                // xperia Pro / Mini fixes -> map unused s and z keys to | and \
                if (xperiaProFixes && (metaState & META_ALT_MASK) != 0) {
                    if (keyCode == KeyEvent.KEYCODE_S) {
                        bridge.transport.write('|');
                        metaState &= ~META_TRANSIENT;
                        bridge.redraw();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_Z) {
                        bridge.transport.write(0x5C);
                        metaState &= ~META_TRANSIENT;
                        bridge.redraw();
                        return true;
                    }
                }

                // Remove shift and alt modifiers
                final int lastMetaState = metaState;
                metaState &= ~(META_SHIFT_ON | META_ALT_ON);
                if (metaState != lastMetaState) {
                    bridge.redraw();
                }

                if ((metaState & META_CTRL_MASK) != 0) {
                    metaState &= ~META_CTRL_ON;
                    bridge.redraw();

                    // If there is no hard keyboard or there is a hard keyboard currently hidden,
                    // CTRL-1 through CTRL-9 will send F1 through F9
                    if ((!hardKeyboard || (hardKeyboard && hardKeyboardHidden))
                            && sendFunctionKey(uchar))
                        return true;

                    uchar = keyAsControl(uchar);
                }

                // if enabled, handle pressing f-keys using shift
                if ((hardKeyboard && !hardKeyboardHidden)
                        && fKeysUsingShiftNumbers
                        && (curMetaState & KeyEvent.META_SHIFT_ON) != 0
                        && sendFunctionKey(uchar)) {
                    return true;
                }

                if (uchar < 0x80) {
                    bridge.transport.write(uchar);
                } else {

                    // HTC Desire Z skandinavian fixes
                    if (!desireZSkandinavianFixes.equals(PreferenceConstants.KEYBOARDFIX_DESIREZSKANDINAVIAN_OFF)) {
                        /*
                         * curMetaState == 0 normal mode
                         * curMetaState == 1 if shift key is pressed or locked
                         * curMetaState == 2 if FN key is pressed or locked
                         * curMetaState == 3 if FN AND shift key is pressed or locked
                         */
                        if(curMetaState == 0) {
                            // Desire Z "All fixes (Æ = Alt, Ø = Ctrl)"
                            if (desireZSkandinavianFixes.equals(PreferenceConstants.KEYBOARDFIX_DESIREZSKANDINAVIAN_ALL)) {
                                // Bind's Ø and ø to CTRL
                                if(uchar == 0xd8 || uchar == 0xf8) {
                                    metaPress(META_CTRL_ON);
                                    bridge.redraw();
                                    return true;
                                } else if(uchar == 0xc6 || uchar == 0xe6) { // Bind's Æ and æ to ALT
                                    sendEscape();
                                    bridge.redraw();
                                    return true;
                                }
                            }
                            // Desire Z, lowercase if curMetaState == 0! Problem with HW keymapping?
                            if(uchar == 0xC4) //Ä
                                uchar = 0xE4; //ä
                            else if(uchar == 0xD6) //Ö
                                uchar = 0xF6; //ö
                            else if(uchar == 0xC5) //Å
                                uchar = 0xE5; //å
                            else if(uchar == 0xD8) //Ø
                                uchar = 0xF8; //ø
                            else if(uchar == 0xC6) //Æ
                                uchar = 0xE6; //æ
                        }
                        else if(curMetaState == 2) {
                            // Desire Z, <>| if FN pressed or locked
                            if(uchar == 0xC4 || uchar == 0xE4) //Ä or ä
                                uchar = 0x3C; //<
                            else if(uchar == 0xD6 || uchar == 0xF6) //Ö or ö
                                uchar = 0x3E; //>
                            else if(uchar == 0xC5 || uchar == 0XE5) //Å or å
                                uchar = 0x7C; //|
                        }
                    }

                    // TODO write encoding routine that doesn't allocate each time
                    bridge.transport.write(new String(Character.toChars(uchar)).getBytes(encoding));
                }

                return true;
            }

            // send ctrl and meta-keys as appropriate
            if (!hardKeyboard || hardKeyboardHidden) {
                int k = event.getUnicodeChar(0);
                int k0 = k;
                boolean sendCtrl = false;
                boolean sendMeta = false;
                if (k != 0) {
                    if ((orgMetaState & HC_META_CTRL_ON) != 0) {
                        k = keyAsControl(k);
                        if (k != k0)
                            sendCtrl = true;
                        // send F1-F10 via CTRL-1 through CTRL-0
                        if (!sendCtrl && sendFunctionKey(k0))
                            return true;
                    } else if ((orgMetaState & KeyEvent.META_ALT_ON) != 0) {
                        sendMeta = true;
                        sendEscape();
                    }
                    if (sendMeta || sendCtrl) {
                        bridge.transport.write(k);
                        return true;
                    }
                }
            }

            if (hardKeyboard && !hardKeyboardHidden &&
                    event.getRepeatCount() == 0) {

                // try handling keymode shortcuts
                if (PreferenceConstants.KEYBOARDFIX_KEYMODE_RIGHT.equals(keymode)) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_ALT_RIGHT:
                        metaState |= META_SLASH;
                        return true;
                    case KeyEvent.KEYCODE_SHIFT_RIGHT:
                        metaState |= META_TAB;
                        return true;
                    case KeyEvent.KEYCODE_SHIFT_LEFT:
                        metaPress(META_SHIFT_ON);
                        return true;
                    case KeyEvent.KEYCODE_ALT_LEFT:
                        metaPress(META_ALT_ON);
                        return true;
                    }
                } else if (PreferenceConstants.KEYBOARDFIX_KEYMODE_LEFT.equals(keymode)) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_ALT_LEFT:
                        metaState |= META_SLASH;
                        return true;
                    case KeyEvent.KEYCODE_SHIFT_LEFT:
                        metaState |= META_TAB;
                        return true;
                    case KeyEvent.KEYCODE_SHIFT_RIGHT:
                        metaPress(META_SHIFT_ON);
                        return true;
                    case KeyEvent.KEYCODE_ALT_RIGHT:
                        metaPress(META_ALT_ON);
                        return true;
                    }
                }

                switch (keyCode) {
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT:
                    metaPress(META_ALT_ON);
                    return true;
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    metaPress(META_SHIFT_ON);
                    return true;
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT:
                    metaPress(META_CTRL_ON);
                    return true;
                }
            }

            //Handle d-pad arrows in copy mode
            if (bridge.isSelectingForCopy()) {
                switch(keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    selectionArea.decrementColumn();
                    bridge.redraw();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    selectionArea.decrementRow();
                    bridge.redraw();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    selectionArea.incrementRow();
                    bridge.redraw();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    selectionArea.incrementColumn();
                    bridge.redraw();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (selectionArea.isSelectingOrigin())
                        selectionArea.finishSelectingOrigin();
                    else {
                        if (clipboard != null) {
                            // copy selected area to clipboard
                            String copiedText = selectionArea.copyFrom(buffer);

                            clipboard.setText(copiedText);
                            //Nofify user
                            ((TerminalView) v).notifyUser(manager.res.getString(
                                    R.string.console_copy_done,copiedText.length() ) );
                            bridge.setSelectingForCopy(false);
                            selectionArea.reset();
                        }
                    }
                    bridge.redraw();
                    return true;
                }
            }


            // look for special chars
            switch(keyCode) {
            case KEYCODE_ESCAPE:
                sendEscape();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
                if (PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON_TAB.equals(searchbutton)) {
                    sendTab();
                } else if (PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON_META.equals(searchbutton)) {
                    sendEscape();
                } else if (PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON_HARDMETA_SOFTURLSCAN.equals(searchbutton)) {
                    if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) == 0) { // hardware key
                        sendEscape();
                    } else { // softkey
                        urlScan(v);
                    }
                } else {
                    urlScan(v);
                }
                return true;
            case KeyEvent.KEYCODE_TAB:
                sendTab();
                return true;
            case KEYCODE_PAGE_DOWN:
                ((vt320)buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', getStateForBuffer());
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KEYCODE_PAGE_UP:
                ((vt320)buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', getStateForBuffer());
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                ((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
                bridge.transport.write("[1~".getBytes());
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                ((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
                bridge.transport.write("[4~".getBytes());
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                // check to see which shortcut the camera button triggers
                if(PreferenceConstants.CAMERA_CTRLA_SPACE.equals(camera)) {
                    bridge.transport.write(0x01);
                    bridge.transport.write(' ');
                } else if(PreferenceConstants.CAMERA_CTRLA.equals(camera)) {
                    bridge.transport.write(0x01);
                } else if(PreferenceConstants.CAMERA_ESC.equals(camera)) {
                    ((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
                } else if(PreferenceConstants.CAMERA_ESC_A.equals(camera)) {
                    ((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
                    bridge.transport.write('a');
                }

                break;

            case KeyEvent.KEYCODE_DEL:
                //Delete key and shift/caps+backspace or alt/capslock fix for Desire Z
                if (delOnShiftBackspace && ( ((metaState & META_ALT_MASK) != 0) || ((metaState & META_SHIFT_MASK) != 0))) {
                    ((vt320) buffer).keyPressed(vt320.KEY_DELETE, ' ', getStateForBuffer());
                } else {
                    ((vt320) buffer).keyPressed(vt320.KEY_BACK_SPACE, ' ', getStateForBuffer());
                }
                metaState &= ~META_TRANSIENT;
                return true;
            case KeyEvent.KEYCODE_ENTER:
                ((vt320)buffer).keyTyped(vt320.KEY_ENTER, ' ', 0);
                metaState &= ~META_TRANSIENT;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if ((metaState & META_ALT_MASK) != 0) {
                    ((vt320) buffer).keyPressed(vt320.KEY_HOME, ' ',
                            getStateForBuffer());
                } else {
                    ((vt320) buffer).keyPressed(vt320.KEY_LEFT, ' ',
                            getStateForBuffer());
                }
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if ((metaState & META_ALT_MASK) != 0) {
                    ((vt320)buffer).keyPressed(vt320.KEY_PAGE_UP, ' ',
                            getStateForBuffer());
                } else {
                    ((vt320) buffer).keyPressed(vt320.KEY_UP, ' ',
                            getStateForBuffer());
                }
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if ((metaState & META_ALT_MASK) != 0) {
                    ((vt320)buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ',
                            getStateForBuffer());
                } else {
                    ((vt320) buffer).keyPressed(vt320.KEY_DOWN, ' ',
                            getStateForBuffer());
                }
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if ((metaState & META_ALT_MASK) != 0) {
                    ((vt320) buffer).keyPressed(vt320.KEY_END, ' ',
                            getStateForBuffer());
                } else {
                    ((vt320) buffer).keyPressed(vt320.KEY_RIGHT, ' ',
                            getStateForBuffer());
                }
                metaState &= ~META_TRANSIENT;
                bridge.tryKeyVibrate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SWITCH_CHARSET:
                if (keyCode == KeyEvent.KEYCODE_SWITCH_CHARSET && !xperiaProFixes)
                    return true;
                if ((metaState & META_CTRL_ON) != 0) {
                    if (dPadEscape) {
                        sendEscape();
                    }
                    metaState &= ~META_CTRL_ON;
                } else
                    metaPress(META_CTRL_ON);
                bridge.redraw();
                return true;

            }

        } catch (IOException e) {
            Log.e(TAG, "Problem while trying to handle an onKey() event", e);
            try {
                bridge.transport.flush();
            } catch (IOException ioe) {
                Log.d(TAG, "Our transport was closed, dispatching disconnect event");
                bridge.dispatchDisconnect(false);
            }
        } catch (NullPointerException npe) {
            Log.d(TAG, "Input before connection established ignored.");
            return true;
        }

        return false;
    }

    public int keyAsControl(int key) {
        // Support CTRL-a through CTRL-z
        if (key >= 0x61 && key <= 0x7A)
            key -= 0x60;
        // Support CTRL-A through CTRL-_
        else if (key >= 0x41 && key <= 0x5F)
            key -= 0x40;
        // CTRL-space sends NULL
        else if (key == 0x20)
            key = 0x00;
        // CTRL-? sends DEL
        else if (key == 0x3F)
            key = 0x7F;
        return key;
    }

    public void sendEscape() {
        ((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
    }

    public void sendTab() {
        ((vt320) buffer).write(0x09);
    }

    /**
     * Send Function Keys F10,F1-F9, if parameter is the ASCII code '0'-'9'
     *
     * @param key
     *         The ASCII code of the key, NOT the android KeyCode
     * @return {@code true} if the parameter was 0-9 and a function key was sent, {@code false} otherwise.
     */
    private boolean sendFunctionKey(int key) {
        if (key >= '0' && key <= '9') {
            ((vt320) buffer).keyPressed(vt320.KEY_F1 + (key == '0' ? 9 : key - '1'), ' ', 0);
            return true;
        }
        return false;
    }

    /**
     * Handle meta key presses where the key can be locked on.
     * <p>
     * 1st press: next key to have meta state<br />
     * 2nd press: meta state is locked on<br />
     * 3rd press: disable meta state
     *
     * @param code
     */
    public void metaPress(int code) {
        if ((metaState & (code << 1)) != 0) {
            metaState &= ~(code << 1);
        } else if ((metaState & code) != 0) {
            metaState &= ~code;
            metaState |= code << 1;
        } else
            metaState |= code;
        bridge.redraw();
    }

    private int getStateForBuffer() {
        int bufferState = 0;

        if ((metaState & META_CTRL_MASK) != 0)
            bufferState |= vt320.KEY_CONTROL;
        if ((metaState & META_SHIFT_MASK) != 0)
            bufferState |= vt320.KEY_SHIFT;
        if ((metaState & META_ALT_MASK) != 0)
            bufferState |= vt320.KEY_ALT;

        return bufferState;
    }

    public int getMetaState() {
        return metaState;
    }

    public int getDeadKey() {
        return mDeadKey;
    }

    public void setClipboardManager(ClipboardManager clipboard) {
        this.clipboard = clipboard;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (PreferenceConstants.KEYBOARDFIX_KEYMODE.equals(key)
                || PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON.equals(key)
                || PreferenceConstants.KEYBOARDFIX_DELBACKSPACE.equals(key)
                || PreferenceConstants.KEYBOARDFIX_XPERIAPRO.equals(key)
                || PreferenceConstants.CAMERA.equals(key)
                || PreferenceConstants.KEYBOARDFIX_DESIREZSKANDINAVIAN.equals(key)
                || PreferenceConstants.KEYBOARDFIX_SHIFT_FKEYS.equals(key)
                || PreferenceConstants.KEYBOARDFIX_DPAD_ESCAPE.equals(key)) {
            updateSettings();
        }
    }

    private void updateSettings() {
        keymode = prefs.getString(PreferenceConstants.KEYBOARDFIX_KEYMODE,
                PreferenceConstants.KEYBOARDFIX_KEYMODE_DISABLED);
        searchbutton = prefs.getString(PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON,
                PreferenceConstants.KEYBOARDFIX_SEARCH_BUTTON_URLSCAN);
        delOnShiftBackspace = prefs.getBoolean(PreferenceConstants.KEYBOARDFIX_DELBACKSPACE, false);
        xperiaProFixes = prefs.getBoolean(PreferenceConstants.KEYBOARDFIX_XPERIAPRO, false);
        camera = prefs.getString(PreferenceConstants.CAMERA,
                PreferenceConstants.CAMERA_CTRLA_SPACE);
        desireZSkandinavianFixes = prefs.getString(PreferenceConstants.KEYBOARDFIX_DESIREZSKANDINAVIAN,
                PreferenceConstants.KEYBOARDFIX_DESIREZSKANDINAVIAN_OFF);
        dPadEscape = prefs.getBoolean(PreferenceConstants.KEYBOARDFIX_DPAD_ESCAPE, false);
        fKeysUsingShiftNumbers = prefs.getBoolean(PreferenceConstants.KEYBOARDFIX_SHIFT_FKEYS, false);
    }


    public void setCharset(String encoding) {
        this.encoding = encoding;
    }

    public boolean showCharPickerDialog(View v) {
        CharSequence str = "";
        Editable content = Editable.Factory.getInstance().newEditable(str);

        final String set = PICKER_SETS.get(KeyCharacterMap.PICKER_DIALOG_INPUT);
        if (set == null) return false;

        CharacterPickerDialog cpd = new CharacterPickerDialog(v.getContext(), v, content, set, true) {
            private void writeCharAndClose(CharSequence result) {
                try {
                    bridge.transport.write(result.toString().getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "Problem with the CharacterPickerDialog", e);
                }
                dismiss();
            }

            @Override
            public void onItemClick(AdapterView p, View v, int pos, long id) {
                String result = String.valueOf(set.charAt(pos));
                writeCharAndClose(result);
            }

            @Override
            public void onClick(View v) {
                if (v instanceof Button) {
                    CharSequence result = ((Button) v).getText();
                    writeCharAndClose(result);
                }
                dismiss(); //Closes the picker
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_PICTSYMBOLS) {
                    dismiss();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        cpd.show();
        return true;
    }

    public void urlScan(View v) {
        //final TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);

        List<String> urls = bridge.scanForURLs();

        Dialog urlDialog = new Dialog(v.getContext());
        urlDialog.setTitle(R.string.console_menu_urlscan);

        ListView urlListView = new ListView(v.getContext());
        URLItemListener urlListener = new URLItemListener(v.getContext(),urlDialog);
        urlListView.setOnItemClickListener(urlListener);
        urlListView.setOnItemLongClickListener(urlListener);

        urlListView.setAdapter(new ArrayAdapter<String>(v.getContext(), R.layout.item_urlscan_url, urls));
        urlDialog.setContentView(urlListView);
        urlDialog.show();
    }

    private class URLItemListener implements OnItemClickListener, OnItemLongClickListener {
        private WeakReference<Context> contextRef;
        private Dialog urlDialog;

        URLItemListener(Context context, Dialog urlDialog) {
            this.contextRef = new WeakReference<Context>(context);
            this.urlDialog = urlDialog;
        }

        public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
            Context context = contextRef.get();

            if (context == null)
                return;

            try {
                TextView urlView = (TextView) view;

                String url = urlView.getText().toString();
                if (url.indexOf("://") < 0)
                    url = "http://" + url;

                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "couldn't open URL", e);
                // We should probably tell the user that we couldn't find a handler...
                Toast.makeText(context, "ERROR: Couldn't open URL", Toast.LENGTH_SHORT).show();
            }
        }

        public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
            Context context = contextRef.get();

            if (context == null)
                return false;
            try {

                String url = ((TextView) v).getText().toString();
                if (url.indexOf("://") < 0)
                    url = "http://" + url;

                clipboard.setText(url);
                Toast.makeText(context, "URL is copied to clipboard", Toast.LENGTH_SHORT).show();
                urlDialog.dismiss();
                return true;

            } catch (Exception e) {
                Log.e(TAG, "couldn't copy URL", e);
                Toast.makeText(context, "ERROR: Couldn't copy URL", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

    }
}
