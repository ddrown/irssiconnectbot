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

package org.woltage.irssiconnectbot;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import de.mud.terminal.vt320;

class ICBSimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener {
	private float totalY = 0;
	private ConsoleActivity consoleActivity;

	public ICBSimpleOnGestureListener (ConsoleActivity consoleActivity) {this.consoleActivity = consoleActivity;}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2,
			float velocityX, float velocityY) {

		final float distx = e2.getRawX() - e1.getRawX();
		final float disty = e2.getRawY() - e1.getRawY();
		final int goalwidth = consoleActivity.flip.getWidth() / 2;

		// need to slide across half of display to trigger
		// console change
		// make sure user kept a steady hand horizontally
		if (Math.abs(disty) < (consoleActivity.flip.getHeight() / 4)) {
			// check wether the gesture occured in the upper or lower half of the screen
			boolean upperScreenHalf = e2.getY() < consoleActivity.flip.getHeight() / 2;
			if (distx > goalwidth) {
				consoleActivity.shiftCurrentTerminalOrIrssiWindow(ConsoleActivity.SHIFT_RIGHT, upperScreenHalf);
				return true;
			}

			if (distx < -goalwidth) {
				consoleActivity.shiftCurrentTerminalOrIrssiWindow(ConsoleActivity.SHIFT_LEFT, upperScreenHalf);
				return true;
			}

		}

		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

		// if copying, then ignore
		if (consoleActivity.copySource != null && consoleActivity.copySource.isSelectingForCopy())
			return false;

		if (e1 == null || e2 == null)
			return false;

		// if releasing then reset total scroll
		if (e2.getAction() == MotionEvent.ACTION_UP) {
			totalY = 0;
		}

		// activate consider if within x tolerance
		if (Math.abs(e1.getX() - e2.getX()) < ViewConfiguration.getTouchSlop() * 4) {

			View flip = consoleActivity.findCurrentView(R.id.console_flip);
			if (flip == null) return false;
			TerminalView terminal = (TerminalView) flip;

			// estimate how many rows we have scrolled through
			// accumulate distance that doesn't trigger
			// immediate scroll
			totalY += distanceY;
			final int moved = (int) (totalY / terminal.bridge.charHeight);

			// consume as scrollback only if towards right half
			// of screen
			if (e2.getX() > flip.getWidth() / 2) {
				if (moved != 0) {
					int base = terminal.bridge.buffer.getWindowBase();
					terminal.bridge.buffer.setWindowBase(base + moved);
					totalY = 0;
					return true;
				}
			} else {
				// otherwise consume as pgup/pgdown for every 5
				// lines
				if (moved > 5) {
					((vt320) terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
					terminal.bridge.tryKeyVibrate();
					terminal.bridge.tryScrollVibrate();
					totalY = 0;
					return true;
				} else if (moved < -5) {
					((vt320) terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
					terminal.bridge.tryKeyVibrate();
					terminal.bridge.tryScrollVibrate();
					totalY = 0;
					return true;
				}

			}

		}

		return false;
	}

	/*
			 * Enables doubletap = ESC+a
			 *
			 * @see
			 * android.view.GestureDetector.SimpleOnGestureListener#
			 * onDoubleTap(android.view.MotionEvent)
			 *
			 * @return boolean
			 */
	@Override
	public boolean onDoubleTap(MotionEvent e) {
		View flip = consoleActivity.findCurrentView(R.id.console_flip);
		if (flip == null) return false;
		TerminalView terminal = (TerminalView) flip;

		((vt320) terminal.bridge.buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
		((vt320) terminal.bridge.buffer).write('a');

		return true;
	}

	/*
			 * Enables longpress and popups menu
			 *
			 * @see
			 * android.view.GestureDetector.SimpleOnGestureListener#
			 * onLongPress(android.view.MotionEvent)
			 *
			 * @return void
			 */
	@Override
	public void onLongPress(MotionEvent e) {
		if(consoleActivity.prefs.getBoolean("longPressMenu", true)) {
			View flip = consoleActivity.findCurrentView(R.id.console_flip);
			if (flip == null) return;
			TerminalView terminal = (TerminalView) flip;

			terminal.bridge.tryKeyVibrate();

			if (e.getX() > flip.getWidth() * 0.9) { // on the right side of the screen, use irssi channel switch menu
				final String[] items = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "X" };

				GridView irssiSwitchGridView = new GridView(consoleActivity);
				irssiSwitchGridView.setNumColumns(5);
				irssiSwitchGridView.setHorizontalSpacing(-1);
				irssiSwitchGridView.setVerticalSpacing(-1);
				irssiSwitchGridView.setAdapter(new ArrayAdapter<String>(consoleActivity, R.layout.item_irssi_channel_switch, items));

				final AlertDialog irssiSwitchDialog = new AlertDialog.Builder(consoleActivity)
						.setTitle("switch to irssi window")
						.setView(irssiSwitchGridView)
						.create();

				irssiSwitchGridView.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						irssiSwitchDialog.dismiss();
						View flip = consoleActivity.findCurrentView(R.id.console_flip);
						if (flip == null) return;
						char c;
						if (position < 9)
							c = (char) (49 + position);
						else if (position == 9)
							c = '0';
						else {
							switch (position) {
							case 10: c = 'q'; break;
							case 11: c = 'w'; break;
							case 12: c = 'e'; break;
							case 13: c = 'r'; break;
							case 14: c = 't'; break;
							case 15: c = 'y'; break;
							case 16: c = 'u'; break;
							case 17: c = 'i'; break;
							case 18: c = 'o'; break;
							case 19: c = 'c'; break; // close window
							default: return;
							}
						}
						TerminalView terminal = (TerminalView) flip;
						((vt320) terminal.bridge.buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
						((vt320) terminal.bridge.buffer).write(c);
						terminal.bridge.tryKeyVibrate();
					}
				});

				irssiSwitchDialog.show();
				return;
			}

			// regular menu
			final CharSequence[] items = { "Alt", "Alt+c", "TAB", "Ctrl+a", "Ctrl+a+d", "Ctrl+d", "Ctrl+c" };

			AlertDialog.Builder builder = new AlertDialog.Builder(consoleActivity);
			builder.setTitle("Send an action");
			builder.setItems(items,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							View flip = consoleActivity.findCurrentView(R.id.console_flip);
							if (flip == null) return;
							TerminalView terminal = (TerminalView) flip;

							if (item == 0) {
								((vt320) terminal.bridge.buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
								terminal.bridge.tryKeyVibrate();
							} else if (item == 1) {
								((vt320) terminal.bridge.buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
								((vt320) terminal.bridge.buffer).write('c');
								terminal.bridge.tryKeyVibrate();
							} else if (item == 2) {
								((vt320) terminal.bridge.buffer).write(0x09);
								terminal.bridge.tryKeyVibrate();
							} else if (item == 3) {
								((vt320) terminal.bridge.buffer).write(0x01);
								terminal.bridge.tryKeyVibrate();
							} else if (item == 4) {
								((vt320) terminal.bridge.buffer).write(0x01);
								((vt320) terminal.bridge.buffer).write('d');
								terminal.bridge.tryKeyVibrate();
							} else if (item == 5) {
								((vt320) terminal.bridge.buffer).write(0x04);
								terminal.bridge.tryKeyVibrate();
							} else if (item == 6) {
								((vt320) terminal.bridge.buffer).write(0x03);
								terminal.bridge.tryKeyVibrate();
							}
						}
					});
			builder.show();
		}
	}
}
