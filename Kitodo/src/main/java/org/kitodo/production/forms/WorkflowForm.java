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

package org.kitodo.production.forms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Named;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.Role;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.data.database.enums.WorkflowStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.WorkflowException;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.model.LazyDTOModel;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.DataEditorSettingService;
import org.kitodo.production.services.data.TemplateService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.production.services.workflow.WorkflowControllerService;
import org.kitodo.production.workflow.model.Converter;
import org.kitodo.production.workflow.model.Reader;

@Named("WorkflowForm")
@ViewScoped
public class WorkflowForm extends BaseForm {

    private static final Logger logger = LogManager.getLogger(WorkflowForm.class);
    private Workflow workflow = new Workflow();

    private final transient DataEditorSettingService dataEditorSettingService = ServiceManager.getDataEditorSettingService();
    private final transient FileService fileService = ServiceManager.getFileService();
    private String svgDiagram;
    private boolean dataEditorSettingsDefined = false;
    private String xmlDiagram;
    private WorkflowStatus workflowStatus;
    private static final String BPMN_EXTENSION = ".bpmn20.xml";
    private static final String SVG_EXTENSION = ".svg";
    private static final String SVG_DIAGRAM_URI = "svgDiagramURI";
    private static final String XML_DIAGRAM_URI = "xmlDiagramURI";
    private Map<String, URI> activeDiagramsUris = new HashMap<>();
    private String activeWorkflowTitle;
    private final String workflowEditPath = MessageFormat.format(REDIRECT_PATH, "workflowEdit");
    private Integer roleId;
    private boolean migration;
    private static final String MIGRATION_FORM_PATH = MessageFormat.format(REDIRECT_PATH,"system");

    /**
     * Constructor.
     */
    public WorkflowForm() {
        super.setLazyDTOModel(new LazyDTOModel(ServiceManager.getWorkflowService()));
    }

    /**
     * Get list of workflow statues for select list.
     *
     * @return array of SelectItem objects
     */
    public WorkflowStatus[] getWorkflowStatuses() {
        return WorkflowStatus.values();
    }

    /**
     * Get workflowStatus.
     *
     * @return value of workflowStatus
     */
    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    /**
     * Set workflowStatus.
     *
     * @param workflowStatus as org.kitodo.data.database.enums.WorkflowStatus
     */
    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    /**
     * Read XML for file chosen out of the select list.
     */
    public void readXMLDiagram() {
        URI xmlDiagramURI = new File(
                ConfigCore.getKitodoDiagramDirectory() + encodeXMLDiagramName(this.workflow.getTitle())).toURI();
        xmlDiagram = readFileContent(xmlDiagramURI);
    }

