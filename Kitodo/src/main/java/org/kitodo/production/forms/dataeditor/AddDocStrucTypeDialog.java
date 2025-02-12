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

import static org.kitodo.production.metadata.InsertionPosition.AFTER_CURRENT_ELEMENT;
import static org.kitodo.production.metadata.InsertionPosition.BEFORE_CURRENT_ELEMENT;
import static org.kitodo.production.metadata.InsertionPosition.CURRENT_POSITION;
import static org.kitodo.production.metadata.InsertionPosition.FIRST_CHILD_OF_CURRENT_ELEMENT;
import static org.kitodo.production.metadata.InsertionPosition.LAST_CHILD_OF_CURRENT_ELEMENT;
import static org.kitodo.production.metadata.InsertionPosition.PARENT_OF_CURRENT_ELEMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.faces.model.SelectItem;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.UnknownTreeNodeDataException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.metadata.InsertionPosition;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.dataeditor.DataEditorService;
import org.primefaces.PrimeFaces;
import org.primefaces.model.TreeNode;

/**
 * Backing bean for the add doc struc type dialog of the metadata editor.
 */
public class AddDocStrucTypeDialog {
    private static final Logger logger = LogManager.getLogger(AddDocStrucTypeDialog.class);

    private final DataEditorForm dataEditor;
    private List<SelectItem> selectionItemsForChildren;
    private List<SelectItem> selectionItemsForParent;
    private List<SelectItem> selectionItemsForSiblings;
    private String docStructAddTypeSelectionSelectedItem;
    private List<SelectItem> docStructPositionSelectionItems;
    private InsertionPosition selectedDocStructPosition = LAST_CHILD_OF_CURRENT_ELEMENT;
    private int elementsToAddSpinnerValue;
    private String inputMetaDataValue = "";
    private LinkedList<LogicalDivision> parents;
    private List<SelectItem> addableMetadata;
    private String selectedMetadata = "";
    private String selectFirstPageOnAddNode;
    private String selectLastPageOnAddNode;
    private List<SelectItem> selectPageOnAddNodeItems;
    private List<View> preselectedViews;
    private String processNumber = "";
    private Process selectedProcess = null;
    private List<Process> processes = Collections.emptyList();
    private boolean linkSubDialogVisible = false;
    private static final String PREVIEW_MODE = "preview";
    private static final String LIST_MODE = "list";
    private TreeNode previouslySelectedLogicalNode;

    /**
     * Backing bean for the add doc struc type dialog of the metadata editor.
     *
     * @see "WEB-INF/templates/includes/metadataEditor/dialogs/addDocStrucType.xhtml"
     */
    AddDocStrucTypeDialog(DataEditorForm dataEditor) {
        this.dataEditor = dataEditor;
    }

    /**
     * Check and return if sub dialog is visible.
     *
     * @return sub dialog visibility
     */
    public boolean isLinkSubDialogVisible() {
        return this.linkSubDialogVisible;
    }

    /**
     * Set sub dialog visibility.
     *
     * @param visible whether sub dialog is visible or not
     */
    public void setLinkSubDialogVisible(boolean visible) {
        this.linkSubDialogVisible = visible;
    }

    /**
     * Add structure element.
     */
    public void addDocStruc(boolean preview) {
        try {
            dataEditor.getMetadataPanel().preserve();
        } catch (InvalidMetadataValueException | NoSuchMetadataFieldException e) {
            logger.info(e.getMessage());
        }
        if (this.elementsToAddSpinnerValue > 1) {
            this.addMultiDocStruc();
        } else {
            this.addSingleDocStruc(preview);
        }
        if (preview && (!(StringUtils.isEmpty(selectFirstPageOnAddNode)
                || StringUtils.isEmpty(this.selectLastPageOnAddNode))
                || Objects.nonNull(this.preselectedViews) && !this.preselectedViews.isEmpty())) {
            dataEditor.getGalleryPanel().setGalleryViewMode(PREVIEW_MODE);
        } else {
            dataEditor.getGalleryPanel().setGalleryViewMode(LIST_MODE);
        }
        try {
            dataEditor.getPaginationPanel().show();
        } catch (UnknownTreeNodeDataException e) {
            Helper.setErrorMessage(e.getMessage());
        }
    }

