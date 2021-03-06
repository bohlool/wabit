/*
 * Copyright (c) 2010, SQL Power Group Inc.
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

package ca.sqlpower.wabit.swingui.report.selectors;

import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.report.selectors.ComboBoxSelector;
import ca.sqlpower.wabit.report.selectors.DateSelector;
import ca.sqlpower.wabit.report.selectors.Selector;
import ca.sqlpower.wabit.report.selectors.TextBoxSelector;

public class SelectorFactory {
	
	private Set<SelectorComponent> components = new HashSet<SelectorComponent>();
	
	public JComponent makeSelector(Selector selector, Runnable refreshRoutine) {
		
		final JComponent comp;
		if (selector instanceof ComboBoxSelector) {
			comp = makeComboBoxSelector((ComboBoxSelector)selector, refreshRoutine);
		} else if (selector instanceof TextBoxSelector) {
			comp = makeTextBoxSelector((TextBoxSelector)selector, refreshRoutine);
		} else if (selector instanceof DateSelector) {
			comp = makeDateSelector((DateSelector)selector, refreshRoutine);
		} else {
			throw new IllegalArgumentException("This factory does not know how to build a component of class " + selector.getClass().getCanonicalName());
		}
		
		SwingUtilities.invokeLater(refreshRoutine);
		return comp;
		
	}

	private JComponent makeDateSelector(DateSelector selector,
			Runnable refreshRoutine) 
	{
		final FancyDateSelectorField date =
				new FancyDateSelectorField(selector, refreshRoutine);
		
		this.components.add(date);
		
		return date;
	}

	public JComponent makeSelector(WabitWorkspace sourceWorkspace, String selectorUuid, Runnable refreshRoutine) {
		
		Selector selector = sourceWorkspace.findByUuid(selectorUuid, Selector.class);
		
		if (selector == null) {
			throw new IllegalArgumentException("No selector with UUID '" + selectorUuid + "' could be found.");
		}
		
		return this.makeSelector(selector, refreshRoutine);
	}
	
	private JComponent makeTextBoxSelector(final TextBoxSelector selector, final Runnable refreshRoutine) {
		
		final FancyTextBoxSelectorField text = 
				new FancyTextBoxSelectorField(
						selector,
						refreshRoutine);
		
		this.components.add(text);
		
		return text;
	}
	
	private JComponent makeComboBoxSelector(final ComboBoxSelector selector, final Runnable refreshRoutine) {
		
		final FancyComboBoxSelector cb = new FancyComboBoxSelector(selector, refreshRoutine);
		
		this.components.add(cb);
		
		return cb;
	}
	
	public void cleanup() {
		for (SelectorComponent comp : this.components) {
			comp.cleanup();
		}
		this.components.clear();
	}
}
