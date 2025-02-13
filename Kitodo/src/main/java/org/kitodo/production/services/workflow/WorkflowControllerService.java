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

package org.kitodo.production.services.workflow;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.command.CommandResult;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Comment;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.WorkflowCondition;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.enums.WorkflowConditionType;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.index.converter.ProcessConverter;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.enums.ProcessState;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.VariableReplacer;
import org.kitodo.production.helper.WebDav;
import org.kitodo.production.helper.metadata.ImageHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.metadata.MetadataLock;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.TaskService;
import org.kitodo.production.thread.TaskScriptThread;

public class WorkflowControllerService {

    private List<Task> automaticTasks = new ArrayList<>();
    private boolean flagWait = false;
    private final ReentrantLock flagWaitLock = new ReentrantLock();
    private final WebDav webDav = new WebDav();
    private static final Logger logger = LogManager.getLogger(WorkflowControllerService.class);
    private final TaskService taskService = ServiceManager.getTaskService();

    /**
     * Set Task status up.
     *
     * @param task
     *            to change status up
     */
    public void setTaskStatusUp(Task task) throws DataException, IOException, DAOException {
        setTaskStatusUp(Collections.singletonList(task));
    }

    /**
     * Set Task status up.
     *
     * @param tasks
     *            to change status up
     */
    public void setTaskStatusUp(List<Task> tasks) throws DataException, IOException, DAOException {
        for (Task task : tasks) {
            if (task.getProcessingStatus() != TaskStatus.DONE) {
                setProcessingStatusUp(task);
                task.setEditType(TaskEditType.ADMIN);
                if (task.getProcessingStatus() == TaskStatus.DONE) {
                    close(task);
                } else {
                    task.setProcessingTime(new Date());
                    taskService.replaceProcessingUser(task, getCurrentUser());
                    ServiceManager.getTaskService().save(task);
                    ServiceManager.getProcessService().save(task.getProcess());
                }
            }
        }
    }

    /**
     * Change Task status down.
     *
     * @param task
     *            to change status down
     */
    public void setTaskStatusDown(Task task) {
        setTaskStatusDown(Collections.singletonList(task));
    }

    /**
     * Change Task status down.
     *
     * @param tasks
     *            to change status down
     */
    public void setTaskStatusDown(List<Task> tasks) {
        for (Task task : tasks) {
            task.setEditType(TaskEditType.ADMIN);
            task.setProcessingTime(new Date());
            taskService.replaceProcessingUser(task, getCurrentUser());
            setProcessingStatusDown(task);
            if (task.getProcessingStatus() == TaskStatus.LOCKED) {
                List<Task> previousTasks = getPreviousTasks(task);
                for (Task previousTask : previousTasks) {
                    setProcessingStatusDown(previousTask);
                }
            }
        }
    }

    /**
     * Change Task status up for list of tasks assigned to given Process.
     *
     * @param process
     *            object
     */
    public void setTasksStatusUp(Process process) throws DataException, IOException, DAOException {
        List<Task> currentTask = ServiceManager.getProcessService().getCurrentTasks(process);
        if (currentTask.isEmpty()) {
            activateNextTasks(process.getTasks());
            return;
        }
        setTaskStatusUp(currentTask);
    }

    /**
     * Change Task status down for list of tasks assigned to given Process.
     *
     * @param process
     *            object
     */
    public void setTasksStatusDown(Process process) {
        List<Task> currentTask = ServiceManager.getProcessService().getCurrentTasks(process);
        if (currentTask.isEmpty()) {
            currentTask = getLastClosedTask(process);
        }
        setTaskStatusDown(currentTask);
    }

    private List<Task> getLastClosedTask(Process process) {
        List<Task> lastOpenTasks = new ArrayList<>();
        int ordering = 0;
        for (Task task : process.getTasks()) {
            if (TaskStatus.DONE.equals(task.getProcessingStatus())) {
                if (task.getOrdering() > ordering) {
                    lastOpenTasks.clear();
                }
                lastOpenTasks.add(task);
            }
        }
        return lastOpenTasks;
    }