    /**
     * This method is invoked if the user clicks on the add multi doc struc
     * submit btn command button.
     */
    private void addMultiDocStruc() {
        Optional<LogicalDivision> selectedStructure = dataEditor.getSelectedStructure();
        if (selectedStructure.isPresent()) {
            if (!selectedMetadata.isEmpty()) {
                MetadataViewInterface metadataView = getMetadataViewFromKey(
                    docStructAddTypeSelectionSelectedItem, selectedMetadata);
                MetadataEditor.addMultipleStructuresWithMetadata(elementsToAddSpinnerValue,
                    docStructAddTypeSelectionSelectedItem, dataEditor.getWorkpiece(), selectedStructure.get(),
                    selectedDocStructPosition, metadataView, inputMetaDataValue);
            } else {
                MetadataEditor.addMultipleStructures(elementsToAddSpinnerValue, docStructAddTypeSelectionSelectedItem,
                    dataEditor.getWorkpiece(), selectedStructure.get(), selectedDocStructPosition);
            }
            dataEditor.refreshStructurePanel();
            dataEditor.getPaginationPanel().show();
        }
    }

    /**
     * This method is invoked if the user clicks on the add single doc struc
     * submit btn command button.
     */
    private void addSingleDocStruc(boolean selectViews) {
        Optional<LogicalDivision> selectedStructure = dataEditor.getSelectedStructure();
        if (selectedStructure.isPresent()) {
            LogicalDivision newStructure = MetadataEditor.addLogicalDivision(docStructAddTypeSelectionSelectedItem,
                    dataEditor.getWorkpiece(), selectedStructure.get(),
                    selectedDocStructPosition, getViewsToAdd());
            dataEditor.getSelectedMedia().clear();
            if (selectViews) {
                for (View view : getViewsToAdd()) {
                    dataEditor.getSelectedMedia().add(new ImmutablePair<>(view.getPhysicalDivision(), newStructure));
                }
            }
            dataEditor.refreshStructurePanel();
            
            try {
                dataEditor.updateSelection(Collections.emptyList(), Collections.singletonList(newStructure));
            } catch (NoSuchMetadataFieldException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            }
            dataEditor.refreshStructurePanel();
            
            List<Pair<PhysicalDivision, LogicalDivision>> selectedMedia = this.dataEditor.getSelectedMedia().stream()
                    .sorted(Comparator.comparingInt(p -> p.getLeft().getOrder()))
                    .collect(Collectors.toList());
            Collections.reverse(selectedMedia);
            this.dataEditor.setSelectedMedia(selectedMedia);
            dataEditor.getPaginationPanel().show();
        }
    }

