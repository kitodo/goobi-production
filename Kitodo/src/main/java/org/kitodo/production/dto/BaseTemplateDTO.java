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

package org.kitodo.production.dto;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.util.Strings;
import org.kitodo.data.interfaces.DocketInterface;
import org.kitodo.data.interfaces.RulesetInterface;
import org.kitodo.data.interfaces.TaskInterface;

public abstract class BaseTemplateDTO extends BaseDTO {

    private String title;
    protected String creationDate;
    private DocketInterface docket;
    private RulesetInterface ruleset;
    private List<? extends TaskInterface> tasks = new ArrayList<>();

    /**
     * Get title.
     *
     * @return title as String
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set title.
     *
     * @param title
     *            as String
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get creation date.
     *
     * @return creation date as String
     */
    public String getCreationTime() {
        return creationDate;
    }

    /**
     * Set creation date.
     *
     * @param creationDate
     *            as String
     */
    public void setCreationTime(String creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Get docket.
     *
     * @return docket as DocketInterface
     */
    public DocketInterface getDocket() {
        return docket;
    }

    /**
     * Set docket.
     *
     * @param docket
     *            as DocketInterface
     */
    public void setDocket(DocketInterface docket) {
        this.docket = docket;
    }

    /**
     * Get ruleset.
     *
     * @return ruleset as RulesetInterface
     */
    public RulesetInterface getRuleset() {
        return ruleset;
    }

    /**
     * Set ruleset.
     *
     * @param ruleset
     *            as RulesetInterface
     */
    public void setRuleset(RulesetInterface ruleset) {
        this.ruleset = ruleset;
    }

    /**
     * Get list of tasks.
     *
     * @return list of tasks as TaskInterface
     */
    public List<? extends TaskInterface> getTasks() {
        return tasks;
    }

    /**
     * Set list of tasks.
     *
     * @param tasks
     *            list of tasks as TaskInterface
     */
    public void setTasks(List<? extends TaskInterface> tasks) {
        this.tasks = tasks;
    }

    public Date getCreationDate() {
        try {
            return Strings.isNotEmpty(this.creationDate) ? DATE_FORMAT.parse(this.creationDate) : null;
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = Objects.nonNull(creationDate) ? DATE_FORMAT.format(creationDate) : null;
    }
}
