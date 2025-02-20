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

package org.kitodo.production.services.dataeditor;

import static org.kitodo.constants.StringConstants.EDIT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.faces.model.SelectItem;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataeditor.DataEditorInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.ComplexMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.Reimport;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.externaldatamanagement.ImportConfigurationType;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.MetadataException;
import org.kitodo.exceptions.NoRecordFoundException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.exceptions.UnsupportedFormatException;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.forms.createprocess.ProcessFieldedMetadata;
import org.kitodo.production.forms.dataeditor.DataEditorForm;
import org.kitodo.production.forms.dataeditor.StructurePanel;
import org.kitodo.production.forms.dataeditor.StructureTreeNode;
import org.kitodo.production.forms.dataeditor.StructureTreeOperations;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.MetadataComparison;
import org.kitodo.production.helper.ProcessHelper;
import org.kitodo.production.helper.TempProcess;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.serviceloader.KitodoServiceLoader;
import org.primefaces.model.TreeNode;
import org.xml.sax.SAXException;

public class DataEditorService {

    private static final Logger logger = LogManager.getLogger(DataEditorService.class);

    /**
     * Reads the data of a given file in xml format. The format of that file
     * needs to be the corresponding to the one which is referenced by the data
     * editor module as data format module.
     *
     * @param xmlFileUri
     *            The path to the metadata file as URI.
     */
    public void readData(URI xmlFileUri) throws IOException {
        DataEditorInterface dataEditor = loadDataEditorModule();
        URI xsltFile = getXsltFileFromConfig();
        dataEditor.readData(xmlFileUri, xsltFile);
    }

    private DataEditorInterface loadDataEditorModule() {
        KitodoServiceLoader<DataEditorInterface> serviceLoader = new KitodoServiceLoader<>(DataEditorInterface.class);
        return serviceLoader.loadModule();
    }

    private URI getXsltFileFromConfig() {
        String path = getXsltFolder();
        String file = ConfigCore.getParameter(ParameterCore.XSLT_FILENAME_METADATA_TRANSFORMATION);
        return Paths.get(path + file).toUri();
    }

    private String getXsltFolder() {
        return ConfigCore.getParameter(ParameterCore.DIR_XSLT);
    }

    /**
     * Retrieve and return title value from given IncludedStructuralElement.
     *
     * @param element IncludedStructuralElement for which the title value is returned.
     * @param metadataTitleKey as a String that its value will be displayed.
     * @return title value of given element or an empty string
     */
    public static String getTitleValue(LogicalDivision element, String metadataTitleKey) {
        String[] metadataPath = metadataTitleKey.split("@");
        int lastIndex = metadataPath.length - 1;
        Collection<Metadata> metadata = element.getMetadata();
        for (int i = 0; i < lastIndex; i++) {
            final String metadataKey = metadataPath[i];
            metadata = metadata.stream()
                    .filter(currentMetadata -> Objects.equals(currentMetadata.getKey(), metadataKey))
                    .filter(MetadataGroup.class::isInstance).map(MetadataGroup.class::cast)
                    .flatMap(metadataGroup -> metadataGroup.getMetadata().stream())
                    .collect(Collectors.toList());
        }
        return metadata.stream()
                .filter(currentMetadata -> Objects.equals(currentMetadata.getKey(), metadataPath[lastIndex]))
                .filter(MetadataEntry.class::isInstance).map(MetadataEntry.class::cast)
                .map(MetadataEntry::getValue)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse("");
    }

    /**
     * Determine and return which metadata can be added to a specific MetadataGroup.
     *
     * @param ruleset ruleset of the process
     * @param metadataNode TreeNode containing MetadataGroup to check
     * @return List of select items representing addable metadata types
     */
    public static List<SelectItem> getAddableMetadataForGroup(Ruleset ruleset, TreeNode metadataNode) {
        ProcessFieldedMetadata fieldedMetadata = ((ProcessFieldedMetadata) metadataNode.getData());
        ComplexMetadataViewInterface metadataView = fieldedMetadata.getMetadataView();
        List<SelectItem> addableMetadata = new ArrayList<>();
        for (MetadataViewInterface keyView : metadataView.getAddableMetadata(fieldedMetadata.getChildMetadata(),
                fieldedMetadata.getAdditionallySelectedFields())) {
            addableMetadata.add(
                    new SelectItem(keyView.getId(), keyView.getLabel(),
                            keyView instanceof SimpleMetadataViewInterface
                                    ? ((SimpleMetadataViewInterface) keyView).getInputType().toString()
                                    : "dataTable"));
        }
        return sortMetadataList(addableMetadata, ruleset);
    }