    private boolean validateMetadata(Task task) throws IOException, DAOException {
        URI metadataFileUri = ServiceManager.getProcessService().getMetadataFileUri(task.getProcess());
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metadataFileUri);
        RulesetManagementInterface ruleset = ServiceManager.getRulesetManagementService().getRulesetManagement();
        ruleset.load(new File(Paths.get(
                ConfigCore.getParameter(ParameterCore.DIR_RULESETS),
                task.getProcess().getRuleset().getFile()).toString()));
        ValidationResult validationResult = ServiceManager.getMetadataValidationService().validate(workpiece, ruleset);
        boolean strictValidation = ConfigCore.getBooleanParameter(ParameterCore.VALIDATION_FAIL_ON_WARNING);
        State state = validationResult.getState();
        if (State.ERROR.equals(state) || (strictValidation && !State.SUCCESS.equals(state))) {
            Helper.setErrorMessage(Helper.getTranslation("dataEditor.validation.state.error"));
            for (String message : validationResult.getResultMessages()) {
                Helper.setErrorMessage(message);
            }
        }
        if (strictValidation) {
            return State.SUCCESS.equals(state);
        } else {
            return !State.ERROR.equals(state);
        }
    }

    /**
     * Close method task called by user action.
     *
     * @param task
     *            object
     */
    public void closeTaskByUser(Task task) throws DataException, IOException, DAOException {
        // if the result of the task is to be verified first, then if necessary,
        // cancel the completion
        if (task.isTypeCloseVerify()) {
            // metadata validation
            if (task.isTypeMetadata()
                    && ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.USE_META_DATA_VALIDATION)
                    && !validateMetadata(task)) {
                throw new DataException("Error on metadata validation!");
            }

            // image validation
            if (task.isTypeImagesWrite()) {
                ImageHelper mih = new ImageHelper();
                URI imageFolder = ServiceManager.getProcessService().getImagesOriginDirectory(false, task.getProcess());
                if (!mih.checkIfImagesValid(task.getProcess().getTitle(), imageFolder)) {
                    throw new DataException("Error on image validation!");
                }
            }
        }

        // unlock the process
        MetadataLock.setFree(task.getProcess().getId());
        if (task.isTypeImagesRead() || task.isTypeImagesWrite()) {
            this.webDav.uploadFromHome(task.getProcess());
        }
        task.setEditType(TaskEditType.MANUAL_SINGLE);
        close(task);
    }

    /**
     * Close task.
     *
     * @param task
     *            as Task object
     */
    public void close(Task task) throws DataException, IOException, DAOException {
        task.setProcessingStatus(TaskStatus.DONE);
        task.setCorrection(false);
        task.setProcessingTime(new Date());
        User user = null;
        if (!task.isTypeAutomatic()) {
            user = getCurrentUser();
        }
        taskService.replaceProcessingUser(task, user);
        task.setProcessingEnd(new Date());

        taskService.save(task);

        automaticTasks = new ArrayList<>();

        activateTasksForClosedTask(task);
    }

    /**
     * Checks if all children of a process are closed.
     * @param process the process to check
     * @return true if all children are closed
     */
    public static boolean allChildrenClosed(Process process) {
        if (!process.getChildren().isEmpty()) {
            boolean allChildrenClosed = true;
            for (Process child : process.getChildren()) {
                allChildrenClosed &= ProcessState.COMPLETED20.getValue().equals(child.getSortHelperStatus())
                        || ProcessState.COMPLETED.getValue().equals(child.getSortHelperStatus());
            }
            return allChildrenClosed;
        }
        return false;
    }

    /**
     * Taken from CurrentTaskForm.
     *
     * @param task
     *            object
     */
    public void assignTaskToUser(Task task) {
        this.flagWaitLock.lock();
        try {
            if (!this.flagWait) {
                this.flagWait = true;

                task.setProcessingStatus(TaskStatus.INWORK);
                task.setEditType(TaskEditType.MANUAL_SINGLE);
                task.setProcessingTime(new Date());
                taskService.replaceProcessingUser(task, getCurrentUser());
                if (Objects.isNull(task.getProcessingBegin())) {
                    task.setProcessingBegin(new Date());
                }

                Process process = task.getProcess();

                List<Task> concurrentTasks = getConcurrentTasksForClose(process.getTasks(), task);
                for (Task concurrentTask : concurrentTasks) {
                    concurrentTask.setProcessingStatus(TaskStatus.LOCKED);
                    taskService.save(concurrentTask);
                }

                updateProcessSortHelperStatus(process);

                // if it is an image task, then download the images into the
                // user home directory
                if (task.isTypeImagesRead() || task.isTypeImagesWrite()) {
                    downloadToHome(task);
                }
            } else {
                Helper.setErrorMessage("stepInWorkError");
            }
            this.flagWait = false;
        } catch (DataException e) {
            Helper.setErrorMessage("stepSaveError", logger, e);
        } finally {
            this.flagWaitLock.unlock();
        }
    }

    /**
     * Unassing user from task.
     *
     * @param task
     *            object
     */
    public void unassignTaskFromUser(Task task) throws DataException {
        if (task.isTypeImagesRead() || task.isTypeImagesWrite()) {
            this.webDav.uploadFromHome(task.getProcess());
        }
        task.setProcessingStatus(TaskStatus.OPEN);
        taskService.replaceProcessingUser(task, null);
        // if we have a correction task here then never remove startdate
        if (task.isCorrection()) {
            task.setProcessingBegin(null);
        }
        task.setEditType(TaskEditType.MANUAL_SINGLE);
        task.setProcessingTime(new Date());

        taskService.save(task);

        // unlock the process
        MetadataLock.setFree(task.getProcess().getId());

        updateProcessSortHelperStatus(task.getProcess());
    }

    /**
     * Unified method for report problem .
     *
     * @param comment
     *         as Comment object
     * @param taskEditType
     *         of task change
     */
    public void reportProblem(Comment comment, TaskEditType taskEditType) throws DataException {
        Task currentTask = comment.getCurrentTask();
        if (currentTask.isTypeImagesRead() || currentTask.isTypeImagesWrite()) {
            this.webDav.uploadFromHome(getCurrentUser(), comment.getProcess());
        }
        Date date = new Date();
        currentTask.setProcessingStatus(
                Objects.nonNull(comment.getCorrectionTask()) ? TaskStatus.LOCKED : TaskStatus.INWORK);
        currentTask.setEditType(taskEditType);
        currentTask.setProcessingTime(date);
        taskService.replaceProcessingUser(currentTask, getCurrentUser());
        currentTask.setProcessingBegin(null);
        taskService.save(currentTask);

        if (Objects.nonNull(comment.getCorrectionTask())) {
            Task correctionTask = comment.getCorrectionTask();
            correctionTask.setProcessingStatus(TaskStatus.OPEN);
            correctionTask.setProcessingEnd(null);
            correctionTask.setCorrection(true);
            taskService.save(correctionTask);
            lockTasksBetweenCurrentAndCorrectionTask(currentTask, correctionTask);
        }
        updateProcessSortHelperStatus(currentTask.getProcess());
    }

    /**
     * Unified method for solve problem.
     *
     * @param comment
     *         as Comment object
     */
    public void solveProblem(Comment comment, TaskEditType taskEditType)
            throws DataException, DAOException, IOException {
        comment.setCorrected(Boolean.TRUE);
        comment.setCorrectionDate(new Date());
        try {
            ServiceManager.getCommentService().saveToDatabase(comment);
        } catch (DAOException e) {
            Helper.setErrorMessage("errorSaving", new Object[] {"comment"}, logger, e);
        }
        if (Objects.nonNull(comment.getCorrectionTask())) {
            closeTaskByUser(comment.getCorrectionTask());
            comment.setCorrectionTask(ServiceManager.getTaskService().getById(comment.getCorrectionTask().getId()));
        } else {
            Task currentTask = comment.getCurrentTask();
            currentTask.setProcessingStatus(TaskStatus.OPEN);
            currentTask.setEditType(taskEditType);
            currentTask.setProcessingTime(new Date());
            currentTask.setProcessingBegin(null);
            taskService.replaceProcessingUser(currentTask, getCurrentUser());
            taskService.save(currentTask);
        }
        // "currentTask" and "process" are re-added to the comment object without saving it to database, again, because
        // comment is further used in calling method, hence we make use of a side effect here (without requiring to save
        // the comment again, because process ID and current task ID remain the same)
        // TODO: refactor to improve readability and maintainability
        comment.setCurrentTask(ServiceManager.getTaskService().getById(comment.getCurrentTask().getId()));
        if (Objects.nonNull(comment.getProcess())) {
            comment.setProcess(ServiceManager.getProcessService().getById(comment.getProcess().getId()));
        }
    }

    /**
     * Set processing status up. This method adds double check of task status.
     *
     * @param task
     *            object
     */
    private void setProcessingStatusUp(Task task) {
        if (task.getProcessingStatus() != TaskStatus.DONE) {
            TaskStatus newTaskStatus = TaskStatus.getStatusFromValue(task.getProcessingStatus().getValue() + 1);
            task.setProcessingStatus(newTaskStatus);
        }
    }

    /**
     * Set processing status down. This method adds double check of task status.
     *
     * @param task
     *            object
     */
    private void setProcessingStatusDown(Task task) {
        if (task.getProcessingStatus() != TaskStatus.LOCKED) {
            TaskStatus newTaskStatus = TaskStatus.getStatusFromValue(task.getProcessingStatus().getValue() - 1);
            task.setProcessingStatus(newTaskStatus);
        }
    }

    private void activateTasksForClosedTask(Task closedTask) throws DataException, IOException, DAOException {
        Process process = closedTask.getProcess();

        // check if there are tasks that take place in parallel but are not yet
        // completed
        List<Task> tasks = process.getTasks();
        List<Task> concurrentTasksForOpen = getConcurrentTasksForOpen(tasks, closedTask);

        if (concurrentTasksForOpen.isEmpty() && !isAnotherTaskInWorkWhichBlocksOtherTasks(tasks, closedTask)) {
            if (!closedTask.isLast()) {
                activateNextTasks(getAllHigherTasks(tasks, closedTask));
            }
        } else {
            activateConcurrentTasks(concurrentTasksForOpen);
        }

        process = ServiceManager.getProcessService().getById(process.getId());

        URI imagesOrigDirectory = ServiceManager.getProcessService().getImagesOriginDirectory(true, process);
        Integer numberOfFiles = ServiceManager.getFileService().getNumberOfFiles(imagesOrigDirectory);
        if (!process.getSortHelperImages().equals(numberOfFiles)) {
            process.setSortHelperImages(numberOfFiles);
        }

        ServiceManager.getProcessService().save(process);
        process = ServiceManager.getProcessService().getById(process.getId());

        for (Task automaticTask : automaticTasks) {
            automaticTask.setProcessingBegin(new Date());
            if (automaticTask.isTypeExportDMS()) {
                taskService.executeDmsExport(automaticTask);
            } else {
                TaskScriptThread thread = new TaskScriptThread(automaticTask);
                TaskManager.addTask(thread);
            }
        }

        closeParent(process);
    }

    private void closeParent(Process process) throws DataException {
        if (Objects.nonNull(process.getParent()) && allChildrenClosed(process.getParent())) {
            process.getParent().setSortHelperStatus(ProcessState.COMPLETED.getValue());
            ServiceManager.getProcessService().save(process.getParent());
            closeParent(process.getParent());
        }
    }

    private void lockTasksBetweenCurrentAndCorrectionTask(Task currentTask, Task correctionTask) throws DataException {
        List<Task> allTasksInBetween = taskService.getAllTasksInBetween(correctionTask.getOrdering(),
            currentTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : allTasksInBetween) {
            taskInBetween.setProcessingStatus(TaskStatus.LOCKED);
            taskInBetween.setCorrection(true);
            taskInBetween.setProcessingEnd(null);
            taskService.save(taskInBetween);
        }
    }

    private List<Task> getAllHigherTasks(List<Task> tasks, Task task) {
        List<Task> allHigherTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering() > task.getOrdering()) {
                allHigherTasks.add(tempTask);
            }
        }
        allHigherTasks.sort(Comparator.comparing(Task::getOrdering));
        return allHigherTasks;
    }

    private List<Task> getConcurrentTasksForClose(List<Task> tasks, Task task) {
        List<Task> allConcurrentTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus().getValue() < 2
                    && !tempTask.getId().equals(task.getId()) && !tempTask.isConcurrent()) {
                allConcurrentTasks.add(tempTask);
            }
        }
        return allConcurrentTasks;
    }

    private List<Task> getConcurrentTasksForOpen(List<Task> tasks, Task task) {
        boolean blocksOtherTasks = isAnotherTaskInWorkWhichBlocksOtherTasks(tasks, task);

        List<Task> allConcurrentTasks = new ArrayList<>();
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus().getValue() < 3
                    && !tempTask.getId().equals(task.getId())) {
                if (blocksOtherTasks) {
                    if (tempTask.isConcurrent()) {
                        allConcurrentTasks.add(tempTask);
                    }
                } else {
                    allConcurrentTasks.add(tempTask);
                }
            }
        }
        return allConcurrentTasks;
    }

    private boolean isAnotherTaskInWorkWhichBlocksOtherTasks(List<Task> tasks, Task task) {
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus() == TaskStatus.INWORK
                    && !tempTask.getId().equals(task.getId()) && !tempTask.isConcurrent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Activate the concurrent tasks.
     */
    private void activateConcurrentTasks(List<Task> concurrentTasks) throws DataException, IOException, DAOException {
        for (Task concurrentTask : concurrentTasks) {
            if (concurrentTask.getProcessingStatus().equals(TaskStatus.LOCKED)) {
                activateTask(concurrentTask);
            }
        }
    }

    /**
     * If no open parallel tasks are available, activate the next tasks.
     */
    public void activateNextTasks(List<Task> allHigherTasks) throws DataException, IOException, DAOException {
        List<Task> nextTasks = getNextTasks(allHigherTasks);

        for (Task nextTask : nextTasks) {
            activateTask(nextTask);
        }
    }

    private List<Task> getNextTasks(List<Task> allHigherTasks) {
        int ordering = 0;
        boolean matched = false;

        List<Task> nextTasks = new ArrayList<>();
        for (Task higherTask : allHigherTasks) {
            if (ordering < higherTask.getOrdering() && !matched) {
                ordering = higherTask.getOrdering();
            }

            if (ordering == higherTask.getOrdering() && higherTask.getProcessingStatus().getValue() < 2) {
                nextTasks.add(higherTask);
                matched = true;
            }
        }
        return nextTasks;
    }

    private List<Task> getPreviousTasks(Task higherTask) {
        List<Task> tasks = higherTask.getProcess().getTasks();

        boolean isConcurrentOpenTask = false;
        List<Task> previousTasks = new ArrayList<>();
        List<Task> concurrentTasks = getConcurrentTasksForClose(tasks, higherTask);
        for (Task concurrentTask : concurrentTasks) {
            if (concurrentTask.getProcessingStatus().equals(TaskStatus.LOCKED)) {
                isConcurrentOpenTask = true;
                break;
            }
        }
        if (!isConcurrentOpenTask) {
            boolean matched = false;
            int ordering = higherTask.getOrdering() - 1;
            for (Task task : tasks) {
                if (task.getOrdering() > ordering && task.getOrdering() < higherTask.getOrdering() && !matched) {
                    ordering = task.getOrdering();
                }
                if (ordering == task.getOrdering()) {
                    previousTasks.add(task);
                    matched = true;
                }
            }
        }
        return previousTasks;
    }

    /**
     * If no open parallel tasks are available, activate the next tasks.
     */
    private void activateTask(Task task) throws DataException, IOException, DAOException {
        if ((!task.isCorrection() || task.isRepeatOnCorrection())
                && isWorkflowConditionFulfilled(task.getProcess(), task.getWorkflowCondition())) {
            // activate the task if it is not fully automatic
            task.setProcessingStatus(TaskStatus.OPEN);
            task.setProcessingTime(new Date());
            task.setEditType(TaskEditType.AUTOMATIC);

            processAutomaticTask(task);

            taskService.save(task);
        } else {
            // close task as it is not going to be executed
            task.setProcessingStatus(TaskStatus.DONE);
            task.setProcessingTime(new Date());
            task.setProcessingEnd(new Date());
            task.setEditType(TaskEditType.AUTOMATIC);

            task.setCorrection(false);
            taskService.save(task);

            activateTasksForClosedTask(task);
        }
    }

    private boolean isWorkflowConditionFulfilled(Process process, WorkflowCondition workflowCondition)
            throws IOException {
        if (Objects.isNull(workflowCondition) || workflowCondition.getType().equals(WorkflowConditionType.NONE)) {
            return true;
        } else {
            if (workflowCondition.getType().equals(WorkflowConditionType.SCRIPT)) {
                return runScriptCondition(workflowCondition.getValue(), process);
            }

            if (workflowCondition.getType().equals(WorkflowConditionType.XPATH)) {
                return runXPathCondition(process, workflowCondition.getValue());
            }
            return true;
        }
    }

    private boolean runScriptCondition(String script, Process process) throws IOException {
        LegacyPrefsHelper legacyPrefsHelper = ServiceManager.getRulesetService().getPreferences(process.getRuleset());

        LegacyMetsModsDigitalDocumentHelper legacyMetsModsDigitalDocumentHelper = ServiceManager.getProcessService()
                .readMetadataFile(ServiceManager.getFileService().getMetadataFilePath(process), legacyPrefsHelper)
                .getDigitalDocument();
        VariableReplacer replacer = new VariableReplacer(legacyMetsModsDigitalDocumentHelper.getWorkpiece(),
                process, null);

        script = replacer.replace(script);

        CommandResult commandResult = ServiceManager.getCommandService().runCommand(script);
        return commandResult.isSuccessful();
    }

    private boolean runXPathCondition(Process process, String xpath) throws IOException {
        return ServiceManager.getProcessService().getNodeListFromMetadataFile(process, xpath).getLength() > 0;
    }

    private void processAutomaticTask(Task task) {
        // if it is an automatic task with script
        if (task.isTypeAutomatic()) {
            task.setProcessingStatus(TaskStatus.INWORK);
            automaticTasks.add(task);
        } 
    }

    /**
     * Update process sort helper status.
     *
     * @param process
     *            object
     */
    public static void updateProcessSortHelperStatus(Process process) {
        if (!process.getTasks().isEmpty()) {
            String value = ProcessConverter.getCombinedProgressAsString(process, false);
            process.setSortHelperStatus(value);
        }
    }

    /**
     * Download to user home directory.
     *
     * @param task
     *            object
     */
    private void downloadToHome(Task task) {
        task.setProcessingTime(new Date());
        if (ServiceManager.getSecurityAccessService().isAuthenticated()) {
            taskService.replaceProcessingUser(task, getCurrentUser());
            this.webDav.downloadToHome(task.getProcess(), !task.isTypeImagesWrite());
        }
    }

    private User getCurrentUser() {
        return ServiceManager.getUserService().getCurrentUser();
    }

    /**
     * Set up processing status for given list of processes.
     */
    public void setTaskStatusUpForProcesses(List<Process> processes) {
        for (Process processForStatus : processes) {
            try {
                setTasksStatusUp(processForStatus);
            } catch (DataException | IOException | DAOException e) {
                Helper.setErrorMessage("errorChangeTaskStatus",
                        new Object[] {Helper.getTranslation("up"), processForStatus.getId() }, logger, e);
            }
        }
    }

    /**
     * Set down processing status for given list of processes.
     */
    public void setTaskStatusDownForProcesses(List<Process> processes) {
        for (Process processForStatus : processes) {
            try {
                setTasksStatusDown(processForStatus);
                ServiceManager.getProcessService().save(processForStatus, true);
                updateProcessSortHelperStatus(processForStatus);
            } catch (DataException e) {
                Helper.setErrorMessage("errorChangeTaskStatus",
                        new Object[] {Helper.getTranslation("down"), processForStatus.getId() }, logger, e);
            }
        }
    }
}
