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

package org.kitodo.production.services.data;

import static org.kitodo.data.database.enums.CorrectionComments.NO_CORRECTION_COMMENTS;
import static org.kitodo.data.database.enums.CorrectionComments.NO_OPEN_CORRECTION_COMMENTS;
import static org.kitodo.data.database.enums.CorrectionComments.OPEN_CORRECTION_COMMENTS;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalDivision;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.docket.DocketData;
import org.kitodo.api.docket.DocketInterface;
import org.kitodo.api.filemanagement.ProcessSubType;
import org.kitodo.api.filemanagement.filters.FileNameBeginsAndEndsWithFilter;
import org.kitodo.api.filemanagement.filters.FileNameEndsAndDoesNotBeginWithFilter;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Batch;
import org.kitodo.data.database.beans.Comment;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Role;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.enums.CommentType;
import org.kitodo.data.database.enums.CorrectionComments;
import org.kitodo.data.database.enums.IndexAction;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.BaseDAO;
import org.kitodo.data.database.persistence.ProcessDAO;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.type.enums.BatchTypeField;
import org.kitodo.data.elasticsearch.index.type.enums.ProcessTypeField;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.data.interfaces.BatchInterface;
import org.kitodo.data.interfaces.ProcessInterface;
import org.kitodo.data.interfaces.PropertyInterface;
import org.kitodo.data.interfaces.TaskInterface;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.export.ExportMets;
import org.kitodo.production.dto.DTOFactory;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.SearchResultGeneration;
import org.kitodo.production.helper.WebDav;
import org.kitodo.production.helper.metadata.ImageHelper;
import org.kitodo.production.helper.metadata.MetadataHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyDocStructHelperInterface;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetadataHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetadataTypeHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.metadata.copier.CopierData;
import org.kitodo.production.metadata.copier.DataCopier;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.base.SearchDatabaseService;
import org.kitodo.production.services.data.interfaces.DatabaseProcessServiceInterface;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.production.services.workflow.WorkflowControllerService;
import org.kitodo.production.workflow.KitodoNamespaceContext;
import org.kitodo.serviceloader.KitodoServiceLoader;
import org.primefaces.model.SortOrder;
import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.axes.cartesian.linear.CartesianLinearAxes;
import org.primefaces.model.charts.bar.BarChartOptions;
import org.primefaces.model.charts.hbar.HorizontalBarChartDataSet;
import org.primefaces.model.charts.hbar.HorizontalBarChartModel;
import org.primefaces.model.charts.optionconfig.tooltip.Tooltip;
import org.primefaces.model.charts.pie.PieChartDataSet;
import org.primefaces.model.charts.pie.PieChartModel;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ProcessService extends SearchDatabaseService<Process, ProcessDAO>
        implements DatabaseProcessServiceInterface {

    private static final Map<String, String> SORT_FIELD_MAPPING;

    static {
        SORT_FIELD_MAPPING = new HashMap<>();
        SORT_FIELD_MAPPING.put("id", "id");
        SORT_FIELD_MAPPING.put("title.keyword", "title");
        SORT_FIELD_MAPPING.put("progressCombined", "sortHelperStatus");
        SORT_FIELD_MAPPING.put("lastEditingUser", "id"); // FIXME
        SORT_FIELD_MAPPING.put("processingBeginLastTask", "id"); // FIXME
        SORT_FIELD_MAPPING.put("processingEndLastTask", "id"); // FIXME
        SORT_FIELD_MAPPING.put("correctionCommentStatus", "id"); // FIXME
        SORT_FIELD_MAPPING.put("project.title.keyword", "project_id");
        SORT_FIELD_MAPPING.put("creationDate", "creationDate");
    }

    private static final FileService fileService = ServiceManager.getFileService();
    private static final Logger logger = LogManager.getLogger(ProcessService.class);
    private static volatile ProcessService instance = null;
    private static final String JSON_TITLE = "title";
    private static final String JSON_VALUE = "value";
    private static final String DIRECTORY_PREFIX = ConfigCore.getParameter(ParameterCore.DIRECTORY_PREFIX, "orig");
    private static final String DIRECTORY_SUFFIX = ConfigCore.getParameter(ParameterCore.DIRECTORY_SUFFIX, "tif");
    private static final String SUFFIX = ConfigCore.getParameter(ParameterCore.METS_EDITOR_DEFAULT_SUFFIX, "");
    private static final String PROCESS_TITLE = "(processtitle)";
    private static final String METADATA_SEARCH_KEY = ProcessTypeField.METADATA + ".mdWrap.xmlData.kitodo.metadata";
    private static final String METADATA_GROUP_SEARCH_KEY = ProcessTypeField.METADATA + ".mdWrap.xmlData.kitodo.metadataGroup.metadata";
    private static final String METADATA_FILE_NAME = "meta.xml";
    private static final String NEW_LINE_ENTITY = "\n";
    private static final boolean USE_ORIG_FOLDER = ConfigCore
            .getBooleanParameterOrDefaultValue(ParameterCore.USE_ORIG_FOLDER);
    private static final Map<Integer, Collection<String>> RULESET_CACHE_FOR_CREATE_FROM_CALENDAR = new HashMap<>();
    private static final Map<Integer, Collection<String>> RULESET_CACHE_FOR_CREATE_CHILD_FROM_PARENT = new HashMap<>();
    private static final List<String> BG_COLORS = Arrays
            .asList(ConfigCore.getParameterOrDefaultValue(ParameterCore.ISSUE_COLOURS).split(";"));

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    private ProcessService() {
        super(new ProcessDAO());
    }

    /**
     * Return singleton variable of type ProcessService.
     *
     * @return unique instance of ProcessService
     */
    public static ProcessService getInstance() {
        ProcessService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (ProcessService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new ProcessService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    /**
     * Emptys the cache generated from ruleset, so changes in Ruleset are recognized in new session.
     */
    public static void emptyCache() {
        RULESET_CACHE_FOR_CREATE_CHILD_FROM_PARENT.clear();
        RULESET_CACHE_FOR_CREATE_FROM_CALENDAR.clear();
    }

    /**
     * Checks if an imported Process should be created with Tasks and removes them if not,
     * depending on the configuration of the doctype.
     * @param process the process to check.
     * @param docType the doctype to check in the ruleset.
     */
    public static void checkTasks(Process process, String docType) throws IOException {
        // remove tasks from process, if doctype is configured not to use a workflow
        Collection<String> divisionsWithNoWorkflow = ServiceManager.getRulesetService()
                .openRuleset(process.getRuleset()).getDivisionsWithNoWorkflow();
        if (divisionsWithNoWorkflow.contains(docType)) {
            process.getTasks().clear();
        }
    }

    @Override
    public Long countDatabaseRows() throws DAOException {
        return countDatabaseRows("SELECT COUNT(*) FROM Process WHERE " + BaseDAO.getDateFilter("creationDate"));
    }

    @Override
    public Long countResults(Map filters) throws DataException {
        return countResults(filters, false, false);
    }

    @Override
    public Long countResults(Map<?, String> filters, boolean showClosedProcesses, boolean showInactiveProjects)
            throws DataException {
        try {
            BeanQuery query = new BeanQuery(Process.class);
            query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
            Iterator<? extends Entry<?, String>> filtersIterator = filters.entrySet().iterator();
            if (filtersIterator.hasNext()) {
                String filterString = filtersIterator.next().getValue();
                if (StringUtils.isNotBlank(filterString)) {
                    query.restrictWithUserFilterString(filterString);
                }
            }
            if (!showClosedProcesses) {
                query.restrictToNotCompletedProcesses();
            }
            Collection<Integer> projectIDs = ServiceManager.getUserService().getCurrentUser().getProjects().stream()
                    .filter(project -> showInactiveProjects || project.isActive()).map(Project::getId)
                    .collect(Collectors.toList());
            query.restrictToProjects(projectIDs);
            return countDatabaseRows(query.formCountQuery(), query.getQueryParameters());
        } catch (DAOException e) {
            throw new DataException(e);
        }
    }

    @Override
    public void save(Process process) throws DataException {
        this.save(process, false);
    }

    @Override
    public void save(Process process, boolean updateRelatedObjectsInIndex) throws DataException {
        WorkflowControllerService.updateProcessSortHelperStatus(process);
        
        // save parent processes if they are new and do not have an id yet
        List<Process> parents = findParentProcesses(process);
        for (Process parent: parents) {
            if (Objects.isNull(parent.getId())) {
                super.save(parent, updateRelatedObjectsInIndex);
            }
        }
        
        super.save(process, updateRelatedObjectsInIndex);

        // save parent processes in order to refresh ElasticSearch index
        for (Process parent : parents) {
            super.save(parent, updateRelatedObjectsInIndex);
        }
    }

    @Override
    public void saveToIndex(Process process, boolean forceRefresh)
            throws CustomResponseException, DataException, IOException {

        enrichProcessData(process, false);

        super.saveToIndex(process, forceRefresh);
    }

    /**
     * Find all parent processes for a process ordered such that the root parent comes first.
     * 
     * @param process the process whose parents are to be found
     * @return the list of parent processes (direct parents and grand parents, and more)
     */
    public List<Process> findParentProcesses(Process process) {
        List<Process> parents = new ArrayList<Process>();
        Process current = process;
        while (Objects.nonNull(current.getParent())) {
            current = current.getParent();
            parents.add(current);
        }
        Collections.reverse(parents);
        return parents;
    }

    private int getNumberOfImagesForIndex(Workpiece workpiece) {
        return Math.toIntExact(Workpiece.treeStream(workpiece.getPhysicalStructure())
                .filter(physicalDivision -> Objects.equals(physicalDivision.getType(), PhysicalDivision.TYPE_PAGE)).count());
    }

    private int getNumberOfMetadata(Workpiece workpiece) {
        return Math.toIntExact(MetsService.countLogicalMetadata(workpiece));
    }

    private int getNumberOfStructures(Workpiece workpiece) {
        return Math.toIntExact(Workpiece.treeStream(workpiece.getLogicalStructure()).count());
    }

    @Override
    public void addAllObjectsToIndex(List<Process> processes) throws CustomResponseException, DAOException, IOException {
        for (Process process : processes) {
            enrichProcessData(process, true);
        }
        super.addAllObjectsToIndex(processes);
    }

    private void enrichProcessData(Process process, boolean forIndexingAll) throws IOException {
        process.setMetadata(getMetadataForIndex(process, forIndexingAll));
        URI metadataFilePath = fileService.getMetadataFilePath(process, false, forIndexingAll);
        if (!fileService.fileExist(metadataFilePath)) {
            logger.info("No metadata file for indexing: {}", metadataFilePath);
        } else {
            try {
                Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metadataFilePath);
                process.setNumberOfImages(getNumberOfImagesForIndex(workpiece));
                process.setNumberOfMetadata(getNumberOfMetadata(workpiece));
                process.setNumberOfStructures(getNumberOfStructures(workpiece));
                process.setBaseType(getBaseType(workpiece));
            } catch (IllegalArgumentException | IOException e) {
                logger.warn("Cannot read metadata file for indexing: {}", metadataFilePath);
                logger.catching(Level.DEBUG, e);
            }
        }
    }

    /**
     * MetadataType aus Preferences eines Prozesses ermitteln.
     *
     * @param inProzess
     *            Process object
     * @param inName
     *            String
     * @return MetadataType
     */
    @Deprecated
    public static LegacyMetadataTypeHelper getMetadataType(Process inProzess, String inName) {
        LegacyPrefsHelper myPrefs = ServiceManager.getRulesetService().getPreferences(inProzess.getRuleset());
        return LegacyPrefsHelper.getMetadataType(myPrefs, inName);
    }

    @Override
    public List<Process> loadData(int first, int pageSize, String sortField,
            org.primefaces.model.SortOrder sortOrder, Map<?, String> filters) throws DataException {
        return loadData(first, pageSize, sortField, sortOrder, filters, false, false);
    }

    @Override
    public List<Process> loadData(int first, int pageSize, String sortField, SortOrder sortOrder, Map<?, String> filters,
                                     boolean showClosedProcesses, boolean showInactiveProjects) throws DataException {
        BeanQuery query = new BeanQuery(Process.class);
        query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
        if (Objects.nonNull(filters)) {
            Iterator<? extends Entry<?, String>> filtersIterator = filters.entrySet().iterator();
            if (filtersIterator.hasNext()) {
                String filterString = filtersIterator.next().getValue();
                if (StringUtils.isNotBlank(filterString)) {
                    query.restrictWithUserFilterString(filterString);
                }
            }
        }
        if (!showClosedProcesses) {
            query.restrictToNotCompletedProcesses();
        }
        Collection<Integer> projectIDs = ServiceManager.getUserService().getCurrentUser().getProjects().stream()
                .filter(project -> showInactiveProjects || project.isActive()).map(Project::getId)
                .collect(Collectors.toList());
        query.restrictToProjects(projectIDs);
        query.defineSorting(SORT_FIELD_MAPPING.get(sortField), sortOrder);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters(), first, pageSize);
    }

    /**
     * Check if IndexAction flag is delete. If true remove process from list of
     * processes and re-save batch, if false only re-save batch object.
     *
     * @param process
     *            object
     */
    private void manageBatchesDependenciesForIndex(Process process)
            throws CustomResponseException, DataException, IOException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Batch batch : process.getBatches()) {
                batch.getProcesses().remove(process);
                ServiceManager.getBatchService().saveToIndex(batch, false);
            }
        } else {
            for (Batch batch : process.getBatches()) {
                ServiceManager.getBatchService().saveToIndex(batch, false);
            }
        }
    }

    /**
     * Add process to project, if project is assigned to process.
     *
     * @param process
     *            object
     */
    private void manageProjectDependenciesForIndex(Process process)
            throws CustomResponseException, DataException, IOException {
        if (Objects.nonNull(process.getProject())) {
            ServiceManager.getProjectService().saveToIndex(process.getProject(), false);
        }
    }

    /**
     * Check IndexAction flag in for process object. If DELETE remove all tasks
     * from index, if other call saveOrRemoveTaskInIndex() method.
     *
     * @param process
     *            object
     */
    private void manageTaskDependenciesForIndex(Process process)
            throws CustomResponseException, DAOException, IOException, DataException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Task task : process.getTasks()) {
                ServiceManager.getTaskService().removeFromIndex(task, false);
            }
        }
    }



    /**
     * Compare two list and return difference between them.
     *
     * @param firstList
     *            list from which records can be remove
     * @param secondList
     *            records stored here will be removed from firstList
     * @return difference between two lists
     */
    private List<Integer> findMissingValues(List<Integer> firstList, List<Integer> secondList) {
        List<Integer> newList = new ArrayList<>(firstList);
        newList.removeAll(secondList);
        return newList;
    }

    @Override
    public void saveList(List<Process> list) throws DAOException {
        dao.saveList(list);
    }

    @Override
    public void refresh(Process process) {
        dao.refresh(process);
    }

    /**
     * Find processes by metadata. Matches do not need to be exact.
     *
     * @param metadata
     *            key is metadata tag and value is metadata content
     * @return list of ProcessInterface objects with processes for specific metadata tag
     */
    public List<ProcessInterface> findByMetadata(Map<String, String> metadata) throws DataException {
        return findByMetadata(metadata, false);
    }

    @Override
    public List<?> findByDocket(int docketId) throws DataException {
        BeanQuery query = new BeanQuery(Process.class);
        query.addIntegerRestriction("docket_id", docketId);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters(), 0, 1);
    }

    @Override
    public List<?> findByTemplate(int templateId) throws DataException {
        BeanQuery query = new BeanQuery(Process.class);
        query.addIntegerRestriction("template_id", templateId);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters(), 0, 1);
    }

    @Override
    public List<?> findByRuleset(int rulesetId) throws DataException {
        BeanQuery query = new BeanQuery(Process.class);
        query.addIntegerRestriction("ruleset_id", rulesetId);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters(), 0, 1);
    }

    @Override
    public List<Process> findLinkableChildProcesses(String searchInput, int rulesetId,
            Collection<String> allowedStructuralElementTypes) throws DataException {

        BeanQuery query = new BeanQuery(Process.class);
        query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
        query.addNullRestriction("parent_id");
        query.addIntegerRestriction("ruleset_id", rulesetId);
        query.forIdOrInTitle(searchInput);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters());
    }

    @Override
    public List<Process> findLinkableParentProcesses(String searchInput, int projectId, int rulesetId)
            throws DataException {

        BeanQuery query = new BeanQuery(Process.class);
        query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
        query.addIntegerRestriction("project_id", projectId);
        query.addIntegerRestriction("ruleset_id", rulesetId);
        query.forIdOrInTitle(searchInput);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters());
    }

    @Override
    public List<Process> findSelectedProcesses(boolean showClosedProcesses, boolean showInactiveProjects,
            String filter, Collection<Integer> excludedProcessIds) throws DataException {
        BeanQuery query = new BeanQuery(Process.class);
        query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
        query.addNotInCollectionRestriction("id", excludedProcessIds);
        if (StringUtils.isNotBlank(filter)) {
            query.restrictWithUserFilterString(filter);
        }
        if (!showClosedProcesses) {
            query.restrictToNotCompletedProcesses();
        }
        Collection<Integer> projectIDs = ServiceManager.getUserService().getCurrentUser().getProjects().stream()
                .filter(project -> showInactiveProjects || project.isActive()).map(Project::getId)
                .collect(Collectors.toList());
        query.restrictToProjects(projectIDs);
        return getByQuery(query.formQueryForAll(), query.getQueryParameters());
    }

    /**
     * Convert list of Interfaces to list of beans.
     *
     * @param dtos
     *            list of Interface objects
     * @return list of beans
     */
    public List<Process> convertDtosToBeans(List<ProcessInterface> dtos) throws DAOException {
        List<Process> processes = new ArrayList<>();
        for (ProcessInterface process : dtos) {
            processes.add(getById(process.getId()));
        }
        return processes;
    }

    /**
     * Parses last processing dates from the jsonObject and adds them to the process bean.
     * 
     * @param jsonObject the json object retrieved from elastic search
     * @param process the process bean that will receive the processing dates
     */
    private void convertLastProcessingDates(Map<String, Object> jsonObject, ProcessInterface process) throws DataException {
        String processingBeginLastTask = ProcessTypeField.PROCESSING_BEGIN_LAST_TASK.getStringValue(jsonObject);
        process.setProcessingBeginLastTask(Helper.parseDateFromFormattedString(processingBeginLastTask));
        String processingEndLastTask = ProcessTypeField.PROCESSING_END_LAST_TASK.getStringValue(jsonObject);
        process.setProcessingEndLastTask(Helper.parseDateFromFormattedString(processingEndLastTask));
    }

    /**
     * Parses task progress properties from the jsonObject and adds them to the process bean.
     * 
     * @param jsonObject the json object retrieved from elastic search
     * @param process the process bean that will receive the progress information
     */
    private void convertTaskProgress(Map<String, Object> jsonObject, ProcessInterface process) throws DataException {
        process.setProgressClosed(ProcessTypeField.PROGRESS_CLOSED.getDoubleValue(jsonObject));
        process.setProgressInProcessing(ProcessTypeField.PROGRESS_IN_PROCESSING.getDoubleValue(jsonObject));
        process.setProgressOpen(ProcessTypeField.PROGRESS_OPEN.getDoubleValue(jsonObject));
        process.setProgressLocked(ProcessTypeField.PROGRESS_LOCKED.getDoubleValue(jsonObject));
        process.setProgressCombined(ProcessTypeField.PROGRESS_COMBINED.getStringValue(jsonObject));
    }

    private List<PropertyInterface> convertProperties(Map<String, Object> jsonObject) throws DataException {
        List<Map<String, Object>> jsonArray = ProcessTypeField.PROPERTIES.getJsonArray(jsonObject);
        List<PropertyInterface> properties = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : jsonArray) {
            PropertyInterface property = DTOFactory.instance().newProperty();
            Object title = stringObjectMap.get(JSON_TITLE);
            Object value = stringObjectMap.get(JSON_VALUE);
            if (Objects.nonNull(title)) {
                property.setTitle(title.toString());
                property.setValue(Objects.nonNull(value) ? value.toString() : "");
                properties.add(property);
            }
        }
        return properties;
    }

    private List<BatchInterface> getBatchesForProcess(Map<String, Object> jsonObject) throws DataException {
        List<Map<String, Object>> jsonArray = ProcessTypeField.BATCHES.getJsonArray(jsonObject);
        List<BatchInterface> batchInterfaceList = new ArrayList<>();
        for (Map<String, Object> singleObject : jsonArray) {
            BatchInterface batch = DTOFactory.instance().newBatch();
            batch.setId(BatchTypeField.ID.getIntValue(singleObject));
            batch.setTitle(BatchTypeField.TITLE.getStringValue(singleObject));
            batchInterfaceList.add(batch);
        }
        return batchInterfaceList;
    }

    /**
     * Check if process is assigned only to one batch.
     *
     * @param list
     *            list of batches for checkout
     * @return true or false
     */
    boolean isProcessAssignedToOnlyOneBatch(List<? extends BatchInterface> list) {
        return list.size() == 1;
    }

    /**
     * Get directory for TIFF images.
     *
     * @param useFallBack
     *            add description
     * @param processId
     *            id of process object
     * @param processTitle
     *            title of process object
     * @param processBaseURI
     *            base URI of process object
     * @return tif directory
     */
    public URI getImagesTifDirectory(boolean useFallBack, Integer processId, String processTitle, URI processBaseURI) {
        URI dir = fileService.getProcessSubTypeURI(processId, processTitle, processBaseURI, ProcessSubType.IMAGE, null);

        /* nur die _tif-Ordner anzeigen, die nicht mir orig_ anfangen */
        FilenameFilter filterDirectory = new FileNameEndsAndDoesNotBeginWithFilter(DIRECTORY_PREFIX + "_",
                "_" + DIRECTORY_SUFFIX);
        URI tifDirectory = null;
        List<URI> directories = fileService.getSubUris(filterDirectory, dir);
        for (URI directory : directories) {
            tifDirectory = directory;
        }

        if (Objects.isNull(tifDirectory) && useFallBack && !SUFFIX.isEmpty()) {
            List<URI> folderList = fileService.getSubUrisForProcess(null, processId, processTitle, processBaseURI,
                ProcessSubType.IMAGE, "");
            for (URI folder : folderList) {
                if (folder.toString().endsWith(SUFFIX)) {
                    tifDirectory = folder;
                    break;
                }
            }
        }

        tifDirectory = getImageDirectory(useFallBack, dir, tifDirectory);

        URI result = fileService.getProcessSubTypeURI(processId, processTitle, processBaseURI, ProcessSubType.IMAGE,
            null);

        if (Objects.isNull(tifDirectory)) {
            tifDirectory = URI
                    .create(result.getRawPath() + Helper.getNormalizedTitle(processTitle) + "_" + DIRECTORY_SUFFIX);
        }

        return tifDirectory;
    }

    /**
     * Get images origin directory.
     *
     * @param useFallBack
     *            as boolean
     * @param process
     *            object
     * @return path
     */
    public URI getImagesOriginDirectory(boolean useFallBack, Process process) {
        if (USE_ORIG_FOLDER) {
            URI dir = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);

            /* nur die _tif-Ordner anzeigen, die mit orig_ anfangen */
            FilenameFilter filterDirectory = new FileNameBeginsAndEndsWithFilter(DIRECTORY_PREFIX + "_",
                    "_" + DIRECTORY_SUFFIX);
            URI origDirectory = null;
            List<URI> directories = fileService.getSubUris(filterDirectory, dir);
            for (URI directory : directories) {
                origDirectory = directory;
            }

            if (Objects.isNull(origDirectory) && useFallBack && !SUFFIX.isEmpty()) {
                List<URI> folderList = fileService.getSubUris(dir);
                for (URI folder : folderList) {
                    if (folder.toString().endsWith(SUFFIX)) {
                        origDirectory = folder;
                        break;
                    }
                }
            }

            origDirectory = getImageDirectory(useFallBack, dir, origDirectory);

            URI result = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);

            if (Objects.isNull(origDirectory)) {
                origDirectory = URI.create(result.toString() + DIRECTORY_PREFIX + "_"
                        + Helper.getNormalizedTitle(process.getTitle()) + "_" + DIRECTORY_SUFFIX);
            }

            return origDirectory;
        } else {
            return getImagesTifDirectory(useFallBack, process.getId(), process.getTitle(), process.getProcessBaseUri());
        }
    }

    private URI getImageDirectory(boolean useFallBack, URI directory, URI imageDirectory) {
        if (Objects.nonNull(imageDirectory) && useFallBack && !SUFFIX.isEmpty()) {
            URI tif = imageDirectory;
            List<URI> files = fileService.getSubUris(tif);
            if (files.isEmpty()) {
                List<URI> folderList = fileService.getSubUris(directory);
                for (URI folder : folderList) {
                    if (folder.toString().endsWith(SUFFIX) && !folder.getPath().startsWith(DIRECTORY_PREFIX)) {
                        imageDirectory = folder;
                        break;
                    }
                }
            }
        }
        return imageDirectory;
    }

    /**
     * Returns the URI of the metadata file of a process.
     *
     * @param process
     *            object
     * @return URI
     */
    public URI getMetadataFileUri(Process process) {
        URI workPathUri = ServiceManager.getFileService().getProcessBaseUriForExistingProcess(process);
        String workDirectoryPath = workPathUri.getPath();
        try {
            return new URI(workPathUri.getScheme(), workPathUri.getUserInfo(), workPathUri.getHost(),
                    workPathUri.getPort(),
                workDirectoryPath.endsWith("/") ? workDirectoryPath.concat(METADATA_FILE_NAME)
                        : workDirectoryPath + '/' + METADATA_FILE_NAME,
                    workPathUri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public URI getProcessDataDirectory(Process process, boolean forIndexingAll) {
        if (Objects.isNull(process.getProcessBaseUri())) {
            process.setProcessBaseUri(fileService.getProcessBaseUriForExistingProcess(process));
            if (!forIndexingAll) {
                try {
                    saveToDatabase(process);
                } catch (DAOException e) {
                    logger.error(e.getMessage(), e);
                    return URI.create("");
                }
            }
        }
        return process.getProcessBaseUri();
    }

    @Override
    public String getProcessDataDirectory(ProcessInterface process) {
        if (Objects.isNull(process.getProcessBase())) {
            process.setProcessBase(fileService.getProcessBaseUriForExistingProcess(process));
        }
        return process.getProcessBase();
    }

    /**
     * Get process data directory.
     *
     * @param process
     *            object
     * @return path
     */
    public URI getProcessDataDirectory(Process process) {
        return getProcessDataDirectory(process, false);
    }

    /**
     * Returns a URI that identifies the process. The URI has the form
     * {@code mysql://?process.id=42}, where {@code 42} is the process ID.
     *
     * @param process
     *            process for which a URI is to be formed that identifies it
     * @return a URI that identifies the process
     */
    public URI getProcessURI(Process process) {
        return getProcessURI(process.getId());
    }

    /**
     * Returns a URI that identifies the process. The URI has the form
     * {@code database://?process.id=42}, where {@code 42} is the process ID.
     *
     * @param processId
     *            process ID for which a URI is to be formed that identifies it
     * @return a URI that identifies the process
     */
    public URI getProcessURI(Integer processId) {
        try {
            return new URI("database", null, "//", "process.id=" + processId, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * The function getBatchID returns the batches the process is associated
     * with as readable text as read-only property "batchID".
     *
     * @return the batches the process is in
     */
    public String getBatchID(ProcessInterface process) {
        if (process.getBatches().isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (BatchInterface batch : process.getBatches()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(ServiceManager.getBatchService().getLabel(batch));
        }
        return result.toString();
    }

    /**
     * Get current task.
     *
     * @param process
     *            object
     * @return current task
     */
    public Task getCurrentTask(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatus().equals(TaskStatus.OPEN)
                    || task.getProcessingStatus().equals(TaskStatus.INWORK)) {
                return task;
            }
        }
        return null;
    }

    /**
     * Get current tasks.
     *
     * @param process
     *            object
     * @return current tasks
     */
    public List<Task> getCurrentTasks(Process process) {
        List<Task> currentTasks = new ArrayList<>();
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatus().equals(TaskStatus.OPEN)
                    || task.getProcessingStatus().equals(TaskStatus.INWORK)) {
                currentTasks.add(task);
            }
        }
        return currentTasks;
    }

    private List<TaskInterface> getOpenTasks(ProcessInterface process) {
        return process.getTasks().stream()
                .filter(t -> TaskStatus.OPEN.equals(t.getProcessingStatus())).collect(Collectors.toList());
    }

    private List<TaskInterface> getTasksInWork(ProcessInterface process) {
        return process.getTasks().stream()
                .filter(t -> TaskStatus.INWORK.equals(t.getProcessingStatus())).collect(Collectors.toList());
    }

    /**
     * Create and return String used as progress tooltip for a given process. Tooltip contains OPEN tasks and tasks
     * INWORK.
     *
     * @param process
     *          process for which the tooltop is created
     * @return String containing the progress tooltip for the given process
     */
    public String createProgressTooltip(ProcessInterface process) {
        String openTasks = getOpenTasks(process).stream()
                .map(t -> " - " + Helper.getTranslation(t.getTitle())).collect(Collectors.joining(NEW_LINE_ENTITY));
        if (!openTasks.isEmpty()) {
            openTasks = Helper.getTranslation(TaskStatus.OPEN.getTitle()) + ":" + NEW_LINE_ENTITY + openTasks;
        }
        String tasksInWork = getTasksInWork(process).stream()
                .map(t -> " - " + Helper.getTranslation(t.getTitle())).collect(Collectors.joining(NEW_LINE_ENTITY));
        if (!tasksInWork.isEmpty()) {
            tasksInWork = Helper.getTranslation(TaskStatus.INWORK.getTitle()) + ":" + NEW_LINE_ENTITY + tasksInWork;
        }
        if (openTasks.isEmpty() && tasksInWork.isEmpty()) {
            return "";
        } else if (openTasks.isEmpty()) {
            return tasksInWork;
        } else if (tasksInWork.isEmpty()) {
            return openTasks;
        } else {
            return openTasks + NEW_LINE_ENTITY + tasksInWork;
        }
    }

    /**
     * Get current task.
     *
     * @param process
     *            Interfaceobject
     * @return current task
     */
    public TaskInterface getCurrentTask(ProcessInterface process) {
        for (TaskInterface task : process.getTasks()) {
            if (task.getProcessingStatus().equals(TaskStatus.OPEN)
                    || task.getProcessingStatus().equals(TaskStatus.INWORK)) {
                return task;
            }
        }
        return null;
    }

    /**
     * Read metadata file.
     *
     * @param process
     *            object
     * @return filer format
     */
    public LegacyMetsModsDigitalDocumentHelper readMetadataFile(Process process) throws IOException {
        URI metadataFileUri = ServiceManager.getFileService().getMetadataFilePath(process);

        // check the format of the metadata - METS, XStream or RDF
        String type = MetadataHelper.getMetaFileType(metadataFileUri);
        logger.debug("current meta.xml file type for id {}: {}", process.getId(), type);

        LegacyMetsModsDigitalDocumentHelper ff = determineFileFormat(type, process);
        try {
            ff.read(ServiceManager.getFileService().getFile(metadataFileUri).toString());
        } catch (IOException e) {
            if (e.getMessage().startsWith("Parse error at line -1")) {
                Helper.setErrorMessage("metadataCorrupt", logger, e);
            } else {
                throw e;
            }
        }
        return ff;
    }

    /**
     * Reads the metadata File.
     *
     * @param metadataFile
     *            The given metadataFile.
     * @param prefs
     *            The Preferences
     * @return The fileFormat.
     */
    public LegacyMetsModsDigitalDocumentHelper readMetadataFile(URI metadataFile, LegacyPrefsHelper prefs)
            throws IOException {
        String type = MetadataHelper.getMetaFileType(metadataFile);
        LegacyMetsModsDigitalDocumentHelper fileFormat = determineFileFormat(type, prefs);
        fileFormat.read(ConfigCore.getKitodoDataDirectory() + metadataFile.getPath());
        return fileFormat;
    }

    private LegacyMetsModsDigitalDocumentHelper determineFileFormat(String type, Process process) {
        RulesetService rulesetService = ServiceManager.getRulesetService();
        return determineFileFormat(type, rulesetService.getPreferences(process.getRuleset()));
    }

    private LegacyMetsModsDigitalDocumentHelper determineFileFormat(String type, LegacyPrefsHelper prefs) {
        LegacyMetsModsDigitalDocumentHelper fileFormat;

        if ("metsmods".equals(type) || "mets".equals(type)) {
            fileFormat = new LegacyMetsModsDigitalDocumentHelper(prefs.getRuleset());
        } else {
            throw new UnsupportedOperationException("Dead code pending removal");
        }
        return fileFormat;
    }

    /**
     * Read metadata as template file.
     *
     * @param process
     *            object
     * @return file format
     */
    public LegacyMetsModsDigitalDocumentHelper readMetadataAsTemplateFile(Process process) throws IOException {
        URI processSubTypeURI = fileService.getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null);
        if (fileService.fileExist(processSubTypeURI)) {
            String type = MetadataHelper.getMetaFileType(processSubTypeURI);
            logger.debug("current template.xml file type: {}", type);
            LegacyMetsModsDigitalDocumentHelper ff = determineFileFormat(type, process);
            String processSubTypePath = fileService.getFile(processSubTypeURI).getAbsolutePath();
            ff.read(processSubTypePath);
            return ff;
        } else {
            throw new IOException("File does not exist: " + processSubTypeURI);
        }
    }

    /**
     * Check if there is one task in edit mode, where the user has the rights to
     * write to image folder.
     *
     * @param process
     *            bean object
     * @return true or false
     */
    public boolean isImageFolderInUse(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatus() == TaskStatus.INWORK && task.isTypeImagesWrite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get user of task in edit mode with rights to write to image folder.
     */
    public User getImageFolderInUseUser(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatus() == TaskStatus.INWORK && task.isTypeImagesWrite()) {
                return task.getProcessingUser();
            }
        }
        return null;
    }

    /**
     * Download docket for given process.
     *
     * @param process
     *            object
     * @throws IOException
     *             when xslt file could not be loaded, or write to output failed
     */
    public void downloadDocket(Process process) throws IOException {
        logger.debug("generate docket for process with id {}", process.getId());
        URI rootPath = Paths.get(ConfigCore.getParameter(ParameterCore.DIR_XSLT)).toUri();
        URI xsltFile;
        if (Objects.nonNull(process.getDocket())) {
            xsltFile = ServiceManager.getFileService().createResource(rootPath, process.getDocket().getFile());
            if (!fileService.fileExist(xsltFile)) {
                Helper.setErrorMessage("docketMissing");
            }
        } else {
            xsltFile = ServiceManager.getFileService().createResource(rootPath, "docket.xsl");
        }
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {
            // write run note to servlet output stream
            DocketInterface module = initialiseDocketModule();

            File file = module.generateDocket(getDocketData(process), xsltFile);
            writeToOutputStream(facesContext, file, Helper.getNormalizedTitle(process.getTitle()) + ".pdf");
            Files.deleteIfExists(file.toPath());
        }
    }

    /**
     * Writes a multi page docket for a list of processes to an output stream.
     *
     * @param processes
     *            The list of processes
     * @throws IOException
     *             when xslt file could not be loaded, or write to output failed
     */
    public void downloadDocket(List<Process> processes) throws IOException {
        logger.debug("generate docket for processes {}", processes);
        URI rootPath = Paths.get(ConfigCore.getParameter(ParameterCore.DIR_XSLT)).toUri();
        URI xsltFile = ServiceManager.getFileService().createResource(rootPath, "docket_multipage.xsl");
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {
            DocketInterface module = initialiseDocketModule();
            File file = module.generateMultipleDockets(getDocketData(processes),
                xsltFile);

            writeToOutputStream(facesContext, file, "batch_docket.pdf");
            Files.deleteIfExists(file.toPath());
        }
    }

    /**
     * Generate result as PDF.
     *
     * @param filter
     *            for generating search results
     */
    public void generateResultAsPdf(String filter, boolean showClosedProcesses, boolean showInactiveProjects)
            throws DocumentException, IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {
            ExternalContext response = prepareHeaderInformation(facesContext, "search.pdf");
            try (OutputStream out = response.getResponseOutputStream()) {
                SearchResultGeneration sr = new SearchResultGeneration(filter, showClosedProcesses,
                        showInactiveProjects);
                HSSFWorkbook wb = sr.getResult();
                List<List<HSSFCell>> rowList = new ArrayList<>();
                HSSFSheet mySheet = wb.getSheetAt(0);
                Iterator<Row> rowIter = mySheet.rowIterator();
                while (rowIter.hasNext()) {
                    HSSFRow myRow = (HSSFRow) rowIter.next();
                    Iterator<Cell> cellIter = myRow.cellIterator();
                    List<HSSFCell> row = new ArrayList<>();
                    while (cellIter.hasNext()) {
                        HSSFCell myCell = (HSSFCell) cellIter.next();
                        row.add(myCell);
                    }
                    rowList.add(row);
                }
                Document document = new Document();
                Rectangle rectangle = new Rectangle(PageSize.A3.getHeight(), PageSize.A3.getWidth());
                PdfWriter.getInstance(document, out);
                document.setPageSize(rectangle);
                document.open();
                if (!rowList.isEmpty()) {
                    Paragraph paragraph = new Paragraph(rowList.get(0).get(0).toString());
                    document.add(paragraph);
                    document.add(getPdfTable(rowList));
                }

                document.close();
                out.flush();
                facesContext.responseComplete();
            }
        }
    }

    /**
     * Generate result set.
     *
     * @param filter
     *            for generating search results
     */
    public void generateResult(String filter, boolean showClosedProcesses, boolean showInactiveProjects)
            throws IOException {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {
            ExternalContext response = prepareHeaderInformation(facesContext, "search.xls");
            try (OutputStream out = response.getResponseOutputStream()) {
                SearchResultGeneration sr = new SearchResultGeneration(filter, showClosedProcesses,
                        showInactiveProjects);
                HSSFWorkbook wb = sr.getResult();
                wb.write(out);
                out.flush();
                facesContext.responseComplete();
            }
        }
    }

    /**
     * Good explanation how it should be implemented:
     * https://stackoverflow.com/a/9394237/2701807.
     *
     * @param facesContext
     *            context
     * @param file
     *            temporal file which contains content to save
     * @param fileName
     *            name of the new docket file
     */
    private void writeToOutputStream(FacesContext facesContext, File file, String fileName) throws IOException {
        ExternalContext externalContext = prepareHeaderInformation(facesContext, fileName);

        try (OutputStream outputStream = externalContext.getResponseOutputStream();
                FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] bytes = IOUtils.toByteArray(fileInputStream);
            outputStream.write(bytes);
            outputStream.flush();
        }
        facesContext.responseComplete();
    }

    private ExternalContext prepareHeaderInformation(FacesContext facesContext, String outputFileName) {
        ExternalContext externalContext = facesContext.getExternalContext();
        externalContext.responseReset();

        String contentType = externalContext.getMimeType(outputFileName);
        externalContext.setResponseContentType(contentType);
        externalContext.setResponseHeader("Content-Disposition", "attachment;filename=\"" + outputFileName + "\"");

        return externalContext;
    }

    private PdfPTable getPdfTable(List<List<HSSFCell>> rowList) throws DocumentException {
        // create formatter for cells with default locale
        DataFormatter formatter = new DataFormatter();

        PdfPTable table = new PdfPTable(8);
        table.setSpacingBefore(20);
        table.setWidths(new int[] {4, 1, 2, 1, 1, 1, 2, 2 });
        for (List<HSSFCell> row : rowList) {
            for (HSSFCell hssfCell : row) {
                String stringCellValue = formatter.formatCellValue(hssfCell);
                table.addCell(stringCellValue);
            }
        }

        return table;
    }

    private static DocketInterface initialiseDocketModule() {
        KitodoServiceLoader<DocketInterface> loader = new KitodoServiceLoader<>(DocketInterface.class);
        return loader.loadModule();
    }

    /**
     * Returns the digital act of this process.
     *
     * @return the digital act of this process
     * @throws IOException
     *             if creating the process directory or reading the meta data file
     *             fails
     */
    public LegacyMetsModsDigitalDocumentHelper getDigitalDocument(Process process) throws IOException {
        return readMetadataFile(process).getDigitalDocument();
    }

    /**
     * Returns the type of the top element of the logical structure, and thus the
     * type of the workpiece of the process.
     *
     * @param process
     *            process whose root type is to be determined
     * @return the type of the logical structure of the workpiece, "" if unreadable
     */
    public String getBaseType(Process process) {
        try {
            URI metadataFilePath = ServiceManager.getFileService().getMetadataFilePath(process);
            return ServiceManager.getMetsService().getBaseType(metadataFilePath);
        } catch (IOException | IllegalArgumentException e) {
            logger.info("Could not determine base type for process {}: {}", process, e.getMessage());
            return "";
        }
    }

    /**
     * Returns the type of the top element of the logical structure, and thus the
     * type of the workpiece of the process.
     *
     * @param workpiece
     *            workpiece whose root type is to be determined
     * @return the type of the logical structure of the workpiece, "" if unreadable
     */
    public String getBaseType(Workpiece workpiece) {
        try {
            return ServiceManager.getMetsService().getBaseType(workpiece);
        } catch (IllegalArgumentException e) {
            logger.info("Could not determine base type for process {}: {}", workpiece.getId(), e.getMessage());
            return "";
        }
    }

    @Override
    public String getBaseType(int processId) throws DataException {
        try {
            ProcessInterface process = getById(processId);
            if (Objects.nonNull(process)) {
                return process.getBaseType();
            }
            return "";
        } catch (DAOException e) {
            throw new DataException(e);
        }
    }

    /**
     * Filter for correction / solution messages.
     *
     * @param lpe
     *            List of process properties
     * @return List of filtered correction / solution messages
     */
    private List<PropertyInterface> filterForCorrectionSolutionMessages(List<PropertyInterface> lpe) {
        List<PropertyInterface> filteredList = new ArrayList<>();

        if (lpe.isEmpty()) {
            return filteredList;
        }

        List<String> translationList = Arrays.asList("Correction required", "Correction performed",
            "Korrektur notwendig", "Korrektur durchgef\u00FChrt");

        // filtering for correction and solution messages
        for (PropertyInterface property : lpe) {
            if (translationList.contains(property.getTitle())) {
                filteredList.add(property);
            }
        }
        return filteredList;
    }

    /**
     * Sanitizes a possibly dirty reference URI and extracts from it the
     * operation ID of the referenced process. In case of error, a speaking
     * exception is thrown.
     *
     * @param uri
     *            URI of the reference to the process number
     * @return process number
     * @throws SecurityException
     *             if the URI names a forbidden protocol or column
     * @throws IllegalArgumentException
     *             if the URI tries to reference a foreign database or if the
     *             query has a wrong format
     * @throws ClassCastException
     *             if the URI references a wrong class / table
     */
    public int processIdFromUri(URI uri) {
        if (!"database".equals(uri.getScheme())) {
            throw new SecurityException("Protocol not allowed: " + uri.getScheme());
        }
        if (Objects.nonNull(uri.getAuthority()) || Objects.nonNull(uri.getPath()) && !uri.getPath().isEmpty()) {
            throw new IllegalArgumentException("Linking across databases is not supported");
        }
        if (Objects.isNull(uri.getQuery())) {
            throw new IllegalArgumentException("No query in database request");
        }
        String[] queryArguments = uri.getQuery().split("&");
        String[] key = queryArguments[0].split("=", 2);
        String[] keySegments = key[0].split("\\.");
        if (queryArguments.length > 1 || keySegments.length > 2) {
            throw new IllegalArgumentException("Complex queries is not supported");
        }
        if (keySegments.length > 1 && !"id".equals(keySegments[1])) {
            throw new SecurityException("Filtering on '" + keySegments[1] + "' is not allowed");
        }
        if (!"process".equals(keySegments[0])) {
            throw new ClassCastException("'" + keySegments[0] + "' cannot be cast to 'process'");
        }
        return Integer.parseInt(key[1]);
    }

    /**
     * Method for avoiding redundant code for exception handling. //TODO: should
     * this exceptions be handled that way?
     *
     * @param newFile
     *            as Fileformat
     * @param process
     *            as Process object
     * @return false if no exception appeared
     */
    public boolean handleExceptionsForConfiguration(LegacyMetsModsDigitalDocumentHelper newFile, Process process) {
        Optional<String> rules = ConfigCore.getOptionalString(ParameterCore.COPY_DATA_ON_EXPORT);
        if (rules.isPresent()) {
            try {
                new DataCopier(rules.get()).process(new CopierData(newFile, process));
            } catch (ConfigurationException e) {
                Helper.setErrorMessage("dataCopier.syntaxError", logger, e);
                return true;
            }
        }
        return false;
    }

    /**
     * Run through all metadata and children of given docStruct to trim the strings
     * calls itself recursively.
     *
     * @param docStruct
     *            metadata to be trimmed
     */
    private void trimAllMetadata(LegacyDocStructHelperInterface docStruct) {
        // trim all metadata values
        for (LegacyMetadataHelper md : docStruct.getAllMetadata()) {
            if (Objects.nonNull(md.getValue())) {
                md.setStringValue(md.getValue().trim());
            }
        }

        // run through all children of docStruct
        for (LegacyDocStructHelperInterface child : docStruct.getAllChildren()) {
            trimAllMetadata(child);
        }
    }

    /**
     * Download full text.
     *
     * @param userHome
     *            safe file
     * @param atsPpnBand
     *            String
     */
    private void downloadFullText(Process process, URI userHome, String atsPpnBand) throws IOException {
        downloadSources(process, userHome, atsPpnBand);
        downloadOCR(process, userHome, atsPpnBand);
    }

    private void downloadSources(Process process, URI userHome, String atsPpnBand) throws IOException {
        URI source = fileService.getSourceDirectory(process);
        if (fileService.fileExist(source) && !fileService.getSubUris(source).isEmpty()) {
            URI destination = userHome.resolve(File.separator + atsPpnBand + "_src");
            if (!fileService.fileExist(destination)) {
                fileService.createDirectory(userHome, atsPpnBand + "_src");
            }
            copyProcessFiles(source, destination, null);
        }
    }

    private void downloadOCR(Process process, URI userHome, String atsPpnBand) throws IOException {
        URI ocr = fileService.getOcrDirectory(process);
        if (fileService.fileExist(ocr)) {
            List<URI> directories = fileService.getSubUris(ocr);
            for (URI directory : directories) {
                if (fileService.isDirectory(directory) && !fileService.getSubUris(directory).isEmpty()
                        && fileService.getFileName(directory).contains("_")) {
                    String suffix = fileService.getFileNameWithExtension(directory)
                            .substring(fileService.getFileNameWithExtension(directory).lastIndexOf('_'));
                    URI destination = userHome.resolve(File.separator + atsPpnBand + suffix);
                    if (!fileService.fileExist(destination)) {
                        fileService.createDirectory(userHome, atsPpnBand + suffix);
                    }
                    copyProcessFiles(directory, destination, null);
                }
            }
        }
    }

    /**
     * Download images.
     *
     * @param process
     *            process object
     * @param userHome
     *            save file
     * @param atsPpnBand
     *            String
     * @param directorySuffix
     *            String
     */
    public void downloadImages(Process process, URI userHome, String atsPpnBand, final String directorySuffix)
            throws IOException {
        Project project = process.getProject();

        // determine the output path
        URI tifDirectory = getImagesTifDirectory(true, process.getId(), process.getTitle(),
            process.getProcessBaseUri());

        // copy the source folder to the destination folder
        if (fileService.fileExist(tifDirectory) && !fileService.getSubUris(tifDirectory).isEmpty()) {
            URI destination = userHome.resolve(File.separator + atsPpnBand + directorySuffix);

            // with Agora import simply create the folder
            if (!fileService.fileExist(destination)) {
                fileService.createDirectory(userHome, atsPpnBand + directorySuffix);
            }
            copyProcessFiles(tifDirectory, destination, ImageHelper.dataFilter);
        }
    }

    private void copyProcessFiles(URI source, URI destination, FilenameFilter filter) throws IOException {
        List<URI> files = fileService.getSubUris(filter, source);

        for (URI file : files) {
            if (fileService.isFile(file)) {
                URI target = destination.resolve(File.separator + fileService.getFileNameWithExtension(file));
                fileService.copyFile(file, target);
            }
        }
    }

    /**
     * write MetsFile to given Path.
     *
     * @param process
     *            the Process to use
     * @param targetFileName
     *            the filename where the metsfile should be written
     * @param gdzfile
     *            the FileFormat-Object to use for Mets-Writing
     */
    protected boolean writeMetsFile(Process process, String targetFileName, LegacyMetsModsDigitalDocumentHelper gdzfile)
            throws IOException {
        LegacyPrefsHelper preferences = ServiceManager.getRulesetService().getPreferences(process.getRuleset());
        LegacyMetsModsDigitalDocumentHelper mm = new LegacyMetsModsDigitalDocumentHelper(preferences.getRuleset());
        // before creating mets file, change relative path to absolute -
        LegacyMetsModsDigitalDocumentHelper dd = gdzfile.getDigitalDocument();
        if (Objects.isNull(dd.getFileSet())) {
            Helper.setErrorMessage(process.getTitle() + ": digital document does not contain images; aborting");
            return false;
        }

        //get the topstruct element of the digital document depending on anchor property
        LegacyDocStructHelperInterface topElement = dd.getLogicalDocStruct();

        //if the top element does not have any image related, set them all
        if (topElement.getAllToReferences("logical_physical").isEmpty()) {
            if (Objects.nonNull(dd.getPhysicalDocStruct()) && dd.getPhysicalDocStruct().getAllChildren().isEmpty()) {
                Helper.setMessage(process.getTitle()
                        + ": topstruct element does not have any referenced images yet; temporarily adding them "
                        + "for mets file creation");
                for (LegacyDocStructHelperInterface mySeitenDocStruct : dd.getPhysicalDocStruct().getAllChildren()) {
                    topElement.addReferenceTo(mySeitenDocStruct, "logical_physical");
                }
            } else {
                Helper.setErrorMessage(process.getTitle() + ": could not find any referenced images, export aborted");
                return false;
            }
        }

        mm.setDigitalDocument(dd);
        mm.write(targetFileName);
        Helper.setMessage(process.getTitle() + ": ", "exportFinished");
        return true;
    }

    /**
     * Get data files.
     *
     * @return String
     */
    public List<URI> getDataFiles(Process process) throws InvalidImagesException {
        URI dir;
        try {
            dir = getImagesTifDirectory(true, process.getId(), process.getTitle(), process.getProcessBaseUri());
        } catch (RuntimeException e) {
            throw new InvalidImagesException(e);
        }

        List<URI> dataList = new ArrayList<>();
        List<URI> files = fileService.getSubUris(ImageHelper.dataFilter, dir);
        if (!files.isEmpty()) {
            dataList.addAll(files);
            Collections.sort(dataList);
        }
        return dataList;
    }

    /**
     * Starts copying all directories configured in kitodo_config.properties
     * parameter "processDirs" to export folder.
     *
     * @param myProcess
     *            the process object
     * @param targetDirectory
     *            the destination directory
     */
    private void directoryDownload(Process myProcess, URI targetDirectory) throws IOException {
        String[] processDirs = ConfigCore.getStringArrayParameter(ParameterCore.PROCESS_DIRS);
        String normalizedTitle = Helper.getNormalizedTitle(myProcess.getTitle());

        for (String processDir : processDirs) {
            URI sourceDirectory = URI.create(getProcessDataDirectory(myProcess).toString() + "/"
                    + processDir.replace(PROCESS_TITLE, normalizedTitle));
            URI destinationDirectory = URI
                    .create(targetDirectory.toString() + "/" + processDir.replace(PROCESS_TITLE, normalizedTitle));

            if (fileService.isDirectory(sourceDirectory)) {
                fileService.copyFile(sourceDirectory, destinationDirectory);
            }
        }
    }

    /**
     * Creates a List of Docket data for the given processes.
     *
     * @param processes
     *            the process to create the docket data for.
     * @return A List of DocketData objects
     */
    private List<DocketData> getDocketData(List<Process> processes) throws IOException {
        List<DocketData> docketData = new ArrayList<>();
        for (Process process : processes) {
            docketData.add(getDocketData(process));
        }
        return docketData;
    }

    /**
     * Creates the DocketData for a given Process.
     *
     * @param process
     *            The process to create the docket data for.
     * @return The DocketData for the process.
     */
    private static DocketData getDocketData(Process process) throws IOException {
        DocketData docketdata = new DocketData();

        docketdata.setCreationDate(process.getCreationDate().toString());
        URI metadataFilePath = fileService.getMetadataFilePath(process);
        docketdata.setMetadataFile(fileService.getFile(metadataFilePath).toURI());
        if (Objects.nonNull(process.getParent())) {
            docketdata.setParent(getDocketData(process.getParent()));
        }
        docketdata.setProcessId(process.getId().toString());
        docketdata.setProcessName(process.getTitle());
        docketdata.setProjectName(process.getProject().getTitle());
        docketdata.setRulesetName(process.getRuleset().getTitle());
        docketdata.setComments(getDocketDataForComments(process.getComments()));

        if (!process.getTemplates().isEmpty()) {
            docketdata.setTemplateProperties(getDocketDataForProperties(process.getTemplates()));
        }
        if (!process.getWorkpieces().isEmpty()) {
            docketdata.setWorkpieceProperties(getDocketDataForProperties(process.getWorkpieces()));
        }
        docketdata.setProcessProperties(getDocketDataForProperties(process.getProperties()));

        return docketdata;
    }

    private static ArrayList<org.kitodo.api.docket.Property> getDocketDataForProperties(List<Property> properties) {
        ArrayList<org.kitodo.api.docket.Property> propertiesForDocket = new ArrayList<>();
        for (Property property : properties) {
            org.kitodo.api.docket.Property propertyForDocket = new org.kitodo.api.docket.Property();
            propertyForDocket.setId(property.getId());
            propertyForDocket.setTitle(property.getTitle());
            propertyForDocket.setValue(property.getValue());

            propertiesForDocket.add(propertyForDocket);

        }

        return propertiesForDocket;
    }

    private static List<String> getDocketDataForComments(List<Comment> comments) {
        List<String> commentsForDocket = new ArrayList<>();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Comment comment : comments) {
            String commentString = dateFormat.format(comment.getCreationDate()) + " "
                    + comment.getAuthor().getFullName() + ": " + comment.getMessage();
            commentsForDocket.add(commentString);
        }
        return commentsForDocket;
    }

    private List<Map<String, Object>> getMetadataForIndex(Process process) {
        return getMetadataForIndex(process, false);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMetadataForIndex(Process process, boolean forIndexingAll) {
        try {
            URI metadataFileUri = ServiceManager.getFileService().getMetadataFilePath(process, false, true);
            if (!ServiceManager.getFileService().fileExist(metadataFileUri)) {
                logger.info("No metadata file for indexing: {}", metadataFileUri);
                return Collections.emptyList();
            }
            String metadataFile;
            try (InputStream inputStream = ServiceManager.getFileService().readMetadataFile(process, forIndexingAll)) {
                metadataFile = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
            JSONObject xmlJSONObject = XML.toJSONObject(metadataFile);
            Map<String, Object> json = iterateOverJsonObject(xmlJSONObject);
            if (json.containsKey("mets")) {
                Map<String, Object> mets = (Map<String, Object>) json.get("mets");
                Object dmdSec = mets.get("dmdSec");
                List<Map<String, Object>> metadata = new ArrayList<>();
                if (dmdSec instanceof List) {
                    metadata = (List<Map<String, Object>>) dmdSec;
                } else if (dmdSec instanceof Map) {
                    metadata.add((Map<String, Object>) dmdSec);
                }
                return metadata;
            }
        } catch (NullPointerException | IOException e) {
            logger.warn(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    private Map<String, Object> iterateOverJsonObject(JSONObject xmlJSONObject) {
        Iterator<String> keys = xmlJSONObject.keys();
        Map<String, Object> json = new HashMap<>();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = xmlJSONObject.get(key);
            if (value instanceof String || value instanceof Integer) {
                json.put(prepareKey(key), value);
            } else if (value instanceof Long || value instanceof BigInteger) {
                json.put(prepareKey(key), value.toString());
            } else if (value instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) value;
                Map<String, Object> map = iterateOverJsonObject(jsonObject);
                json.put(prepareKey(key), map);
            } else if (value instanceof JSONArray) {
                json.put(prepareKey(key), iterateOverJsonArray((JSONArray) value));
            }
        }
        return json;
    }

    private Object iterateOverJsonArray(JSONArray jsonArray) {
        int jsonArraySize = jsonArray.length();
        List<Object> json = new ArrayList<>(jsonArraySize);
        for (int i = 0; i < jsonArraySize; i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                json.add(iterateOverJsonObject((JSONObject) value));
            } else if (value instanceof String) {
                json.add(value);
            } else if (value instanceof JSONArray) {
                json.add(iterateOverJsonArray((JSONArray) value));
            }
        }
        return json;
    }

    private String prepareKey(String key) {
        if (key.contains(":")) {
            return key.substring(key.indexOf(':') + 1);
        }
        return key;
    }

    /**
     * Retrieve and return process property value of property with given name
     * 'propertyName' from given ProcessInterface 'process'.
     *
     * @param process
     *            the ProcessInterface object from which the property value is retrieved
     * @param propertyName
     *            name of the property for the property value is retrieved
     * @return property value if process has property with name 'propertyName',
     *         empty String otherwise
     */
    public static String getPropertyValue(ProcessInterface process, String propertyName) {
        for (PropertyInterface property : process.getProperties()) {
            if (property.getTitle().equals(propertyName)) {
                return property.getValue();
            }
        }
        return "";
    }

    /**
     * Calculate and return duration/age of given process as a String.
     *
     * @param process
     *            ProcessInterface object for which duration/age is calculated
     * @return process age of given process
     */
    public static String getProcessDuration(ProcessInterface process) {
        String creationDateTimeString = process.getCreationTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime createLocalDate = LocalDateTime.parse(creationDateTimeString, formatter);
        Duration duration = Duration.between(createLocalDate, LocalDateTime.now());
        return String.format("%sd; %sh", duration.toDays(),
            duration.toHours() - TimeUnit.DAYS.toHours(duration.toDays()));
    }

    @Override
    public void updateChildrenFromLogicalStructure(Process process, LogicalDivision logicalStructure)
            throws DAOException, DataException {
        removeLinksFromNoLongerLinkedProcesses(process, logicalStructure);
        addNewLinks(process, logicalStructure);
    }

    private void removeLinksFromNoLongerLinkedProcesses(Process process, LogicalDivision logicalStructure)
            throws DAOException, DataException {
        ArrayList<Process> childrenToRemove = new ArrayList<>(process.getChildren());
        childrenToRemove.removeAll(getProcessesLinkedInLogicalDivision(logicalStructure));
        for (Process childToRemove : childrenToRemove) {
            childToRemove.setParent(null);
            process.getChildren().remove(childToRemove);
            save(childToRemove);
        }
        if (!childrenToRemove.isEmpty()) {
            save(process);
        }
    }

    private void addNewLinks(Process process, LogicalDivision logicalStructure)
            throws DAOException, DataException {
        HashSet<Process> childrenToAdd = getProcessesLinkedInLogicalDivision(logicalStructure);
        process.getChildren().forEach(childrenToAdd::remove);
        for (Process childToAdd : childrenToAdd) {
            childToAdd.setParent(process);
            process.getChildren().add(childToAdd);
            save(childToAdd);
        }
        if (!childrenToAdd.isEmpty()) {
            save(process);
        }
    }

    private HashSet<Process> getProcessesLinkedInLogicalDivision(
            LogicalDivision logicalDivision) throws DAOException {
        HashSet<Process> processesLinkedInLogicalDivision = new HashSet<>();
        if (Objects.nonNull(logicalDivision.getLink())) {
            int processId = processIdFromUri(logicalDivision.getLink().getUri());
            processesLinkedInLogicalDivision.add(getById(processId));
        }
        for (LogicalDivision child : logicalDivision.getChildren()) {
            processesLinkedInLogicalDivision.addAll(getProcessesLinkedInLogicalDivision(child));
        }
        return processesLinkedInLogicalDivision;
    }

    /**
     * Set given Process "parentProcess" as parent of given Process "childProcess" and Process "childProcess" as child
     * of given Process "parentProcess".
     * @param parentProcess
     *          parentProcess of given childProcess
     * @param childProcess
     *          childProcess of given parentProcess
     */
    public static void setParentRelations(Process parentProcess, Process childProcess) {
        childProcess.setParent(parentProcess);
        parentProcess.getChildren().add(childProcess);
    }

    /**
     * Get all parent processes of given Process recursively.
     * @param process the Process to get the parent process for
     * @return List of parent Processes
     */
    public static List<Process> getAllParentProcesses(Process process) {
        List<Process> parents = new ArrayList<>();
        while (Objects.nonNull(process.getParent())) {
            parents.add(0, process.getParent());
            process = process.getParent();
        }
        return parents;
    }

    @Override
    public int getNumberOfChildren(int processId) throws DAOException {
        return Math.toIntExact(countDatabaseRows("SELECT COUNT(*) FROM Process WHERE parent_id = " + processId));
    }

    public static void deleteProcess(int processID) throws DAOException, DataException, IOException {
        Process process = ServiceManager.getProcessService().getById(processID);
        deleteProcess(process);
    }

    /**
     * Delete given process.
     *
     * @param processToDelete process to delete
     */
    public static void deleteProcess(Process processToDelete) throws DataException, IOException {
        deleteMetadataDirectory(processToDelete);

        if (Objects.nonNull(processToDelete.getProject())
                && Objects.nonNull(processToDelete.getProject().getProcesses())) {
            processToDelete.getProject().getProcesses().remove(processToDelete);
        }
        processToDelete.setProject(null);
        if (Objects.nonNull(processToDelete.getTemplate())
                && Objects.nonNull(processToDelete.getTemplate().getProcesses())) {
            processToDelete.getTemplate().getProcesses().remove(processToDelete);
        }
        processToDelete.setTemplate(null);
        Process parent = processToDelete.getParent();
        if (Objects.nonNull(parent)) {
            parent.getChildren().remove(processToDelete);
            processToDelete.setParent(null);
            MetadataEditor.removeLink(parent, processToDelete.getId());
            ServiceManager.getProcessService().save(processToDelete);
            ServiceManager.getProcessService().save(parent);
        }
        List<Batch> batches = new CopyOnWriteArrayList<>(processToDelete.getBatches());
        for (Batch batch : batches) {
            batch.getProcesses().remove(processToDelete);
            processToDelete.getBatches().remove(batch);
            ServiceManager.getBatchService().save(batch);
        }
        ServiceManager.getProcessService().remove(processToDelete);
    }

    private static void deleteMetadataDirectory(Process process) {
        for (Task task : process.getTasks()) {
            deleteSymlinksFromUserHomes(task);
        }
        try {
            FileService fileService = ServiceManager.getFileService();
            fileService.delete(ServiceManager.getProcessService().getProcessDataDirectory(process));
            URI ocrDirectory = fileService.getOcrDirectory(process);
            if (fileService.fileExist(ocrDirectory)) {
                fileService.delete(ocrDirectory);
            }
        } catch (IOException | RuntimeException e) {
            Helper.setErrorMessage("errorDirectoryDeleting", new Object[] {Helper.getTranslation("metadata") }, logger,
                    e);
        }
    }

    /**
     * Delete symlinks from user home directories.
     *
     * @param task Task for which symlinks are removed
     */
    public static void deleteSymlinksFromUserHomes(Task task) {
        if (Objects.nonNull(task.getProcessingUser()) && (task.isTypeImagesRead() || task.isTypeImagesWrite())) {
            WebDav webDav = new WebDav();
            try {
                webDav.uploadFromHome(task.getProcessingUser(), task.getProcess());
            } catch (RuntimeException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            }
        }
    }

    /**
     * Get the node list from metadata file by the xpath.
     *
     * @param process
     *            The process for which the metadata file is searched for
     * @param xpath
     *            The xpath to get to the node list
     * @return The node list of process by the of xpath
     */
    public NodeList getNodeListFromMetadataFile(Process process, String xpath) throws IOException {
        try (InputStream fileInputStream = ServiceManager.getFileService().readMetadataFile(process)) {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            org.w3c.dom.Document xmlDocument = builder.parse(fileInputStream);

            XPath xPath = XPathFactory.newInstance().newXPath();
            xPath.setNamespaceContext(new KitodoNamespaceContext());
            return (NodeList) xPath.compile(xpath).evaluate(xmlDocument, XPathConstants.NODESET);
        } catch (ParserConfigurationException | SAXException | XPathExpressionException e) {
            logger.error(e.getMessage(), e);
            throw new IOException(e);
        }
    }

    /**
     * Export Mets.
     *
     * @param processId
     *            Id of which process should be exported
     * @throws DAOException
     *             Thrown on database error
     * @throws DataException
     *             Thrown on index error
     * @throws IOException
     *             Thrown on I/O error
     */
    public static void exportMets(int processId) throws DAOException, DataException, IOException {
        Process process = ServiceManager.getProcessService().getById(processId);
        ExportMets export = new ExportMets();
        export.startExport(process);
    }

    /**
     * Link a list of given processes to user home directory.
     *
     * @param processes List of processes
     * @throws DAOException Thrown on database like error
     */
    public static void downloadToHome(List<Process> processes) throws DAOException {
        WebDav webDav = new WebDav();
        for (Process processForDownload : processes) {
            downloadToHome(webDav, processForDownload.getId());
        }
    }

    /**
     * Download to home for single process. First check if this volume is currently
     * being edited by another user and placed in his home directory, otherwise
     * download.
     *
     * @param webDav
     *            for download
     * @param processId
     *            ID of process for which download is going to be performed
     */
    public static void downloadToHome(WebDav webDav, int processId) throws DAOException {
        Process process = ServiceManager.getProcessService().getById(processId);
        if (ServiceManager.getProcessService().isImageFolderInUse(process)) {
            Helper.setMessage(
                    Helper.getTranslation("directory ") + " " + process.getTitle() + " "
                            + Helper.getTranslation("isInUse"),
                    ServiceManager.getUserService()
                            .getFullName(ServiceManager.getProcessService().getImageFolderInUseUser(process)));
            webDav.downloadToHome(process, true);
        } else {
            webDav.downloadToHome(process, false);
        }
    }

    /** 
     * Upload from home for list of processes for current user. 
     * Deletes symlinks in home directory of current user.
     * 
     * @param processes the list of processes
     */
    public static void uploadFromHome(List<Process> processes) {
        WebDav webDav = new WebDav();
        User currentUser = ServiceManager.getUserService().getCurrentUser();
        for (Process process : processes) {
            try {
                webDav.uploadFromHome(currentUser, process);
            } catch (RuntimeException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            }
        }
    }

    /**
     * Check and return whether the process with the ID 'processId' has any correction comments or not.
     *
     * @param processID
     *          ID of process to check
     * @return CorrectionComment status of process
     */
    public static CorrectionComments hasCorrectionComment(int processID) throws DAOException {
        Process process = ServiceManager.getProcessService().getById(processID);
        List<Comment> correctionComments = ServiceManager.getCommentService().getAllCommentsByProcess(process)
                .stream().filter(c -> CommentType.ERROR.equals(c.getType())).collect(Collectors.toList());
        if (correctionComments.size() < 1) {
            return NO_CORRECTION_COMMENTS;
        } else if (correctionComments.stream().anyMatch(c -> !c.isCorrected())) {
            return OPEN_CORRECTION_COMMENTS;
        } else {
            return NO_OPEN_CORRECTION_COMMENTS;
        }
    }

    /**
     * Retrieve comments for the given process.
     *
     * @param process
     *          process for which the tooltip is created
     * @return List containing comments of given process
     *
     * @throws DAOException thrown when process cannot be loaded from database
     */
    public List<Comment> getComments(ProcessInterface process) throws DAOException {
        Process processBean = ServiceManager.getProcessService().getById(process.getId());
        return ServiceManager.getCommentService().getAllCommentsByProcess(processBean);
    }

    /**
     * Check and return if child process for given ProcessInterface process can be created via calendar or not.
     *
     * @param process ProcessInterface for which child processes may be created via calendar
     * @return whether child processes for the given ProcessInterface can be created via the calendar or not
     * @throws DAOException if process could not be loaded from database
     * @throws IOException if ruleset file could not be read
     */
    public static boolean canCreateProcessWithCalendar(ProcessInterface process)
            throws DAOException, IOException {
        Collection<String> functionalDivisions;
        if (Objects.isNull(process.getRuleset())) {
            return false;
        }
        Integer rulesetId = process.getRuleset().getId();
        if (RULESET_CACHE_FOR_CREATE_FROM_CALENDAR.containsKey(rulesetId)) {
            functionalDivisions = RULESET_CACHE_FOR_CREATE_FROM_CALENDAR.get(rulesetId);
        } else {
            Ruleset ruleset = ServiceManager.getRulesetService().getById(rulesetId);
            functionalDivisions = ServiceManager.getRulesetService().openRuleset(ruleset)
                    .getFunctionalDivisions(FunctionalDivision.CREATE_CHILDREN_WITH_CALENDAR);
            RULESET_CACHE_FOR_CREATE_FROM_CALENDAR.put(rulesetId, functionalDivisions);
        }
        return functionalDivisions.contains(process.getBaseType());
    }

    /**
     * Check and return if child process for given ProcessInterface process can be created or not.
     *
     * @param process ProcessInterface for which child processes may be created
     * @return whether child processes for the given ProcessInterface can be created via the calendar or not
     * @throws DAOException if process could not be loaded from database
     * @throws IOException if ruleset file could not be read
     */
    public static boolean canCreateChildProcess(ProcessInterface process) throws DAOException,
            IOException {
        Collection<String> functionalDivisions;
        if (Objects.isNull(process.getRuleset())) {
            return false;
        }
        Integer rulesetId = process.getRuleset().getId();
        if (RULESET_CACHE_FOR_CREATE_CHILD_FROM_PARENT.containsKey(rulesetId)) {
            functionalDivisions = RULESET_CACHE_FOR_CREATE_CHILD_FROM_PARENT.get(rulesetId);
        } else {
            Ruleset ruleset = ServiceManager.getRulesetService().getById(rulesetId);
            functionalDivisions = ServiceManager.getRulesetService().openRuleset(ruleset)
                    .getFunctionalDivisions(FunctionalDivision.CREATE_CHILDREN_FROM_PARENT);
            RULESET_CACHE_FOR_CREATE_CHILD_FROM_PARENT.put(rulesetId, functionalDivisions);
        }
        return functionalDivisions.contains(process.getBaseType());
    }

    /**
     * Starts generation of xml logfile for current process.
     */
    public static void createXML(Process process, User user) throws IOException {
        DocketInterface xmlExport = initialiseDocketModule();
        String directory = new File(ServiceManager.getUserService().getHomeDirectory(user)).getPath();
        String destination = directory + "/" + Helper.getNormalizedTitle(process.getTitle()) + "_log.xml";
        xmlExport.exportXmlLog(getDocketData(process), destination);
    }

    /**
     * Create and return PieChartModel for given process values.
     *
     * @param processValues Map containing process values
     * @return PieChartModel
     */
    public PieChartModel getPieChardModel(Map<String, Integer> processValues) {

        PieChartDataSet dataSet = new PieChartDataSet();
        List<Number> values = new ArrayList<>(processValues.values());
        dataSet.setData(values);

        dataSet.setBackgroundColor(BG_COLORS);

        ChartData data = new ChartData();
        data.addChartDataSet(dataSet);
        ArrayList<String> labels = new ArrayList<>();
        for (Map.Entry<String, Integer> processValueEntry : processValues.entrySet()) {
            labels.add(processValueEntry.getKey().concat(" ").concat(processValueEntry.getValue().toString()));
        }
        data.setLabels(labels);

        PieChartModel pieModel = new PieChartModel();
        pieModel.setData(data);
        return pieModel;
    }

    /**
     * Create and return HorizontalBarChartModel for given processes.
     *
     * @param processes List of processes
     * @return HorizontalBarChartModel
     */
    public HorizontalBarChartModel getBarChartModel(List<Process> processes) {
        LinkedHashMap<String, LinkedHashMap<String,Integer>> durationOfTasks = new LinkedHashMap<>();
        for (Process selectedProcess : processes) {
            LinkedHashMap<String,Integer> taskValues = new LinkedHashMap<>();
            for (Task task  : selectedProcess.getTasks()) {
                long durationInDays = ServiceManager.getTaskService().getDurationInDays(task);
                taskValues.put(task.getTitle(), Math.toIntExact(durationInDays));
            }
            durationOfTasks.put(selectedProcess.getTitle(), taskValues);
        }
        ChartData data = new ChartData();
        boolean isTask;
        int i = 0;
        while (true) {
            isTask = false;
            HorizontalBarChartDataSet barDataSet = new HorizontalBarChartDataSet();
            List<Number> taskDurations = new ArrayList<>();
            for (String processTitle : durationOfTasks.keySet()) {
                LinkedHashMap<String, Integer> tasksForProcess = durationOfTasks.get(processTitle);
                ArrayList<Integer> durations = new ArrayList<>(tasksForProcess.values());
                Integer taskDuration = 0;
                if (durations.size() > i) {
                    barDataSet.setLabel(new ArrayList<>(tasksForProcess.keySet()).get(i));
                    taskDuration = durations.get(i);
                    isTask = true;
                }
                taskDurations.add(taskDuration);
            }
            if (isTask) {
                barDataSet.setStack("Stack 0");
                barDataSet.setBackgroundColor(BG_COLORS.get(i % BG_COLORS.size()));
                barDataSet.setData(taskDurations);
                data.addChartDataSet(barDataSet);
                i++;
            } else {
                break;
            }
        }
        List<String> labels = new ArrayList<>(durationOfTasks.keySet());
        data.setLabels(labels);

        HorizontalBarChartModel horizontalBarChartModel = new HorizontalBarChartModel();
        horizontalBarChartModel.setData(data);
        horizontalBarChartModel.setOptions(getBarChartOptions());
        return horizontalBarChartModel;
    }

    private BarChartOptions getBarChartOptions() {
        CartesianLinearAxes linearAxes = new CartesianLinearAxes();
        linearAxes.setStacked(true);
        BarChartOptions options = new BarChartOptions();

        Tooltip tooltip = new Tooltip();
        tooltip.setMode("index");
        tooltip.setIntersect(false);
        options.setTooltip(tooltip);
        return options;
    }

    /**
     * Aggregate and return statistical data about task status of given processes.
     *
     * @param processes List of processes for which statistical data is aggregated.
     * @return statistical data about tasks status of given processes
     */
    public Map<String, Integer> getProcessTaskStates(List<Process> processes) {
        Map<String, Integer> processTaskStates = new LinkedHashMap<>();
        for (Process process : processes) {
            Task currentTask = ServiceManager.getProcessService().getCurrentTask(process);
            if (Objects.nonNull(currentTask)) {
                String currentTaskTitle = currentTask.getTitle();
                if (processTaskStates.containsKey(currentTaskTitle)) {
                    processTaskStates.put(currentTaskTitle, Math.addExact(processTaskStates.get(currentTaskTitle), 1));
                } else {
                    processTaskStates.put(currentTaskTitle, 1);
                }
            }
        }
        return processTaskStates;
    }

    /**
     * Get all tasks of given process which should be visible to the user.
     * @param process process as Interface object
     * @param user user to filter the tasks for
     * @return List of filtered tasks as Interface objects
     */
    public List<TaskInterface> getCurrentTasksForUser(ProcessInterface process, User user) {
        Set<Integer> userRoles = user.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
        return process.getTasks().stream()
                .filter(task -> TaskStatus.OPEN.equals(task.getProcessingStatus()) || TaskStatus.INWORK.equals(task.getProcessingStatus()))
                .filter(task -> !task.getRoleIds().stream()
                        .filter(userRoles::contains)
                        .collect(Collectors.toSet()).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Checks and returns whether the process with the given ID 'processId' can be exported or not.
     * @param processId process ID
     * @return whether process can be exported or not
     */
    public static boolean canBeExported(int processId) throws IOException, DAOException {
        Process process = ServiceManager.getProcessService().getById(processId);
        // superordinate processes normally do not contain images but should always be exportable
        if (!process.getChildren().isEmpty()) {
            return true;
        }
        Folder generatorSource = process.getProject().getGeneratorSource();
        // processes without a generator source should be exportable because they may contain multimedia files
        // that are not used as generator sources
        if (Objects.isNull(generatorSource)) {
            return true;
        }
        return FileService.hasImages(process, generatorSource);
    }
}
