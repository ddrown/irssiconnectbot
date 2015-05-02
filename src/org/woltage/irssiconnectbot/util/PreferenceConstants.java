/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package org.woltage.irssiconnectbot.util;

import android.os.Build;

/**
 * @author Kenny Root
 *
 */
public class PreferenceConstants {
	// Build.VERSION.SDK_INT is sdk4+, we still support sdk3, so get it once
	public static final int SDK_INT = Integer.parseInt(Build.VERSION.SDK);
	public static final boolean PRE_ECLAIR = SDK_INT < 5;
	public static final boolean PRE_FROYO = SDK_INT < 8;
	public static final boolean PRE_HONEYCOMB = SDK_INT < 11;


	public static final String MEMKEYS = "memkeys";
	public static final String UPDATE = "update";

	public static final String UPDATE_DAILY = "Daily";
	public static final String UPDATE_WEEKLY = "Weekly";
	public static final String UPDATE_NEVER = "Never";

	public static final String LAST_CHECKED = "lastchecked";

	public static final String SCROLLBACK = "scrollback";

	public static final String EMULATION = "emulation";

	public static final String ROTATION = "rotation";

	public static final String ROTATION_DEFAULT = "Default";
	public static final String ROTATION_LANDSCAPE = "Force landscape";
	public static final String ROTATION_PORTRAIT = "Force portrait";
	public static final String ROTATION_SENSOR = "Sensor";
	public static final String ROTATION_SYSTEM = "Automatic (System)";

	public static final String FULLSCREEN = "fullscreen";

	public static final String KEYBOARDFIX_KEYMODE = "keymode";
	public static final String KEYBOARDFIX_KEYMODE_RIGHT = "Use right-side keys";
	public static final String KEYBOARDFIX_KEYMODE_LEFT = "Use left-side keys";
	public static final String KEYBOARDFIX_KEYMODE_DISABLED = "Disable";
	public static final String KEYBOARDFIX_DELBACKSPACE = "deletebackspace";
	public static final String KEYBOARDFIX_SEARCH_BUTTON = "searchbutton";
	public static final String KEYBOARDFIX_SEARCH_BUTTON_TAB = "tab";
	public static final String KEYBOARDFIX_SEARCH_BUTTON_META = "meta";
	public static final String KEYBOARDFIX_SEARCH_BUTTON_URLSCAN = "urlscan";
	public static final String KEYBOARDFIX_SEARCH_BUTTON_HARDMETA_SOFTURLSCAN = "hardmeta_softurlscan";
	public static final String KEYBOARDFIX_XPERIAPRO = "xperiaProFix";
	public static final String KEYBOARDFIX_DESIREZSKANDINAVIAN = "htcDesireZfix";
	public static final String KEYBOARDFIX_DESIREZSKANDINAVIAN_OFF = "false";
	public static final String KEYBOARDFIX_DESIREZSKANDINAVIAN_ON = "only_scandinavian_keys";
	public static final String KEYBOARDFIX_DESIREZSKANDINAVIAN_ALL = "true";
	public static final String KEYBOARDFIX_DPAD_ESCAPE = "dpadescape";
	public static final String KEYBOARDFIX_SHIFT_FKEYS = "shiftfkeys";


	public static final String CAMERA = "camera";

	public static final String CAMERA_CTRLA_SPACE = "Ctrl+A then Space";
	public static final String CAMERA_CTRLA = "Ctrl+A";
	public static final String CAMERA_ESC = "Esc";
	public static final String CAMERA_ESC_A = "Esc+A";

	public static final String KEEP_ALIVE = "keepalive";

	public static final String WIFI_LOCK = "wifilock";

	public static final String BUMPY_ARROWS = "bumpyarrows";
	public static final String BUMPY_SCROLL = "bumpyscroll";

	public static final String ACTIONBAR = "actionbarsetting";

	public static final String EULA = "eula";

	public static final String SORT_BY_COLOR = "sortByColor";

	public static final String BELL = "bell";
	public static final String BELL_VOLUME = "bellVolume";
	public static final String BELL_VIBRATE = "bellVibrate";
	public static final String BELL_NOTIFICATION = "bellNotification";
	public static final String BELL_NOTIFICATION_SOUND = "bellNotificationSound";
	public static final float DEFAULT_BELL_VOLUME = 0.25f;

	public static final String CONNECTION_PERSIST = "connPersist";

        public static final String INSTALLED_MOSH_VERSION = "moshVersion";

	public static final String ON_SCREEN_BUTTONS = "onScreenButtons";
	public static final String ON_SCREEN_BUTTONS_BOTTOM_ONLY = "onScreenButtonsBottomOnly";

	/* Backup identifiers */
	public static final String BACKUP_PREF_KEY = "prefs";
}
