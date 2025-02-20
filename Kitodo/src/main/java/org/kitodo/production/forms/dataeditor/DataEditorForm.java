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

package org.kitodo.production.forms.dataeditor;

import static org.kitodo.constants.StringConstants.EDIT;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.DataEditorSetting;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.MediaNotFoundException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.LocaleHelper;
import org.kitodo.production.interfaces.MetadataTreeTableInterface;
import org.kitodo.production.interfaces.RulesetSetupInterface;
import org.kitodo.production.metadata.MetadataLock;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ImportService;
import org.kitodo.production.services.dataeditor.DataEditorService;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.PrimeFaces;
import org.primefaces.model.TreeNode;

@Named("DataEditorForm")
@ViewScoped
public class DataEditorForm implements MetadataTreeTableInterface, RulesetSetupInterface, Serializable {

    private static final Logger logger = LogManager.getLogger(DataEditorForm.class);

    /**
     * Backing bean for the add doc struc type dialog.
     */
    private final AddDocStrucTypeDialog addDocStrucTypeDialog;

    /**
     * Dialog for adding metadata.
     */
    private final AddMetadataDialog addMetadataDialog;

    private final UpdateMetadataDialog updateMetadataDialog;

    /**
     * Backing bean for the add PhysicalDivision dialog.
     */
    private final AddPhysicalDivisionDialog addPhysicalDivisionDialog;

    /**
     * Backing bean for the change doc struc type dialog.
     */
    private final ChangeDocStrucTypeDialog changeDocStrucTypeDialog;

    /**
     * Backing bean for the edit pages dialog.
     */
    private final EditPagesDialog editPagesDialog;

    private final UploadFileDialog uploadFileDialog;
    /**
     * Backing bean for the gallery panel.
     */
    private final GalleryPanel galleryPanel;

    /**
     * The current process children.
     */
    private final Set<Process> currentChildren = new HashSet<>();

    /**
     * The path to the main file, to save it later.
     */
    private URI mainFileUri;

    /**
     * Backing bean for the metadata panel.
     */
    private final MetadataPanel metadataPanel;

    /**
     * Backing bean for the pagination panel.
     */
    private final PaginationPanel paginationPanel;

    /**
     * The language preference list of the editing user for displaying the
     * metadata labels. We cache this because it’s used thousands of times and
     * otherwise the access would always go through the search engine, which
     * would delay page creation.
     */
    private List<LanguageRange> priorityList;

    /**
     * Process whose workpiece is under edit.
     */
    private Process process;

    private String referringView = "desktop";

    /**
     * The ruleset that the file is based on.
     */
    private RulesetManagementInterface ruleset;

    /**
     * Backing bean for the structure panel.
     */
    private final StructurePanel structurePanel;

    /**
     * User sitting in front of the editor.
     */
    private User user;

    /**
     * The file content.
     */
    private Workpiece workpiece;

    /**
     * Original state of workpiece. Used to check whether any unsaved changes exist when leaving the editor.
     */
    private Workpiece workpieceOriginalState;

    /**
     * This List of Pairs stores all selected physical elements and the logical elements in which the physical element was selected.
     * It is necessary to store the logical elements as well, because a physical element can be assigned to multiple logical elements.
     */
    private List<Pair<PhysicalDivision, LogicalDivision>> selectedMedia;

    /**
     * The template task corresponding to the current task that is under edit.
     * This is used for saving and loading the metadata editor settings.
     * The current task, the corresponding template task id and the settings are only available
     * if the user opened the editor from a task.
     */
    private Task templateTask;

    /**
     * The list of metadata keys that are annotated with <code>use="structureTreeTitle"</code>, meaning, 
     * they are used to generate the tree node label in case the title is requested by the user.
     */
    private Collection<String> structureTreeTitles = new ArrayList<>();

    private DataEditorSetting dataEditorSetting;

    private static final String DESKTOP_LINK = "/pages/desktop.jsf";

    private List<PhysicalDivision> unsavedDeletedMedia = new ArrayList<>();

    private List<PhysicalDivision> unsavedUploadedMedia = new ArrayList<>();

    private boolean folderConfigurationComplete = false;

    private int numberOfScans = 0;
    private String errorMessage;

    @Inject
    private MediaProvider mediaProvider;
    private boolean mediaUpdated = false;

    private DualHashBidiMap<URI, URI> filenameMapping = new DualHashBidiMap<>();

    private int numberOfNewMappings = 0;

    private String renamingError = "";
    private String metadataFileLoadingError = "";

    static final String GROWL_MESSAGE =
            "PF('notifications').renderMessage({'summary':'SUMMARY','detail':'DETAIL','severity':'SEVERITY'});";

    private boolean globalLayoutLoaded = false;
    private boolean taskLayoutLoaded = false;

    /**
     * Public constructor.
     */
    public DataEditorForm() {
        this.structurePanel = new StructurePanel(this);
        this.metadataPanel = new MetadataPanel(this);
        this.galleryPanel = new GalleryPanel(this);
        this.paginationPanel = new PaginationPanel(this);
        this.addDocStrucTypeDialog = new AddDocStrucTypeDialog(this);
        this.addMetadataDialog = new AddMetadataDialog(this);
        this.updateMetadataDialog = new UpdateMetadataDialog(this);
        this.addPhysicalDivisionDialog = new AddPhysicalDivisionDialog(this);
        this.changeDocStrucTypeDialog = new ChangeDocStrucTypeDialog(this);
        this.editPagesDialog = new EditPagesDialog(this);
        this.uploadFileDialog = new UploadFileDialog(this);
    }

