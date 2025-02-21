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

import static org.kitodo.constants.StringConstants.CREATE;
import static org.kitodo.constants.StringConstants.KITODO;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.faces.model.SelectItem;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.MdSec;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.externaldatamanagement.DataImport;
import org.kitodo.api.externaldatamanagement.ExternalDataImportInterface;
import org.kitodo.api.externaldatamanagement.ImportConfigurationType;
import org.kitodo.api.externaldatamanagement.SearchInterfaceType;
import org.kitodo.api.externaldatamanagement.SearchResult;
import org.kitodo.api.schemaconverter.DataRecord;
import org.kitodo.api.schemaconverter.ExemplarRecord;
import org.kitodo.api.schemaconverter.FileFormat;
import org.kitodo.api.schemaconverter.MetadataFormat;
import org.kitodo.api.schemaconverter.SchemaConverterInterface;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.ConfigProject;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.constants.StringConstants;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.beans.MappingFile;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.SearchField;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.UrlParameter;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CatalogException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.ConfigException;
import org.kitodo.exceptions.DoctypeMissingException;
import org.kitodo.exceptions.ImportException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoRecordFoundException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.ParameterNotFoundException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.exceptions.RecordIdentifierMissingDetail;
import org.kitodo.exceptions.UnsupportedFormatException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.forms.createprocess.CreateProcessForm;
import org.kitodo.production.forms.createprocess.ProcessBooleanMetadata;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.forms.createprocess.ProcessFieldedMetadata;
import org.kitodo.production.forms.createprocess.ProcessSelectMetadata;
import org.kitodo.production.forms.createprocess.ProcessTextMetadata;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.ProcessHelper;
import org.kitodo.production.helper.TempProcess;
import org.kitodo.production.helper.XMLUtils;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.process.ProcessGenerator;
import org.kitodo.production.process.ProcessValidator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.workflow.KitodoNamespaceContext;
import org.kitodo.serviceloader.KitodoServiceLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class ImportService {

    private static final Logger logger = LogManager.getLogger(ImportService.class);

    private static volatile ImportService instance = null;
    private static ExternalDataImportInterface importModule;
    private static final String KITODO_NAMESPACE = "http://meta.kitodo.org/v1/";

    private ProcessGenerator processGenerator;
    private static final String REPLACE_ME = "REPLACE_ME";
    // default value for identifierMetadata if no OPAC specific metadata has been configured in kitodo_opac.xml
    private static final String PARENT_XPATH = "//kitodo:metadata[@name='" + REPLACE_ME + "']";
    private static final String PARENTHESIS_TRIM_MODE = "parenthesis";
    private LinkedList<ExemplarRecord> exemplarRecords;

    private static final String PERSON = "Person";
    private static final String ROLE = "Role";
    private static final String AUTHOR = "Author";
    private static final String FIRST_NAME = "FirstName";
    private static final String LAST_NAME = "LastName";

    private static final String MONOGRAPH = "Monograph";
    private static final String VOLUME = "Volume";
    private static final String MULTI_VOLUME_WORK = "MultiVolumeWork";

    private static final Collection<RecordIdentifierMissingDetail> recordIdentifierMissingDetails = new ArrayList<>();
    private String tiffDefinition = "";
    private boolean usingTemplates;

    private TempProcess parentTempProcess;

    private static final String CATALOG_IDENTIFIER = "CatalogIDDigital";

    private static final String SRU_OPERATION = "operation";
    private static final String SRU_SEARCH_RETRIEVE = "searchRetrieve";
    private static final String SRU_VERSION = "version";
    private static final String SRU_RECORD_SCHEMA = "recordSchema";
    private static final String OAI_VERB = "verb";
    private static final String OAI_GET_RECORD = "GetRecord";
    private static final String OAI_METADATA_PREFIX = "metadataPrefix";

    /**
     * Return singleton variable of type ImportService.
     *
     * @return unique instance of ImportService
     */
    public static ImportService getInstance() {
        ImportService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (ImportService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new ImportService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    /**
     * Load ExternalDataImportInterface implementation with KitodoServiceLoader and perform given query string
     * with loaded module.
     *
     * @param searchField field to query
     * @param searchTerm  given search term
     * @param importConfiguration ImportConfiguration to use
     * @param start index of first record returned
     * @param rows number of records returned
     * @return search result
     */
    public SearchResult performSearch(String searchField, String searchTerm, ImportConfiguration importConfiguration,
                                      int start, int rows) {
        importModule = initializeImportModule();
        searchTerm = getSearchTermWithDelimiter(searchTerm, importConfiguration);
        return importModule.search(createDataImportFromImportConfiguration(importConfiguration), searchField,
                searchTerm, start, rows);
    }

    private ExternalDataImportInterface initializeImportModule() {
        KitodoServiceLoader<ExternalDataImportInterface> loader =
                new KitodoServiceLoader<>(ExternalDataImportInterface.class);
        return loader.loadModule();
    }

    /**
     * Load search fields from provided ImportConfiguration and return them as a list of Strings.
     *
     * @param importConfiguration ImportConfiguration to use
     * @return list containing search fields
     */
    public List<String> getAvailableSearchFields(ImportConfiguration importConfiguration) {
        try {
            if (SearchInterfaceType.FTP.name().equals(importConfiguration.getInterfaceType())) {
                // FTP servers do not support query parameters but only use the filename for OPAC search!
                return Collections.singletonList(Helper.getTranslation("filename"));
            } else if (SearchInterfaceType.OAI.name().equals(importConfiguration.getInterfaceType())) {
                // OAI PMH interfaces do not support query parameters but only use the ID of the record to retrieve it!
                return Collections.singletonList(Helper.getTranslation("recordId"));
            } else {
                List<String> fields = new ArrayList<>();
                List<SearchField> searchFields = importConfiguration.getSearchFields();

                if (Objects.nonNull(searchFields)) {
                    for (SearchField searchField : searchFields) {
                        if (!searchField.isDisplayed()) {
                            continue;
                        }
                        fields.add(searchField.getLabel());
                    }
                }
                return fields;
            }
        } catch (IllegalArgumentException e) {
            logger.error(e.getLocalizedMessage());
            throw new IllegalArgumentException("Error retrieving search fields from ImportConfiguration '"
                    + importConfiguration.getTitle() + "': " + e.getMessage());
        }
    }

    /**
     * Retrieve default search field label of given ImportConfiguration.
     *
     * @param importConfiguration ImportConfiguration
     * @return label of default search field
     */
    public static String getDefaultSearchField(ImportConfiguration importConfiguration) {
        if (SearchInterfaceType.FTP.name().equals(importConfiguration.getInterfaceType())) {
            return Helper.getTranslation("filename");
        } else if (SearchInterfaceType.OAI.name().equals(importConfiguration.getInterfaceType())) {
            return Helper.getTranslation("recordId");
        } else if (Objects.nonNull(importConfiguration.getDefaultSearchField())) {
            return importConfiguration.getDefaultSearchField().getLabel();
        } else if (!importConfiguration.getSearchFields().isEmpty()) {
            return importConfiguration.getSearchFields().get(0).getLabel();
        }
        return "";
    }

    /**
     * Check and return whether to skip hit list for given ImportConfiguration and search field or not.
     * Hit list is skipped either if SearchInterfaceType of given ImportConfiguration does not support
     * hit lists (e.g. OAI interfaces in their current implementation) or if the provided search field
     * equals the ID search field of the given ImportConfiguration.
     * @param configuration ImportConfiguration to check
     * @param field value of SearchField to check
     * @return whether to skip hit list or not
     */
    public static boolean skipHitlist(ImportConfiguration configuration, String field) {
        if (SearchInterfaceType.FTP.name().equals(configuration.getInterfaceType())) {
            return false;
        }
        else if (SearchInterfaceType.OAI.name().equals(configuration.getInterfaceType())
                || field.equals(configuration.getIdSearchField().getLabel())) {
            return true;
        }
        return (Objects.isNull(configuration.getMetadataRecordIdXPath())
                || Objects.isNull(configuration.getMetadataRecordTitleXPath()));
    }

    /**
     * Get default import depth for given import configuration.
     *
     * @param importConfiguration ImportConfiguration
     * @return default import depth of given import configuration
     */
    public static int getDefaultImportDepth(ImportConfiguration importConfiguration) {
        int depth = importConfiguration.getDefaultImportDepth();
        if (depth < 0 || depth > 5) {
            return 2;
        } else {
            return depth;
        }
    }

    private LinkedList<ExemplarRecord> extractExemplarRecords(DataRecord record,
                                                              ImportConfiguration importConfiguration)
            throws XPathExpressionException,
            ParserConfigurationException, SAXException, IOException {
        LinkedList<ExemplarRecord> exemplarRecords = new LinkedList<>();
        String exemplarXPath = importConfiguration.getItemFieldXpath();
        String ownerXPath = importConfiguration.getItemFieldOwnerSubPath();
        String signatureXPath = importConfiguration.getItemFieldSignatureSubPath();

        if (!StringUtils.isBlank(exemplarXPath) && !StringUtils.isBlank(ownerXPath)
                && !StringUtils.isBlank(signatureXPath) && record.getOriginalData() instanceof String) {
            String xmlString = (String) record.getOriginalData();
            XPath xPath = XPathFactory.newInstance().newXPath();
            xPath.setNamespaceContext(new KitodoNamespaceContext());
            Document doc = XMLUtils.parseXMLString(xmlString);
            NodeList exemplars = (NodeList) xPath.compile(exemplarXPath).evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < exemplars.getLength(); i++) {
                Node exemplar = exemplars.item(i);
                Node ownerNode = (Node) xPath.compile(ownerXPath).evaluate(exemplar, XPathConstants.NODE);
                Node signatureNode = (Node) xPath.compile(signatureXPath).evaluate(exemplar, XPathConstants.NODE);

                if (Objects.nonNull(ownerNode) && Objects.nonNull(signatureNode)) {
                    String owner = ownerNode.getTextContent();
                    String signature = signatureNode.getTextContent();
                    if (!StringUtils.isBlank(owner) && !StringUtils.isBlank(signature)) {
                        exemplarRecords.add(new ExemplarRecord(owner, signature));
                    }
                }
            }
        }
        return exemplarRecords;
    }

    /**
     * Iterate over "SchemaConverterInterface" implementations using KitodoServiceLoader and return
     * first implementation that supports the Metadata and File formats of the given DataRecord object
     * as source formats and the Kitodo internal format and XML as target formats, respectively.
     *
     * @param record
     *      Record whose metadata and return formats are used to filter the SchemaConverterInterface implementations
     *
     * @return List of SchemaConverterInterface implementations that support the metadata and return formats of the
     *      given Record.
     *
     * @throws UnsupportedFormatException when no SchemaConverter module with matching formats could be found
     */
    private SchemaConverterInterface getSchemaConverter(DataRecord record) throws UnsupportedFormatException {
        KitodoServiceLoader<SchemaConverterInterface> loader =
                new KitodoServiceLoader<>(SchemaConverterInterface.class);
        List<SchemaConverterInterface> converterModules = loader.loadModules().stream()
                .filter(converter -> converter.supportsSourceFileFormat(record.getFileFormat())
                        && converter.supportsTargetFileFormat(FileFormat.XML))
                .collect(Collectors.toList());
        if (converterModules.isEmpty()) {
            throw new UnsupportedFormatException("No SchemaConverter found that supports '"
                    + record.getMetadataFormat() + "' and '" + record.getFileFormat() + "'!");
        }
        return converterModules.get(0);
    }

    /**
     * Get docType form imported record.
     * @param record imported record
     *       as Document
     * @return docType as String
     */
    private String getRecordDocType(Document record, Ruleset ruleset) throws IOException {
        Collection<String> doctypes = getDocTypeMetadata(ruleset);
        Element root = record.getDocumentElement();
        NodeList kitodoNodes = root.getElementsByTagNameNS(KITODO_NAMESPACE, KITODO);
        if (kitodoNodes.getLength() > 0 && !doctypes.isEmpty() && kitodoNodes.item(0) instanceof Element) {
            Element kitodoElement = (Element) kitodoNodes.item(0);
            NodeList importedMetadata = kitodoElement.getElementsByTagNameNS(KITODO_NAMESPACE, "metadata");
            for (int i = 0; i < importedMetadata.getLength(); i++) {
                Node metadataNode = importedMetadata.item(i);
                Element metadataElement = (Element) metadataNode;
                if (doctypes.contains(metadataElement.getAttribute("name"))) {
                    return metadataElement.getTextContent();
                }
            }
        }
        return "";
    }

    /**
     * Get the parent ID from the document.
     * @param document Document to parse
     * @param higherLevelIdentifier the given identifier
     * @param trimMode trim mode for parent id
     * @return parent ID
     */
    public String getParentID(Document document, String higherLevelIdentifier, String trimMode)
        throws XPathExpressionException {
        XPath parentIDXpath = XPathFactory.newInstance().newXPath();
        parentIDXpath.setNamespaceContext(new KitodoNamespaceContext());
        NodeList nodeList = (NodeList) parentIDXpath.compile(PARENT_XPATH.replace(REPLACE_ME, higherLevelIdentifier))
                .evaluate(document, XPathConstants.NODESET);
        if (nodeList.getLength() == 1) {
            Node parentIDNode = nodeList.item(0);
            if (PARENTHESIS_TRIM_MODE.equals(trimMode)) {
                return parentIDNode.getTextContent().replaceAll("\\([^)]+\\)", "");
            } else {
                return parentIDNode.getTextContent();
            }
        } else {
            return null;
        }
    }

    /**
     * Creates a temporary Process from the given document with templateID und projectID.
     * @param importConfiguration ImportConfiguration used to create TempProcess
     * @param document the given document
     * @param templateID the template to use
     * @param projectID the project to use
     * @return a temporary process
     * @throws ProcessGenerationException when creating process for given template and project fails
     * @throws IOException when loading workpiece of TempProcess or retrieving type of document fails
     * @throws TransformerException when loading workpiece of TempProcess fails
     */
    public TempProcess createTempProcessFromDocument(ImportConfiguration importConfiguration, Document document,
                                                     int templateID, int projectID)
            throws ProcessGenerationException, IOException, TransformerException {
        Process process = null;
        // "processGenerator" needs to be initialized when function is called for the first time
        if (Objects.isNull(processGenerator)) {
            processGenerator = new ProcessGenerator();
        }
        if (processGenerator.generateProcess(templateID, projectID)) {
            process = processGenerator.getGeneratedProcess();
            process.setImportConfiguration(importConfiguration);
        }
        TempProcess tempProcess;

        if (importConfiguration.getPrestructuredImport()) {
            // logical structure is created by import XSLT file!
            Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(document);
            tempProcess = new TempProcess(process, workpiece);
        } else if (Objects.nonNull(process) && Objects.nonNull(process.getRuleset())) {
            String docType = getRecordDocType(document, process.getRuleset());
            NodeList metadataNodes = extractMetadataNodeList(document);
            tempProcess = new TempProcess(process, metadataNodes, docType);
        } else {
            throw new ProcessGenerationException("Ruleset missing!");
        }
        return tempProcess;
    }

    private String importProcessAndReturnParentID(String recordId, LinkedList<TempProcess> allProcesses,
                                                  ImportConfiguration importConfiguration, int projectID,
                                                  int templateID, boolean isParentInRecord, String parentIdMetadata)
            throws IOException, ProcessGenerationException, XPathExpressionException, ParserConfigurationException,
            NoRecordFoundException, UnsupportedFormatException, URISyntaxException, SAXException, TransformerException,
            InvalidMetadataValueException, NoSuchMetadataFieldException {

        DataRecord dataRecord = importExternalDataRecord(importConfiguration, recordId, allProcesses.isEmpty());
        Document internalDocument = convertDataRecordToInternal(dataRecord, importConfiguration, isParentInRecord);
        TempProcess tempProcess = createTempProcessFromDocument(importConfiguration, internalDocument, templateID, projectID);

        // Workaround for classifying MultiVolumeWorks with insufficient information
        if (!allProcesses.isEmpty()) {
            String childDocType = allProcesses.getLast().getWorkpiece().getLogicalStructure().getType();
            Workpiece workpiece = tempProcess.getWorkpiece();
            if (Objects.nonNull(workpiece) && Objects.nonNull(workpiece.getLogicalStructure())) {
                String docType = workpiece.getLogicalStructure().getType();
                if ((MONOGRAPH.equals(childDocType) || VOLUME.equals(childDocType)) && MONOGRAPH.equals(docType)) {
                    tempProcess.getWorkpiece().getLogicalStructure().setType(MULTI_VOLUME_WORK);
                    allProcesses.getFirst().getWorkpiece().getLogicalStructure().setType(VOLUME);
                }
            }
        }
        allProcesses.add(tempProcess);
        if (!isParentInRecord && StringUtils.isNotBlank(parentIdMetadata)) {
            return getParentID(internalDocument, parentIdMetadata, importConfiguration.getParentElementTrimMode());
        }
        return null;
    }

    /**
     * Import data record with given ID 'recordId' into project with given ID 'projectID' with template with given ID
     * 'templateId' and using given ImportConfiguration 'importConfiguration'. Returns TempProcess containing imported
     * data record.
     *
     * @param importConfiguration ImportConfiguration containing settings for import data record
     * @param recordId catalog identifier of data record to import
     * @param templateID ID of template to use when importing data record to TempProcess
     * @param projectID ID of project to which imported data record is assigned
     * @return TempProcess containing imported data record
     * @throws UnsupportedFormatException when external data record contains data in unsupported format
     * @throws NoRecordFoundException when no record with given ID 'recordId' could be found
     * @throws XPathExpressionException when error message XPath in ImportConfiguration has syntax errors
     * @throws ProcessGenerationException when creating TempProcess from imported data record fails
     * @throws URISyntaxException when loading MappingFiles from ImportConfiguration fails
     * @throws IOException when saving or loading imported XML file fails
     * @throws ParserConfigurationException when parsing import XML file fails
     * @throws SAXException when parsing import XML file fails
     * @throws TransformerException when loading internal format document fails
     */
    public TempProcess importTempProcess(ImportConfiguration importConfiguration, String recordId, int templateID,
                                         int projectID)
            throws UnsupportedFormatException, NoRecordFoundException, XPathExpressionException,
            ProcessGenerationException, URISyntaxException, IOException, ParserConfigurationException, SAXException,
            TransformerException {
        DataRecord dataRecord = importExternalDataRecord(importConfiguration, recordId, false);
        Document internalDocument = convertDataRecordToInternal(dataRecord, importConfiguration, false);
        return createTempProcessFromDocument(importConfiguration, internalDocument, templateID, projectID);
    }

    /**
     * Returns the searchTerm with configured Delimiter.
     * @param searchTerm the search term to add delimiters.
     * @param importConfiguration the ImportConfiguration to use
     * @return searchTermWithDelimiter
     */
    public String getSearchTermWithDelimiter(String searchTerm, ImportConfiguration importConfiguration) {
        String searchTermWithDelimiter = searchTerm;
        String queryDelimiter = importConfiguration.getQueryDelimiter();
        if (Objects.nonNull(queryDelimiter)) {
            searchTermWithDelimiter = queryDelimiter + searchTermWithDelimiter + queryDelimiter;
        }
        return searchTermWithDelimiter;
    }

    /**
     * Import a record identified by the given ID 'recordId'.
     * Additionally, import all ancestors of the given process referenced in the original data of the process imported
     * from the OPAC selected in the given CreateProcessForm instance.
     * Return the list of processes as a LinkedList of TempProcess.
     *
     * @param recordId identifier of the process to import
     * @param importConfiguration ImportConfiguration used to import the record
     * @param projectId the ID of the project for which a process is created
     * @param templateId the ID of the template from which a process is created
     * @param importDepth the number of hierarchical processes that will be imported from the catalog
     * @param parentIdMetadata names of Metadata types holding parent IDs of structure elements in internal format
     * @return List of TempProcess
     */
    public LinkedList<TempProcess> importProcessHierarchy(String recordId, ImportConfiguration importConfiguration,
                                                          int projectId, int templateId, int importDepth,
                                                          Collection<String> parentIdMetadata)
            throws IOException, ProcessGenerationException, XPathExpressionException, ParserConfigurationException,
            NoRecordFoundException, UnsupportedFormatException, URISyntaxException, SAXException, DAOException,
            TransformerException, InvalidMetadataValueException, NoSuchMetadataFieldException {
        importModule = initializeImportModule();
        processGenerator = new ProcessGenerator();
        LinkedList<TempProcess> processes = new LinkedList<>();
        String parentMetadataKey = "";
        if (parentIdMetadata.isEmpty()) {
            if (importDepth > 1) {
                Helper.setErrorMessage("newProcess.catalogueSearch.parentIDMetadataMissing");
                importDepth = 1;
            }
        } else {
            parentMetadataKey = parentIdMetadata.toArray()[0].toString();
        }

        String parentID = importProcessAndReturnParentID(recordId, processes, importConfiguration, projectId,
                templateId, false, parentMetadataKey);
        Template template = ServiceManager.getTemplateService().getById(templateId);
        if (Objects.isNull(template.getRuleset())) {
            throw new ProcessGenerationException("Ruleset of template " + template.getId() + " is null!");
        }
        importParents(recordId, importConfiguration, projectId, templateId, importDepth, processes, parentID, template,
                parentMetadataKey);

        ListIterator<TempProcess> processesIterator = processes.listIterator();
        while (processesIterator.hasNext()) {
            int fromIndex = processesIterator.nextIndex() + 1;
            List<TempProcess> parents = new ArrayList<>();
            if (fromIndex < processes.size()) {
                parents = processes.subList(fromIndex, processes.size());
            }
            ProcessHelper.generateAtstslFields(processesIterator.next(), parents, CREATE, false);
        }

        return processes;
    }

    private void importParents(String recordId, ImportConfiguration importConfiguration, int projectId, int templateId,
                               int importDepth, LinkedList<TempProcess> processes, String parentID, Template template,
                               String parentIdMetadata)
            throws ProcessGenerationException, IOException, XPathExpressionException, ParserConfigurationException,
            NoRecordFoundException, UnsupportedFormatException, URISyntaxException, SAXException,
            InvalidMetadataValueException, NoSuchMetadataFieldException {
        int level = 1;
        this.parentTempProcess = null;
        while (Objects.nonNull(parentID) && level < importDepth) {
            try {
                Process parentProcess = loadParentProcess(template.getRuleset(), projectId, parentID);
                if (Objects.isNull(parentProcess)) {
                    if (Objects.nonNull(importConfiguration.getParentMappingFile())) {
                        parentID = importProcessAndReturnParentID(recordId, processes, importConfiguration, projectId,
                                templateId, true, parentIdMetadata);
                    } else {
                        parentID = importProcessAndReturnParentID(parentID, processes, importConfiguration, projectId,
                                templateId, false, parentIdMetadata);
                    }
                    level++;
                } else {
                    logger.info("Process with ID '{}' already in database. Stop hierarchical import.", parentID);
                    URI workpieceUri = ServiceManager.getProcessService().getMetadataFileUri(parentProcess);
                    Workpiece parentWorkpiece = ServiceManager.getMetsService().loadWorkpiece(workpieceUri);
                    this.parentTempProcess = new TempProcess(parentProcess, parentWorkpiece);
                    break;
                }
            } catch (SAXParseException | DAOException | TransformerException e) {
                // this happens for example if a document is part of a "Virtueller Bestand" in
                // Kalliope for which a
                // proper "record" is not returned from its SRU interface
                logger.error(e.getLocalizedMessage());
                break;
            }
        }
        // always try to find a parent for last imported process (e.g. level ==
        // importDepth) in the database!
        if (Objects.nonNull(parentID) && level == importDepth) {
            checkForParent(parentID, template.getRuleset(), projectId);
        }
    }

    /**
     * Check if there already is a parent process in Database.
     *
     * @param parentID ID of parent process to retrieve
     * @param ruleset ruleset of parent process to retrieve
     * @param projectID ID of project to which parent process must belong
     */
    public void checkForParent(String parentID, Ruleset ruleset, int projectID) {
        this.parentTempProcess = retrieveParentTempProcess(parentID, ruleset, projectID);
    }

    /**
     * Get parentTempProcess.
     *
     * @return value of parentTempProcess
     */
    public TempProcess getParentTempProcess() {
        return parentTempProcess;
    }

    /**
     * Retrieve temp process containing process with given 'parentRecordId' as functional metadata 'recordIdentifier'.
     *
     * @param parentRecordId 'recordIdentifier' value of parent process
     * @param ruleset Ruleset containing metadata rules
     * @param projectID ID of project to which process belongs
     * @return TempProcess containing parent process if it exists in database or null otherwise
     */
    public TempProcess retrieveParentTempProcess(String parentRecordId, Ruleset ruleset, int projectID) {
        if (Objects.isNull(parentRecordId) || Objects.isNull(ruleset)) {
            logger.info("Unable to get parent temp process: parentRecordId or ruleset is null!");
            return null;
        }
        Process parentProcess;
        try {
            parentProcess = loadParentProcess(ruleset, projectID, parentRecordId);
            if (Objects.isNull(parentProcess)) {
                return null;
            }
            URI workpieceUri = ServiceManager.getProcessService().getMetadataFileUri(parentProcess);
            Workpiece parentWorkpiece = ServiceManager.getMetsService().loadWorkpiece(workpieceUri);
            return new TempProcess(parentProcess, parentWorkpiece);
        } catch (ProcessGenerationException | DAOException | IOException e) {
            logger.error("Error retrieving parent process with 'recordIdentifier' {}, project ID {} and ruleset {}",
                    parentRecordId, projectID, ruleset.getTitle());
            return null;
        }
    }

    private List<DataRecord> searchChildRecords(ImportConfiguration config, String parentId, int numberOfRows) {
        SearchField parenIDSearchField = config.getParentSearchField();
        if (Objects.isNull(parenIDSearchField)) {
            throw new ConfigException("Unable to find parent ID search field for catalog '" + config.getTitle() + "'!");
        }
        return importModule.getMultipleFullRecordsFromQuery(createDataImportFromImportConfiguration(config),
                parenIDSearchField.getLabel(), parentId, numberOfRows);
    }

    /**
     * Get number of child records of record with ID 'parentId' from catalog 'opac'.
     *
     * @param importConfiguration ImportConfiguration to use
     * @param parentId ID of the parent record
     * @return number of child records
     */
    public int getNumberOfChildren(ImportConfiguration importConfiguration, String parentId) {
        SearchField parentIDSearchField = importConfiguration.getParentSearchField();
        if (Objects.isNull(parentIDSearchField)) {
            throw new ConfigException("Unable to find parent ID search field for catalog '"
                    + importConfiguration.getTitle() + "'!");
        }
        SearchResult searchResult = performSearch(parentIDSearchField.getLabel(), parentId, importConfiguration, 0, 0);
        if (Objects.nonNull(searchResult)) {
            return searchResult.getNumberOfHits();
        } else {
            Helper.setErrorMessage("Error retrieving number of children for record with ID " + parentId + " from OPAC "
                    + importConfiguration.getTitle() + "!");
            return 0;
        }
    }

    /**
     * Search child records of record with ID 'elementID' from catalog 'opac', transform them into a list of
     * 'TempProcess' and return the list.
     *
     * @param importConfiguration ImportConfiguration to use
     * @param elementID ID of record for which child records are retrieved
     * @param projectId ID of project for which processes are created
     * @param templateId ID of template with which processes are created
     * @param rows number of child records to retrieve from catalog
     * @param parentProcesses parent processes of the children
     * @return list of TempProcesses containing the retrieved child records.
     */
    public LinkedList<TempProcess> getChildProcesses(ImportConfiguration importConfiguration, String elementID,
                                                     int projectId, int templateId, int rows, List<TempProcess> parentProcesses)
            throws SAXException, UnsupportedFormatException, URISyntaxException, ParserConfigurationException,
            NoRecordFoundException, IOException, ProcessGenerationException, TransformerException,
            InvalidMetadataValueException, NoSuchMetadataFieldException {
        importModule = initializeImportModule();
        List<DataRecord> childRecords = searchChildRecords(importConfiguration, elementID, rows);
        LinkedList<TempProcess> childProcesses = new LinkedList<>();
        if (!childRecords.isEmpty()) {
            SchemaConverterInterface converter = getSchemaConverter(childRecords.get(0));
            List<File> mappingFiles = getMappingFiles(importConfiguration);
            for (DataRecord childRecord : childRecords) {
                DataRecord internalRecord = converter.convert(childRecord, MetadataFormat.KITODO, FileFormat.XML, mappingFiles);
                Document childDocument = XMLUtils.parseXMLString((String)internalRecord.getOriginalData());
                TempProcess tempProcess = createTempProcessFromDocument(importConfiguration, childDocument,
                        templateId, projectId);
                tempProcess.getProcess().setImportConfiguration(importConfiguration);
                ProcessHelper.generateAtstslFields(tempProcess, parentProcesses, CREATE, false);
                childProcesses.add(tempProcess);
            }

            // TODO: sort child processes (by what? catalog ID? Signature?)
            return childProcesses;
        } else {
            throw new NoRecordFoundException("No child records found for data record with ID '" + elementID
                    + "' in OPAC '" + importConfiguration.getTitle() + "'!");
        }
    }

    /**
     * Retrieve data from external data source and return DataRecord containing said external data.
     *
     * @param importConfiguration ImportConfiguration used for data import
     * @param identifier ID of record to be loaded from external source
     * @param extractExemplars boolean flag signaling whether exemplar records should be extracted from data record
     * @return DataRecord containing data loaded from external source
     * @throws NoRecordFoundException when loading full record by ID fails
     * @throws IOException when extracting exemplar records fails
     * @throws XPathExpressionException when extracting exemplar records fails
     * @throws ParserConfigurationException when extracting exemplar records fails
     * @throws SAXException when extracting exemplar records fails
     */
    public DataRecord importExternalDataRecord(ImportConfiguration importConfiguration, String identifier,
                                                boolean extractExemplars)
            throws NoRecordFoundException, IOException,
            XPathExpressionException, ParserConfigurationException, SAXException {
        importModule = initializeImportModule();
        DataRecord dataRecord = importModule.getFullRecordById(
                createDataImportFromImportConfiguration(importConfiguration),
                getSearchTermWithDelimiter(identifier, importConfiguration));
        if (extractExemplars) {
            exemplarRecords = extractExemplarRecords(dataRecord, importConfiguration);
        }
        return dataRecord;
    }

    /**
     * This method transforms a given data record that contains an EAD collection as an XML string into a list of
     * temp processes. The first temp process in the list will contain the 'collection' itself, while all following temp
     * processes contain the 'item' level child records of the collection.
     *
     * @param importedEADRecord XML string representation of
     * @return list of temp processes
     */
    public LinkedList<TempProcess> parseImportedEADCollection(DataRecord importedEADRecord,
                                                              ImportConfiguration importConfiguration, int projectId,
                                                              int templateId, String eadChildProcessLevel,
                                                              String eadParentProcessLevel)
            throws IOException, ParserConfigurationException, SAXException, ProcessGenerationException,
            TransformerException, UnsupportedFormatException, XPathExpressionException, URISyntaxException,
            InvalidMetadataValueException, NoSuchMetadataFieldException {
        LinkedList<TempProcess> eadCollectionProcesses = new LinkedList<>();

        Document eadCollectionDocument = XMLUtils.parseXMLString((String) importedEADRecord.getOriginalData());

        List<Element> parentElements = getEADElements(eadCollectionDocument, eadParentProcessLevel);
        List<Element> childElements = getEADElements(eadCollectionDocument, eadChildProcessLevel);

        if (parentElements.size() != 1) {
            throw new ProcessGenerationException(Helper.getTranslation(
                    "importError.wrongNumberOfEadParentLevelElements", eadParentProcessLevel));
        }

        // create temp processes for parent (e.g. "collection") and children (e.g. "files")
        TempProcess collectionProcess = createTempProcessFromElement(parentElements.get(0), importConfiguration,
                projectId, templateId, true);
        eadCollectionProcesses.add(collectionProcess);

        List<TempProcess> parentProcesses = Collections.singletonList(collectionProcess);
        for (Element fileElement : childElements) {
            TempProcess currentFileProcess = createTempProcessFromElement(fileElement, importConfiguration,
                    projectId, templateId, false);
            ProcessHelper.generateAtstslFields(currentFileProcess, parentProcesses, CREATE, false);
            eadCollectionProcesses.add(currentFileProcess);
        }

        return eadCollectionProcesses;
    }

    /**
     * Create and return TempProcess from given XML element 'Element'.
     *
     * @param element XML element to be transformed into TempProcess
     * @param importConfiguration ImportConfiguration containing settings used to transform XML element to TempProcess
     * @param projectId ID of project for which process is created
     * @param templateId ID of template with which process is created
     * @param isParent boolean flag signaling whether the process to be created is a parent process and thus a separate
     *                 parentMappingFile should be used for the XML transformation or not
     * @return TempProcess created from given XML element
     * @throws TransformerException when parsing external data failed
     * @throws UnsupportedFormatException when external data could not be transformed to internal metadata format
     * @throws XPathExpressionException when external data could not be transformed to internal metadata format
     * @throws ProcessGenerationException when external data could not be transformed to internal metadata format
     * @throws URISyntaxException when external data could not be transformed to internal metadata format
     * @throws IOException when creating new process failed
     * @throws ParserConfigurationException when external data could not be transformed to internal metadata format
     * @throws SAXException when external data could not be transformed to internal metadata format
     */
    public TempProcess createTempProcessFromElement(Element element, ImportConfiguration importConfiguration,
                                                           int projectId, int templateId, boolean isParent)
            throws TransformerException, UnsupportedFormatException, XPathExpressionException,
            ProcessGenerationException, URISyntaxException, IOException, ParserConfigurationException, SAXException {
        String collectionString = XMLUtils.elementToString(element);
        DataRecord externalCollectionRecord = XMLUtils.createRecordFromXMLElement(collectionString,
                importConfiguration);
        Document internalEadCollectionDocument = convertDataRecordToInternal(externalCollectionRecord,
                importConfiguration, isParent);
        return createTempProcessFromDocument(importConfiguration, internalEadCollectionDocument, templateId, projectId);
    }

    private List<Element> getEADElements(Document document, String level) {
        return XMLUtils.getElementsByTagNameAndAttributeValue(document, StringConstants.C_TAG_NAME,
                StringConstants.LEVEL, level);
    }

    /**
     * Check and return whether maximum number of tags with given tag name 'tagName' in XML with content given as
     * 'xmlString' exceeds limit configured as maxNumberOfProcessesForImportMask in kitodo_config.properties.
     *
     * @param xmlString String representation of XML content to check
     * @param tagName name of XML tag counted in XML content
     * @return whether the number of tags exceeds the allowed limit
     * @throws XMLStreamException when retrieving number of tags in XML content fails
     */
    public boolean isMaxNumberOfRecordsExceeded(String xmlString, String tagName) throws XMLStreamException {
        int numberOfRecords = XMLUtils.getNumberOfEADElements(xmlString, tagName);
        return numberOfRecords > ConfigCore.getIntParameterOrDefaultValue(ParameterCore.MAX_NUMBER_OF_PROCESSES_FOR_IMPORT_MASK);
    }

    /**
     * Converts a given dataRecord to an internal document.
     * @param dataRecord the dataRecord to convert.
     * @param importConfiguration the import configuration to use
     * @param isParentInRecord if parentRecord is in childRecord
     * @return the converted Document
     */
    public Document convertDataRecordToInternal(DataRecord dataRecord, ImportConfiguration importConfiguration,
                                                boolean isParentInRecord)
            throws UnsupportedFormatException, URISyntaxException, IOException, ParserConfigurationException,
            SAXException, XPathExpressionException, ProcessGenerationException {
        SchemaConverterInterface converter = getSchemaConverter(dataRecord);

        List<File> mappingFiles = getMappingFiles(importConfiguration, isParentInRecord);

        // transform dataRecord to Kitodo internal format using appropriate SchemaConverter!
        File debugFolder = ConfigCore.getKitodoDebugDirectory();
        if (Objects.nonNull(debugFolder)) {
            FileUtils.writeStringToFile(new File(debugFolder, "catalogRecord.xml"),
                    (String) dataRecord.getOriginalData(), StandardCharsets.UTF_8);
        }
        DataRecord internalRecord = converter.convert(dataRecord, MetadataFormat.KITODO, FileFormat.XML, mappingFiles);
        if (Objects.nonNull(debugFolder)) {
            FileUtils.writeStringToFile(new File(debugFolder, "internalRecord.xml"),
                    (String) internalRecord.getOriginalData(), StandardCharsets.UTF_8);
        }

        if (!(internalRecord.getOriginalData() instanceof String)) {
            throw new UnsupportedFormatException("Original metadata of internal record has to be an XML String, '"
                    + internalRecord.getOriginalData().getClass().getName() + "' found!");
        }

        Document resultDocument = null;
        try {
            resultDocument = XMLUtils.parseXMLString((String) internalRecord.getOriginalData());
        } catch (SAXParseException e) {
            String interfaceName = importConfiguration.getInterfaceType();
            if (Arrays.stream(SearchInterfaceType.values()).anyMatch(sit -> sit.name().equals(interfaceName))) {
                SearchInterfaceType searchInterfaceType = SearchInterfaceType.valueOf(interfaceName);
                String errorMessageXpath = searchInterfaceType.getErrorMessageXpath();
                if (Objects.nonNull(errorMessageXpath) && dataRecord.getOriginalData() instanceof String) {
                    Element originalDocument = XMLUtils.parseXMLString((String) dataRecord.getOriginalData()).getDocumentElement();
                    String errorMessage = XPathFactory.newInstance().newXPath().evaluate(errorMessageXpath, originalDocument);
                    if (StringUtils.isNotBlank(errorMessage)) {
                        errorMessage = interfaceName.toUpperCase() + " error: '" + errorMessage + "'";
                        throw new CatalogException(errorMessage);
                    }
                }
            } else {
                throw e;
            }
        }
        if (Objects.isNull(resultDocument)) {
            throw new ProcessGenerationException(Helper.getTranslation("importError.emptyDocument"));
        }
        return resultDocument;
    }

    private NodeList extractMetadataNodeList(Document document) throws ProcessGenerationException {
        NodeList kitodoNodes = document.getElementsByTagNameNS(KITODO_NAMESPACE, KITODO);
        if (kitodoNodes.getLength() != 1) {
            throw new ProcessGenerationException("Number of 'kitodo' nodes unequal to '1' => unable to generate process!");
        }
        Node kitodoNode = kitodoNodes.item(0);
        return kitodoNode.getChildNodes();
    }

    private List<File> getMappingFiles(ImportConfiguration importConfiguration, boolean forParentInRecord)
            throws URISyntaxException {
        List<File> mappingFiles = new ArrayList<>();

        List<String> mappingFileNames;
        try {
            if (forParentInRecord) {
                mappingFileNames = Collections.singletonList(importConfiguration.getParentMappingFile().getFile());
            } else {
                mappingFileNames = importConfiguration.getMappingFiles().stream().map(MappingFile::getFile)
                        .collect(Collectors.toList());
            }
            for (String mappingFileName : mappingFileNames) {
                URI xsltFile = Paths.get(ConfigCore.getParameter(ParameterCore.DIR_XSLT)).toUri()
                        .resolve(new URI(mappingFileName.trim()));
                mappingFiles.add(ServiceManager.getFileService().getFile(xsltFile));
            }
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
        }
        return mappingFiles;
    }

    private List<File> getMappingFiles(ImportConfiguration importConfiguration) throws URISyntaxException {
        return getMappingFiles(importConfiguration, false);
    }

    /**
     * Get the value of a specific processDetail in the processDetails.
     *
     * @param processDetail
     *            as ProcessDetail
     * @return the value as a java.lang.String
     */
    public static String getProcessDetailValue(ProcessDetail processDetail) {
        String value = "";
        if (processDetail instanceof ProcessTextMetadata) {
            return ((ProcessTextMetadata) processDetail).getValue();
        } else if (processDetail instanceof ProcessBooleanMetadata) {
            return String.valueOf(((ProcessBooleanMetadata) processDetail).isActive());
        } else if (processDetail instanceof ProcessSelectMetadata) {
            return String.join(", ", ((ProcessSelectMetadata) processDetail).getSelectedItems());
        } else if (processDetail instanceof ProcessFieldedMetadata && processDetail.getMetadataID().equals(PERSON)) {
            value = getCreator(((ProcessFieldedMetadata) processDetail).getRows());
        }
        return value;
    }

    /**
     * Set the value of a specific process detail in processDetails.
     * @param processDetail the specific process detail whose value should be set to the param value
     *      as ProcessDetail
     * @param value
     *       as a java.lang.String
     */
    public static void setProcessDetailValue(ProcessDetail processDetail, String value) {
        if (processDetail instanceof ProcessTextMetadata) {
            // TODO: incorporate "initstart" and "initend" values from kitodo_projects.xml like AddtionalField!
            ((ProcessTextMetadata) processDetail).setValue(value);
        } else if (processDetail instanceof ProcessBooleanMetadata) {
            ((ProcessBooleanMetadata) processDetail).setActive(Boolean.parseBoolean(value));
        } else if (processDetail instanceof ProcessSelectMetadata) {
            ((ProcessSelectMetadata) processDetail).setSelectedItem(value);
        }
    }

    /**
     * Get all creators names.
     * @param processDetailsList the list of elements in processDetails
     *      as a list of processDetail
     * @return all creators names as a String
     */
    public static String getListOfCreators(List<ProcessDetail> processDetailsList) {
        String listofAuthors = "";
        for (ProcessDetail detail : processDetailsList) {
            if (detail instanceof ProcessFieldedMetadata
                    && PERSON.equals(detail.getMetadataID())) {
                ProcessFieldedMetadata tableRow = (ProcessFieldedMetadata) detail;
                for (ProcessDetail detailsTableRow : tableRow.getRows()) {
                    if (ROLE.equals(detailsTableRow.getMetadataID())
                            && AUTHOR.equals(getProcessDetailValue(detailsTableRow))) {
                        listofAuthors = listofAuthors.concat(getCreator(tableRow.getRows()));
                        break;
                    }
                }
            }
        }
        return listofAuthors;
    }

    private static String getCreator(List<ProcessDetail> processDetailList) {
        String author = "";
        for (ProcessDetail detail : processDetailList) {
            String detailMetadataID = detail.getMetadataID();
            String detailValue = getProcessDetailValue(detail);
            if ((FIRST_NAME.equals(detailMetadataID)
                    || LAST_NAME.equals(detailMetadataID))
                    && !StringUtils.isBlank(detailValue)) {
                author = author.concat(detailValue);
            }
        }
        return author;
    }

    /**
     * Prepare.
     * @param projectTitle
     *      title of the project
     * @throws IOException when trying to create a 'ConfigProject' instance.
     * @throws DoctypeMissingException when trying to load TifDefinition fails
     */
    public void prepare(String projectTitle) throws IOException, DoctypeMissingException {
        ConfigProject configProject = new ConfigProject(projectTitle);
        usingTemplates = configProject.isUseTemplates();
        tiffDefinition = configProject.getTifDefinition();
    }

    /**
     * Get useTemplate.
     *
     * @return value of useTemplate
     */
    public boolean isUsingTemplates() {
        return usingTemplates;
    }

    /**
     * Set useTemplate.
     *
     * @param usingTemplates as boolean
     */
    public void setUsingTemplates(boolean usingTemplates) {
        this.usingTemplates = usingTemplates;
    }

    /**
     * Returns details about the missing record identifiers.
     * 
     * @return details about the missing record identifiers
     */
    public Collection<RecordIdentifierMissingDetail> getRecordIdentifierMissingDetails() {
        return recordIdentifierMissingDetails;
    }

    /**
     * Get tiffDefinition.
     *
     * @return value of tifDefinition
     */
    public String getTiffDefinition() {
        return tiffDefinition;
    }

    /**
     * Get exemplarRecords.
     *
     * @return value of exemplarRecords
     */
    public LinkedList<ExemplarRecord> getExemplarRecords() {
        return exemplarRecords;
    }

    /**
     * Set selected exemplar record data.
     * @param exemplarRecord
     *          selected exemplar record
     * @param importConfiguration
     *          ImportConfiguration
     * @param metadata
     *          list of metadata fields
     * @throws ParameterNotFoundException if a parameter required for exemplar record extraction is missing
     */
    public static void setSelectedExemplarRecord(ExemplarRecord exemplarRecord, ImportConfiguration importConfiguration,
                                                 List<ProcessDetail> metadata)  throws ParameterNotFoundException {
        String ownerMetadataName = importConfiguration.getItemFieldOwnerMetadata();
        String signatureMetadataName = importConfiguration.getItemFieldSignatureMetadata();
        if (StringUtils.isBlank(ownerMetadataName)) {
            throw new ParameterNotFoundException("ownerMetadata");
        } else if (StringUtils.isBlank(signatureMetadataName)) {
            throw new ParameterNotFoundException("signatureMetadata");
        }
        for (ProcessDetail processDetail : metadata) {
            if (ownerMetadataName.equals(processDetail.getMetadataID())) {
                ImportService.setProcessDetailValue(processDetail, exemplarRecord.getOwner());
            } else if (signatureMetadataName.equals(processDetail.getMetadataID())) {
                ImportService.setProcessDetailValue(processDetail, exemplarRecord.getSignature());
            }
        }
    }

    private Process loadParentProcess(Ruleset ruleset, int projectId, String parentId)
            throws ProcessGenerationException, DAOException, IOException {

        Process parentProcess = null;
        for (String identifierMetadata : getFunctionalMetadata(ruleset, FunctionalMetadata.RECORD_IDENTIFIER)) {
            if (Objects.isNull(parentProcess)) {
                HashMap<String, String> parentIDMetadata = new HashMap<>();
                parentIDMetadata.put(identifierMetadata, parentId);
                try {
                    for (ProcessDTO processDTO : sortProcessesByProjectID(ServiceManager.getProcessService()
                            .findByMetadataInAllProjects(parentIDMetadata, true), projectId)) {
                        Process process = ServiceManager.getProcessService().getById(processDTO.getId());
                        if (Objects.isNull(process.getRuleset()) || Objects.isNull(process.getRuleset().getId())) {
                            throw new ProcessGenerationException("Ruleset or ruleset ID of potential parent process "
                                    + process.getId() + " is null!");
                        }
                        if (process.getRuleset().getId().equals(ruleset.getId())) {
                            parentProcess = process;
                            break;
                        }
                    }
                } catch (DataException e) {
                    logger.error(e.getLocalizedMessage());
                }
            }
        }
        return parentProcess;
    }

    /**
     * Sorts a list of process DTOs based on a provided projectId.
     * Processes which match the provided projectId should come first.
     * @param processDTOs list of process DTOs
     * @param projectId projectId by which the list gets sorted
     */
    public List<ProcessDTO> sortProcessesByProjectID(List<ProcessDTO> processDTOs, int projectId) {
        List<ProcessDTO> sortedList = new ArrayList<>(processDTOs);
        Comparator<ProcessDTO> comparator = Comparator.comparingInt(obj -> {
            if (obj.getProject().getId() == projectId) {
                return 0; // Matching value should come first
            } else {
                return 1; // Non-matching value comes later
            }
        });
        sortedList.sort(comparator);
        return sortedList;
    }

    /**
     * Check and return whether the "parentIdSearchField" is configured in the current ImportConfiguration.
     *
     * @param importConfiguration name of the OPAC to check
     * @return whether "parentIdSearchField" is configured for current ImportConfiguration
     * @throws ConfigException thrown if configuration for OPAC 'catalogName' could not be found
     */
    public boolean isParentIdSearchFieldConfigured(ImportConfiguration importConfiguration) throws ConfigException {
        return Objects.nonNull(importConfiguration.getParentSearchField());
    }

    /**
     * Ensure all processes in given list 'tempProcesses' have a non empty title.
     *
     * @param tempProcesses list of TempProcesses to be checked
     * @return whether a title was changed or not
     * @throws IOException if the meta.xml file of a process could not be loaded
     */
    public static boolean ensureNonEmptyTitles(LinkedList<TempProcess> tempProcesses) throws IOException {
        boolean changedTitle = false;
        for (TempProcess tempProcess : tempProcesses) {
            Process process = tempProcess.getProcess();
            if (Objects.nonNull(process) && StringUtils.isEmpty(process.getTitle())) {
                // FIXME:
                //  if metadataFileUri is null or no meta.xml can be found, the tempProcess has not
                //  yet been saved to disk and contains the workpiece directly, instead!
                URI metadataFileUri = ServiceManager.getProcessService().getMetadataFileUri(process);
                Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metadataFileUri);
                Collection<Metadata> metadata = workpiece.getLogicalStructure().getMetadata();
                String processTitle = "[" + Helper.getTranslation("process") + " " + process.getId() + "]";
                for (Metadata metadatum : metadata) {
                    if (CATALOG_IDENTIFIER.equals(metadatum.getKey())) {
                        processTitle = ((MetadataEntry) metadatum).getValue();
                    }
                }
                process.setTitle(processTitle);
                changedTitle = true;
            }
        }
        return changedTitle;
    }

    /**
     * Process list of child processes.
     *
     * @param mainProcess main process to which list of child processes are attached
     * @param childProcesses list of child processes that are attached to the main process
     * @throws DataException thrown if saving a process fails
     * @throws InvalidMetadataValueException thrown if process workpiece contains invalid metadata
     * @throws NoSuchMetadataFieldException thrown if process workpiece contains undefined metadata
     * @throws ProcessGenerationException thrown if process title cannot be created
     */
    public static void processProcessChildren(Process mainProcess, LinkedList<TempProcess> childProcesses,
                                              RulesetManagementInterface rulesetManagement, String acquisitionStage,
                                              List<Locale.LanguageRange> priorityList)
            throws DataException, InvalidMetadataValueException, NoSuchMetadataFieldException,
            ProcessGenerationException, IOException {
        for (TempProcess tempProcess : childProcesses) {
            if (Objects.isNull(tempProcess) || Objects.isNull(tempProcess.getProcess())) {
                logger.error("Child process {} is null => Skip!", childProcesses.indexOf(tempProcess) + 1);
                continue;
            }
            processTempProcess(tempProcess, rulesetManagement, acquisitionStage, priorityList, null);
            Process childProcess = tempProcess.getProcess();
            ServiceManager.getProcessService().save(childProcess, true);
            ProcessService.setParentRelations(mainProcess, childProcess);
        }
    }

    /**
     * Add workpiece and template properties to given Process 'process'.
     *
     * @param tempProcess
     *         TempProcess that will be processed
     * @param template
     *         Template of process
     * @param processDetails
     *         metadata of process
     * @param docType
     *         String containing document type
     * @param imageDescription
     *         String containing image description
     */
    public static void addProperties(TempProcess tempProcess, Template template, List<ProcessDetail> processDetails,
            String docType, String imageDescription) {
        Process process = tempProcess.getProcess();
        addMetadataProperties(processDetails, process);
        ProcessGenerator.addPropertyForWorkpiece(process, "TSL/ATS", tempProcess.getAtstsl());
        ProcessGenerator.addPropertyForWorkpiece(process, "DocType", docType);
        ProcessGenerator.addPropertyForWorkpiece(process, "TifHeaderImagedescription", imageDescription);
        ProcessGenerator.addPropertyForWorkpiece(process, "TifHeaderDocumentname", process.getTitle());
        if (Objects.nonNull(template)) {
            ProcessGenerator.addPropertyForProcess(process, "Template", template.getTitle());
            ProcessGenerator.addPropertyForProcess(process, "TemplateID", String.valueOf(template.getId()));
        }
    }

    private static void addMetadataProperties(List<ProcessDetail> processDetailList, Process process) {
        try {
            for (ProcessDetail processDetail : processDetailList) {
                Collection<Metadata> processMetadata = processDetail.getMetadataWithFilledValues();
                if (!processMetadata.isEmpty() && processMetadata.toArray()[0] instanceof Metadata) {
                    String metadataValue = ImportService.getProcessDetailValue(processDetail);
                    Metadata metadata = (Metadata) processMetadata.toArray()[0];
                    if (Objects.nonNull(metadata.getDomain())) {
                        switch (metadata.getDomain()) {
                            case DMD_SEC:
                                ProcessGenerator.addPropertyForWorkpiece(process, processDetail.getLabel(), metadataValue);
                                break;
                            case SOURCE_MD:
                                ProcessGenerator.addPropertyForTemplate(process, processDetail.getLabel(), metadataValue);
                                break;
                            case TECH_MD:
                                ProcessGenerator.addPropertyForProcess(process, processDetail.getLabel(), metadataValue);
                                break;
                            default:
                                logger.info("Don't save metadata '{}' with domain '{}' to property.",
                                    processDetail.getMetadataID(), metadata.getDomain());
                                break;
                        }
                    } else {
                        ProcessGenerator.addPropertyForWorkpiece(process, processDetail.getLabel(), metadataValue);
                    }
                }
            }
        } catch (InvalidMetadataValueException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    /**
     * Update tasks of given Process 'process'.
     *
     * @param process Process whose tasks are updated
     */
    public static void updateTasks(Process process) {
        for (Task task : process.getTasks()) {
            task.setProcessingTime(process.getCreationDate());
            task.setEditType(TaskEditType.AUTOMATIC);
            if (task.getProcessingStatus() == TaskStatus.DONE) {
                task.setProcessingBegin(process.getCreationDate());
                Date date = new Date();
                task.setProcessingTime(date);
                task.setProcessingEnd(date);
            }
        }
    }

    /**
     * Process given TempProcess 'tempProcess' by creating the metadata, doc type and properties for the process and
     * updating the process' tasks.
     *
     * @param tempProcess TempProcess that will be processed
     * @param rulesetManagement Ruleset management to create metadata and TIFF header
     * @param acquisitionStage String containing the acquisition stage
     * @param priorityList List of LanguageRange objects
     * @throws InvalidMetadataValueException thrown if the process contains invalid metadata
     * @throws NoSuchMetadataFieldException thrown if the process contains undefined metadata
     * @throws ProcessGenerationException thrown if process title could not be generated
     */
    public static void processTempProcess(TempProcess tempProcess, RulesetManagementInterface rulesetManagement,
            String acquisitionStage, List<Locale.LanguageRange> priorityList, TempProcess parentTempProcess)
            throws InvalidMetadataValueException, NoSuchMetadataFieldException, ProcessGenerationException,
            IOException {

        List<ProcessDetail> processDetails = ProcessHelper.transformToProcessDetails(tempProcess, rulesetManagement,
                acquisitionStage, priorityList);
        String docType = tempProcess.getWorkpiece().getLogicalStructure().getType();

        List<TempProcess> parentTempProcesses = new ArrayList<>();
        if (Objects.nonNull(parentTempProcess)) {
            parentTempProcesses.add(parentTempProcess);
        }
        ProcessHelper.generateAtstslFields(tempProcess, processDetails, parentTempProcesses, docType,
                rulesetManagement, acquisitionStage, priorityList);

        String processTitle = tempProcess.getProcess().getTitle();
        if (!ProcessValidator.isProcessTitleCorrect(processTitle)) {
            throw new ProcessGenerationException(String.format("Unable to create process (invalid process title '%s')",
                    processTitle));
        }

        Process process = tempProcess.getProcess();
        process.setSortHelperImages(tempProcess.getGuessedImages());
        addProperties(tempProcess, tempProcess.getProcess().getTemplate(), processDetails, docType,
                tempProcess.getProcess().getTitle());
        ProcessService.checkTasks(process, docType);
        updateTasks(process);
    }

    /**
     * Imports a process and saves it to database.
     * @param ppn the ppn to import
     * @param projectId the projectId
     * @param templateId the templateId
     * @param importConfiguration the selected import configuration
     * @param presetMetadata Map containing preset metadata with keys as metadata keys and values as metadata values
     * @return the importedProcess
     */
    public Process importProcess(String ppn, int projectId, int templateId, ImportConfiguration importConfiguration,
                                 Map<String, List<String>> presetMetadata) throws ImportException {
        LinkedList<TempProcess> processList = new LinkedList<>();
        TempProcess tempProcess;
        Template template;
        try {
            template = ServiceManager.getTemplateService().getById(templateId);
            String parentMetadataKey = "";
            List<String> higherLevelIdentifiers = new ArrayList<>(
                    getHigherLevelIdentifierMetadata(template.getRuleset()));
            if (!higherLevelIdentifiers.isEmpty()) {
                parentMetadataKey = higherLevelIdentifiers.get(0);
            }
            final String parentId = importProcessAndReturnParentID(ppn, processList, importConfiguration, projectId,
                    templateId, false, parentMetadataKey);
            setParentProcess(parentId, projectId, template);
            tempProcess = processList.get(0);
            String metadataLanguage = ServiceManager.getUserService().getCurrentUser().getMetadataLanguage();
            tempProcess.getWorkpiece().getLogicalStructure().getMetadata().addAll(createMetadata(presetMetadata));
            processTempProcess(tempProcess, ServiceManager.getRulesetService().openRuleset(template.getRuleset()),
                    CREATE, Locale.LanguageRange.parse(metadataLanguage.isEmpty() ? "en" : metadataLanguage),
                    parentTempProcess);
            setLabelAndOrderLabelOfImportedProcess(tempProcess, presetMetadata);
            String title = tempProcess.getProcess().getTitle();
            String validateRegEx = ConfigCore.getParameterOrDefaultValue(ParameterCore.VALIDATE_PROCESS_TITLE_REGEX);
            if (StringUtils.isBlank(title)) {
                throw new ProcessGenerationException(Helper.getTranslation("processTitleEmpty"));
            } else if (!title.matches(validateRegEx)) {
                throw new ProcessGenerationException(Helper.getTranslation("processTitleInvalid", title));
            } else if (ServiceManager.getProcessService().findNumberOfProcessesWithTitle(title) > 0) {
                throw new ProcessGenerationException(Helper.getTranslation("processTitleAlreadyInUse", title));
            }
            ServiceManager.getProcessService().save(tempProcess.getProcess(), true);
            URI processBaseUri = ServiceManager.getFileService().createProcessLocation(tempProcess.getProcess());
            tempProcess.getProcess().setProcessBaseUri(processBaseUri);
            OutputStream out = ServiceManager.getFileService()
                    .write(ServiceManager.getProcessService().getMetadataFileUri(tempProcess.getProcess()));
            tempProcess.getWorkpiece().setId(tempProcess.getProcess().getId().toString());
            ServiceManager.getMetsService().save(tempProcess.getWorkpiece(), out);
            linkToParent(tempProcess);
            ServiceManager.getProcessService().save(tempProcess.getProcess());
        } catch (DAOException | IOException | ProcessGenerationException | XPathExpressionException
                | ParserConfigurationException | NoRecordFoundException | UnsupportedFormatException
                | URISyntaxException | SAXException | InvalidMetadataValueException | NoSuchMetadataFieldException
                | DataException | CommandException | TransformerException | CatalogException e) {
            logger.error(e);
            throw new ImportException(e.getLocalizedMessage());
        }
        return tempProcess.getProcess();
    }

    private void linkToParent(TempProcess tempProcess) throws DAOException, ProcessGenerationException, IOException {
        if (Objects.nonNull(parentTempProcess) && Objects.nonNull(parentTempProcess.getProcess())) {
            URI parentProcessUri = ServiceManager.getProcessService()
                    .getMetadataFileUri(parentTempProcess.getProcess());
            Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(parentProcessUri);
            if (Objects.isNull(workpiece)) {
                throw new ProcessGenerationException("Workpiece of parent process is null!");
            }
            MetadataEditor.addLink(workpiece.getLogicalStructure(), tempProcess.getProcess().getId());
            try (OutputStream outputStream = ServiceManager.getFileService().write(parentProcessUri)) {
                ServiceManager.getMetsService().save(workpiece, outputStream);
            }
            ProcessService.setParentRelations(parentTempProcess.getProcess(), tempProcess.getProcess());
        }
    }

    private void setParentProcess(String parentId, int projectId, Template template)
            throws DAOException, IOException, ProcessGenerationException {
        parentTempProcess = null;
        if (StringUtils.isNotBlank(parentId)) {
            checkForParent(parentId, template.getRuleset(), projectId);
        }
    }

    private static Collection<String> getFunctionalMetadata(Ruleset ruleset, FunctionalMetadata metadata)
            throws IOException {
        RulesetManagementInterface rulesetManagement = ServiceManager.getRulesetManagementService()
                .getRulesetManagement();
        String rulesetDir = ConfigCore.getParameter(ParameterCore.DIR_RULESETS);
        String rulesetPath = Paths.get(rulesetDir, ruleset.getFile()).toString();
        rulesetManagement.load(new File(rulesetPath));
        return rulesetManagement.getFunctionalKeys(metadata);
    }

    private List<MetadataEntry> createMetadata(Map<String, List<String>> presetMetadata) {
        List<MetadataEntry> metadata = new LinkedList<>();
        for (Map.Entry<String, List<String>> presetMetadataEntry : presetMetadata.entrySet()) {
            for (String presetMetadataEntryValue : presetMetadataEntry.getValue()) {
                MetadataEntry metadataEntry = new MetadataEntry();
                metadataEntry.setKey(presetMetadataEntry.getKey());
                metadataEntry.setValue(presetMetadataEntryValue);
                metadataEntry.setDomain(MdSec.DMD_SEC);
                metadata.add(metadataEntry);
            }
        }
        return metadata;
    }

    private void setLabelAndOrderLabelOfImportedProcess(TempProcess tempProcess, Map<String, List<String>> presetMetadata) {
        List<String> labelList = presetMetadata.get(ProcessFieldedMetadata.METADATA_KEY_LABEL);
        List<String> orderLabelList = presetMetadata.get(ProcessFieldedMetadata.METADATA_KEY_ORDERLABEL);

        if (Objects.nonNull(labelList) && !labelList.isEmpty() && !labelList.get(0).isBlank()) {
            tempProcess.getWorkpiece().getLogicalStructure().setLabel(labelList.get(0));
        }

        if (Objects.nonNull(orderLabelList) && !orderLabelList.isEmpty() && !orderLabelList.get(0).isBlank()) {
            tempProcess.getWorkpiece().getLogicalStructure().setOrderlabel(orderLabelList.get(0));
        }
    }

    /**
     * Load doc type metadata keys from provided ruleset.
     * @param ruleset Ruleset from which doc type metadata keys are loaded and returned
     * @return list of Strings containing the IDs of the doc type metadata defined in the provided ruleset.
     * @throws IOException thrown if ruleset file cannot be loaded
     */
    public static Collection<String> getDocTypeMetadata(Ruleset ruleset) throws IOException {
        return getFunctionalMetadata(ruleset, FunctionalMetadata.DOC_TYPE);
    }

    /**
     * Load and return higher level identifier metadata keys from provided ruleset.
     * @param ruleset Ruleset from which higher level identifier metadata keys are loaded and returned
     * @return list of String containing the keys of metadata defined as higher level identifier
     * @throws IOException thrown if ruleset file cannot be loaded
     */
    public static Collection<String> getHigherLevelIdentifierMetadata(Ruleset ruleset) throws IOException {
        return getFunctionalMetadata(ruleset, FunctionalMetadata.HIGHERLEVEL_IDENTIFIER);
    }

    /**
     * Load and return keys of functional metadata 'groupDisplayLabel' from provided ruleset.
     * @param ruleset Ruleset from which keys are loaded and returned
     * @return list of String containing the keys of functional metadata for type 'groupDisplayLabel'
     * @throws IOException thrown if ruleset file cannot be loaded
     */
    public static Collection<String> getGroupDisplayLabelMetadata(Ruleset ruleset) throws IOException {
        return getFunctionalMetadata(ruleset, FunctionalMetadata.GROUP_DISPLAY_LABEL);
    }

    private DataImport createDataImportFromImportConfiguration(ImportConfiguration importConfiguration) {
        String configType = importConfiguration.getConfigurationType();
        if (!ImportConfigurationType.OPAC_SEARCH.name().equals(configType)) {
            throw new ConfigException("Configuration error: given import configuration '"
                    + importConfiguration.getTitle() + "' is of type '" + configType
                    + "' (OPAC_SEARCH expected instead)!");
        }
        DataImport dataImport = new DataImport();
        dataImport.setTitle(importConfiguration.getTitle());
        dataImport.setSearchInterfaceType(SearchInterfaceType.valueOf(importConfiguration.getInterfaceType()));
        dataImport.setReturnFormat(FileFormat.valueOf(importConfiguration.getReturnFormat()));
        dataImport.setMetadataFormat(MetadataFormat.valueOf(importConfiguration.getMetadataFormat()));
        dataImport.setScheme(importConfiguration.getScheme());
        dataImport.setHost(importConfiguration.getHost());
        dataImport.setPath(importConfiguration.getPath());
        if (Objects.nonNull(importConfiguration.getPort())) {
            dataImport.setPort(importConfiguration.getPort());
        }
        dataImport.setIdPrefix(importConfiguration.getIdPrefix());
        dataImport.setUsername(importConfiguration.getUsername());
        dataImport.setPassword(importConfiguration.getPassword());
        dataImport.setAnonymousAccess(importConfiguration.isAnonymousAccess());
        if (Objects.nonNull(importConfiguration.getIdSearchField())) {
            dataImport.setIdParameter(importConfiguration.getIdSearchField().getValue());
        }
        HashMap<String, String> searchFields = new HashMap<>();
        for (SearchField searchField : importConfiguration.getSearchFields()) {
            searchFields.put(searchField.getLabel(), searchField.getValue());
        }
        dataImport.setSearchFields(searchFields);
        dataImport.setUrlParameters(getUrlParameters(importConfiguration));
        dataImport.setRecordIdXPath(importConfiguration.getMetadataRecordIdXPath());
        dataImport.setRecordTitleXPath(importConfiguration.getMetadataRecordTitleXPath());
        return dataImport;
    }

    private HashMap<String, String> getUrlParameters(ImportConfiguration importConfiguration) {
        HashMap<String, String> urlParameters = new HashMap<>();
        if (SearchInterfaceType.SRU.name().equals(importConfiguration.getInterfaceType())) {
            urlParameters.put(SRU_OPERATION, SRU_SEARCH_RETRIEVE);
            if (Objects.isNull(importConfiguration.getSruVersion())
                    || Objects.isNull(importConfiguration.getSruRecordSchema())) {
                throw new ConfigException("Either SRU version or SRU record schema is null!");
            }
            urlParameters.put(SRU_VERSION, importConfiguration.getSruVersion());
            urlParameters.put(SRU_RECORD_SCHEMA, importConfiguration.getSruRecordSchema());
        }
        if (SearchInterfaceType.OAI.name().equals(importConfiguration.getInterfaceType())) {
            urlParameters.put(OAI_VERB, OAI_GET_RECORD);
            if (Objects.isNull(importConfiguration.getOaiMetadataPrefix())) {
                throw new ConfigException("OAI metadata prefix is null!");
            }
            urlParameters.put(OAI_METADATA_PREFIX, importConfiguration.getOaiMetadataPrefix());
        }
        if (SearchInterfaceType.CUSTOM.name().equals(importConfiguration.getInterfaceType())) {
            for (UrlParameter parameter : importConfiguration.getUrlParameters()) {
                urlParameters.put(parameter.getParameterKey(), parameter.getParameterValue());
            }
        }
        return urlParameters;
    }

    /**
     * Check and return whether the functional metadata 'recordIdentifier' is configured for all top level doc struct
     * types in the given RulesetManagementInterface or not.
     * @param rulesetManagement Ruleset management to use
     * @return whether 'recordIdentifier' is set for all doc struct types
     */
    public boolean isRecordIdentifierMetadataConfigured(RulesetManagementInterface rulesetManagement) {
        User user = ServiceManager.getUserService().getCurrentUser();
        String metadataLanguage = user.getMetadataLanguage();
        List<Locale.LanguageRange> languages = Locale.LanguageRange.parse(metadataLanguage.isEmpty()
                ? Locale.ENGLISH.getCountry() : metadataLanguage);
        Map<String, String> structuralElements = rulesetManagement.getStructuralElements(languages);
        Collection<String> recordIdentifierMetadata = rulesetManagement
                .getFunctionalKeys(FunctionalMetadata.RECORD_IDENTIFIER);
        String recordIdentifierLabels = recordIdentifierMetadata.stream()
                .map(key -> rulesetManagement.getTranslationForKey(key, languages).orElse(key))
                .collect(Collectors.joining(", "));
        recordIdentifierMissingDetails.clear();
        boolean isConfigured = true;
        for (Map.Entry<String, String> division : structuralElements.entrySet()) {
            StructuralElementViewInterface divisionView = rulesetManagement
                    .getStructuralElementView(division.getKey(), CREATE, languages);
            List<String> allowedMetadataKeys = divisionView.getAllowedMetadata().stream()
                    .map(MetadataViewInterface::getId).collect(Collectors.toList());
            allowedMetadataKeys.retainAll(recordIdentifierMetadata);
            if (allowedMetadataKeys.isEmpty()) {
                recordIdentifierMissingDetails.add(
                    new RecordIdentifierMissingDetail(division.getValue(), recordIdentifierLabels, divisionView.getAllowedMetadata())
                );
                isConfigured = false;
            }
        }
        return isConfigured;
    }

    /**
     * Returns the details of the missing record identifier error.
     * 
     * @return the details as a list of error description
     */
    public Collection<RecordIdentifierMissingDetail> getDetailsOfRecordIdentifierMissingError() {
        return recordIdentifierMissingDetails;
    }

    /**
     * Create and return list of "SelectItem" objects for given list of "ProcessDTO"s. For each "ProcessDTO" object
     * check whether current user can link to it as a parent process.
     * - If a specific process belongs to a project that is not assigned to the current user, add a hint to message to
     *   the corresponding "SelectItem"
     * - If the user cannot link to a specific process, because the process belongs to a project which is not assigned
     *   to him, and he also lacks the special permission to link to processes in unassigned projects, the corresponding
     *   "SelectItem" is disabled.
     * @param parentCandidates list of "ProcessDTO"s
     * @param maxNumber limit
     * @return list of "SelectItem" objects corresponding to given "ProcessDTO" objects.
     * @throws DAOException when checking whether user can link to given "ProcessDTO"s fails
     */
    public ArrayList<SelectItem> getPotentialParentProcesses(List<ProcessDTO> parentCandidates, int maxNumber)
            throws DAOException {
        ArrayList<SelectItem> possibleParentProcesses = new ArrayList<>();
        for (ProcessDTO process : parentCandidates.subList(0, Math.min(parentCandidates.size(), maxNumber))) {
            SelectItem selectItem = new SelectItem(process.getId().toString(), process.getTitle());
            selectItem.setDisabled(!userMayLinkToParent(process.getId()));
            if (!processInAssignedProject(process.getId())) {
                String problem = Helper.getTranslation("projectNotAssignedToCurrentUser", process.getProject()
                        .getTitle());
                selectItem.setDescription(problem);
                selectItem.setLabel(selectItem.getLabel() + " (" + problem + ")");
            }
            possibleParentProcesses.add(selectItem);
        }
        return possibleParentProcesses;
    }

    /**
     * Check and return whether the process with the provided ID "processId" belongs to a project that is assigned to
     * the current user or not.
     * @param processId ID of the process to check
     * @return whether the process with the provided ID belongs to a project assigned to the current user or not
     * @throws DAOException when retrieving the process with the ID "processId" from the database fails
     */
    public static boolean processInAssignedProject(int processId) throws DAOException {
        Process process = ServiceManager.getProcessService().getById(processId);
        if (Objects.nonNull(process)) {
            return ServiceManager.getUserService().getCurrentUser().getProjects().contains(process.getProject());
        }
        return false;
    }

    /**
     * Check and return whether current user is allowed to link to process with provided ID "processId". For this
     * method to return "true", one of the following two conditions has to be met:
     * 1. the project of the process with the provided ID is assigned to the current user OR
     * 2. the current user has the special permission to link to parent processes of unassigned projects
     * @param processId the ID of the process to which a link is to be established
     * @return whether the current user can link to the process with the provided ID or not
     * @throws DAOException when checking whether the process with provided ID fails
     */
    public static boolean userMayLinkToParent(int processId) throws DAOException {
        return processInAssignedProject(processId)
                || ServiceManager.getSecurityAccessService().hasAuthorityToLinkToProcessesOfUnassignedProjects();
    }

    /**
     * Get and return message informing user that the max number of processes that can be handled in the GUI at the same
     * time has been exceeded and that the import will therefore be delegated to a background task.
     *
     * @param processLevelElement EAD level of elements that are being imported as processes
     * @param numberOfElements number of processes that are to be imported
     * @return max number exceeded message
     */
    public static String getMaximumNumberOfRecordsExceededMessage(String processLevelElement, int numberOfElements) {
        return Helper.getTranslation("createProcessForm.limitExceeded",
                String.valueOf(ConfigCore.getIntParameterOrDefaultValue(ParameterCore
                        .MAX_NUMBER_OF_PROCESSES_FOR_IMPORT_MASK)),
                String.valueOf(numberOfElements),
                processLevelElement);
    }

    /**
     * Create list of TempProcess from XML string stored in given CreateProcessForm 'createProcessForm'.
     *
     * @param createProcessForm CreateProcessForm containing XML string to convert into TempProcesses
     * @return list of TempProcess created from XML string stored in given CreateProcessForm
     * @throws ProcessGenerationException when converting XML string to TempProcesses fails
     * @throws IOException when creating TempProcesses fails
     * @throws InvalidMetadataValueException when creating EAD processes from XML string fails
     * @throws TransformerException when creating TempProcesses fails
     * @throws NoSuchMetadataFieldException when creating EAD processes from XML string fails
     * @throws UnsupportedFormatException when converting XML string to TempProcesses fails
     * @throws XPathExpressionException when creating TempProcesses fails
     * @throws ParserConfigurationException when creating TempProcesses fails
     * @throws URISyntaxException when creating TempProcesses fails
     * @throws SAXException when creating TempProcesses fails
     */
    public LinkedList<TempProcess> processUploadedFile(CreateProcessForm createProcessForm)
            throws ProcessGenerationException, IOException, InvalidMetadataValueException, TransformerException,
            NoSuchMetadataFieldException, UnsupportedFormatException, XPathExpressionException,
            ParserConfigurationException, URISyntaxException, SAXException {
        LinkedList<TempProcess> processes = new LinkedList<>();
        ImportConfiguration importConfiguration = createProcessForm.getCurrentImportConfiguration();
        DataRecord externalRecord = XMLUtils.createRecordFromXMLElement(createProcessForm.getXmlString(),
                importConfiguration);
        if (MetadataFormat.EAD.name().equals(importConfiguration.getMetadataFormat())) {
            LinkedList<TempProcess> eadProcesses = parseImportedEADCollection(externalRecord, importConfiguration,
                    createProcessForm.getProject().getId(), createProcessForm.getTemplate().getId(),
                    createProcessForm.getSelectedEadLevel(), createProcessForm.getSelectedParentEadLevel());
            createProcessForm.setChildProcesses(new LinkedList<>(eadProcesses.subList(1, eadProcesses.size())));
            processes = new LinkedList<>(Collections.singletonList(eadProcesses.get(0)));
        } else {
            Document internalDocument = convertDataRecordToInternal(externalRecord, importConfiguration, false);
            TempProcess tempProcess = createTempProcessFromDocument(importConfiguration, internalDocument,
                    createProcessForm.getTemplate().getId(), createProcessForm.getProject().getId());
            processes.add(tempProcess);
            Collection<String> higherLevelIdentifier = createProcessForm.getRulesetManagement()
                    .getFunctionalKeys(FunctionalMetadata.HIGHERLEVEL_IDENTIFIER);
            if (!higherLevelIdentifier.isEmpty()) {
                String parentID = getParentID(internalDocument, higherLevelIdentifier.toArray()[0]
                        .toString(), importConfiguration.getParentElementTrimMode());
                checkForParent(parentID, createProcessForm.getTemplate().getRuleset(),
                        createProcessForm.getProject().getId());
                if (Objects.isNull(getParentTempProcess())) {
                    TempProcess parentTempProcess = extractParentRecordFromFile(internalDocument, createProcessForm);
                    if (Objects.nonNull(parentTempProcess)) {
                        processes.add(parentTempProcess);
                    }
                }
            }
        }
        return processes;
    }

    /**
     * Retrieve and return label of metadata with given key 'metadataKey'.
     *
     * @param ruleset Ruleset from which metadata label is retrieved
     * @param metadataKey key of metadata for which label ir retrieved
     * @return label of metadata
     * @throws IOException if ruleset file could not be read
     */
    public String getMetadataTranslation(Ruleset ruleset, String metadataKey) throws IOException {
        RulesetManagementInterface managementInterface = ServiceManager.getRulesetService().openRuleset(ruleset);
        User user = ServiceManager.getUserService().getCurrentUser();
        String metadataLanguage = user.getMetadataLanguage();
        List<Locale.LanguageRange> languages = Locale.LanguageRange.parse(metadataLanguage.isEmpty()
                ? Locale.ENGLISH.getCountry() : metadataLanguage);
        return managementInterface.getTranslationForKey(metadataKey, languages).orElse(metadataKey);
    }

    private TempProcess extractParentRecordFromFile(Document internalDocument, CreateProcessForm createProcessForm)
            throws XPathExpressionException, UnsupportedFormatException, URISyntaxException, IOException,
            ParserConfigurationException, SAXException, ProcessGenerationException, TransformerException {
        Collection<String> higherLevelIdentifier = createProcessForm.getRulesetManagement()
                .getFunctionalKeys(FunctionalMetadata.HIGHERLEVEL_IDENTIFIER);

        if (!higherLevelIdentifier.isEmpty()) {
            ImportConfiguration importConfiguration = createProcessForm.getCurrentImportConfiguration();
            ImportService importService = ServiceManager.getImportService();
            String parentID = importService.getParentID(internalDocument, higherLevelIdentifier.toArray()[0].toString(),
                    importConfiguration.getParentElementTrimMode());
            if (Objects.nonNull(parentID) && Objects.nonNull(importConfiguration.getParentMappingFile())) {
                Document internalParentDocument = importService.convertDataRecordToInternal(
                        XMLUtils.createRecordFromXMLElement(createProcessForm.getXmlString(), importConfiguration),
                        importConfiguration, true);
                return importService.createTempProcessFromDocument(importConfiguration, internalParentDocument,
                        createProcessForm.getTemplate().getId(), createProcessForm.getProject().getId());
            }
        }
        return null;
    }
}
