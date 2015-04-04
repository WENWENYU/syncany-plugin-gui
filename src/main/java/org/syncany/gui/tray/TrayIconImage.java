/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.gui.tray;

/**
 * The tray icon image represents the filename of the
 * image being displayed in the tray icon.
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Vincent Wiencek <vwiencek@gmail.com>
 */
public enum TrayIconImage {
	TRAY_NO_OVERLAY("tray.png"), 
	TRAY_IN_SYNC("tray-in-sync.png"), 
	TRAY_SYNCING1("tray-syncing1.png"), 
	TRAY_SYNCING2("tray-syncing2.png"), 
	TRAY_SYNCING3("tray-syncing3.png"), 
	TRAY_SYNCING4("tray-syncing4.png"), 
	TRAY_SYNCING5("tray-syncing5.png"), 
	TRAY_SYNCING6("tray-syncing6.png"),
	TRAY_CONFLICT("tray-conflict.png");

	public static final int MAX_SYNC_IMAGES = 6;
	private String fileName;

	TrayIconImage(String filenName) {
		this.fileName = filenName;
	}

	public String getFileName() {
		return fileName;
	}

	public static TrayIconImage getSyncImage(int syncImageIndex) {
		switch (syncImageIndex + 1) {
		default:
		case 1:
			return TRAY_SYNCING1;
		case 2:
			return TRAY_SYNCING2;
		case 3:
			return TRAY_SYNCING3;
		case 4:
			return TRAY_SYNCING4;
		case 5:
			return TRAY_SYNCING5;
		case 6:
			return TRAY_SYNCING6;
		}
	}
}