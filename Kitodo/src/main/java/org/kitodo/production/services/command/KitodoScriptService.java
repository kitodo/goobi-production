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

package org.kitodo.production.services.command;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.text.StrTokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Role;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.exceptions.KitodoScriptExecutionException;
import org.kitodo.exceptions.MediaNotFoundException;
import org.kitodo.export.ExportDms;
import org.kitodo.production.enums.GenerationMode;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.production.services.image.ImageGenerator;
import org.kitodo.production.thread.TaskImageGeneratorThread;

public class KitodoScriptService {
    private static volatile KitodoScriptService instance = null;
    private Map<String, String> parameters;
    private static final Logger logger = LogManager.getLogger(KitodoScriptService.class);
    private final FileService fileService = ServiceManager.getFileService();
    private static final String RULESET = "ruleset";
    private static final String SCRIPT = "script";
    private static final String SOURCE_FOLDER = "sourcefolder";
    private static final String STATUS = "status";
    private static final String TASK_TITLE = "tasktitle";
    private static final String ROLE = "role";

    /**
     * Return the singleton instance of the Kitodo script service.
     *
     * @return singleton instance of the Kitodo script service
     */
    public static KitodoScriptService getInstance() {
        KitodoScriptService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (KitodoScriptService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new KitodoScriptService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    /**
     * Private constructor. Use {@link #getInstance()} to get the instance.
     */
    private KitodoScriptService() {
    }

    /**
     * Start the script execution.
     *
     * @param processes
     *            list of Process objects
     * @param script
     *            from frontend passed as String
     */
    public void execute(List<Process> processes, String script)
            throws DataException, IOException, InvalidImagesException, MediaNotFoundException {
        this.parameters = new HashMap<>();
        // decompose and capture all script parameters
        StrTokenizer tokenizer = new StrTokenizer(script, ' ', '\"');
        while (tokenizer.hasNext()) {
            String tok = tokenizer.nextToken();
            if (Objects.nonNull(tok) && tok.contains(":")) {
                String key = tok.substring(0, tok.indexOf(':'));
                String value = tok.substring(tok.indexOf(':') + 1);
                this.parameters.put(key, value);
            }
        }

        // pass the appropriate method with the correct parameters
        if (Objects.isNull(this.parameters.get("action"))) {
            Helper.setErrorMessage("missing action",
                " - possible: 'action:addRole, action:setTaskProperty, action:setStepStatus, "
                        + "action:swapprozessesout, action:swapprozessesin, action:deleteTiffHeaderFile, "
                        + "action:importFromFileSystem'");
            return;
        }

        if (executeScript(processes, script)) {
            Helper.setMessage("kitodoScript finished");
        }
    }

    private boolean executeScript(List<Process> processes, String script)
            throws DataException, IOException, InvalidImagesException, MediaNotFoundException {
        // call the correct method via the parameter
        switch (this.parameters.get("action")) {
            case "importFromFileSystem":
                importFromFileSystem(processes);
                break;
            case "addRole":
                addRole(processes);
                break;
            case "createFolders":
                createFolders(processes);
                break;
            case "setTaskProperty":
                setTaskProperty(processes);
                break;
            case "setStepStatus":
                setTaskStatus(processes);
                break;
            case "addShellScriptToStep":
                addShellScriptToStep(processes);
                break;
            case "updateContentFiles":
                updateContentFiles(processes);
                break;
            case "deleteTiffHeaderFile":
                deleteTiffHeaderFile(processes);
                break;
            case "setRuleset":
                setRuleset(processes);
                break;
            case "exportDms":
            case "export":
                exportDms(processes, this.parameters.get("exportImages"));
                break;
            case "doit":
            case "doit2":
                exportDms(processes, String.valueOf(Boolean.FALSE));
                break;
            default:
                return executeOtherScript(processes, script);
        }
        return true;
    }

    private boolean executeOtherScript(List<Process> processes, String script)
            throws DataException, IOException, InvalidImagesException, MediaNotFoundException {
        // call the correct method via the parameter
        switch (this.parameters.get("action")) {
            case "runscript":
                String taskName = this.parameters.get("stepname");
                String scriptName = this.parameters.get(SCRIPT);
                if (Objects.isNull(scriptName)) {
                    Helper.setErrorMessage("Missing parameter");
                    return false;
                } else {
                    runScript(processes, taskName, scriptName);
                }
                break;
            case "deleteProcess":
                String value = parameters.get("contentOnly");
                boolean contentOnly = true;
                if (Objects.nonNull(value) && value.equalsIgnoreCase("false")) {
                    contentOnly = false;
                }
                deleteProcess(processes, contentOnly);
                break;
            case "addData":
                addData(processes, script);
                break;
            case "overwriteData":
                overwriteData(processes, script);
                break;
            case "deleteData":
                deleteData(processes, script);
                break;
            case "copyDataToChildren":
                copyDataToChildren(processes, script);
                break;
            default:
                return executeRemainingScript(processes);
        }
        return true;
    }

    private boolean executeRemainingScript(List<Process> processes)
            throws IOException, InvalidImagesException, MediaNotFoundException {
        // call the correct method via the parameter
        switch (this.parameters.get("action")) {
            case "generateImages":
                String folders = parameters.get("folders");
                List<String> foldersList = List.of("all");
                if (Objects.nonNull(folders)) {
                    foldersList = Arrays.asList(folders.split(","));
                }
                GenerationMode mode = GenerationMode.ALL;
                String images = parameters.get("images");
                if (Objects.nonNull(images) && images.toLowerCase().startsWith("missing")) {
                    mode = images.length() > 7 ? GenerationMode.MISSING_OR_DAMAGED : GenerationMode.MISSING;
                }
                generateImages(processes, mode, foldersList);
                break;
            case "searchForMedia":
                searchForMedia(processes);
                break;
            case "importProcesses":
                String indir = parameters.get("indir");
                String project = parameters.get("project");
                String template = parameters.get("template");
                String errors = parameters.get("errors");
                try {
                    TaskManager.addTask(new ImportProcesses(indir, project, template, errors));
                    Helper.setMessage("kitodoScript.importProcesses.executesInTaskManager");
                } catch (IllegalArgumentException e) {
                    Helper.setErrorMessage(e.getMessage());
                }
                break;
            default:
                Helper.setErrorMessage("Unknown action",
                    " - use: 'action:addRole, action:setTaskProperty, action:setStepStatus, "
                            + "action:swapprozessesout, action:swapprozessesin, action:deleteTiffHeaderFile, "
                            + "action:importFromFileSystem'");
                return false;
        }
        return true;
    }

    private void deleteData(List<Process> processes, String script) {
        String currentProcessTitle = null;
        script = script.replaceFirst("\\s*action:deleteData\\s+(.*?)[\r\n\\s]*", "$1");
        DeleteDataScript deleteDataScript = new DeleteDataScript();
        for (Process process : processes) {
            try {
                currentProcessTitle = process.getTitle();
                LegacyMetsModsDigitalDocumentHelper metadataFile = ServiceManager.getProcessService()
                        .readMetadataFile(process);
                deleteDataScript.process(metadataFile, process, script);
                ServiceManager.getMetsService().saveWorkpiece(metadataFile.getWorkpiece(),
                        ServiceManager.getProcessService().getMetadataFileUri(process));
                Helper.setMessage("deleteDataOk", currentProcessTitle);
            } catch (IOException | KitodoScriptExecutionException e) {
                Helper.setErrorMessage("deleteDataError", currentProcessTitle + ": " + e.getMessage(), logger, e);
            }
        }
    }

    private void copyDataToChildren(List<Process> processes, String script) {
        String currentProcessTitle;
        script = script.replaceFirst("\\s*action:copyDataToChildren\\s+(.*?)[\r\n\\s]*", "$1");
        AddDataScript addDataScript = new AddDataScript();
        for (Process parentProcess : processes) {
            currentProcessTitle = parentProcess.getTitle();
            List<MetadataScript> metadataScripts = addDataScript.parseScript(script);
            try {
                generateScriptValues(addDataScript, metadataScripts, parentProcess);
                for (Process child : parentProcess.getChildren()) {
                    LegacyMetsModsDigitalDocumentHelper childMetadataFile = ServiceManager.getProcessService()
                            .readMetadataFile(child);
                    for (MetadataScript metadataScript : metadataScripts) {
                        addDataScript.executeScript(childMetadataFile, child, metadataScript);
                    }
                }
                Helper.setMessage("addDataOk", currentProcessTitle);
            } catch (IOException | KitodoScriptExecutionException e) {
                Helper.setErrorMessage("addDataError", currentProcessTitle + ": " + e.getMessage(), logger, e);
            }
        }
    }

    private void generateScriptValues(AddDataScript addDataScript, List<MetadataScript> metadataScripts,
            Process parentProcess) throws IOException {
        for (MetadataScript metadataScript : metadataScripts) {
            addDataScript.generateValueFromParent(metadataScript, parentProcess);
        }
    }

    private void overwriteData(List<Process> processes, String script) {
        String currentProcessTitle = null;
        script = script.replaceFirst("\\s*action:overwriteData\\s+(.*?)[\r\n\\s]*", "$1");
        OverwriteDataScript overwriteDataScript = new OverwriteDataScript();
        for (Process process : processes) {
            try {
                currentProcessTitle = process.getTitle();
                LegacyMetsModsDigitalDocumentHelper metadataFile = ServiceManager.getProcessService()
                        .readMetadataFile(process);
                overwriteDataScript.process(metadataFile, process, script);
                ServiceManager.getMetsService().saveWorkpiece(metadataFile.getWorkpiece(),
                        ServiceManager.getProcessService().getMetadataFileUri(process));
                Helper.setMessage("overwriteDataOk", currentProcessTitle);
            } catch (IOException | KitodoScriptExecutionException e) {
                Helper.setErrorMessage("overwriteDataError", currentProcessTitle + ": " + e.getMessage(), logger, e);
            }
        }
    }

    private void updateContentFiles(List<Process> processes) {
        for (Process process : processes) {
            try {
                LegacyMetsModsDigitalDocumentHelper rdf = ServiceManager.getProcessService().readMetadataFile(process);
                fileService.writeMetadataFile(rdf, process);
                Helper.setMessage("ContentFiles updated: ", process.getTitle());
            } catch (IOException | RuntimeException e) {
                Helper.setErrorMessage("Error while updating content files", logger, e);
            }
        }
        Helper.setMessage("updateContentFiles finished");
    }

    private void createFolders(List<Process> processes) {
        for (Process process : processes) {
            try {
                fileService.createProcessFolders(process);
            } catch (IOException | CommandException e) {
                Helper.setErrorMessage("Error while creating folders", logger, e);
            }
        }
    }

    private void deleteProcess(List<Process> processes, boolean contentOnly) {
        for (Process process : processes) {
            String title = process.getTitle();
            if (contentOnly) {
                try {
                    URI ocr = fileService.getOcrDirectory(process);
                    if (fileService.fileExist(ocr)) {
                        fileService.delete(ocr);
                    }
                    URI images = fileService.getImagesDirectory(process);
                    if (fileService.fileExist(images)) {
                        fileService.delete(images);
                    }
                    Helper.setMessage("Content deleted for " + title);
                } catch (IOException | RuntimeException e) {
                    Helper.setErrorMessage("errorDeleting", new Object[] {"content for " + title }, logger, e);
                }
            } else {
                try {
                    ServiceManager.getProcessService().deleteProcess(process);
                    Helper.setMessage("Process " + title + " deleted.");
                } catch (DataException | IOException e) {
                    Helper.setErrorMessage("errorDeleting",
                        new Object[] {Helper.getTranslation("process") + " " + title }, logger, e);
                }
            }
        }
    }

    private void addData(List<Process> processes, String script) {
        String currentProcessTitle = null;
        script = script.replaceFirst("\\s*action:addData\\s+(.*?)[\r\n\\s]*", "$1");
        AddDataScript addDataScript = new AddDataScript();
        for (Process process : processes) {
            try {
                currentProcessTitle = process.getTitle();
                LegacyMetsModsDigitalDocumentHelper metadataFile = ServiceManager.getProcessService()
                        .readMetadataFile(process);
                addDataScript.process(metadataFile, process, script);
                ServiceManager.getMetsService().saveWorkpiece(metadataFile.getWorkpiece(),
                        ServiceManager.getProcessService().getMetadataFileUri(process));
                Helper.setMessage("addDataOk", currentProcessTitle);
            } catch (IOException | KitodoScriptExecutionException e) {
                Helper.setErrorMessage("addDataError", currentProcessTitle + ": " + e.getMessage(), logger, e);
            }
        }
    }

    private void deleteMetadataDirectory(Process process) throws IOException {
        fileService.deleteProcessContent(process);
    }


    private void generateImages(List<Process> processes, GenerationMode generationMode, List<String> folders) {
        for (Process process : processes) {
            Folder generatorSource = process.getProject().getGeneratorSource();
            if (Objects.isNull(generatorSource)) {
                Helper.setErrorMessage("kitodoScript.generateImages.error.noSourceFolder",
                    process.getTitle(), process.getProject().getTitle());
                continue;
            }
            Subfolder sourceFolder = new Subfolder(process, generatorSource);
            if (sourceFolder.listContents().isEmpty()) {
                Helper.setErrorMessage("kitodoScript.generateImages.error.noSourceFiles",
                    process.getTitle(), sourceFolder.getRelativeDirectoryPath());
                continue;
            }
            boolean all = folders.size() == 1 && folders.get(0).equalsIgnoreCase("all");
            List<String> ungeneratableFolders = all ? new ArrayList<>() : new ArrayList<>(folders);
            List<Subfolder> outputFolders = new ArrayList<>();
            for (Folder folder : process.getProject().getFolders()) {
                if ((all || folders.contains(folder.getPath())) && !folder.equals(generatorSource)
                        && (folder.getDerivative().isPresent() || folder.getDpi().isPresent()
                                || folder.getImageSize().isPresent())) {
                    outputFolders.add(new Subfolder(process, folder));
                    ungeneratableFolders.remove(folder.getPath());
                }
            }
            if (outputFolders.isEmpty()) {
                Helper.setErrorMessage("kitodoScript.generateImages.error.noDestination",
                    process.getTitle(), String.join(", ", ungeneratableFolders));
                continue;
            }
            ImageGenerator imageGenerator = new ImageGenerator(sourceFolder, generationMode, outputFolders);
            TaskManager.addTask(new TaskImageGeneratorThread(process.getTitle(), imageGenerator));
            String generationModeTranslated = Helper
                    .getTranslation("imageGenerator.generationMode.".concat(generationMode.toString()));
            String generatedFolders = // folders whose contents CAN BE generated
                    outputFolders.stream().map(Subfolder::getFolder).map(Folder::getPath)
                            .collect(Collectors.joining(", "));
            if (ungeneratableFolders.isEmpty()) {
                Helper.setMessage(MessageFormat.format(Helper.getTranslation("kitodoScript.generateImages.ok"),
                    generationModeTranslated, process.getTitle(), String.join(", ", generatedFolders)));
            } else {
                Helper.setMessage(MessageFormat.format(Helper.getTranslation("kitodoScript.generateImages.partitial"),
                    generationModeTranslated, process.getTitle(), generatedFolders,
                    String.join(", ", ungeneratableFolders)));
            }
        }
    }

    private void searchForMedia(List<Process> processes)
            throws IOException, InvalidImagesException, MediaNotFoundException {
        FileService fileService = ServiceManager.getFileService();
        MetsService metsService = ServiceManager.getMetsService();
        ProcessService processService = ServiceManager.getProcessService();

        for (Process process : processes) {
            URI metadataFileUri = processService.getMetadataFileUri(process);
            Workpiece workpiece = metsService.loadWorkpiece(metadataFileUri);
            fileService.searchForMedia(process, workpiece);
            metsService.saveWorkpiece(workpiece, metadataFileUri);
        }
    }

    private void runScript(List<Process> processes, String taskName, String scriptName) throws DataException {
        for (Process process : processes) {
            for (Task task : process.getTasks()) {
                if (task.getTitle().equalsIgnoreCase(taskName)) {
                    if (Objects.nonNull(scriptName)) {
                        if (task.getScriptName().equals(scriptName)) {
                            String path = task.getScriptPath();
                            ServiceManager.getTaskService().executeScript(task, path, false);
                        }
                    } else {
                        ServiceManager.getTaskService().executeScript(task, false);
                    }
                }
            }
        }
    }

    /**
     * Import the data from a directories of the given processes.
     *
     * @param processes
     *            list of Process objects
     */
    private void importFromFileSystem(List<Process> processes) {
        if (isActionParameterInvalid(SOURCE_FOLDER)) {
            return;
        }

        URI sourceFolder = new File(this.parameters.get(SOURCE_FOLDER)).toURI();
        try {
            if (!fileService.isDirectory(sourceFolder)) {
                Helper.setErrorMessage(
                    "Directory " + this.parameters.get(SOURCE_FOLDER) + " does not exisist");
                return;
            }
            for (Process process : processes) {
                Integer processId = process.getId();
                String processTitle = process.getTitle();
                URI imagesFolder = ServiceManager.getProcessService().getImagesOriginDirectory(false, process);
                if (!fileService.getSubUris(imagesFolder).isEmpty()) {
                    Helper.setErrorMessage(
                        "The process " + processTitle + " [" + processId + "] has already data in image folder");
                } else {
                    URI sourceFolderProcess = fileService.createResource(sourceFolder, processTitle);
                    if (!fileService.isDirectory(sourceFolder)) {
                        Helper.setErrorMessage(
                            "The directory for process " + processTitle + " [" + processId + "] is not existing");
                    } else {
                        fileService.copyDirectory(sourceFolderProcess, imagesFolder);
                        Helper.setMessage(
                            "The directory for process " + processTitle + " [" + processId + "] is copied");
                    }
                    Helper.setMessage(
                        "The process " + processTitle + " [" + processId + "] is copied");
                }
            }
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Set ruleset.
     *
     * @param processes
     *            list of Process objects
     */
    private void setRuleset(List<Process> processes) {
        if (isActionParameterInvalid(RULESET)) {
            return;
        }

        try {
            List<Ruleset> rulesets = ServiceManager.getRulesetService()
                    .getByQuery("from Ruleset where title='" + this.parameters.get(RULESET) + "'");
            if (rulesets.isEmpty()) {
                Helper.setErrorMessage("Could not find ruleset: ", RULESET);
                return;
            }
            Ruleset ruleset = rulesets.get(0);

            for (Process process : processes) {
                process.setRuleset(ruleset);
                ServiceManager.getProcessService().save(process);
            }
        } catch (DataException | RuntimeException e) {
            Helper.setErrorMessage(e);
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Add ShellScript to task of the given processes.
     *
     * @param processes
     *            list of Process objects
     */
    private void addShellScriptToStep(List<Process> processes) {
        if (isActionParameterInvalid(TASK_TITLE) || isActionParameterInvalid("label")
                || isActionParameterInvalid(SCRIPT)) {
            return;
        }

        executeActionForAddShellToScript(processes);
        Helper.setMessage("addShellScriptToStep finished: ");
    }

    private void executeActionForAddShellToScript(List<Process> processes) {
        for (Process process : processes) {
            for (Task task : process.getTasks()) {
                if (task.getTitle().equals(this.parameters.get(TASK_TITLE))) {
                    task.setScriptPath(this.parameters.get(SCRIPT));
                    task.setScriptName(this.parameters.get("label"));
                    saveProcess(process);
                    Helper.setMessage("Added script to step: ", process.getTitle());
                    break;
                }
            }
        }
    }

    /**
     * Flag von Schritten setzen.
     *
     * @param processes
     *            list of Process objects
     */
    private void setTaskProperty(List<Process> processes) {
        if (isActionParameterInvalid(TASK_TITLE) || isActionParameterInvalid("property")
                || isActionParameterInvalid("value")) {
            return;
        }

        String property = this.parameters.get("property");
        String value = this.parameters.get("value");

        if (!("metadata".equals(property) || "readimages".equals(property) || "writeimages".equals(property)
                || property.equals("validate") || property.equals("exportdms") || property.equals("batch")
                || property.equals("automatic"))) {
            Helper.setErrorMessage(
                "wrong parameter 'property'; possible values: metadata, readimages, writeimages, "
                        + "validate, exportdms");
            return;
        }

        if (!"true".equals(value) && !value.equalsIgnoreCase(String.valueOf(Boolean.FALSE))) {
            Helper.setErrorMessage("wrong parameter 'value'; possible " + "values: true, false");
            return;
        }

        executeActionForSetTaskProperty(processes, property, value);
        Helper.setMessage("setTaskProperty abgeschlossen: ");
    }

    private void executeActionForSetTaskProperty(List<Process> processes, String property, String value) {
        for (Process process : processes) {
            for (Task task : process.getTasks()) {
                if (task.getTitle().equals(this.parameters.get(TASK_TITLE))) {
                    switch (property) {
                        case "metadata":
                            task.setTypeMetadata(Boolean.parseBoolean(value));
                            break;
                        case "automatic":
                            task.setTypeAutomatic(Boolean.parseBoolean(value));
                            break;
                        case "batch":
                            task.setBatchStep(Boolean.parseBoolean(value));
                            break;
                        case "readimages":
                            task.setTypeImagesRead(Boolean.parseBoolean(value));
                            break;
                        case "writeimages":
                            task.setTypeImagesWrite(Boolean.parseBoolean(value));
                            break;
                        case "validate":
                            task.setTypeCloseVerify(Boolean.parseBoolean(value));
                            break;
                        case "exportdms":
                            task.setTypeExportDMS(Boolean.parseBoolean(value));
                            break;
                        default:
                            break;
                    }

                    saveProcess(process);
                    Helper.setMessage("Error while saving process: ", process.getTitle());
                    break;
                }
            }
        }
    }

    /**
     * Set task status for the given processes.
     *
     * @param processes
     *            list of Process objects
     */
    private void setTaskStatus(List<Process> processes) {
        if (isActionParameterInvalid(TASK_TITLE) || isActionParameterInvalid(STATUS)) {
            return;
        }

        if (!this.parameters.get(STATUS).equals("0") && !this.parameters.get(STATUS).equals("1")
                && !this.parameters.get(STATUS).equals("2") && !this.parameters.get(STATUS).equals("3")) {
            Helper.setErrorMessage("Wrong status parameter: status ",
                "(possible: 0=closed, 1=open, 2=in work, 3=finished");
            return;
        }

        executeActionForSetTaskStatus(processes);
        Helper.setMessage("setStepStatus finished: ");
    }

    private void executeActionForSetTaskStatus(List<Process> processes) {
        for (Process process : processes) {
            for (Task task : process.getTasks()) {
                if (task.getTitle().equals(this.parameters.get(TASK_TITLE))) {
                    TaskStatus newTaskStatus = TaskStatus
                            .getStatusFromValue(Integer.valueOf(this.parameters.get(STATUS)));
                    task.setProcessingStatus(newTaskStatus);
                    saveTask(process.getTitle(), task);
                    Helper.setMessage("stepstatus set in process: ", process.getTitle());
                    break;
                }
            }
        }
    }

    /**
     * Add role to the task of given processes.
     *
     * @param processes
     *            list of Process objects
     */
    private void addRole(List<Process> processes) {
        if (isActionParameterInvalid(TASK_TITLE) || isActionParameterInvalid(ROLE)) {
            return;
        }

        // check if role exists
        Role role;
        List<Role> foundRoles = ServiceManager.getRoleService()
                .getByQuery("FROM Role WHERE title='" + this.parameters.get(ROLE) + "'");
        if (!foundRoles.isEmpty()) {
            role = foundRoles.get(0);
        } else {
            Helper.setErrorMessage("Unknown role: ", this.parameters.get(ROLE));
            return;
        }

        executeActionForAddRole(processes, role);
        Helper.setMessage("addRole finished");
    }

    private void executeActionForAddRole(List<Process> processes, Role role) {
        for (Process process : processes) {
            for (Task task : process.getTasks()) {
                if (task.getTitle().equals(this.parameters.get(TASK_TITLE))) {
                    List<Role> roles = task.getRoles();
                    if (!roles.contains(role)) {
                        roles.add(role);
                        saveTask(process.getTitle(), task);
                    }
                }
            }
            Helper.setMessage("added role to task: ", process.getTitle());
        }
    }

    /**
     * Delete TiffHeader file from given processes.
     *
     * @param processes
     *            list of Process objects
     */
    public void deleteTiffHeaderFile(List<Process> processes) {
        for (Process process : processes) {
            try {
                File tiffHeaderFile = new File(fileService.getImagesDirectory(process) + "tiffwriter.conf");
                if (tiffHeaderFile.exists()) {
                    Files.delete(tiffHeaderFile.toPath());
                }
                Helper.setMessage("TiffHeaderFile deleted: ", process.getTitle());
            } catch (IOException | RuntimeException e) {
                Helper.setErrorMessage("Error while deleting TiffHeader", logger, e);
            }
        }
        Helper.setMessage("deleteTiffHeaderFile finished");
    }

    private void exportDms(List<Process> processes, String exportImages) {
        boolean withoutImages = Objects.nonNull(exportImages) && exportImages.equalsIgnoreCase("false");
        for (Process process : processes) {
            try {
                ExportDms dms = new ExportDms(!withoutImages);
                dms.startExport(process);
            } catch (DataException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private boolean isActionParameterInvalid(String parameter) {
        if (Objects.isNull(this.parameters.get(parameter)) || Objects.equals(this.parameters.get(parameter), "")) {
            Helper.setErrorMessage("missing parameter: ", parameter);
            return true;
        }
        return false;
    }

    private void saveProcess(Process process) {
        try {
            ServiceManager.getProcessService().save(process);
        } catch (DataException e) {
            Helper.setErrorMessage("Error while saving process: " + process.getTitle(), logger, e);
        }
    }

    private void saveTask(String processTitle, Task task) {
        try {
            ServiceManager.getTaskService().save(task);
        } catch (DataException e) {
            Helper.setErrorMessage("Error while saving - " + processTitle, logger, e);
        }
    }
}