    private String readFileContent(URI fileUri) {
        if (fileService.fileExist(fileUri)) {
            try (InputStream inputStream = fileService.read(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            }
        }
        return "";
    }

    /**
     * Save workflow and redirect to list view.
     *
     * @return url to list view
     */
    public String saveAndRedirect() {
        if (migration && WorkflowStatus.DRAFT.equals(this.workflowStatus)) {
            Helper.setErrorMessage(Helper.getTranslation("errorMigrationDraft"));
            return this.stayOnCurrentPage;
        }
        try {
            if (saveFiles()) {
                this.workflow.setStatus(this.workflowStatus);
                saveWorkflow();
                if (!this.workflow.getTemplates().isEmpty()) {
                    updateTemplateTasks();
                }
                if (Objects.nonNull(activeWorkflowTitle)
                        && !activeWorkflowTitle.equals(this.workflow.getTitle())) {
                    deleteOldWorkflowFiles(activeWorkflowTitle);
                    activeWorkflowTitle = this.workflow.getTitle();
                }
                if (migration) {
                    migration = false;
                    return MIGRATION_FORM_PATH + "&workflowId=" + workflow.getId();
                }
                return projectsPage;
            } else {
                return this.stayOnCurrentPage;
            }
        } catch (DataException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return this.stayOnCurrentPage;
        } catch (IOException | DAOException e) {
            Helper.setErrorMessage("errorDiagramFile", new Object[] {this.workflow.getTitle() }, logger, e);
            return this.stayOnCurrentPage;
        } catch (WorkflowException e) {
            Helper.setErrorMessage("errorDiagramTask", new Object[] {this.workflow.getTitle(), e.getMessage() }, logger,
                e);
            return this.stayOnCurrentPage;
        }
    }

    /**
     * Update the tasks of the templates associated with the current workflow and delete associated
     * editor settings.
     */
    public void updateTemplateTasks() throws DAOException, IOException, WorkflowException, DataException {
        Converter converter = new Converter(this.workflow.getTitle());
        for (Template workflowTemplate : this.workflow.getTemplates()) {
            List<Task> templateTasks = new ArrayList<>(workflowTemplate.getTasks());
            if (!templateTasks.isEmpty()) {
                if (this.dataEditorSettingsDefined) {
                    for (Task templateTask : templateTasks) {
                        this.dataEditorSettingService.removeFromDatabaseByTaskId(templateTask.getId());
                    }
                }
                workflowTemplate.getTasks().clear();
                TemplateService templateService = ServiceManager.getTemplateService();
                converter.convertWorkflowToTemplate(workflowTemplate);
                templateService.save(workflowTemplate, true);
                new WorkflowControllerService().activateNextTasks(workflowTemplate.getTasks());
            }
        }
    }

    /**
     * Check if the workflow has associated data editor settings.
     * @return value of dataEditorSettingsDefined
     */
    public boolean hasWorkflowDataEditorSettingsDefined() {
        return this.dataEditorSettingsDefined;
    }

    /**
     * Cancel Workflow creation.
     * @return redirectPath
     */
    public String cancel() {
        if (migration) {
            try {
                ServiceManager.getWorkflowService().remove(workflow);
            } catch (DataException e) {
                Helper.setErrorMessage(ERROR_DELETING, new Object[] {this.workflow.getTitle(), e.getMessage() }, logger,
                    e);
                return this.stayOnCurrentPage;
            }
            migration = false;
            return MIGRATION_FORM_PATH;
        }

        return "projects?keepPagination=true&faces-redirect=true";
    }

    /**
     * Archive active workflow.
     */
    public void archive() {
        this.workflow.setStatus(WorkflowStatus.ARCHIVED);
        try {
            saveWorkflow();
        } catch (DataException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Remove workflow if no template is assigned to it.
     */
    public void delete() {
        if (!this.workflow.getTemplates().isEmpty()) {
            Helper.setErrorMessage("templateAssignedError");
        } else {
            try {
                ServiceManager.getWorkflowService().remove(this.workflow);
                deleteOldWorkflowFiles(this.workflow.getTitle());

            } catch (DataException e) {
                Helper.setErrorMessage(ERROR_DELETING, new Object[] {ObjectType.WORKFLOW.getTranslationSingular() },
                    logger, e);
            }
        }
    }

    private void deleteOldWorkflowFiles(String oldDiagramTitle) {
        try {
            String diagramDirectory = ConfigCore.getKitodoDiagramDirectory();
            URI svgDiagramURI = new File(
                    diagramDirectory + decodeXMLDiagramName(oldDiagramTitle) + SVG_EXTENSION).toURI();
            URI xmlDiagramURI = new File(diagramDirectory + encodeXMLDiagramName(oldDiagramTitle))
                    .toURI();
            if (fileService.fileExist(svgDiagramURI)) {
                fileService.delete(svgDiagramURI);
            }
            if (fileService.fileExist(xmlDiagramURI)) {
                fileService.delete(xmlDiagramURI);
            }
        } catch (IOException e) {
            Helper.setErrorMessage(ERROR_DELETING, new Object[] {ObjectType.WORKFLOW.getTranslationSingular() },
                    logger, e);
        }

    }

    /**
     * Save content of the diagram files.
     *
     * @return true if save, false if not
     */
    private boolean saveFiles() throws IOException, WorkflowException {
        Map<String, String> requestParameterMap = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap();

        Map<String, URI> diagramsUris = getDiagramUris();

        URI svgDiagramURI = diagramsUris.get(SVG_DIAGRAM_URI);
        URI xmlDiagramURI = diagramsUris.get(XML_DIAGRAM_URI);

        xmlDiagram = requestParameterMap.get("editForm:workflowTabView:xmlDiagram");
        if (Objects.nonNull(xmlDiagram)) {
            if (xmlDiagram.contains("kitodo-diagram-separator")) {
                svgDiagram = StringUtils.substringAfter(xmlDiagram, "kitodo-diagram-separator");
                xmlDiagram = StringUtils.substringBefore(xmlDiagram, "kitodo-diagram-separator");
            }
            if (xmlDiagram.isEmpty()) {
                Helper.setErrorMessage("emptyWorkflow");
                return false;
            }
            activeDiagramsUris.put(XML_DIAGRAM_URI, xmlDiagramURI);


            Reader reader = new Reader(new ByteArrayInputStream(xmlDiagram.getBytes(StandardCharsets.UTF_8)));
            reader.validateWorkflowTasks();

            Converter converter = new Converter(new ByteArrayInputStream(xmlDiagram.getBytes(StandardCharsets.UTF_8)));
            converter.validateWorkflowTaskList();

            if (Objects.nonNull(svgDiagram)) {
                saveFile(svgDiagramURI, svgDiagram);
                activeDiagramsUris.put(SVG_DIAGRAM_URI, svgDiagramURI);
            } else {
                if (fileService.fileExist(activeDiagramsUris.get(SVG_DIAGRAM_URI))) {
                    try (InputStream svgInputStream = ServiceManager.getFileService().read(activeDiagramsUris.get(SVG_DIAGRAM_URI))) {
                        svgDiagram = IOUtils.toString(svgInputStream, StandardCharsets.UTF_8);
                        saveFile(svgDiagramURI, svgDiagram);
                    }
                } else {
                    saveFile(svgDiagramURI, "");
                }
                activeDiagramsUris.put(SVG_DIAGRAM_URI, svgDiagramURI);
            }
            saveFile(xmlDiagramURI, xmlDiagram);
        }
        return fileService.fileExist(xmlDiagramURI) && fileService.fileExist(svgDiagramURI);
    }

    private Map<String, URI> getDiagramUris() {
        return getDiagramUris(this.workflow.getTitle());
    }

    private Map<String, URI> getDiagramUris(String fileName) {
        String diagramDirectory = ConfigCore.getKitodoDiagramDirectory();
        URI svgDiagramURI = new File(diagramDirectory + decodeXMLDiagramName(fileName) + SVG_EXTENSION)
                .toURI();
        URI xmlDiagramURI = new File(diagramDirectory + encodeXMLDiagramName(fileName)).toURI();

        Map<String, URI> diagramUris = new HashMap<>();
        diagramUris.put(SVG_DIAGRAM_URI, svgDiagramURI);
        diagramUris.put(XML_DIAGRAM_URI, xmlDiagramURI);
        return diagramUris;
    }

    void saveFile(URI fileURI, String fileContent) {
        try (OutputStream outputStream = fileService.write(fileURI);
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            bufferedWriter.write(fileContent);
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    private String decodeXMLDiagramName(String xmlDiagramName) {
        if (xmlDiagramName.contains(BPMN_EXTENSION)) {
            return xmlDiagramName.replace(BPMN_EXTENSION, "");
        }
        return xmlDiagramName;

    }

    private String encodeXMLDiagramName(String xmlDiagramName) {
        if (!xmlDiagramName.contains(BPMN_EXTENSION)) {
            return xmlDiagramName + BPMN_EXTENSION;
        }
        return xmlDiagramName;
    }

    private void saveWorkflow() throws DataException {
        ServiceManager.getWorkflowService().saveWorkflow(this.workflow);
    }

    /**
     * Create new workflow.
     *
     * @return page
     */
    public String newWorkflow() {
        this.workflow = new Workflow();
        this.workflow.setClient(ServiceManager.getUserService().getSessionClientOfAuthenticatedUser());
        return workflowEditPath + "&id=" + (Objects.isNull(this.workflow.getId()) ? 0 : this.workflow.getId());
    }

    /**
     * Duplicate the selected workflow.
     *
     * @param itemId
     *            ID of the workflow to duplicate
     * @return page address; either redirect to the edit workflow page or return
     *         'null' if the workflow could not be retrieved, which will prompt JSF
     *         to remain on the same page and reuse the bean.
     */
    public String duplicate(Integer itemId) {
        try {
            Workflow baseWorkflow = ServiceManager.getWorkflowService().getById(itemId);

            Map<String, URI> diagramsUris = getDiagramUris(baseWorkflow.getTitle());

            URI xmlDiagramURI = diagramsUris.get(XML_DIAGRAM_URI);
            URI svgDiagramURI = diagramsUris.get(SVG_DIAGRAM_URI);

            this.workflow = ServiceManager.getWorkflowService().duplicateWorkflow(baseWorkflow);
            setWorkflowStatus(WorkflowStatus.DRAFT);

            // Read XML diagram
            try (InputStream xmlInputStream = ServiceManager.getFileService().read(xmlDiagramURI)) {
                this.xmlDiagram = IOUtils.toString(xmlInputStream, StandardCharsets.UTF_8);
            }
            // Read SVG diagram (use a separate input stream)
            try (InputStream svgInputStream = ServiceManager.getFileService().read(svgDiagramURI)) {
                this.svgDiagram = IOUtils.toString(svgInputStream, StandardCharsets.UTF_8);
            }
            // Store duplicated workflow in Flash scope
            ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
            externalContext.getFlash().put("duplicatedWorkflow", this.workflow);
            externalContext.getFlash().put("xmlDiagram", this.xmlDiagram);
            externalContext.getFlash().put("svgDiagram", this.svgDiagram);

            return workflowEditPath + "&id=0";


        } catch (IOException | DAOException e) {
            Helper.setErrorMessage(ERROR_DUPLICATE, new Object[] {ObjectType.WORKFLOW.getTranslationSingular() },
                logger, e);
            return this.stayOnCurrentPage;
        }
    }

    /**
     * Set workflow by id.
     *
     * @param id
     *            of workflow to set
     */
    public void setWorkflowById(int id) {
        try {
            setWorkflow(ServiceManager.getWorkflowService().getById(id));
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_LOADING_ONE, new Object[] {ObjectType.WORKFLOW.getTranslationSingular(), id },
                logger, e);
        }
    }

    /**
     * Method being used as viewAction for workflow edit form. If the given
     * parameter 'id' is '0', the form for creating a new workflow will be
     * displayed.
     *
     * @param id
     *            of the workflow to load
     */
    public void load(int id) {
        try {
            if (id > 0) {
                // Normal case: Load workflow from database
                Workflow workflow = ServiceManager.getWorkflowService().getById(id);
                setWorkflow(workflow);
                setWorkflowStatus(workflow.getStatus());
                activeDiagramsUris = getDiagramUris(workflow.getTitle());
                activeWorkflowTitle = workflow.getTitle();
                readXMLDiagram();
                this.dataEditorSettingsDefined = this.dataEditorSettingService.areDataEditorSettingsDefinedForWorkflow(workflow);
            } else {
                // Check if duplicated workflow is stored in Flash scope
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                Map<String, Object> flash = externalContext.getFlash();

                if (flash.containsKey("duplicatedWorkflow")) {
                    this.workflow = (Workflow) flash.get("duplicatedWorkflow");
                    this.xmlDiagram = (String) flash.get("xmlDiagram");
                    this.svgDiagram = (String) flash.get("svgDiagram");
                    setWorkflowStatus(workflow.getStatus());
                    this.dataEditorSettingsDefined = this.dataEditorSettingService.areDataEditorSettingsDefinedForWorkflow(workflow);
                }
                if (this.workflow.getClient() == null) {
                    this.workflow.setClient(ServiceManager.getUserService().getSessionClientOfAuthenticatedUser());
                }
            }
            setSaveDisabled(false);
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_LOADING_ONE, new Object[] {ObjectType.WORKFLOW.getTranslationSingular(), id },
                logger, e);
        }
    }

    /**
     * Get role id.
     *
     * @return value of roleId
     */
    public Integer getRoleId() {
        return roleId;
    }

    /**
     * Set role idd.
     *
     * @param roleId
     *            as Integer.
     */
    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    /**
     * Get hidden list of roles.
     *
     * @return hidden list of roles
     */
    public List<SelectItem> getRoles() {
        List<SelectItem> selectItems = new ArrayList<>();

        List<Role> roles = ServiceManager.getRoleService()
                .getAllRolesByClientId(ServiceManager.getUserService().getSessionClientId());
        for (Role role : roles) {
            selectItems.add(new SelectItem(role.getId(), role.getTitle(), null));
        }
        return selectItems;
    }

    /**
     * Get workflow.
     *
     * @return value of workflow
     */
    public Workflow getWorkflow() {
        return workflow;
    }

    /**
     * Set workflow.
     *
     * @param workflow
     *            as Workflow
     */
    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    /**
     * Get content of XML diagram file.
     *
     * @return content of XML diagram file as String
     */
    public String getXmlDiagram() {
        return xmlDiagram;
    }

    /**
     * Set content of XML diagram file.
     *
     * @param xmlDiagram
     *            content of XML diagram as String
     */
    public void setXmlDiagram(String xmlDiagram) {
        this.xmlDiagram = xmlDiagram;
    }

    /**
     * Get content of SVG diagram file.
     *
     * @return content of SVG diagram file as String
     */
    String getSvgDiagram() {
        return svgDiagram;
    }

    /**
     * Set content of SVG diagram file.
     *
     * @param svgDiagram
     *            content of SVG diagram as String
     */
    void setSvgDiagram(String svgDiagram) {
        this.svgDiagram = svgDiagram;
    }

    /**
     * Get migration.
     *
     * @return value of migration
     */
    public boolean isMigration() {
        return migration;
    }

    /**
     * Set migration.
     *
     * @param migration as boolean
     */
    public void setMigration(boolean migration) {
        this.migration = migration;
    }

    /**
     * Get language.
     *
     * @return language of the currently logged-in user
     */
    public String getLanguage() {
        return ServiceManager.getUserService().getCurrentUser().getLanguage();
    }

    /**
     * Set language.
     *
     * @param language as String
     */
    public void setLanguage(String language) {
        // We don't need to do anything. The language value is written into a hidden input field for the localization
        // of the editor. On saving the workflow form it gets submitted again. Therefore, a setter is expected, and we
        // only need it for completeness’s sake. If we find a better way to get the language value into the editor's JS
        // we should do so. :)
    }
}