    /**
     * Checks if the process is correctly set. Otherwise, redirect to desktop,
     * because metadata editor doesn't work without a process.
     */
    public void initMetadataEditor() {
        if (Objects.isNull(process)) {
            try {
                Helper.setErrorMessage("noProcessSelected");
                FacesContext context = FacesContext.getCurrentInstance();
                String path = context.getExternalContext().getRequestContextPath() + DESKTOP_LINK;
                context.getExternalContext().redirect(path);
            } catch (IOException e) {
                Helper.setErrorMessage("noProcessSelected");
            }
        } else {
            if (mediaUpdated) {
                PrimeFaces.current().executeScript("PF('fileReferencesUpdatedDialog').show();");
            }
        }
    }

    /**
     * Open the metadata file of the process with the given ID in the metadata editor.
     *
     * @param processID
     *            ID of the process that is opened
     * @param referringView
     *            JSF page the user came from
     */
    public void open(String processID, String referringView, String taskId) {
        try {
            this.referringView = referringView;
            this.process = ServiceManager.getProcessService().getById(Integer.parseInt(processID));
            this.currentChildren.addAll(process.getChildren());
            this.user = ServiceManager.getUserService().getCurrentUser();
            this.checkProjectFolderConfiguration();
            this.loadTemplateTask(taskId);
            this.loadDataEditorSettings();
            errorMessage = "";

            User blockedUser = MetadataLock.getLockUser(process.getId());
            if (Objects.nonNull(blockedUser) && !blockedUser.equals(this.user)) {
                errorMessage = Helper.getTranslation("blocked");
            }
            String metadataLanguage = user.getMetadataLanguage();
            priorityList = LanguageRange.parse(metadataLanguage.isEmpty() ? "en" : metadataLanguage);
            ruleset = ServiceManager.getRulesetService().openRuleset(process.getRuleset());
            this.loadStructureTreeTitlesFromRuleset();
            try {
                mediaUpdated = openMetsFile();
            } catch (MediaNotFoundException e) {
                mediaUpdated = false;
                Helper.setWarnMessage(e.getMessage());
            }
            if (!workpiece.getId().equals(process.getId().toString())) {
                errorMessage = Helper.getTranslation("metadataConfusion", String.valueOf(process.getId()),
                        workpiece.getId());
            }
            selectedMedia = new LinkedList<>();
            unsavedUploadedMedia = new ArrayList<>();
            init();
            if (Objects.isNull(errorMessage) || errorMessage.isEmpty()) {
                MetadataLock.setLocked(process.getId(), user);
            } else {
                PrimeFaces.current().executeScript("PF('metadataLockedDialog').show();");
            }
        } catch (FileNotFoundException e) {
            metadataFileLoadingError = e.getLocalizedMessage();
        } catch (IOException | DAOException | InvalidImagesException | NoSuchElementException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    private void checkProjectFolderConfiguration() {
        if (Objects.nonNull(this.process)) {
            Project project = this.process.getProject();
            if (Objects.nonNull(project)) {
                this.folderConfigurationComplete = Objects.nonNull(project.getGeneratorSource())
                        && Objects.nonNull(project.getMediaView()) && Objects.nonNull(project.getPreview());
            } else {
                this.folderConfigurationComplete = false;
            }
        } else {
            this.folderConfigurationComplete = false;
        }
    }

    private void loadStructureTreeTitlesFromRuleset() {
        structureTreeTitles = getRulesetManagement().getFunctionalKeys(FunctionalMetadata.STRUCTURE_TREE_TITLE);
        if (structureTreeTitles.isEmpty()) {
            Locale locale = LocaleHelper.getCurrentLocale();
            Helper.setWarnMessage(Helper.getString(locale, "dataEditor.noStructureTreeTitleFoundWarning"));
        }
    }

    /**
     * Load template task from database.
     * 
     * @param taskId the id of the template task
     * @throws DAOException if loading fails
     */
    private void loadTemplateTask(String taskId) throws DAOException {
        if (StringUtils.isNotBlank(taskId) && StringUtils.isNumeric(taskId)) {
            try {
                int templateTaskId = Integer.parseInt(taskId);
                if (templateTaskId > 0) {
                    this.templateTask = ServiceManager.getTaskService().getById(templateTaskId);
                }
            } catch (NumberFormatException e) {
                logger.warn("view parameter 'templateTaskId' is not a valid integer");
            }
        }
    }

    /**
     * Load data editor settings (width of metadata editor columns) from database. Either load task-specific 
     * configuration (if it exists) or default configuration (if it exists) or initialize new empty data editor setting.
     */
    private void loadDataEditorSettings() {
        // use template task id if it exists, otherwise use null for task-independent layout
        Integer taskId = Objects.nonNull(this.templateTask) ? this.templateTask.getId() : null;
        int userId = user.getId();

        // try to load data editor setting from database
        dataEditorSetting = ServiceManager.getDataEditorSettingService().loadDataEditorSetting(userId, taskId);

        // try to load task-independent data editor setting from database
        if (Objects.isNull(dataEditorSetting)) {
            dataEditorSetting = ServiceManager.getDataEditorSettingService().loadDataEditorSetting(userId, null);
        }

        // initialize empty data editor setting if none were previously saved by the user
        if (Objects.isNull(dataEditorSetting)) {
            dataEditorSetting = new DataEditorSetting();
            dataEditorSetting.setUserId(userId);
            dataEditorSetting.setTaskId(taskId);
        }

        // initialize flags to signal whether global or task specific settings have been loaded or not
        boolean layoutLoaded = (dataEditorSetting.getStructureWidth() > 0
                || dataEditorSetting.getMetadataWidth() > 0
                || dataEditorSetting.getGalleryWidth() > 0);

        globalLayoutLoaded = Objects.isNull(dataEditorSetting.getTaskId()) && layoutLoaded;
        taskLayoutLoaded = Objects.nonNull(dataEditorSetting.getTaskId()) && layoutLoaded;
    }

    /**
     * Opens the METS file.
     *
     * @throws IOException
     *             if filesystem I/O fails
     */
    private boolean openMetsFile() throws IOException, InvalidImagesException, MediaNotFoundException {
        mainFileUri = ServiceManager.getProcessService().getMetadataFileUri(process);
        workpiece = ServiceManager.getMetsService().loadWorkpiece(mainFileUri);
        workpieceOriginalState = ServiceManager.getMetsService().loadWorkpiece(mainFileUri);
        if (Objects.isNull(workpiece.getId())) {
            logger.warn("Workpiece has no ID. Cannot verify workpiece ID. Setting workpiece ID.");
            workpiece.setId(process.getId().toString());
        }
        metadataFileLoadingError = "";
        return ServiceManager.getFileService().searchForMedia(process, workpiece);
    }

    private void init() {
        final long begin = System.nanoTime();

        List<PhysicalDivision> severalAssignments = new LinkedList<>();
        initSeveralAssignments(workpiece.getPhysicalStructure(), severalAssignments);
        structurePanel.getSeveralAssignments().addAll(severalAssignments);

        structurePanel.show();
        metadataPanel.showLogical(getSelectedStructure());
        metadataPanel.showPhysical(getSelectedPhysicalDivision());
        galleryPanel.setGalleryViewMode(GalleryViewMode.getByName(user.getDefaultGalleryViewMode()).name());
        galleryPanel.show();
        paginationPanel.show();
        editPagesDialog.prepare();
        updateNumberOfScans();
        updateToDefaultSelection();

        if (logger.isTraceEnabled()) {
            logger.trace("Initializing editor beans took {} ms",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Clears all remaining content from the data editor form.
     *
     * @return the referring view, to return there
     */
    public String closeAndReturn() {
        if (referringView.contains("?")) {
            return referringView + "&faces-redirect=true";
        } else {
            return referringView + "?faces-redirect=true";
        }
    }

    /**
     * Close method called before destroying ViewScoped DataEditorForm bean instance. Cleans up various properties
     * and releases metadata lock of current process if current user equals user of metadata lock.
     */
    @PreDestroy
    public void close() {
        deleteNotSavedUploadedMedia();
        unsavedDeletedMedia.clear();
        if (Objects.nonNull(workpiece)) {
            ServiceManager.getFileService().revertRenaming(filenameMapping.inverseBidiMap(), workpiece);
        }
        metadataPanel.clear();
        structurePanel.clear();
        workpiece = null;
        workpieceOriginalState = null;
        mainFileUri = null;
        ruleset = null;
        currentChildren.clear();
        if (Objects.nonNull(selectedMedia)) {
            selectedMedia.clear();
        }
        if (!FacesContext.getCurrentInstance().isPostback()) {
            mediaProvider.resetMediaResolverForProcess(process.getId());
        }
        // do not unlock process if this locked process was opened by a different user opening editor
        // directly via URL bookmark and 'preDestroy' method was being triggered redirecting him to desktop page
        if (this.user.equals(MetadataLock.getLockUser(process.getId()))) {
            MetadataLock.setFree(process.getId());
        }
        process = null;
        user = null;
        mediaUpdated = false;
    }

    private void deleteUnsavedDeletedMedia() {
        URI uri = Paths.get(ConfigCore.getKitodoDataDirectory(),
                ServiceManager.getProcessService().getProcessDataDirectory(this.process).getPath()).toUri();
        for (PhysicalDivision physicalDivision : this.unsavedDeletedMedia) {
            for (URI fileURI : physicalDivision.getMediaFiles().values()) {
                try {
                    ServiceManager.getFileService().delete(uri.resolve(fileURI));
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    /**
     * Get unsavedDeletedMedia.
     *
     * @return value of unsavedDeletedMedia
     */
    public List<PhysicalDivision> getUnsavedDeletedMedia() {
        return unsavedDeletedMedia;
    }

    private void deleteNotSavedUploadedMedia() {
        URI uri = Paths.get(ConfigCore.getKitodoDataDirectory(),
                ServiceManager.getProcessService().getProcessDataDirectory(this.process).getPath()).toUri();
        for (PhysicalDivision mediaUnit : this.unsavedUploadedMedia) {
            for (URI fileURI : mediaUnit.getMediaFiles().values()) {
                this.filenameMapping = ServiceManager.getFileService()
                        .removeUnsavedUploadMediaUriFromFileMapping(fileURI, this.filenameMapping);
                try {
                    ServiceManager.getFileService().delete(uri.resolve(fileURI));
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
        this.unsavedUploadedMedia.clear();
    }

    /**
     * Validate the structure and metadata.
     *
     * @return whether the validation was successful or not
     */
    public boolean validate() {
        try {
            ValidationResult validationResult = ServiceManager.getMetadataValidationService().validate(workpiece,
                ruleset);
            State state = validationResult.getState();
            switch (state) {
                case ERROR:
                    Helper.setErrorMessage(Helper.getTranslation("dataEditor.validation.state.error"));
                    for (String message : validationResult.getResultMessages()) {
                        Helper.setErrorMessage(message);
                    }
                    return false;
                case WARNING:
                    Helper.setWarnMessage(Helper.getTranslation("dataEditor.validation.state.warning"));
                    for (String message : validationResult.getResultMessages()) {
                        Helper.setWarnMessage(message);
                    }
                    return true;
                default:
                    Helper.setMessage(Helper.getTranslation("dataEditor.validation.state.success"));
                    for (String message : validationResult.getResultMessages()) {
                        Helper.setMessage(message);
                    }
                    return true;
            }
        } catch (DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return false;
        }
    }

    /**
     * Save the structure and metadata.
     */
    public void save() {
        save(false);
    }

    private String save(boolean close) {
        try {
            metadataPanel.preserve();
            structurePanel.preserve();
            // reset "image filename renaming map" so nothing is reverted after saving!
            filenameMapping = new DualHashBidiMap<>();
            ServiceManager.getProcessService().updateChildrenFromLogicalStructure(process, workpiece.getLogicalStructure());
            ServiceManager.getFileService().createBackupFile(process);
            try (OutputStream out = ServiceManager.getFileService().write(mainFileUri)) {
                ServiceManager.getMetsService().save(workpiece, out);
                ServiceManager.getProcessService().saveToIndex(process,false);
                unsavedUploadedMedia.clear();
                deleteUnsavedDeletedMedia();
                if (close) {
                    return closeAndReturn();
                } else {
                    PrimeFaces.current().executeScript("PF('notifications').renderMessage({'summary':'"
                            + Helper.getTranslation("metadataSaved") + "','severity':'info'})");
                    workpieceOriginalState = ServiceManager.getMetsService().loadWorkpiece(mainFileUri);
                    PrimeFaces.current().executeScript("setUnsavedChanges(false);");
                }
            } catch (IOException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            }
        } catch (Exception e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
        PrimeFaces.current().executeScript("PF('sticky-notifications').removeAll();");
        PrimeFaces.current().ajax().update("notifications");
        return null;
    }

    /**
     * Save the structure and metadata.
     *
     * @return navigation target
     */
    public String saveAndExit() {
        return save(true);
    }

    private void initSeveralAssignments(PhysicalDivision physicalDivision, List<PhysicalDivision> severalAssignments) {
        if (physicalDivision.getLogicalDivisions().size() > 1) {
            severalAssignments.add(physicalDivision);
        }
        for (PhysicalDivision child : physicalDivision.getChildren()) {
            initSeveralAssignments(child, severalAssignments);
        }
    }

    /**
     * Deletes the selected outline point from the logical outline. This method
     * is called by PrimeFaces to inform the application that the user has
     * clicked on the shortcut menu entry to clear the outline point.
     */
    public void deleteStructure() {
        structurePanel.deleteSelectedStructure();
    }

    /**
     * Deletes the selected physical division from the media list. The associated files
     * on the drive are not deleted. The next time the editor is started, files
     * that are not yet in the media list will be inserted there again. This
     * method is called by PrimeFaces to inform the application that the user
     * clicked on the context menu entry to delete the physical division.
     */
    public void deletePhysicalDivision() {
        structurePanel.deleteSelectedPhysicalDivisions();
        updateNumberOfScans();
    }

    @Override
    public String getAcquisitionStage() {
        return EDIT;
    }

    /**
     * Get unsavedUploadedMedia.
     *
     * @return value of unsavedUploadedMedia
     */
    public List<PhysicalDivision> getUnsavedUploadedMedia() {
        return unsavedUploadedMedia;
    }

    /**
     * Returns the backing bean for the add doc struc type dialog. This function
     * is used by PrimeFaces to access the elements of the add doc struc type
     * dialog.
     *
     * @return the backing bean for the add doc struc type dialog
     */
    public AddDocStrucTypeDialog getAddDocStrucTypeDialog() {
        return addDocStrucTypeDialog;
    }

    /**
     * Get addMetadataDialog.
     *
     * @return value of addMetadataDialog
     */
    public AddMetadataDialog getAddMetadataDialog() {
        return addMetadataDialog;
    }

    /**
     * Get updateMetadataDialog.
     *
     * @return value of updateMetadataDialog
     */
    public UpdateMetadataDialog getUpdateMetadataDialog() {
        return updateMetadataDialog;
    }


    /**
     * Returns the backing bean for the add media dialog. This function is used
     * by PrimeFaces to access the elements of the add media dialog.
     *
     * @return the backing bean for the add media dialog
     */
    public AddPhysicalDivisionDialog getAddPhysicalDivisionDialog() {
        return addPhysicalDivisionDialog;
    }

    /**
     * Returns the backing bean for the change doc struc type dialog. This
     * function is used by PrimeFaces to access the elements of the change doc
     * struc type dialog.
     *
     * @return the backing bean for the change doc struc type dialog
     */
    public ChangeDocStrucTypeDialog getChangeDocStrucTypeDialog() {
        return changeDocStrucTypeDialog;
    }

    /**
     * Returns the backing bean for the edit pages dialog. This function is used
     * by PrimeFaces to access the elements of the edit pages dialog.
     *
     * @return the backing bean for the edit pages dialog
     */
    public EditPagesDialog getEditPagesDialog() {
        return editPagesDialog;
    }

    /**
     * Returns the backing bean for the gallery panel. This function is used by
     * PrimeFaces to access the elements of the gallery panel.
     *
     * @return the backing bean for the gallery panel
     */
    public GalleryPanel getGalleryPanel() {
        return galleryPanel;
    }

    Set<Process> getCurrentChildren() {
        return currentChildren;
    }

    /**
     * Returns the backing bean for the metadata panel. This function is used
     * by PrimeFaces to access the elements of the metadata panel.
     *
     * @return the backing bean for the metadata panel
     */
    public MetadataPanel getMetadataPanel() {
        return metadataPanel;
    }

    /**
     * Returns the backing bean for the pagination panel. This function is used
     * by PrimeFaces to access the elements of the pagination panel.
     *
     * @return the backing bean for the pagination panel
     */
    public PaginationPanel getPaginationPanel() {
        return paginationPanel;
    }

    @Override
    public List<LanguageRange> getPriorityList() {
        return priorityList;
    }

    /**
     * Get process.
     *
     * @return value of process
     */
    public Process getProcess() {
        return process;
    }

    /**
     * Get process title.
     *
     * @return value of process title
     */
    public String getProcessTitle() {
        return process.getTitle();
    }

    @Override
    public RulesetManagementInterface getRulesetManagement() {
        return ruleset;
    }

    public Optional<LogicalDivision> getSelectedStructure() {
        return structurePanel.getSelectedStructure();
    }

    Optional<PhysicalDivision> getSelectedPhysicalDivision() {
        return structurePanel.getSelectedPhysicalDivision();
    }

    /**
     * Check if the passed LogicalDivision is part of the selection.
     * @param structure LogicalDivision to be checked
     * @return boolean representing selection status
     */
    public boolean isStripeSelected(LogicalDivision structure) {
        Optional<LogicalDivision> selectedStructure = structurePanel.getSelectedStructure();
        return selectedStructure.filter(logicalDivision -> Objects.equals(structure, logicalDivision)).isPresent();
    }

    /**
     * Return structurePanel.
     *
     * @return structurePanel
     */
    public StructurePanel getStructurePanel() {
        return structurePanel;
    }

    Workpiece getWorkpiece() {
        return workpiece;
    }

    void refreshStructurePanel() {
        structurePanel.show(true);
        galleryPanel.updateStripes();
    }

    void setProcess(Process process) {
        this.process = process;
    }

    /**
     * Get selectedMedia.
     *
     * @return value of selectedMedia
     */
    public List<Pair<PhysicalDivision, LogicalDivision>> getSelectedMedia() {
        return selectedMedia;
    }

    /**
     * Checks and returns if consecutive physical divisions in one structure element are selected or not.
     *
     * <p>Note: This method is called potentially thousands of times when rendering large galleries.</p>
     */
    public boolean consecutivePagesSelected() {
        if (Objects.isNull(selectedMedia) || selectedMedia.isEmpty()) {
            return false;
        }
        int maxOrder = selectedMedia.stream().mapToInt(m -> m.getLeft().getOrder()).max().orElseThrow(NoSuchElementException::new);
        int minOrder = selectedMedia.stream().mapToInt(m -> m.getLeft().getOrder()).min().orElseThrow(NoSuchElementException::new);

        // Check whether the set of selected media all belong to the same logical division, otherwise the selection
        // is not consecutive. However, do not use stream().distinct(), which will do pairwise comparisons, which is
        // slow for large amounts of selected images. Instead, just check whether the first logical division matches
        // all others in a simple loop.
        boolean theSameLogicalDivisions = true;
        LogicalDivision firstSelectedMediaLogicalDivision = null;
        for (Pair<PhysicalDivision, LogicalDivision> pair : selectedMedia) {
            if (Objects.isNull(firstSelectedMediaLogicalDivision)) {
                firstSelectedMediaLogicalDivision = pair.getRight();
            } else {
                if (!Objects.equals(firstSelectedMediaLogicalDivision, pair.getRight())) {
                    theSameLogicalDivisions = false;
                    break;
                }
            }
        }
        return selectedMedia.size() - 1 == maxOrder - minOrder && theSameLogicalDivisions;
    }

    void setSelectedMedia(List<Pair<PhysicalDivision, LogicalDivision>> media) {
        this.selectedMedia = media;
    }

    /**
     * Check if the passed PhysicalDivision is selected.
     * @param physicalDivision PhysicalDivision object to check for selection
     * @param logicalDivision object to check whether the PhysicalDivision is selected as a child of this LogicalDivision.
     *                                  A PhysicalDivision can be assigned to multiple logical divisionss but can be selected
     *                                  in one of these LogicalDivisions.
     * @return boolean whether the PhysicalDivision is selected at the specified position
     */
    public boolean isSelected(PhysicalDivision physicalDivision, LogicalDivision logicalDivision) {
        if (Objects.nonNull(physicalDivision) && Objects.nonNull(logicalDivision)) {
            if (physicalDivision.hasMediaPartial() && physicalDivision.getLogicalDivisions().size() == 1) {
                return selectedMedia.contains(new ImmutablePair<>(physicalDivision, physicalDivision.getLogicalDivisions().get(0)));
            }
            return selectedMedia.contains(new ImmutablePair<>(physicalDivision, logicalDivision));
        }
        return false;
    }

    /**
     * Select first logical division when opening data editor.
     */
    public void updateToDefaultSelection() {
        TreeNode firstSelectedLogicalNode = getStructurePanel().getLogicalTree().getChildren().get(
            getStructurePanel().getLogicalTree().getChildCount() - 1
        );
        try {
            updateSelection(
                Collections.emptyList(), 
                Collections.singletonList(StructureTreeOperations.getLogicalDivisionFromTreeNode(firstSelectedLogicalNode))
            );
        } catch (NoSuchMetadataFieldException e) {
            logger.error("exception updating to default selection", e);
        }
    }

    /**
     * Update the current selection in the metadata editor by dispatching update events to structure trees,
     * gallery and pagination panel.
     * 
     * @param selectedPhysicalDivisions the list of selected physical divisions (and their parent logical divisions)
     * @param selectedLogicalDivisions the list of selected logical divisions
     * @throws NoSuchMetadataFieldException exception in case metadata can not be saved correctly
     */
    public void updateSelection(
        List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions,
        List<LogicalDivision> selectedLogicalDivisions
    ) throws NoSuchMetadataFieldException {
        try {
            // save previously edited meta data
            getMetadataPanel().preserveLogical();
        } catch (InvalidMetadataValueException e) {
            logger.info(e.getLocalizedMessage(), e);
        }

        // update data editor selection (used e.g. in gallery)
        getSelectedMedia().clear();
        getSelectedMedia().addAll(selectedPhysicalDivisions);

        // update logical metadata panel
        if (!getStructurePanel().isSeparateMedia() && selectedPhysicalDivisions.size() == 1 
                && selectedLogicalDivisions.isEmpty()) {
            // show physical division in logical metadata panel in combined meta data mode
            getMetadataPanel().showPageInLogical(selectedPhysicalDivisions.get(0).getLeft());
        } else if (selectedLogicalDivisions.size() == 1 && selectedPhysicalDivisions.isEmpty()) {
            // show logical division in logical metadata panel
            getMetadataPanel().showLogical(Optional.of(selectedLogicalDivisions.get(0)));
        } else {
            // show nothing in logical metadata panel
            getMetadataPanel().showPageInLogical(null);
        }

        // update physical metadata panel
        if (getStructurePanel().isSeparateMedia()) {
            if (selectedPhysicalDivisions.size() == 1) {
                // show physical division in physical metadata panel
                getMetadataPanel().showPhysical(Optional.of(selectedPhysicalDivisions.get(0).getLeft()));
            } else {
                // show nothing in physical metadata panel
                getMetadataPanel().showPhysical(Optional.empty());
            }
        }

        // update structure trees
        getStructurePanel().updateNodeSelection(
            selectedPhysicalDivisions, 
            selectedLogicalDivisions
        );
    
        // update pagination panel
        getPaginationPanel().preparePaginationSelectionSelectedItems();
    }


    void assignView(LogicalDivision logicalDivision, View view, Integer index) {
        if (Objects.nonNull(index) && index >= 0 && index < logicalDivision.getViews().size()) {
            logicalDivision.getViews().add(index, view);
        } else {
            logicalDivision.getViews().add(view);
        }
        view.getPhysicalDivision().getLogicalDivisions().add(logicalDivision);
    }

    void unassignView(LogicalDivision logicalDivision, View view, boolean removeLast) {
        // if View was moved within one element, we need to distinguish two possible directions it could have been moved
        if (removeLast) {
            logicalDivision.getViews().removeLastOccurrence(view);
        } else {
            logicalDivision.getViews().removeFirstOccurrence(view);
        }
        view.getPhysicalDivision().getLogicalDivisions().remove(logicalDivision);
    }

    /**
     * Retrieve and return title of dataObject if it is a 'LogicalDivision' and if it has a title. 
     * Uses metadata value as title if one of the provided keys exists.
     * Return empty string otherwise.
     *
     * @param dataObject
     *          StructureTreeNode containing the LogicalDivision whose title is returned
     * @param metadataKeys
     *          the list of metadata keys that are annotated with "structureTreeTitle"
     * @return 'title' value of the LogicalDivision contained in the given StructureTreeNode 'treeNode'
     */
    public static String getStructureElementTitle(Object dataObject, Collection<String> metadataKeys) {
        String title = "";
        if (dataObject instanceof LogicalDivision) {
            LogicalDivision logicalDivision = ((LogicalDivision) dataObject);
            
            title = metadataKeys.stream()
                .map((key) -> DataEditorService.getTitleValue(logicalDivision, key))
                .filter((t) -> !t.isEmpty()).findFirst().orElse("");

            if (StringUtils.isBlank(title)) {
                title = logicalDivision.getLabel();
                if (StringUtils.isBlank(title)) {
                    title = " - ";
                }
            }
        }
        return title;
    }

    /**
     * Retrieve and return title of dataObject if it is a 'LogicalDivision' and if it has a title. 
     * Uses metadata value as title if one of the provided keys exists.
     * Return empty string otherwise.
     *
     * @param dataObject
     *          StructureTreeNode containing the LogicalDivision whose title is returned
     * @return 'title' value of the LogicalDivision contained in the given StructureTreeNode 'treeNode'
     */
    public String getStructureElementTitle(Object dataObject) {
        return DataEditorForm.getStructureElementTitle(dataObject, structureTreeTitles);
    }

    /**
     * Get referringView.
     *
     * @return value of referringView
     */
    public String getReferringView() {
        return referringView;
    }

    /**
     * Check and return whether the given ProcessDetail 'processDetail' is contained in the current list of addable
     * metadata types in the addDocStrucTypeDialog.
     *
     * @param treeNode treeNode to be added
     * @return whether the given ProcessDetail can be added or not
     */
    @Override
    public boolean canBeAdded(TreeNode treeNode) {
        if (Objects.isNull(treeNode.getParent().getParent())) {
            if (Objects.nonNull(metadataPanel.getSelectedMetadataTreeNode()) || Objects.isNull(addMetadataDialog.getAddableMetadata())) {
                this.addMetadataDialog.prepareAddableMetadataForStructure(treeNode.getParent().getChildren());
            }
        } else if (!Objects.equals(metadataPanel.getSelectedMetadataTreeNode(), treeNode.getParent())
                || Objects.isNull(addMetadataDialog.getAddableMetadata())) {
            prepareAddableMetadataForGroup(treeNode.getParent());
        }
        if (Objects.nonNull(addMetadataDialog.getAddableMetadata())) {
            return addMetadataDialog.getAddableMetadata().stream()
                    .map(SelectItem::getValue).collect(Collectors.toList()).contains(((ProcessDetail) treeNode.getData()).getMetadataID());
        }
        return false;
    }

    @Override
    public boolean canBeDeleted(ProcessDetail processDetail) {
        return processDetail.getOccurrences() > 1 && processDetail.getOccurrences() > processDetail.getMinOccurs()
                || (!processDetail.isRequired() && !this.ruleset.isAlwaysShowingForKey(processDetail.getMetadataID()));
    }

    /**
     * Check for changes in workpiece.
     */
    public void checkForChanges() {
        if (Objects.nonNull(PrimeFaces.current())) {
            boolean unsavedChanges = !this.workpiece.equals(workpieceOriginalState);
            PrimeFaces.current().executeScript("setUnsavedChanges(" + unsavedChanges + ");");
        }
    }

    /**
     * Get the shortcuts for the current user.
     *
     * @return shortcuts as java.lang.String
     */
    public String getShortcuts() {
        try {
            return ServiceManager.getUserService().getShortcuts(user.getId());
        } catch (DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return "{}";
        }
    }

    /**
     * Get templateTaskId.
     *
     * @return value of templateTaskId
     */
    public Task getTemplateTask() {
        return templateTask;
    }

    /**
     * Get dataEditorSetting.
     *
     * @return value of dataEditorSetting
     */
    public DataEditorSetting getDataEditorSetting() {
        return dataEditorSetting;
    }

    /**
     * Set dataEditorSetting.
     *
     * @param dataEditorSetting as org.kitodo.data.database.beans.DataEditorSetting
     */
    public void setDataEditorSetting(DataEditorSetting dataEditorSetting) {
        this.dataEditorSetting = dataEditorSetting;
    }

    /**
     * Gets numberOfScans.
     *
     * @return value of numberOfScans
     */
    public int getNumberOfScans() {
        return numberOfScans;
    }

    /**
     * Sets numberOfScans.
     *
     */
    public void updateNumberOfScans() {
        this.numberOfScans = workpiece.getNumberOfAllPhysicalDivisionChildrenFilteredByTypes(PhysicalDivision.TYPES);
    }


    /**
     * Save current metadata editor layout. Either save it as task-specific layout (if editor was opened from a task) 
     * or as task-independent layout (otherwise).
     */
    public void saveDataEditorSetting() {
        if (Objects.nonNull(dataEditorSetting)) {
            if (Objects.nonNull(templateTask) && !templateTask.getId().equals(dataEditorSetting.getTaskId())) {
                // create a copy of the task-independent configuration 
                // in case the user wants to save it as task-specific config
                dataEditorSetting = new DataEditorSetting(dataEditorSetting);
                dataEditorSetting.setTaskId(templateTask.getId());
            }
            try {
                ServiceManager.getDataEditorSettingService().saveToDatabase(dataEditorSetting);
                loadDataEditorSettings();
                PrimeFaces.current().executeScript("PF('dataEditorSavingResultDialog').show();");
            } catch (DAOException e) {
                Helper.setErrorMessage("errorSaving", new Object[] {ObjectType.DATAEDITORSETTING.getTranslationSingular() }, logger, e);
            }
        } else {
            // should never happen any more, since layout settings are always created (even outside of task context)
            int taskId = Objects.nonNull(this.templateTask) ? this.templateTask.getId() : 0;
            int userId = user.getId();
            logger.error("Could not save DataEditorSettings with userId {} and templateTaskId {}", userId, taskId);
        }
    }

    /**
     * Delete current metadata editor layout.
     */
    public void deleteDataEditorSetting() {
        if (Objects.nonNull(dataEditorSetting)) {
            try {
                ServiceManager.getDataEditorSettingService().removeFromDatabase(dataEditorSetting);
                this.loadDataEditorSettings();
                PrimeFaces.current().executeScript("PF('dataEditorDeletedResultDialog').show();");
            } catch (DAOException e) {
                Helper.setErrorMessage("errorDeleting", new Object[] { ObjectType.DATAEDITORSETTING.getTranslationSingular() }, logger, e);
            }
        }
    }

    /**
     * Get uploadFileDialog.
     *
     * @return value of uploadFileDialog
     */
    public UploadFileDialog getUploadFileDialog() {
        return uploadFileDialog;
    }

    /**
     * Get folderConfigurationComplete.
     *
     * @return value of folderConfigurationComplete
     */
    public boolean isFolderConfigurationComplete() {
        return folderConfigurationComplete;
    }

    /**
     * Preserve changes in the metadata panel.
     */
    public void preserveMetadataPanel() {
        try {
            metadataPanel.preserve();
        } catch (InvalidMetadataValueException | NoSuchMetadataFieldException e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * Check and return whether given TreeNode contains ProcessFieldedMetadata and if any further metadata can
     * be added to it or not.
     *
     * @return whether given TreeNode contains ProcessFieldedMetadata and if any further metadata can be added to it
     */
    @Override
    public boolean metadataAddableToGroup(TreeNode metadataNode) {
        return metadataPanel.metadataAddableToGroup(metadataNode);
    }

    /**
     * Prepare addable metadata for metadata group.
     */
    @Override
    public void prepareAddableMetadataForGroup(TreeNode treeNode) {
        addMetadataDialog.prepareAddableMetadataForGroup(treeNode);
    }

    /**
     * Get errorMessage.
     *
     * @return value of errorMessage
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get mediaProvider.
     *
     * @return value of mediaProvider
     */
    public MediaProvider getMediaProvider() {
        return mediaProvider;
    }

    /**
     * Get mediaUpdated.
     *
     * @return value of mediaUpdated
     */
    public boolean isMediaUpdated() {
        return mediaUpdated;
    }

    /**
     * Set mediaUpdated.
     *
     * @param mediaUpdated as boolean
     */
    public void setMediaUpdated(boolean mediaUpdated) {
        this.mediaUpdated = mediaUpdated;
    }

    /**
     * Rename media files of current process according to their corresponding physical divisions ORDER attribute.
     */
    public void renameMediaFiles() {
        renamingError = "";
        try {
            numberOfNewMappings = ServiceManager.getFileService().renameMediaFiles(process, workpiece, filenameMapping);
        } catch (IOException | URISyntaxException e) {
            renamingError = e.getMessage();
        }
        showPanels();
        if (StringUtils.isBlank(renamingError)) {
            PrimeFaces.current().executeScript("PF('renamingMediaSuccessDialog').show();");
        } else {
            PrimeFaces.current().executeScript("PF('renamingMediaErrorDialog').show();");
        }
    }

    /**
     * Get renamingError.
     * @return renamingError
     */
    public String getRenamingError() {
        return renamingError;
    }

    /**
     * Return renaming success message containing number of renamed media files and configured sub-folders.
     * @return renaming success message
     */
    public String getRenamingSuccessMessage() {
        return Helper.getTranslation("dataEditor.renamingMediaText", String.valueOf(numberOfNewMappings),
                String.valueOf(process.getProject().getFolders().size()));
    }

    private void showPanels() {
        galleryPanel.show();
        paginationPanel.show();
        structurePanel.show();
    }

    /**
     * Get metadata file loading error.
     * @return metadata file loading error
     */
    public String getMetadataFileLoadingError() {
        return metadataFileLoadingError;
    }

    /**
     * Check and return whether conditions for metadata update are met or not.
     *
     * @return whether metadata of process can be updated
     */
    public boolean canUpdateMetadata() {
        try {
            return DataEditorService.canUpdateCatalogMetadata(process, workpiece, structurePanel.getSelectedLogicalNodeIfSingle());
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return false;
        }
    }

    /**
     * Perform metadata update for current process.
     */
    public void applyMetadataUpdate() {
        DataEditorService.updateMetadataWithNewValues(workpiece, updateMetadataDialog.getMetadataComparisons());
        metadataPanel.update();
    }

    /**
     * Retrieve and return value of metadata configured as functional metadata 'recordIdentifier'.
     *
     * @return the 'recordIdentifier' metadata value of the current process
     */
    public String getProcessRecordIdentifier() {
        try {
            return DataEditorService.getRecordIdentifierValueOfProcess(process, workpiece);
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return "";
        }
    }

    /**
     * Get label of metadata with key 'metadataKey'.
     *
     * @param metadataKey key of metadata for which label is returned
     *
     * @return label of metadata with given key 'metadataKey'
     */
    public String getMetadataLabel(String metadataKey) {
        return ServiceManager.getRulesetService().getMetadataLabel(ruleset, metadataKey, getAcquisitionStage(),
                getPriorityList());
    }

    /**
     * Get translated label of MetadataEntry with key 'metadataEntryKey' nested in MetadataGroup with key 'groupKey'.
     *
     * @param metadataEntryKey key of MetadataEntry nested in MetadataGroup with key 'groupKey' whose label is returned
     *
     * @param groupKey key of MetadataGroup to which MetadataEntry with given key 'metadataEntryKey' belongs
     *
     * @return label of nested MetadataEntry
     */
    public String getMetadataEntryLabel(String metadataEntryKey, String groupKey) {
        return ServiceManager.getRulesetService().getMetadataEntryLabel(ruleset, metadataEntryKey, groupKey,
                getAcquisitionStage(), getPriorityList());
    }

    /**
     * Retrieve and return value of functional metadata 'groupDisplayLabel' from given MetadataGroup 'metadataGroup'.
     *
     * @param metadataGroup MetadataGroup for which 'groupDisplayLabel' value is returned
     * @return value of functional metadata 'groupDisplayLabel'
     */
    public String getGroupDisplayLabel(MetadataGroup metadataGroup) {
        try {
            Collection<String> groupDisplayLabel = ImportService.getGroupDisplayLabelMetadata(process.getRuleset());
            return ServiceManager.getRulesetService().getAnyNestedMetadataValue(metadataGroup, groupDisplayLabel);
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return "";
        }
    }

    /**
     * Get value of 'globalLayoutLoaded'.
     *
     * @return value of 'globalLayoutLoaded'
     */
    public boolean isGlobalLayoutLoaded() {
        return globalLayoutLoaded;
    }

    /**
     * Get value of 'taskLayoutLoaded'.
     *
     * @return value of 'taskLayoutLoaded'
     */
    public boolean isTaskLayoutLoaded() {
        return taskLayoutLoaded;
    }
}