    /**
     * Determine and return which metadata can be added to the currently selected IncludedStructuralElement.
     * @param dataEditor DataEditorForm instance used to determine addable metadata types
     * @param currentElement whether addable metadata should be determined for currently selected
     *                       IncludedStructuralElement or not (if "false", it is determined for a new
     *                       IncludedStructuralElement added by "AddDocStrucElementDialog"!)
     * @param metadataNodes TreeNodes containing the metadata already assigned to the current structure element
     * @param structureType type of the new structure to be added and for which addable metadata is to be determined
     * @return List of select items representing addable metadata types
     */
    public static List<SelectItem> getAddableMetadataForStructureElement(DataEditorForm dataEditor,
                                                                         boolean currentElement,
                                                                         List<TreeNode> metadataNodes,
                                                                         String structureType,
                                                                         boolean isLogicalStructure) {
        List<SelectItem> addableMetadata = new ArrayList<>();
        Collection<Metadata> existingMetadata = Collections.emptyList();
        StructuralElementViewInterface structureView;
        try {
            if (currentElement) {
                structureView = getStructuralElementView(dataEditor);
                existingMetadata = getExistingMetadataRows(metadataNodes);
            } else {
                structureView = dataEditor.getRulesetManagement()
                        .getStructuralElementView(structureType,
                                dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
            }
            Collection<String> additionalFields = isLogicalStructure ? dataEditor.getMetadataPanel()
                    .getLogicalMetadataTable().getAdditionallySelectedFields() : dataEditor.getMetadataPanel()
                    .getPhysicalMetadataTable().getAdditionallySelectedFields();
            if (Objects.nonNull(structureView)) {
                addableMetadata = getAddableMetadataForStructureElement(structureView, existingMetadata,
                        additionalFields, dataEditor.getProcess().getRuleset());
            }
        } catch (InvalidMetadataValueException e) {
            Helper.setErrorMessage(e);
        }
        return addableMetadata;
    }

    /**
     * Determine and return list of metadata that can be added to currently selected structure element.
     *
     * @param dataEditor DataEditorForm instance used to determine list of addable metadata types
     * @return List of select items representing addable metadata types
     */
    public static List<SelectItem> getAddableMetadataForStructureElement(DataEditorForm dataEditor) {
        return getAddableMetadataForStructureElement(dataEditor,
                true, dataEditor.getMetadataPanel().getLogicalMetadataRows().getChildren(), null, true);
    }

    /**
     * Determine and return which metadata can be added to the currently selected IncludedStructuralElement.
     * @param structureView StructureElementViewInterface corresponding to structure element currently selected
     * @param existingMetadata existing Metadata of the structureView
     * @param additionalFields additionally added Metadata of the structureView
     * @param ruleset ruleset
     * @return List of select items representing addable metadata types
     */
    public static List<SelectItem> getAddableMetadataForStructureElement(StructuralElementViewInterface structureView,
                                                                         Collection<Metadata> existingMetadata,
                                                                         Collection<String> additionalFields, Ruleset ruleset) {
        List<SelectItem> addableMetadata = new ArrayList<>();
        Collection<MetadataViewInterface> viewInterfaces = structureView
                .getAddableMetadata(existingMetadata, additionalFields);
        for (MetadataViewInterface keyView : viewInterfaces) {
            addableMetadata.add(
                    new SelectItem(keyView.getId(), keyView.getLabel(),
                            keyView instanceof SimpleMetadataViewInterface
                                    ? ((SimpleMetadataViewInterface) keyView).getInputType().toString()
                                    : "dataTable"));
        }
        return sortMetadataList(addableMetadata, ruleset);
    }

    /**
     * Determine and return list of metadata that can be added to currently selected media unit.
     *
     * @param dataEditor DataEditorForm instance used to determine list of addable metadata types
     * @return List of select items representing addable metadata types
     */
    public static List<SelectItem> getAddableMetadataForMediaUnit(DataEditorForm dataEditor) {
        return getAddableMetadataForStructureElement(dataEditor,
                true, dataEditor.getMetadataPanel().getPhysicalMetadataRows().getChildren(), null, false);
    }

    /**
     * Determine and return StructureElementViewInterface corresponding to structure element currently selected or to be
     * added via AddDocStructTypeDialog.
     *
     * @param dataEditor DataEditorForm instance used to determine StructureElementViewInterface
     * @return StructureElementViewInterface corresponding to structure element currently selected or to be added
     */
    public static StructuralElementViewInterface getStructuralElementView(DataEditorForm dataEditor) {
        Optional<LogicalDivision> selectedStructure = dataEditor.getSelectedStructure();
        if (selectedStructure.isPresent()) {
            return dataEditor.getRulesetManagement()
                    .getStructuralElementView(
                            selectedStructure.get().getType(),
                            dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
        }

        TreeNode selectedNode = dataEditor.getStructurePanel().getSelectedLogicalNodeIfSingle();
        PhysicalDivision physicalDivision = StructureTreeOperations.getPhysicalDivisionFromTreeNode(selectedNode);
        
        if (Objects.isNull(physicalDivision)) {
            // selected node is not a physical division
            return null;
        }
        
        return dataEditor.getRulesetManagement().getStructuralElementView(
            physicalDivision.getType(),
            dataEditor.getAcquisitionStage(), 
            dataEditor.getPriorityList()
        );
    }

    /**
     * Get existing Metadata in metadataTreeNodes.
     * @param metadataTreeNodes as a List of TreeNode
     * @return the existing metadata
     */
    public static Collection<Metadata> getExistingMetadataRows(List<TreeNode> metadataTreeNodes) throws InvalidMetadataValueException {
        Collection<Metadata> existingMetadataRows = new ArrayList<>();

        for (TreeNode metadataNode : metadataTreeNodes) {
            if (metadataNode.getData() instanceof ProcessDetail) {
                try {
                    existingMetadataRows.addAll(((ProcessDetail) metadataNode.getData()).getMetadata(false));
                } catch (NullPointerException e) {
                    logger.error(e);
                }
            }
        }
        return existingMetadataRows;
    }

    /**
     * Get allowed substructural elements as sorted list of select items.
     *
     * @param divisionView
     *         The division View
     * @param ruleset
     *         The ruleset
     * @return Sorted list of select items
     */
    public static List<SelectItem> getSortedAllowedSubstructuralElements(
            StructuralElementViewInterface divisionView, Ruleset ruleset) {
        List<SelectItem> selectItems = new ArrayList<>();
        for (Map.Entry<String, String> entry : divisionView.getAllowedSubstructuralElements().entrySet()) {
            selectItems.add(new SelectItem(entry.getKey(), entry.getValue()));
        }
        sortMetadataList(selectItems, ruleset);
        return selectItems;
    }

    /**
     * Sort a metadata list alphabetically if the 'orderMetadataByRuleset' parameter of the ruleset not set as true.
     * @param itemList as a List of SelectItem
     * @param ruleset as a Ruleset
     * @return itemList
     */
    public static List<SelectItem> sortMetadataList(List<SelectItem> itemList, Ruleset ruleset) {
        if (!(itemList.isEmpty() || ruleset.isOrderMetadataByRuleset())) {
            itemList.sort(Comparator.comparing(SelectItem::getLabel));
        }
        return itemList;
    }

    /**
     * Get the view of base media by comparing media files of tree nodes.
     *
     * @param treeNodes
     *         The tree nodes
     * @param mediaFiles
     *         The media files to compare too
     * @return View or null
     */
    public static View getViewOfBaseMediaByMediaFiles(List<TreeNode> treeNodes, Map<MediaVariant, URI> mediaFiles) {
        for (TreeNode treeNode : treeNodes) {
            if (StructurePanel.VIEW_NODE_TYPE.equals(
                    treeNode.getType()) && treeNode.getData() instanceof StructureTreeNode) {
                StructureTreeNode structureMediaTreeNode = (StructureTreeNode) treeNode.getData();
                if (structureMediaTreeNode.getDataObject() instanceof View) {
                    View view = (View) structureMediaTreeNode.getDataObject();
                    if (view.getPhysicalDivision().getMediaFiles().equals(mediaFiles)) {
                        return view;
                    }
                }
            }
            if (treeNode.getChildCount() > 0) {
                View view = getViewOfBaseMediaByMediaFiles(treeNode.getChildren(), mediaFiles);
                if (Objects.nonNull(view)) {
                    return view;
                }
            }
        }
        return null;
    }

    /**
     * Check and return whether catalog metadata of given process can be updated or not.
     * Conditions that must be met:
     * - process has an import configuration of type 'OPAC_SEARCH'
     * - process functional metadata of type 'recordIdentifier'.
     * - selected node is root of logical structure
     *
     * @param process Process for which check is performed
     * @param workpiece Workpiece of process
     * @param selectedNode currently selected logical structure node
     * @return whether catalog metadata update is supported or not
     * @throws IOException when retrieving functional metadata of type 'recordIdentifier' from process' ruleset fails
     */
    public static boolean canUpdateCatalogMetadata(Process process, Workpiece workpiece, TreeNode selectedNode) throws IOException {
        return (Objects.nonNull(getRecordIdentifierValueOfProcess(process, workpiece))
                && Objects.nonNull(selectedNode)
                && selectedNode.getData() instanceof StructureTreeNode
                && Objects.equals(((StructureTreeNode) selectedNode.getData()).getDataObject(), workpiece.getLogicalStructure())
                && Objects.nonNull(process.getImportConfiguration())
                && ImportConfigurationType.OPAC_SEARCH.name().equals(process.getImportConfiguration().getConfigurationType()));
    }

    /**
     * Re-imports catalog metadata of given process and return list of resulting metadata comparison that are displayed
     * to the user.
     * @param process Process for which metadata update is performed
     * @param workpiece Workpiece of given process
     * @param oldMetadataSet Set containing old metadata
     * @return list of metadata comparisons
     */
    public static List<MetadataComparison> reimportCatalogMetadata(Process process, Workpiece workpiece,
                                                                   HashSet<Metadata> oldMetadataSet,
                                                                   List<Locale.LanguageRange> languages,
                                                                   String selectedDivisionType)
            throws IOException, UnsupportedFormatException, XPathExpressionException, NoRecordFoundException,
            ProcessGenerationException, ParserConfigurationException, URISyntaxException, InvalidMetadataValueException,
            TransformerException, NoSuchMetadataFieldException, SAXException {
        String recordID = getRecordIdentifierValueOfProcess(process, workpiece);
        ImportConfiguration importConfig = process.getImportConfiguration();
        if (Objects.isNull(recordID) || Objects.isNull(importConfig)) {
            String errorMessage = String.format("Unable to update metadata of process %d; "
                    + "(either import configuration or record identifier are missing)", process.getId());
            throw new MetadataException(errorMessage, null);
        }
        TempProcess updatedProcess = ServiceManager.getImportService().importTempProcess(importConfig, recordID,
                process.getTemplate().getId(), process.getProject().getId());
        if (Objects.isNull(updatedProcess)) {
            throw new ProcessGenerationException("Unable to re-import data record for metadata update");
        } else {
            RulesetManagementInterface ruleset = ServiceManager.getRulesetService().openRuleset(process.getRuleset());
            for (Metadata metadata : updatedProcess.getWorkpiece().getLogicalStructure().getMetadata()) {
                ProcessHelper.setMetadataDomain(metadata, ruleset);
            }
            ProcessHelper.generateAtstslFields(updatedProcess, Collections.emptyList(), EDIT, false);
            return initializeMetadataComparisons(process, oldMetadataSet,
                updatedProcess.getWorkpiece().getLogicalStructure().getMetadata(), languages, selectedDivisionType);
        }
    }

    /**
     * Load and return functional metadata of type 'recordIdentifier' from current process.
     *
     * @param process Process for which recordIdentifier is retrieved
     * @param workpiece Workpiece of process
     * @return value of 'recordIdentifier'
     * @throws IOException when opening process' ruleset fails
     */
    public static String getRecordIdentifierValueOfProcess(Process process, Workpiece workpiece) throws IOException {
        RulesetManagementInterface ruleset = ServiceManager.getRulesetService().openRuleset(process.getRuleset());
        HashSet<Metadata> processMetadata = workpiece.getLogicalStructure().getMetadata();
        for (String recordIdentifierMetadata : ruleset.getFunctionalKeys(FunctionalMetadata.RECORD_IDENTIFIER)) {
            Optional<Metadata> recordIdMetadata = processMetadata.stream().filter(pm -> recordIdentifierMetadata
                    .equals(pm.getKey())).findFirst();
            if (recordIdMetadata.isPresent() && recordIdMetadata.get() instanceof MetadataEntry) {
                return ((MetadataEntry)recordIdMetadata.get()).getValue();
            }
        }
        return null;
    }

    private static List<MetadataComparison> initializeMetadataComparisons(Process process,
                                                                          HashSet<Metadata> oldMetadata,
                                                                          HashSet<Metadata> newMetadata,
                                                                          List<Locale.LanguageRange> languages,
                                                                          String logicalDivisionType)
            throws IOException {
        RulesetManagementInterface ruleset = ServiceManager.getRulesetService().openRuleset(process.getRuleset());
        List<MetadataComparison> metadataComparisons = new LinkedList<>();
        HashSet<String> metadataKeyCollection = oldMetadata.stream().map(Metadata::getKey).collect(Collectors.toCollection(HashSet::new));
        metadataKeyCollection.addAll(newMetadata.stream().map(Metadata::getKey).collect(Collectors.toCollection(HashSet::new)));
        StructuralElementViewInterface divisionView = ruleset.getStructuralElementView(logicalDivisionType, EDIT, languages);
        Map<String, MetadataViewInterface> allowedMetadata = divisionView.getAllowedMetadata().stream()
                .collect(Collectors.toMap(MetadataViewInterface::getId, item -> item));
        for (String metadataKey : metadataKeyCollection) {
            // determine default mode for metadata from ruleset! (e.g. "keep", "replace", etc.)
            Reimport selectionMode = ruleset.getMetadataReimport(metadataKey, EDIT);
            MetadataViewInterface metadataView = allowedMetadata.get(metadataKey);
            // only add metadata comparison when there is a difference between old and new values!
            HashSet<Metadata> oldValues = filterEntries(metadataKey, oldMetadata);
            HashSet<Metadata> newValues = filterEntries(metadataKey, newMetadata);
            if (!Objects.equals(oldValues, newValues) && Objects.nonNull(metadataView)) {
                if (newValues.isEmpty()) {
                    selectionMode = Reimport.KEEP;
                }
                metadataComparisons.add(new MetadataComparison(metadataKey, oldValues, newValues, metadataView,
                        selectionMode));
            }
        }
        return metadataComparisons;
    }

    private static HashSet<Metadata> filterEntries(String key, HashSet<Metadata> entries) {
        return entries.stream()
                .filter(md -> key.equals(md.getKey()))
                .filter(md -> StringUtils.isNotBlank(metadataToString(md)))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Convert given metadata to string and return it. In contrast to 'toString' methods of 'MetadataEntry' and
     * 'MetadataGroup', this method only returns the value of the metadata, without the domain.
     * If the given metadata is of type 'MetadataGroup', the values of the groups entries are concatenated and returned
     * instead.
     * Note: metadata elements of the group that are 'MetadataGroup's themselves are intentionally not traversed again,
     * so this function does not work recursively.
     * @param metadata Metadata to be converted to string
     * @return String representation of given metadata
     */
    public static String metadataToString(Metadata metadata) {
        if (metadata instanceof MetadataEntry) {
            return ((MetadataEntry) metadata).getValue();
        } else if (metadata instanceof MetadataGroup) {
            StringBuilder groupString = new StringBuilder();
            for (Metadata groupMetadata : ((MetadataGroup) metadata).getMetadata().stream()
                    .sorted(Comparator.comparing(Metadata::getKey)).collect(Collectors.toList())) {
                if (groupMetadata instanceof MetadataEntry) {
                    groupString.append(((MetadataEntry) groupMetadata).getValue());
                } else {
                    groupString.append("...");
                }
            }
            return groupString.toString();
        } else {
            return "";
        }
    }

    /**
     * Update metadata of logical root element in given Workpiece by applying given metadata comparisons and selections
     * old and new values therein.
     * @param workpiece Workpiece to which metadata update is applied
     * @param comparisons list of metadata comparisons used for the update
     */
    public static void updateMetadataWithNewValues(Workpiece workpiece, List<MetadataComparison> comparisons) {
        for (MetadataComparison comparison : comparisons) {
            switch (comparison.getSelection()) {
                case ADD:
                    // extend existing values with new values
                    workpiece.getLogicalStructure().getMetadata().addAll(comparison.getNewValues());
                    break;
                case REPLACE:
                    // replace existing values with new values
                    workpiece.getLogicalStructure().getMetadata().removeAll(comparison.getOldValues());
                    workpiece.getLogicalStructure().getMetadata().addAll(comparison.getNewValues());
                    break;
                default:
                    // keep existing values and discard new values
                    logger.info("Keep existing values for metadata {}", comparison.getMetadataKey());
            }
        }
    }
}
