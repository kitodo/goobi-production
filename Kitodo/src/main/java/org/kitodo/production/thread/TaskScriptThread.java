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

package org.kitodo.production.thread;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.tasks.EmptyTask;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.TaskService;

public class TaskScriptThread extends EmptyTask {

    private final Task task;
    private static final Logger logger = LogManager.getLogger(TaskScriptThread.class);
    private final TaskService taskService = ServiceManager.getTaskService();

    /**
     * Constructor to set up task for script execution.
     *
     * @param task
     *            for script execution
     */
    public TaskScriptThread(Task task) {
        super(task.getTitle() + ", " + Helper.getTranslation("process") + ": " + task.getProcess().getTitle() + " ("
                + task.getProcess().getId() + ")");
        this.task = task;
    }

    /**
     * The clone constructor creates a new instance of this object. This is
     * necessary for Threads that have terminated in order to render to run them
     * again possible.
     *
     * @param origin
     *            copy master to create a clone of
     */
    private TaskScriptThread(TaskScriptThread origin) {
        super(origin);
        this.task = origin.task;
    }

    @Override
    public void run() {
        boolean automatic = this.task.isTypeAutomatic();
        logger.debug("task is automatic: {}", automatic);
        String scriptPath = taskService.getScriptPath(this.task);
        if (!scriptPath.isEmpty()) {
            try {
                this.taskService.executeScript(this.task, automatic);
            } catch (DataException e) {
                logger.error("Data Error occurred", e);
            }
        }
        if (task.isTypeGenerateImages() && !task.getContentFolders().isEmpty()) {
            try {
                taskService.generateImages(this, task, automatic);
            } catch (DataException e) {
                logger.error(e.getMessage(), e);
            }
        }
        super.setProgress(100);
    }

    /**
     * Calls the clone constructor to create a not yet executed instance of this
     * thread object. This is necessary for threads that have terminated in
     * order to render possible to restart them.
     *
     * @return a not-yet-executed replacement of this thread
     * @see org.kitodo.production.helper.tasks.EmptyTask#replace()
     */
    @Override
    public TaskScriptThread replace() {
        return new TaskScriptThread(this);
    }
}
