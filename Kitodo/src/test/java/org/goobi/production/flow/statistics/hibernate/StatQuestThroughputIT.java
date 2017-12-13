/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.goobi.production.flow.statistics.hibernate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import de.intranda.commons.chart.renderer.ChartRenderer;
import de.intranda.commons.chart.renderer.HtmlTableRenderer;
import de.intranda.commons.chart.renderer.IRenderer;
import de.intranda.commons.chart.results.DataTable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.goobi.production.flow.statistics.enums.CalculationUnit;
import org.goobi.production.flow.statistics.enums.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

public class StatQuestThroughputIT {

    StatQuestThroughput test = new StatQuestThroughput();
    Locale locale = new Locale("GERMAN");
    private List testFilter = new ArrayList();

    @Test
    public void testSetTimeUnit() {
        test.setTimeUnit(TimeUnit.days);
    }

    @Ignore("Crashing")
    @Test
    public void testGetDataTables() {
        test.setTimeUnit(TimeUnit.days);
        List<DataTable> table = test.getDataTables(testFilter);
        assertNotNull(table);
    }

    @Test
    public void testSetCalculationUnit() {
        test.setCalculationUnit(CalculationUnit.pages);
    }

    @Test
    public void testSetTimeFrame() {
        Calendar one = Calendar.getInstance();
        Calendar other = Calendar.getInstance();
        one.set(2009, 01, 01);
        other.set(2009, 03, 31);
        test.setTimeFrame(one.getTime(), other.getTime());
    }

    @Test
    public void testIsRendererInverted() {
        IRenderer chartReader = new ChartRenderer();
        IRenderer htmlTableReader = new HtmlTableRenderer();
        assertFalse(test.isRendererInverted(htmlTableReader));
        assertTrue(test.isRendererInverted(chartReader));
    }

    @Test
    public void testGetNumberFormatPattern() {
        String answer = null;
        answer = test.getNumberFormatPattern();
        assertNotNull(answer);
    }

}
