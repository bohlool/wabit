/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui.action;

import java.awt.Window;

import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.image.WabitImage;

public class CopyImageAction extends CopyAction {
	private WabitSession session;
	private WabitImage image;
	
	public CopyImageAction(WabitImage image, WabitSession session, Window dialogOwner) {
		super(image, dialogOwner);
		this.session = session;
		this.image = image;
	}
	
	public void copy(String name) {
		WabitImage newImage = new WabitImage(image);
		newImage.setName(name);
		session.getWorkspace().addImage(newImage);
	}

}