    /**
     * Returns a List of SelectItems representing the available types for the element to be created.
     *
     * @return the selected items for the docStructAddTypeSelection
     */
    public List<SelectItem> getDocStructAddTypeSelectionItems() {
        if (Objects.isNull(selectedDocStructPosition)) {
            return Collections.emptyList();
        }
        switch (selectedDocStructPosition) {
            case AFTER_CURRENT_ELEMENT:
            case BEFORE_CURRENT_ELEMENT:
                return selectionItemsForSiblings;
            case CURRENT_POSITION:
            case FIRST_CHILD_OF_CURRENT_ELEMENT:
            case LAST_CHILD_OF_CURRENT_ELEMENT:
                return selectionItemsForChildren;
            case PARENT_OF_CURRENT_ELEMENT:
                return selectionItemsForParent;
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Return selected doc struct type.
     *
     * @return selected doc struct type
     */
    public String getDocStructAddTypeSelectionSelectedItem() {
        return StringUtils.isBlank(docStructAddTypeSelectionSelectedItem) ? null : docStructAddTypeSelectionSelectedItem;
    }

    /**
     * Sets the selected item of the docStructAddTypeSelection drop-down menu.
     *
     * @param docStructAddTypeSelectionSelectedItem
     *            selected item to set
     */
    public void setDocStructAddTypeSelectionSelectedItem(String docStructAddTypeSelectionSelectedItem) {
        this.docStructAddTypeSelectionSelectedItem = docStructAddTypeSelectionSelectedItem;
    }

    /**
     * Returns the items of the docStructPositionSelection drop-down menu.
     *
     * @return the items of the docStructPositionSelection
     */
    public List<SelectItem> getDocStructPositionSelectionItems() {
        return docStructPositionSelectionItems;
    }

    /**
     * Returns the selected item of the docStructPositionSelection drop-down
     * menu.
     *
     * @return the selected item of the docStructPositionSelection
     */
    public InsertionPosition getSelectedDocStructPosition() {
        if (!docStructPositionSelectionItems.stream()
                .map(SelectItem::getValue)
                .collect(Collectors.toList())
                .contains(selectedDocStructPosition)) {
            setSelectedDocStructPosition(LAST_CHILD_OF_CURRENT_ELEMENT);
        }
        return selectedDocStructPosition;
    }

    /**
     * Sets the selected item of the docStructPositionSelection drop-down menu.
     *
     * @param selectedDocStructPosition
     *            selected item to set
     */
    public void setSelectedDocStructPosition(InsertionPosition selectedDocStructPosition) {
        this.selectedDocStructPosition = selectedDocStructPosition;
    }

    /**
     * Returns the value of the elementsToAddSpinner number input box.
     *
     * @return the value of the elementsToAddSpinner
     */
    public int getElementsToAddSpinnerValue() {
        return elementsToAddSpinnerValue;
    }

    /**
     * Sets the value of the elementsToAddSpinner number input box.
     *
     * @param elementsToAddSpinnerValue
     *            value to set
     */
    public void setElementsToAddSpinnerValue(int elementsToAddSpinnerValue) {
        this.elementsToAddSpinnerValue = elementsToAddSpinnerValue;
    }

    /**
     * Returns the value of the inputMetaData text box.
     *
     * @return the value of the inputMetaData
     */
    public String getInputMetaDataValue() {
        return inputMetaDataValue;
    }

    /**
     * Sets the value of the inputMetaData text box.
     *
     * @param inputMetaDataValue
     *            value to set
     */
    public void setInputMetaDataValue(String inputMetaDataValue) {
        this.inputMetaDataValue = inputMetaDataValue;
    }

    /**
     * Returns the items of the selectAddableMetadataTypes drop-down menu.
     *
     * @return the items of the selectAddableMetadataTypes
     */
    public List<SelectItem> getAddableMetadata() {
        return addableMetadata;
    }

    /**
     * Returns the selected item of the selectAddableMetadataTypes drop-down
     * menu.
     *
     * @return the selected item of the selectAddableMetadataTypes
     */
    public String getSelectedMetadata() {
        return selectedMetadata;
    }

    /**
     * Sets the selected item of the selectAddableMetadataTypes drop-down menu.
     *
     * @param selectedMetadata
     *            selected item to set
     */
    public void setSelectedMetadata(String selectedMetadata) {
        this.selectedMetadata = selectedMetadata;
        dataEditor.getMetadataPanel().setAddMetadataKeySelectedItem(selectedMetadata);
    }

    /**
     * Returns the selected item of the selectFirstPageOnAddNode drop-down menu.
     *
     * @return the selected item of the selectFirstPageOnAddNode
     */
    public String getSelectFirstPageOnAddNode() {
        return selectFirstPageOnAddNode;
    }

    /**
     * Sets the selected item of the selectFirstPageOnAddNode drop-down menu.
     *
     * @param selectFirstPageOnAddNode
     *            selected item to set
     */
    public void setSelectFirstPageOnAddNode(String selectFirstPageOnAddNode) {
        this.selectFirstPageOnAddNode = selectFirstPageOnAddNode;
    }

    /**
     * Returns the selected item of the selectLastPageOnAddNode drop-down menu.
     *
     * @return the selected item of the selectLastPageOnAddNode
     */
    public String getSelectLastPageOnAddNode() {
        return selectLastPageOnAddNode;
    }

    /**
     * Sets the selected item of the selectLastPageOnAddNode drop-down menu.
     *
     * @param selectLastPageOnAddNode
     *            selected item to set
     */
    public void setSelectLastPageOnAddNode(String selectLastPageOnAddNode) {
        this.selectLastPageOnAddNode = selectLastPageOnAddNode;
    }

    /**
     * Returns the items of the selectFirstPageOnAddNode and
     * selectLastPageOnAddNode drop-down menus.
     *
     * @return the items of the selectFirstPageOnAddNode and
     *         selectLastPageOnAddNode
     */
    public List<SelectItem> getSelectPageOnAddNodeItems() {
        return selectPageOnAddNodeItems;
    }

    private List<View> getViewsToAdd() {
        if (Objects.nonNull(preselectedViews) && !preselectedViews.isEmpty()) {
            return preselectedViews;
        }
        try {
            int firstPage = Integer.parseInt(selectFirstPageOnAddNode);
            int lastPage = Integer.parseInt(selectLastPageOnAddNode);
            return dataEditor.getEditPagesDialog().getViewsToAdd(firstPage, lastPage);
        } catch (NumberFormatException e) {
            // user didn’t select both start and end page
            logger.catching(Level.TRACE, e);
            return Collections.emptyList();
        }
    }

    /**
     * Prepare popup dialog by retrieving available insertion positions and doc struct types for selected element.
     */
    public void prepare() {
        elementsToAddSpinnerValue = 1;
        checkSelectedLogicalNode();
        Optional<LogicalDivision> selectedStructure = dataEditor.getSelectedStructure();
        if (selectedStructure.isPresent()) {
            this.parents = MetadataEditor.getAncestorsOfLogicalDivision(selectedStructure.get(),
                dataEditor.getWorkpiece().getLogicalStructure());
            prepareDocStructPositionSelectionItems(parents.isEmpty());
            prepareAddableMetadataForStructure(true);
        } else {
            docStructPositionSelectionItems = Collections.emptyList();
            addableMetadata = Collections.emptyList();
        }
        this.prepareDocStructTypes();
        prepareSelectPageOnAddNodeItems();
        // reset type selection if previous selection is not allowed on current parent/position
        if (getDocStructAddTypeSelectionItems()
                .stream()
                .noneMatch(selectItem -> selectItem.getValue().equals(docStructAddTypeSelectionSelectedItem))) {
            docStructAddTypeSelectionSelectedItem = "";
        }
    }

    /** 
     * Determines the logical parent division that can be used as a basis for the dialog and overwrites the 
     * user selection with this logical parent division. 
     * 
     * <p>Note: Sneakily overwriting the current user selection is a very bad legacy design choice of this dialog. 
     * This should be improved in the future!</p>
     */
    private void checkSelectedLogicalNode() {
        Set<LogicalDivision> logicalDivisionSet =  dataEditor.getStructurePanel().getSelectedLogicalNodes().stream()
            .map(StructureTreeOperations::getTreeNodeLogicalParentOrSelf)
            .map(StructureTreeOperations::getLogicalDivisionFromTreeNode)
            .collect(Collectors.toSet());

        if (logicalDivisionSet.isEmpty()) {
            // determine logical parent division for selection of media (in case of gallery selection)
            logicalDivisionSet.addAll(
                dataEditor.getSelectedMedia().stream()
                    .map(Pair::getRight)
                    .collect(Collectors.toSet())
            );
        }

        if (logicalDivisionSet.size() == 1) {
            LogicalDivision logicalDivision = logicalDivisionSet.iterator().next();
            if (Objects.nonNull(logicalDivision)) {
                try {
                    dataEditor.updateSelection(Collections.emptyList(), Collections.singletonList(logicalDivision));
                } catch (NoSuchMetadataFieldException e) {
                    Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
                }
            }
        }
    }

    /**
     * Update lists of available doc struct types that can be added to the currently selected structure element in the
     * currently selected position.
     */
    public void prepareDocStructTypes() {
        Optional<LogicalDivision> selectedStructure = dataEditor.getSelectedStructure();
        if (selectedStructure.isPresent()) {
            this.parents = MetadataEditor.getAncestorsOfLogicalDivision(selectedStructure.get(),
                    dataEditor.getWorkpiece().getLogicalStructure());
            if (parents.isEmpty()) {
                selectionItemsForParent = Collections.emptyList();
            } else {
                prepareDocStructAddTypeSelectionItemsForParent();
            }
            prepareDocStructAddTypeSelectionItemsForChildren();
            prepareDocStructAddTypeSelectionItemsForSiblings();
        } else {
            selectionItemsForChildren = Collections.emptyList();
            selectionItemsForParent = Collections.emptyList();
            selectionItemsForSiblings = Collections.emptyList();
        }
    }

    private void prepareDocStructAddTypeSelectionItemsForChildren() {
        selectionItemsForChildren = DataEditorService.getSortedAllowedSubstructuralElements(
                dataEditor.getRulesetManagement()
                        .getStructuralElementView(
                                dataEditor.getSelectedStructure().orElseThrow(IllegalStateException::new).getType(),
                                dataEditor.getAcquisitionStage(), dataEditor.getPriorityList()),
                dataEditor.getProcess().getRuleset());
    }

    private void prepareDocStructAddTypeSelectionItemsForParent() {
        selectionItemsForParent = new ArrayList<>();
        if (!parents.isEmpty()) {
            StructuralElementViewInterface parentDivisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                parents.getLast().getType(), dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
            for (Entry<String, String> entry : parentDivisionView.getAllowedSubstructuralElements().entrySet()) {
                String newParent = entry.getKey();
                StructuralElementViewInterface newParentDivisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                    newParent, dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
                if (newParentDivisionView.getAllowedSubstructuralElements().containsKey(
                    dataEditor.getSelectedStructure().orElseThrow(IllegalStateException::new).getType())) {
                    selectionItemsForParent.add(new SelectItem(newParent, entry.getValue()));
                }
            }
            DataEditorService.sortMetadataList(selectionItemsForParent,
                    dataEditor.getProcess().getRuleset());
        }
    }

    private void prepareDocStructAddTypeSelectionItemsForSiblings() {
        selectionItemsForSiblings = new ArrayList<>();
        if (!parents.isEmpty()) {
            selectionItemsForSiblings = DataEditorService.getSortedAllowedSubstructuralElements(
                    dataEditor.getRulesetManagement().getStructuralElementView(
                    parents.getLast().getType(), dataEditor.getAcquisitionStage(), dataEditor.getPriorityList()),
                    dataEditor.getProcess().getRuleset());
        }
    }

    private void prepareDocStructPositionSelectionItems(boolean rootNode) {
        docStructPositionSelectionItems = new ArrayList<>();
        if (!dataEditor.getSelectedMedia().isEmpty() && dataEditor.consecutivePagesSelected()) {
            selectedDocStructPosition = CURRENT_POSITION;
        } else {
            if (!rootNode) {
                docStructPositionSelectionItems.add(new SelectItem(BEFORE_CURRENT_ELEMENT,
                        Helper.getTranslation("dataEditor.position.beforeCurrentElement")));
                docStructPositionSelectionItems.add(new SelectItem(AFTER_CURRENT_ELEMENT,
                        Helper.getTranslation("dataEditor.position.afterCurrentElement")));
            }
            docStructPositionSelectionItems.add(new SelectItem(FIRST_CHILD_OF_CURRENT_ELEMENT,
                    Helper.getTranslation("dataEditor.position.asFirstChildOfCurrentElement")));
            docStructPositionSelectionItems.add(new SelectItem(LAST_CHILD_OF_CURRENT_ELEMENT,
                    Helper.getTranslation("dataEditor.position.asLastChildOfCurrentElement")));
            docStructPositionSelectionItems.add(new SelectItem(PARENT_OF_CURRENT_ELEMENT,
                    Helper.getTranslation("dataEditor.position.asParentOfCurrentElement")));
        }
    }

    /**
     * Prepare the list of available Metadata that can be added to the currently selected structure element.
     *
     * @param currentElement flag controlling whether to return a list of metadata for the currently selected logical
     *                       structure element (currentElement = true) or for a new element to be added to the structure
     *                       (currentElement = false)
     * @param metadataNodes list of TreeNodes containing the metadata that is already assigned to the structure element
     */
    public void prepareAddableMetadataForStructure(boolean currentElement, List<TreeNode> metadataNodes) {
        addableMetadata = DataEditorService.getAddableMetadataForStructureElement(this.dataEditor, currentElement,
                metadataNodes, docStructAddTypeSelectionSelectedItem, true);
        setSelectedMetadata("");
    }

    /**
     * Convenience function to call metadata preparation with a single parameter.
     *
     * @param currentElement flag controlling whether to return a list of metadata for the currently selected logical
     *                       structure element (currentElement = true) or for a new element to be added to the structure
     *                       (currentElement = false)
     */
    public void prepareAddableMetadataForStructure(boolean currentElement) {
        dataEditor.getMetadataPanel().setSelectedMetadataTreeNode(null);
        prepareAddableMetadataForStructure(currentElement, Collections.emptyList());
    }

    private void prepareSelectPageOnAddNodeItems() {
        List<PhysicalDivision> physicalDivisions = dataEditor.getWorkpiece().getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack();
        selectPageOnAddNodeItems = new ArrayList<>(physicalDivisions.size());
        for (int i = 0; i < physicalDivisions.size(); i++) {
            View view = View.of(physicalDivisions.get(i));
            String label = dataEditor.getStructurePanel().buildViewLabel(view);
            selectPageOnAddNodeItems.add(new SelectItem(Integer.toString(i), label));
        }
    }

    /**
     * Fill preselectedViews with the views matching the List of selectedMedia.
     */
    public void preparePreselectedViews() {
        preselectedViews = new ArrayList<>();
        List<Pair<PhysicalDivision, LogicalDivision>> selectedMedia = dataEditor.getSelectedMedia();
        for (Pair<PhysicalDivision, LogicalDivision> pair : selectedMedia) {
            for (View view : pair.getValue().getViews()) {
                if (Objects.equals(view.getPhysicalDivision(), pair.getKey())) {
                    preselectedViews.add(view);
                }
            }
        }
        preselectedViews.sort(Comparator.comparingInt(view -> view.getPhysicalDivision().getOrder()));
    }

    /**
     * Reset values.
     */
    public void resetValues() {
        preselectedViews = Collections.emptyList();
        processNumber = "";
        processes = Collections.emptyList();
        linkSubDialogVisible = false;
        inputMetaDataValue = "";
        elementsToAddSpinnerValue = 1;
        selectFirstPageOnAddNode = null;
        selectLastPageOnAddNode = null;
        if (Objects.nonNull(previouslySelectedLogicalNode)) {
            dataEditor.getStructurePanel().setSelectedLogicalNodes(Arrays.asList(previouslySelectedLogicalNode));
            previouslySelectedLogicalNode = null;
        }
    }

    /**
     * Returns the process number. The process number is an input field where
     * the user can enter the process number or the process title, and then it
     * is searched for. But search is only when the button is clicked (too much
     * load otherwise).
     *
     * @return the process number
     */
    public String getProcessNumber() {
        return processNumber;
    }

    /**
     * Sets the process number when the user entered it.
     *
     * @param processNumber
     *            process number to set
     */
    public void setProcessNumber(String processNumber) {
        this.processNumber = processNumber;
    }

    /**
     * Function for the button for the search. Looks for suitable processes. If
     * the process number is a number and the process exists, it is already
     * found. Otherwise it must be searched for, excluding the wrong ruleset or
     * the wrong client.
     */
    public void search() {
        if (processNumber.trim().isEmpty()) {
            alert(Helper.getTranslation("dialogAddDocStrucType.searchButtonClick.empty"));
            return;
        }
        try {
            Set<String> allowedSubstructuralElements = DataEditorService.getStructuralElementView(this.dataEditor)
                    .getAllowedSubstructuralElements().keySet();
            List<Integer> ids = ServiceManager.getProcessService().findLinkableChildProcesses(processNumber,
                dataEditor.getProcess().getRuleset().getId(), allowedSubstructuralElements)
                    .stream().map(ProcessDTO::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                alert(Helper.getTranslation("dialogAddDocStrucType.searchButtonClick.noHits"));
            }
            processes = new LinkedList<>();
            for (int processId : ids) {
                processes.add(ServiceManager.getProcessService().getById(processId));
            }
        } catch (DataException | DAOException e) {
            logger.catching(e);
            alert(Helper.getTranslation("dialogAddDocStrucType.searchButtonClick.error", e.getMessage()));
        }
    }

    /**
     * Displays a dialog box with a message to the user.
     *
     * @param message
     *            message to show
     */
    private void alert(String message) {
        PrimeFaces.current().executeScript("alert('" + message + "');");
    }

    /**
     * Returns the process selected by the user in the drop-down list.
     *
     * @return the selected process
     */
    public Process getSelectedProcess() {
        return selectedProcess;
    }

    /**
     * Sets the number of the process selected by the user.
     *
     * @param selectedProcess
     *            selected process
     */
    public void setSelectedProcess(Process selectedProcess) {
        this.selectedProcess = selectedProcess;
    }

    /**
     * Returns the list of items to populate the drop-down list to select a
     * process.
     *
     * @return the list of processes
     */
    public List<Process> getProcesses() {
        return processes;
    }

    /**
     * Get preselectedViews.
     *
     * @return value of preselectedViews
     */
    public List<View> getPreselectedViews() {
        return preselectedViews;
    }

    /**
     * Adds the link when the user clicks OK.
     */
    public void addProcessLink() {
        dataEditor.getCurrentChildren().add(selectedProcess);
        MetadataEditor.addLink(dataEditor.getSelectedStructure().orElseThrow(IllegalStateException::new),
            selectedProcess.getId());
        dataEditor.getStructurePanel().show(true);
        dataEditor.getPaginationPanel().show();
        selectedProcess = null;
        processes = Collections.emptyList();
    }

    /**
     * Return the label of the currently selected structure.
     *
     * @return label of the currently selected structure
     */
    public String getCurrentStructureLabel() {
        if (dataEditor.getSelectedStructure().isPresent()) {
            StructuralElementViewInterface divisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                    dataEditor.getSelectedStructure().get().getType(), dataEditor.getAcquisitionStage(),
                    dataEditor.getPriorityList());
            return divisionView.getLabel();
        } else {
            return " - ";
        }
    }

    private StructuralElementViewInterface getDivisionViewOfStructure(String structure) {
        StructuralElementViewInterface divisionView = dataEditor.getRulesetManagement()
                .getStructuralElementView(structure, dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
        return divisionView;
    }

    /**
     * This method checks if the selected metadatakey refers to complex metadata.
     * 
     * @return True if metadata is of complex type.
     */
    public boolean isSelectedMetadataComplex() {
        MetadataViewInterface mvi = getMetadataViewFromKey(docStructAddTypeSelectionSelectedItem,selectedMetadata);
        return mvi.isComplex();
    }

    private MetadataViewInterface getMetadataViewFromKey(String structure, String metadataKey) {
        StructuralElementViewInterface divisionView = getDivisionViewOfStructure(structure);

        return divisionView.getAllowedMetadata().stream().filter(metaDatum -> metaDatum.getId().equals(metadataKey))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

}
