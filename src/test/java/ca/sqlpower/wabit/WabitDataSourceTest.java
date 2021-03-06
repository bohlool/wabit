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

package ca.sqlpower.wabit;

import java.io.File;

import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.PlDotIni;

public class WabitDataSourceTest extends AbstractWabitObjectTest {
    
    private PlDotIni plIni;
    private WabitDataSource wabitDS;
    private JDBCDataSource ds;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        plIni = new PlDotIni();
        plIni.read(new File("src/test/resources/pl.regression.ini"));
        ds = plIni.getDataSource("regression_test", JDBCDataSource.class);
        
        wabitDS = new WabitDataSource(ds);
        getWorkspace().addDataSource(wabitDS);
    }

    @Override
    public WabitObject getObjectUnderTest() {
        return wabitDS;
    }

}
