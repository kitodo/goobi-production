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

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.UnknownTreeNodeDataException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.primefaces.event.NodeCollapseEvent;
import org.primefaces.event.NodeExpandEvent;
import org.primefaces.event.NodeSelectEvent;
import org.primefaces.event.TreeDragDropEvent;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

public class StructurePanel implements Serializable {
    private static final Logger logger = LogManager.getLogger(StructurePanel.class);
    public static final String STRUCTURE_NODE_TYPE = "Structure";
    public static final String PHYS_STRUCTURE_NODE_TYPE = "PhysStructure";
    public static final String MEDIA_NODE_TYPE = "Media";
    public static final String MEDIA_PARTIAL_NODE_TYPE = "MediaPartial";
    public static final String VIEW_NODE_TYPE = "View";

    private final DataEditorForm dataEditor;

    /**
     * If changing the tree node fails, we need this value to undo the user’s
     * select action.
     */
    private List<TreeNode> previouslySelectedLogicalNodes = new ArrayList<>();

    /**
     * If changing the tree node fails, we need this value to undo the user’s
     * select action.
     */
    private List<TreeNode> previouslySelectedPhysicalNodes = new ArrayList<>();

    private TreeNode[] selectedLogicalNodes = new TreeNode[] {};

    private TreeNode[] selectedPhysicalNodes = new TreeNode[] {};

    private LogicalDivision structure;

    /**
     * The logical structure tree of the edited document.
     */
    private DefaultTreeNode logicalTree = null;

    /**
     * The physical structure tree of the edited document.
     */
    private DefaultTreeNode physicalTree = null;

    /**
     * HashMap containing the current expansion states of all TreeNodes in the logical structure tree.
     */
    private HashMap<LogicalDivision, Boolean> previousExpansionStatesLogicalTree;

    /**
     * HashMap containing the current expansion states of all TreeNodes in the physical structure tree.
     */
    private HashMap<PhysicalDivision, Boolean> previousExpansionStatesPhysicalTree;

    /**
     * HashMap acting as cache for faster retrieval of Subfolders.
     */
    Map<String, Subfolder> subfoldersCache = new HashMap<>();

    /**
     * List of all physicalDivisions assigned to multiple LogicalDivisions.
     */
    private List<PhysicalDivision> severalAssignments = new LinkedList<>();

    /**
     * Variable used to set the correct order value when building the logical and physical trees from the PrimeFaces tree.
     */
    private int order = 1;

    /**
     * Active tabs in StructurePanel's accordion.
     */
    private String activeTabs;

    /**
     * Stores the users choice of the drop down selection above the logical structure tree. Can be either "type" 
     * (default), "title" (uses metadata key annotated with "structureTreeTitle" in ruleset) or "type+title".
     */
    private String nodeLabelOption = "type";

    /**
     * Determines whether the logical tree is built as a combination of physical media nodes and
     * logical structure nodes (default) or whether it only contains structure nodes.
     */
    private boolean hideMediaInLogicalTree = false;

    /**
     * Determines whether a page range is show together with the label of a logical structure node.
     * The page consists of the label of the first and last media of the logical division,
     * e.g. "4 : iv - 6 : vi".
     */
    private boolean showPageRangeInLogicalTree = false;

    /**
     * Determines whether the hierarchy level of a tree node should be displayed with its label or not.
     */
    private boolean showHierarchyLevel = false;

    /**
     * Creates a new structure panel.
     *
     * @param dataEditor
     *            the master metadata editor
     */
    StructurePanel(DataEditorForm dataEditor) {
        this.dataEditor = dataEditor;
    }

    /**
     * Clear content.
     */
    public void clear() {
        logicalTree = null;
        physicalTree = null;
        selectedLogicalNodes = new TreeNode[] {};
        selectedPhysicalNodes = new TreeNode[] {};
        previouslySelectedLogicalNodes = new ArrayList<>();
        previouslySelectedPhysicalNodes = new ArrayList<>();
        structure = null;
        subfoldersCache = new HashMap<>();
        severalAssignments = new LinkedList<>();
    }

    void deleteSelectedStructure() {
        if (getSelectedStructure().isEmpty()) {
            /*
             * No element is selected or the selected element is not a structure
             * but, for example, a physical division.
             */
            return;
        }
        deleteLogicalDivision(getSelectedStructure().get());
    }

    /**
     * Delete the logical division.
     *
     * @param selectedStructure The logical division.
     */
    public void deleteLogicalDivision(LogicalDivision selectedStructure) {
        LinkedList<LogicalDivision> ancestors = MetadataEditor.getAncestorsOfLogicalDivision(selectedStructure,
                structure);
        if (ancestors.isEmpty()) {
            // The selected element is the root node of the tree.
            return;
        }

        Collection<View> subViews = new ArrayList<>();
        getAllSubViews(selectedStructure, subViews);

        List<View> multipleViews = subViews.stream().filter(v -> v.getPhysicalDivision().getLogicalDivisions().size() > 1)
                .collect(Collectors.toList());
        for (View view : multipleViews) {
            dataEditor.unassignView(selectedStructure, view, selectedStructure.getViews().getLast().equals(view));
            if (view.getPhysicalDivision().getLogicalDivisions().size() <= 1) {
                severalAssignments.remove(view.getPhysicalDivision());
            }
        }

        LogicalDivision parent = ancestors.getLast();

        parent.getViews().addAll(subViews);
        parent.getViews().sort(Comparator.comparingInt(v -> v.getPhysicalDivision().getOrder()));

        parent.getChildren().remove(selectedStructure);
        show();
        dataEditor.getGalleryPanel().updateStripes();
    }

    private void getAllSubViews(LogicalDivision selectedStructure, Collection<View> views) {
        if (Objects.nonNull(selectedStructure.getViews())) {
            views.addAll(selectedStructure.getViews());
        }
        for (LogicalDivision child : selectedStructure.getChildren()) {
            getAllSubViews(child, views);
        }
    }

    /**
     * Delete a single physical division that is part of the current selection.
     * 
     * @param treeNode the PrimeFaces treeNode that is currently selected for deletion
     */
    private void deleteSelectedPhysicalDivision(TreeNode treeNode) {
        if (Objects.isNull(treeNode)) {
            // there is nothing to do
            return;
        }
        if (MEDIA_PARTIAL_NODE_TYPE.equals(treeNode.getType()) && treeNode.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
            PhysicalDivision physicalDivision = ((View) structureTreeNode.getDataObject()).getPhysicalDivision();
            for (LogicalDivision structuralElement : physicalDivision.getLogicalDivisions()) {
                structuralElement.getViews().removeIf(view -> view.getPhysicalDivision().equals(physicalDivision));
            }
            if (deletePhysicalDivision(physicalDivision)) {
                physicalDivision.getLogicalDivisions().clear();
            }
        } else {
            for (Pair<PhysicalDivision, LogicalDivision> selectedPhysicalDivision : dataEditor.getSelectedMedia()) {
                PhysicalDivision physicalDivision = selectedPhysicalDivision.getKey();
                if (!dataEditor.getUnsavedDeletedMedia().contains(physicalDivision)) {
                    if (physicalDivision.getLogicalDivisions().size() > 1) {
                        Helper.setMessage(physicalDivision + ": is removed fom all assigned structural elements");
                    }
                    for (LogicalDivision structuralElement : physicalDivision.getLogicalDivisions()) {
                        structuralElement.getViews().removeIf(view -> view.getPhysicalDivision().equals(physicalDivision));
                    }
                    physicalDivision.getLogicalDivisions().clear();
                    if (!deletePhysicalDivision(physicalDivision)) {
                        return;
                    }

                    dataEditor.getUnsavedDeletedMedia().add(physicalDivision);
                }
            }
        }
    }

    /**
     * Delete all currently selected physical divisons.
     */
    public void deleteSelectedPhysicalDivisions() {
        if (Objects.isNull(selectedLogicalNodes)) {
            // there is nothing to do
            return;
        }
        for (TreeNode selectedLogicalNode : selectedLogicalNodes) {
            deleteSelectedPhysicalDivision(selectedLogicalNode);
        }

        int i = 1;
        for (PhysicalDivision physicalDivision : dataEditor.getWorkpiece().getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack()) {
            physicalDivision.setOrder(i);
            i++;
        }
        show();
        dataEditor.getMetadataPanel().clear();
        dataEditor.getSelectedMedia().clear();
        dataEditor.getGalleryPanel().updateStripes();
        dataEditor.getPaginationPanel().show();
    }

    /**
     * Delete as physical division.
     *
     * @param physicalDivision The physical division.
     * @return True if deleted
     */
    public boolean deletePhysicalDivision(PhysicalDivision physicalDivision) {
        LinkedList<PhysicalDivision> ancestors = MetadataEditor.getAncestorsOfPhysicalDivision(physicalDivision,
                dataEditor.getWorkpiece().getPhysicalStructure());
        if (ancestors.isEmpty()) {
            // The selected element is the root node of the tree.
            return false;
        }
        PhysicalDivision parent = ancestors.getLast();
        parent.getChildren().remove(physicalDivision);
        return true;
    }

    /**
     * Get selected logical TreeNodes if it is the only selected TreeNode 
     * (e.g. when opening the context menu).
     *
     * @return TreeNode instance if it is the only selected node or null
     */
    public TreeNode getSelectedLogicalNodeIfSingle() {
        List<TreeNode> nodes = getSelectedLogicalNodes();
        if (Objects.nonNull(nodes) && nodes.size() == 1) {
            return nodes.get(0);
        }
        return null;
    }

    /**
     * Get selected logical TreeNodes as List.
     *
     * @return value of selectedLogicalNodes as List
     */
    public List<TreeNode> getSelectedLogicalNodes() {
        return Arrays.asList(selectedLogicalNodes);
    }

    /**
     * Get selected logical TreeNodes as Array (as required by PrimeFaces).
     *
     * @return value of selectedLogicalNodes as array
     */
    public TreeNode[] getSelectedLogicalNodesAsArray() {
        return selectedLogicalNodes;
    }

    /**
     * Set selected logical TreeNodes from Array (as required by PrimeFaces).
     *
     * @param selected
     *          array of TreeNodes that will be selected
     */
    public void setSelectedLogicalNodesAsArray(TreeNode[] selected) {
        if (Objects.nonNull(selected)) {
            this.selectedLogicalNodes = selected;
            for (TreeNode node : selected) {
                if (Objects.nonNull(node)) {
                    expandNode(node.getParent());
                }
            }
        } else {
            this.selectedLogicalNodes =  new TreeNode[] {};
        }
    }

    /**
     * Set selected logical TreeNodes from Collection.
     *
     * @param selected
     *          collection of TreeNodes that will be selected
     */
    public void setSelectedLogicalNodes(List<TreeNode> selected) {
        if (Objects.nonNull(selected)) {
            this.setSelectedLogicalNodesAsArray((TreeNode[])selected.toArray(new TreeNode[selected.size()]));
        }        
    }

    /**
     * Get selected physical TreeNodes if it is the only selected TreeNode 
     * (e.g. when opening the context menu).
     *
     * @return TreeNode instance if it is the only selected node or null
     */
    public TreeNode getSelectedPhysicalNodeIfSingle() {
        List<TreeNode> nodes = getSelectedPhysicalNodes();
        if (Objects.nonNull(nodes) && nodes.size() == 1) {
            return nodes.get(0);
        }
        return null;
    }

    /**
     * Get selected physical nodes as list.
     *
     * @return value of selectedPhysicalNode
     */
    public List<TreeNode> getSelectedPhysicalNodes() {
        return Arrays.asList(selectedPhysicalNodes);
    }

    /**
     * Get selected physical TreeNodes as Array (as required by PrimeFaces).
     *
     * @return value of selectedLogicalNodes as array
     */
    public TreeNode[] getSelectedPhysicalNodesAsArray() {
        return selectedLogicalNodes;
    }

    /**
     * Set selected physical nodes array (as required by PrimeFaces).
     *
     * @param selected array of selected org.primefaces.model.TreeNode
     */
    public void setSelectedPhysicalNodesAsArray(TreeNode[] selected) {
        if (Objects.nonNull(selected)) {
            this.selectedPhysicalNodes = selected;
            for (TreeNode node : selected) {
                if (Objects.nonNull(node)) {
                    expandNode(node.getParent());
                }
            }
        } else {
            this.selectedPhysicalNodes = new TreeNode[] {};
        }
    }

    /**
     * Set selected physical TreeNodes from Collection.
     *
     * @param selected
     *          collection of TreeNodes that will be selected
     */
    public void setSelectedPhysicalNodes(List<TreeNode> selected) {
        if (Objects.nonNull(selected)) {
            this.setSelectedPhysicalNodesAsArray((TreeNode[])selected.toArray(new TreeNode[selected.size()]));
        }        
    }

    /**
     * Return the currently selected logical divison if it is the only selected structure element.
     * @return LogicalDivison as Optional instance
     */
    Optional<LogicalDivision> getSelectedStructure() {
        TreeNode selectedLogicalNode = getSelectedLogicalNodeIfSingle();
        if (Objects.isNull(selectedLogicalNode) || !(selectedLogicalNode.getData() instanceof StructureTreeNode)) {
            return Optional.empty();
        }
        StructureTreeNode structureTreeNode = (StructureTreeNode) selectedLogicalNode.getData();
        Object dataObject = structureTreeNode.getDataObject();
        return Optional.ofNullable(dataObject instanceof LogicalDivision ? (LogicalDivision) dataObject : null);
    }

    Optional<PhysicalDivision> getSelectedPhysicalDivision() {
        TreeNode selectedPhysicalNode = getSelectedPhysicalNodeIfSingle();
        if (Objects.isNull(selectedPhysicalNode)) {
            return Optional.empty();
        }
        StructureTreeNode structureTreeNode = (StructureTreeNode) selectedPhysicalNode.getData();
        Object dataObject = structureTreeNode.getDataObject();
        return Optional.ofNullable(dataObject instanceof PhysicalDivision ? (PhysicalDivision) dataObject : null);
    }

    /**
     * Get logicalTree.
     *
     * @return value of logicalTree
     */
    public DefaultTreeNode getLogicalTree() {
        return this.logicalTree;
    }

    /**
     * Get physicalTree.
     *
     * @return value of physicalTree
     */
    public DefaultTreeNode getPhysicalTree() {
        return physicalTree;
    }

    void preserve() throws UnknownTreeNodeDataException {
        if (isSeparateMedia()) {
            this.preserveLogical();
            this.preservePhysical();
        } else {
            if (isHideMediaInLogicalTree()) {
                this.preserveLogical();
            } else {
                this.preserveLogicalAndPhysical();
            }
        }
    }

    /**
     * Updates the live structure of the workpiece with the current members of
     * the structure tree in their given order. The live structure of the
     * workpiece which is stored in the logical structure of the structure tree.
     */
    private void preserveLogical() {
        if (!this.logicalTree.getChildren().isEmpty()) {
            preserveLogicalRecursive(this.logicalTree.getChildren().get(logicalTree.getChildCount() - 1));
            dataEditor.checkForChanges();
        }
    }

    /**
     * Updates the live structure of a structure tree node and returns it, to
     * provide for updating the parent. If the tree node contains children which
     * aren’t structures, {@code null} is returned to skip them on the level
     * above.
     */
    private static LogicalDivision preserveLogicalRecursive(TreeNode treeNode) {
        StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
        if (Objects.isNull(structureTreeNode) || !(structureTreeNode.getDataObject() instanceof LogicalDivision)) {
            return null;
        }
        LogicalDivision structure = (LogicalDivision) structureTreeNode.getDataObject();

        List<LogicalDivision> childrenLive = structure.getChildren();
        childrenLive.clear();
        for (TreeNode child : treeNode.getChildren()) {
            LogicalDivision maybeChildStructure = preserveLogicalRecursive(child);
            if (Objects.nonNull(maybeChildStructure)) {
                childrenLive.add(maybeChildStructure);
            }
        }
        return structure;
    }

    private void preservePhysical() {
        if (!physicalTree.getChildren().isEmpty()) {
            preservePhysicalRecursive(physicalTree.getChildren().get(0));
            dataEditor.checkForChanges();
        }
    }

    private static PhysicalDivision preservePhysicalRecursive(TreeNode treeNode) {
        StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
        if (Objects.isNull(structureTreeNode) || !(structureTreeNode.getDataObject() instanceof PhysicalDivision)) {
            return null;
        }
        PhysicalDivision physicalDivision = (PhysicalDivision) structureTreeNode.getDataObject();

        List<PhysicalDivision> childrenLive = physicalDivision.getChildren();
        childrenLive.clear();
        for (TreeNode child : treeNode.getChildren()) {
            PhysicalDivision possibleChildPhysicalDivision = preservePhysicalRecursive(child);
            if (Objects.nonNull(possibleChildPhysicalDivision)) {
                childrenLive.add(possibleChildPhysicalDivision);
            }
        }
        return physicalDivision;
    }

    /**
     * Loads the tree(s) into the panel and sets the selected element to the
     * logical structure of the structure tree.
     *
     * @param keepSelection
     *            if true, keeps the currently selected node(s)
     */
    public void show(boolean keepSelection) {
        if (!keepSelection) {
            show();
            return;
        }

        final Set<String> logicalRowKeys = getTreeNodeRowKeys(this.getSelectedLogicalNodes());
        final Set<String> physicalRowKeys = getTreeNodeRowKeys(this.getSelectedPhysicalNodes());
        List<TreeNode> keepSelectedLogicalNodes = getSelectedLogicalNodes();
        List<TreeNode> keepSelectedPhysicalNodes = getSelectedPhysicalNodes();

        show();

        setSelectedLogicalNodes(keepSelectedLogicalNodes);
        setSelectedPhysicalNodes(keepSelectedPhysicalNodes);
        restoreSelectionFromRowKeys(logicalRowKeys, this.logicalTree);
        restoreSelectionFromRowKeys(physicalRowKeys, this.physicalTree);
    }

    /**
     * Loads the tree(s) into the panel and sets the selected element to the
     * logical structure of the structure tree.
     */
    public void show() {
        this.structure = dataEditor.getWorkpiece().getLogicalStructure();

        this.previousExpansionStatesLogicalTree = getLogicalTreeNodeExpansionStates(this.logicalTree);
        this.logicalTree = buildStructureTree();
        updateLogicalNodeExpansionStates(this.logicalTree, this.previousExpansionStatesLogicalTree);

        this.previousExpansionStatesPhysicalTree = getPhysicalTreeNodeExpansionStates(this.physicalTree);
        this.physicalTree = buildMediaTree(dataEditor.getWorkpiece().getPhysicalStructure());
        updatePhysicalNodeExpansionStates(this.physicalTree, this.previousExpansionStatesPhysicalTree);

        this.previouslySelectedLogicalNodes = getSelectedLogicalNodes();
        this.previouslySelectedPhysicalNodes = getSelectedLogicalNodes();
        dataEditor.checkForChanges();
    }

    private Set<String> getTreeNodeRowKeys(Collection<TreeNode> nodes) {
        HashSet<String> logicalRowKeys = new HashSet<>();
        if (Objects.nonNull(nodes)) {
            for (TreeNode node: nodes) {
                logicalRowKeys.add(node.getRowKey());
            }
        }
        return logicalRowKeys;
    }

    private void restoreSelectionFromRowKeys(Set<String> rowKeys, TreeNode parentNode) {
        for (TreeNode childNode : parentNode.getChildren()) {
            if (Objects.nonNull(childNode)) {
                childNode.setSelected(rowKeys.contains(childNode.getRowKey()));
                restoreSelectionFromRowKeys(rowKeys, childNode);
            }
        }
    }

    /**
     * Creates the structure tree. If hierarchical links exist upwards, they are
     * displayed above the tree as separate trees.
     *
     * @return the structure tree(s) and the collection of views displayed in
     *         the tree
     */
    private DefaultTreeNode buildStructureTree() {
        DefaultTreeNode invisibleRootNode = new DefaultTreeNode();
        invisibleRootNode.setExpanded(true);
        invisibleRootNode.setType(STRUCTURE_NODE_TYPE);
        addParentLinksRecursive(dataEditor.getProcess(), invisibleRootNode);
        List<Integer> processIds = getAllLinkedProcessIds(structure);
        Map<Integer, String> processTypeMap = processIds.isEmpty() ? Collections.emptyMap() : fetchProcessTypes(processIds);
        Map<String, StructuralElementViewInterface> viewCache = new HashMap<>();
        buildStructureTreeRecursively(structure, invisibleRootNode, processTypeMap, viewCache);
        return invisibleRootNode;
    }

    private List<Integer> getAllLinkedProcessIds(LogicalDivision structure) {
        return structure.getAllChildren().stream()
                .filter(division -> division.getLink() != null)
                .map(division -> ServiceManager.getProcessService().processIdFromUri(division.getLink().getUri()))
                .collect(Collectors.toList());
    }

    private Map<Integer, String> fetchProcessTypes(List<Integer> processIds) {
        try {
            return ServiceManager.getProcessService().getIdBaseTypeMap(processIds);
        } catch (DataException e) {
            Helper.setErrorMessage("metadataReadError", e.getMessage(), logger, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Constructs a page range string by combining the labels of the first and last view
     * of the provided logical division.
     *
     * @param structure the logical division
     * @return the page range string
     */
    private String buildPageRangeFromLogicalDivision(LogicalDivision structure) {
        LinkedList<View> views = structure.getViews();
        if (views.size() > 1) {
            return buildViewLabel(views.getFirst()) + " | " + buildViewLabel(views.getLast());
        } else if (!views.isEmpty() && Objects.nonNull(views.getFirst())) {
            return buildViewLabel(views.getFirst());
        }
        return null;
    }

    /**
     * Build a StructureTreeNode for a logical division, which is then visualized in the logical structure tree.
     *
     * @param structure the logical division for which the tree node is being constructed
     * @param idTypeMap the mapping of process id to basetype
     * @param viewCache a cache for storing and retrieving already processed StructuralElementViews
     * @return the constructed {@link StructureTreeNode} instance representing the given logical division
     */
    private StructureTreeNode buildStructureTreeNode(LogicalDivision structure,  Map<Integer, String> idTypeMap,
                                                     Map<String, StructuralElementViewInterface> viewCache) {
        StructureTreeNode node;
        if (Objects.isNull(structure.getLink())) {
            StructuralElementViewInterface divisionView = viewCache.computeIfAbsent(structure.getType(), key ->
                    dataEditor.getRulesetManagement().getStructuralElementView(
                            key, dataEditor.getAcquisitionStage(), dataEditor.getPriorityList())
            );
            String label = divisionView.getLabel();
            String pageRange = buildPageRangeFromLogicalDivision(structure);
            boolean undefined = divisionView.isUndefined() && Objects.nonNull(structure.getType());
            node = new StructureTreeNode(label, pageRange, undefined, false, structure);
        } else {
            node = new StructureTreeNode(structure.getLink().getUri().toString(), null, true, true, structure);
            for (Process child : dataEditor.getCurrentChildren()) {
                if (child.getId() == ServiceManager.getProcessService().processIdFromUri(structure.getLink().getUri())) {
                    String type = idTypeMap.get(child.getId());
                    // Retrieve the view from cache if it exists, otherwise compute and cache it
                    StructuralElementViewInterface view = viewCache.computeIfAbsent(type, key ->
                            dataEditor.getRulesetManagement().getStructuralElementView(
                                    key, dataEditor.getAcquisitionStage(), dataEditor.getPriorityList())
                    );
                    node = new StructureTreeNode("[" + child.getId() + "] " + view.getLabel() + " - "
                            + child.getTitle(), null, view.isUndefined(), true, structure);
                }
            }
        }
        return node;
    }

    private boolean setContainsViewByReference(List<View> set, View view) {
        for (View v : set) {
            if (v == view) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively build the logical structure tree.
     *
     * @param structure the current logical structure
     * @param result the current corresponding primefaces tree node
     * @param processTypeMap the mapping of process id to basetype
     * @param viewCache a cache for storing and retrieving already processed StructuralElementViews
     * @return a collection of views that contains all views of the full sub-tree
     */
    private Collection<View> buildStructureTreeRecursively(LogicalDivision structure, TreeNode result, Map<Integer,
            String> processTypeMap, Map<String, StructuralElementViewInterface> viewCache) {
        StructureTreeNode node = buildStructureTreeNode(structure, processTypeMap, viewCache);
        /*
         * Creating the tree node by handing over the parent node automatically
         * appends it to the parent as a child. That’s the logic of the JSF
         * framework. So you do not have to add the result anywhere.
         */
        DefaultTreeNode parent = new DefaultTreeNode(STRUCTURE_NODE_TYPE, node, result);
        if (logicalNodeStateUnknown(this.previousExpansionStatesLogicalTree, parent)) {
            parent.setExpanded(true);
        }

        List<View> viewsShowingOnAChild = new LinkedList<>();
        if (!this.logicalStructureTreeContainsMedia()) {
            for (LogicalDivision child : structure.getChildren()) {
                viewsShowingOnAChild.addAll(buildStructureTreeRecursively(child, parent, processTypeMap, viewCache));
            }
        } else {
            // iterate through children and views ordered by the ORDER attribute
            List<Pair<View, LogicalDivision>> merged = mergeLogicalStructureViewsAndChildren(structure);
            for (Pair<View, LogicalDivision> pair : merged) {
                if (Objects.nonNull(pair.getRight())) {
                    // add child and their views
                    viewsShowingOnAChild.addAll(buildStructureTreeRecursively(pair.getRight(), parent,
                            processTypeMap, viewCache));
                } else if (!(setContainsViewByReference(viewsShowingOnAChild, pair.getLeft()))) {
                    // add views of current logical division as leaf nodes
                    DefaultTreeNode viewNode = addTreeNode(buildViewLabel(pair.getLeft()), false, viewsShowingOnAChild.contains(pair.getLeft()), pair.getLeft(), parent);
                    viewNode.setType(pair.getLeft().getPhysicalDivision().hasMediaPartial()
                            ? MEDIA_PARTIAL_NODE_TYPE
                            : VIEW_NODE_TYPE);
                    viewsShowingOnAChild.add(pair.getLeft());
                }
            }
        }
        return viewsShowingOnAChild;
    }

    /**
     * Returns a list containing both views and children of a LogicalDivision ordered by their ORDER attribute.
     * This ordering reflects how tree nodes are visualized in the logical structure tree.
     *
     * <p>Unfortunately, the mets ORDER attribute of logical divisions and physical divisions is maintained and stored
     * separately, which means the order does not reflect a consistent tree traversal strategy, e.g. depth-first search.
     * Instead, the ORDER-attribute is partially updated upon various drag-&-drop operations, which can lead to
     * arbitrary ORDER-values, e.g. a view can have the same ORDER-value as a child.</p>
     *
     * @param structure the logical division
     * @return a sorted list of Views and LogicalDivisions, each pair only containing one or the other
     */
    public static List<Pair<View, LogicalDivision>> mergeLogicalStructureViewsAndChildren(LogicalDivision structure) {
        List<Pair<View, LogicalDivision>> merged = new ArrayList<>();

        Iterator<View> viewIterator = structure.getViews().iterator();
        Iterator<LogicalDivision> childIterator = structure.getChildren().iterator();
        View nextView = null;
        LogicalDivision nextChild = null;

        // iterate through both the list of children and views at the same time
        while (viewIterator.hasNext() || childIterator.hasNext() || Objects.nonNull(nextView) || Objects.nonNull(nextChild)) {
            // pull the next view from the list of remaining views
            if (Objects.isNull(nextView) && viewIterator.hasNext()) {
                nextView = viewIterator.next();
            }
            // pull the next child from the list of remaining children
            if (Objects.isNull(nextChild) && childIterator.hasNext()) {
                nextChild = childIterator.next();
            }

            // decide on whether to add child or view first
            boolean addChildNext;
            if (Objects.nonNull(nextChild) && Objects.nonNull(nextView)) {
                // compare order attribute between child and view to figure out which one is added first in tree
                addChildNext = nextChild.getOrder() <= nextView.getPhysicalDivision().getOrder();
            } else {
                addChildNext = Objects.nonNull(nextChild);
            }

            // add child or view to the merged result list
            if (addChildNext) {
                merged.add(new ImmutablePair<>(null, nextChild));
                nextChild = null;
            } else {
                merged.add(new ImmutablePair<>(nextView, null));
                nextView = null;
            }
        }

        return merged;
    }

    /**
     * Finds canonical id for view by checking all folders.
     *
     * @param view the view
     * @return string representing media canonical id
     */
    public String findCanonicalIdForView(View view) {
        PhysicalDivision mediaUnit = view.getPhysicalDivision();
        Iterator<Entry<MediaVariant, URI>> mediaFileIterator = mediaUnit.getMediaFiles().entrySet().iterator();
        String canonical = "-";
        if (mediaFileIterator.hasNext()) {
            Entry<MediaVariant, URI> mediaFileEntry = mediaFileIterator.next();
            Subfolder subfolder = this.subfoldersCache.computeIfAbsent(mediaFileEntry.getKey().getUse(),
                    use -> new Subfolder(dataEditor.getProcess(), dataEditor.getProcess().getProject().getFolders()
                            .parallelStream().filter(folder -> folder.getFileGroup().equals(use)).findAny()
                            .orElseThrow(() ->  new IllegalStateException("Missing folder with file group \"" + use
                                    + "\" in project \"" + dataEditor.getProcess().getProject().getTitle()))));
            canonical = subfolder.getCanonical(mediaFileEntry.getValue());
        }
        return canonical;
    }

    /**
     * Builds the display text for a MediaUnit in the StructurePanel.
     * Using a regular expression to strip leading zeros. (?!$) lookahead ensures
     * that not the entire string will be matched. Using Hashmap for subfolder caching
     *
     * @param view
     *            View which holds the MediaUnit
     * @return the display label of the MediaUnit
     */
    public String buildViewLabel(View view) {
        String canonical = findCanonicalIdForView(view);
        PhysicalDivision mediaUnit = view.getPhysicalDivision();
        return canonical.replaceFirst("^0+(?!$)", "") + " : "
            + (Objects.isNull(mediaUnit.getOrderlabel()) ? "uncounted" : mediaUnit.getOrderlabel());
    }

    /**
     * Adds a tree node to the given parent node. The tree node is set to
     * ‘expanded’.
     *
     * @param parentProcess
     *            parent process of current process
     * @param type
     *            the internal name of the type of node, to be resolved through
     *            the rule set
     * @param parent
     *            parent node to which the new node is to be added
     * @return the generated node so that you can add children to it
     */
    private DefaultTreeNode addTreeNode(Process parentProcess, String type, DefaultTreeNode parent) {
        StructuralElementViewInterface structuralElementView = dataEditor.getRulesetManagement().getStructuralElementView(type,
            dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
        return addTreeNode("[" + parentProcess.getId() + "] " + structuralElementView.getLabel() + " - "
                        + parentProcess.getTitle(), structuralElementView.isUndefined(), true, null, parent);
    }

    /**
     * Adds a tree node to the given parent node. The tree node is set to
     * ‘expanded’.
     *
     * @param label
     *            Label of the tree node displayed to the user
     * @param undefined
     *            whether the given type in the rule set is undefined. If so,
     *            the node is highlighted in color and marked with a warning
     *            symbol.
     * @param linked
     *            whether the node is a link. If so, it will be marked with a
     *            link symbol.
     * @param dataObject
     *            the internal object represented by the node
     * @param parent
     *            parent node to which the new node is to be added
     * @return the generated node so that you can add children to it
     */
    private DefaultTreeNode addTreeNode(String label, boolean undefined, boolean linked, Object dataObject,
            DefaultTreeNode parent) {
        DefaultTreeNode node = new DefaultTreeNode(new StructureTreeNode(label, null, undefined, linked, dataObject),
                parent);
        if (dataObject instanceof PhysicalDivision && physicalNodeStateUnknown(this.previousExpansionStatesPhysicalTree, node)
                || dataObject instanceof LogicalDivision
                && logicalNodeStateUnknown(this.previousExpansionStatesLogicalTree, node)) {
            node.setExpanded(true);
        }
        return node;
    }

    /**
     * Recursively adds the parent processes in the display. For each parent
     * process, the recursion is run through once, that is for a newspaper issue
     * twice (annual process, overall process). If this fails (child is not
     * found in the parent process, or I/O error), instead only a link is added
     * to the process and the warning sign is activated.
     *
     * @param child
     *            child process, calling recursion
     * @param tree
     *            list of structure trees, in this list the parent links are
     *            inserted on top, therefore LinkedList
     */
    private void addParentLinksRecursive(Process child, DefaultTreeNode tree) {
        Process parent = child.getParent();
        // Termination condition of recursion, if the process has no parent
        if (Objects.isNull(parent)) {
            return;
        }
        // Process parent link of the parent recursively
        addParentLinksRecursive(parent, tree);
        URI uri = ServiceManager.getProcessService().getMetadataFileUri(parent);
        try {
            LogicalDivision logicalStructure = ServiceManager.getMetsService().loadWorkpiece(uri).getLogicalStructure();
            List<LogicalDivision> logicalDivisionList
                    = MetadataEditor.determineLogicalDivisionPathToChild(logicalStructure, child.getId());
            DefaultTreeNode parentNode = tree;
            if (logicalDivisionList.isEmpty()) {
                /*
                 * Error case: The child is not linked in the parent process.
                 * Show the process title of the parent process and a warning
                 * sign.
                 */
                addTreeNode(parent.getTitle(), true, true, parent, tree).setType(STRUCTURE_NODE_TYPE);
            } else {
                /*
                 * Default case: Show the path through the parent process to the
                 * linked child
                 */
                for (LogicalDivision logicalDivision : logicalDivisionList) {
                    if (Objects.isNull(logicalDivision.getType())) {
                        break;
                    } else {
                        parentNode = addTreeNode(parent, logicalDivision.getType(), parentNode);
                        parentNode.setType(STRUCTURE_NODE_TYPE);
                        parentNode.setExpanded(true);
                    }
                }
            }
        } catch (IOException e) {
            /*
             * Error case: The metadata file of the parent process cannot be
             * loaded. Show the process title of the parent process and the
             * warning sign.
             */
            Helper.setErrorMessage("metadataReadError", e.getMessage(), logger, e);
            addTreeNode(parent.getTitle(), true, true, parent, tree).setType(STRUCTURE_NODE_TYPE);
        }
    }

    /**
     * Builds the parent link tree in a temporary primefaces tree in order to determine how many
     * nodes are added to the tree. The number of nodes influences the order of nodes in the logical
     * structure tree and is needed to determine the correct tree node id, see
     * `GalleryPanel.addStripesRecursive`.
     *
     * @return the number of root nodes (first level children) that are
     *         added as a result of calling `addParentLinksRecursive`.
     */
    public Integer getNumberOfParentLinkRootNodesAdded() {
        DefaultTreeNode node = new DefaultTreeNode();
        addParentLinksRecursive(dataEditor.getProcess(), node);
        return node.getChildCount();
    }

    /**
     * Creates the media tree.
     *
     * @param mediaRoot
     *            root of physical divisions to show on the tree
     * @return the media tree
     */
    private DefaultTreeNode buildMediaTree(PhysicalDivision mediaRoot) {
        DefaultTreeNode rootTreeNode = new DefaultTreeNode();
        rootTreeNode.setType(PHYS_STRUCTURE_NODE_TYPE);
        if (physicalNodeStateUnknown(this.previousExpansionStatesPhysicalTree, rootTreeNode)) {
            rootTreeNode.setExpanded(true);
        }
        buildMediaTreeRecursively(mediaRoot, rootTreeNode);
        return rootTreeNode;
    }

    private void buildMediaTreeRecursively(PhysicalDivision physicalDivision, DefaultTreeNode parentTreeNode) {
        StructuralElementViewInterface divisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                physicalDivision.getType(), dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());
        DefaultTreeNode treeNode = addTreeNode(Objects.equals(physicalDivision.getType(), PhysicalDivision.TYPE_PAGE)
                        ? divisionView.getLabel().concat(" " + physicalDivision.getOrderlabel()) : divisionView.getLabel(),
                false, false, physicalDivision, parentTreeNode);

        if (PhysicalDivision.TYPE_TRACK.equals(physicalDivision.getType())) {
            treeNode.setType(MEDIA_PARTIAL_NODE_TYPE);
        } else if (PhysicalDivision.TYPES.contains(physicalDivision.getType())) {
            treeNode.setType(MEDIA_NODE_TYPE);
        } else {
            treeNode.setType(PHYS_STRUCTURE_NODE_TYPE);
        }

        if (physicalNodeStateUnknown(this.previousExpansionStatesPhysicalTree, treeNode)) {
            treeNode.setExpanded(true);
        }
        if (Objects.nonNull(physicalDivision.getChildren())) {
            for (PhysicalDivision child : physicalDivision.getChildren()) {
                buildMediaTreeRecursively(child, treeNode);
            }
        }
    }

    /**
     * Callback function triggered when a node is selected in the logical structure tree.
     *
     * @param event
     *            NodeSelectEvent triggered by logical node being selected
     */
    public void treeLogicalSelect(NodeSelectEvent event) {
        /*
         * The newly selected element has already been set in 'selectedLogicalNodes' by
         * JSF at this point.
         */
        try {
            // find selected physical divisions
            List<TreeNode> selectedTreeNodes = getSelectedLogicalNodes();
            List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions = selectedTreeNodes.stream()
                .map(StructureTreeOperations::getPhysicalDivisionPairFromTreeNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // find selected logical divisions
            List<LogicalDivision> selectedLogicalDivisions = selectedTreeNodes.stream()
                .map(StructureTreeOperations::getLogicalDivisionFromTreeNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // update selection throughout meta data editor
            dataEditor.updateSelection(selectedPhysicalDivisions, selectedLogicalDivisions);

            previouslySelectedLogicalNodes = getSelectedLogicalNodes();
        } catch (NoSuchMetadataFieldException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            setSelectedLogicalNodes(previouslySelectedLogicalNodes);
        }
    }

    /**
     * Callback function triggered when a node is selected in the physical structure tree.
     *
     * @param event
     *            NodeSelectEvent triggered by logical node being selected
     */
    public void treePhysicalSelect(NodeSelectEvent event) {
        /*
         * The newly selected element has already been set in 'selectedPhysicalNode' by
         * JSF at this point.
         */
        try {
            // find selected physical divisions
            List<TreeNode> selectedTreeNodes = getSelectedPhysicalNodes();
            List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions = selectedTreeNodes.stream()
                .map(StructureTreeOperations::getPhysicalDivisionPairFromTreeNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // update selection throughout meta data editor
            dataEditor.updateSelection(selectedPhysicalDivisions, Collections.emptyList());

            previouslySelectedPhysicalNodes = getSelectedPhysicalNodes();
        } catch (NoSuchMetadataFieldException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            setSelectedPhysicalNodes(previouslySelectedPhysicalNodes);
        }
    }

    /**
     * Overwrite both physical and logical trees with new tree node selection from outside user interactions 
     * (e.g. gallery, pagination panel).
     * 
     * @param selectedPhysicalDivisions the list of selected physical divisions (and their parent logical divisions)
     * @param selectedLogicalDivisions the list of selected logical divisions
     */
    public void updateNodeSelection(
        List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions, 
        List<LogicalDivision> selectedLogicalDivisions
    ) { 
        // update logical tree
        this.updateLogicalTreeNodeSelection(selectedPhysicalDivisions, selectedLogicalDivisions);

        // update physical tree if available
        if (this.isSeparateMedia()) {
            this.updatePhysicalTreeNodeSelection(selectedPhysicalDivisions);
        }
    }

    /** 
     * Overwrite physical tree with new tree node selection from outside user interactions 
     * (e.g. gallery, pagination panel).
     */
    private void updatePhysicalTreeNodeSelection(List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions) {
        if (Objects.nonNull(physicalTree)) {
            // clear tree node selection
            StructureTreeOperations.clearTreeNodeSelection(physicalTree);

            // find tree nodes matching selected physical divisions
            Set<TreeNode> selectedTreeNodes = StructureTreeOperations.findTreeNodesMatchingDivisions(
                physicalTree, selectedPhysicalDivisions, Collections.emptyList()
            );

            // select those tree nodes
            StructureTreeOperations.selectTreeNodes(selectedTreeNodes);

            // remember new selection
            previouslySelectedPhysicalNodes = getSelectedPhysicalNodes();
            setSelectedPhysicalNodes(new ArrayList<>(selectedTreeNodes));
        }
    }

    /** 
     * Overwrite logical tree with new tree node selection from outside user interactions 
     * (e.g. gallery, pagination panel).
     */
    private void updateLogicalTreeNodeSelection(
        List<Pair<PhysicalDivision, LogicalDivision>> selectedPhysicalDivisions, 
        List<LogicalDivision> selectedLogicalDivisions
    ) {
        if (Objects.nonNull(logicalTree)) {
            // clear tree node selection
            StructureTreeOperations.clearTreeNodeSelection(logicalTree);

            // find tree nodes matching selected logical and physical divisions
            Set<TreeNode> selectedTreeNodes = StructureTreeOperations.findTreeNodesMatchingDivisions(
                logicalTree, selectedPhysicalDivisions, selectedLogicalDivisions
            );

            // select those tree nodes
            StructureTreeOperations.selectTreeNodes(selectedTreeNodes);

            // remember new selection
            previouslySelectedLogicalNodes = getSelectedLogicalNodes();
            setSelectedLogicalNodes(new ArrayList<>(selectedTreeNodes));
        }
    }

    /**
     * Callback function triggered on NodeCollapseEvent. Sets the 'expanded' flag of the corresponding tree node to
     * 'false' because this is not done automatically by PrimeFaces on a NodeCollapseEvent.
     *
     * @param event
     *          the NodeCollapseEvent triggered in the corresponding structure tree
     */
    public void onNodeCollapse(NodeCollapseEvent event) {
        if (Objects.nonNull(event) && Objects.nonNull(event.getTreeNode())) {
            event.getTreeNode().setExpanded(false);
        }
    }

    /**
     * Callback function triggered on NodeExpandEvent. Sets the 'expanded' flag of the corresponding tree node to
     * 'true' because this is not done automatically by PrimeFaces on a NodeExpandEvent.
     *
     * @param event
     *          the NodeExpandEvent triggered in the corresponding structure tree
     */
    public void onNodeExpand(NodeExpandEvent event) {
        if (Objects.nonNull(event) && Objects.nonNull(event.getTreeNode())) {
            event.getTreeNode().setExpanded(true);
        }
    }

    /**
     * Callback function triggered on TreeDragDropEvent. Checks whether performed drag'n'drop action is allowed
     * considering ruleset restrictions on structure hierarchy. In case some ruleset rules were violated by the action
     * displays a corresponding error message to the user and reverts tree to prior state.
     *
     * @param event TreeDragDropEvent
     *              event triggering this callback function
     */
    public void onDragDrop(TreeDragDropEvent event) {
        TreeNode dropTreeNode = event.getDropNode();
        Object dropNodeObject = dropTreeNode.getData();
        expandNode(dropTreeNode);

        try {
            boolean logicalMoved = false;
            boolean physicalMoved = false;
            boolean pageMoved = false;

            for (TreeNode dragTreeNode : event.getDragNodes()) {
                Object dragNodeObject = dragTreeNode.getData();
                StructureTreeNode dropNode = (StructureTreeNode) dropNodeObject;
                StructureTreeNode dragNode = (StructureTreeNode) dragNodeObject;

                if (dropNode.isLinked()) {
                    throw new IllegalArgumentException(Helper.getTranslation("dataEditor.dragNDropLinkError"));
                } else if (dragNode.getDataObject() instanceof LogicalDivision
                        && dropNode.getDataObject() instanceof LogicalDivision) {
                    logicalMoved |= checkLogicalDragDrop(dragNode, dropNode);
                } else if (dragNode.getDataObject() instanceof PhysicalDivision
                        && dropNode.getDataObject() instanceof PhysicalDivision) {
                    physicalMoved |= checkPhysicalDragDrop(dragNode, dropNode);
                } else if (dragNode.getDataObject() instanceof View
                        && dropNode.getDataObject() instanceof LogicalDivision) {
                    pageMoved |= movePageNode(dragTreeNode, dropNode, dragNode);
                } else {
                    throw new IllegalArgumentException(
                        Helper.getTranslation("dataEditor.dragNDropError", dragNode.getLabel(), dropNode.getLabel())
                    );
                }
            }

            preserveAfterDragDrop(logicalMoved, pageMoved, physicalMoved);

        } catch (IllegalArgumentException e) {
            // invalid drag and drop operation
            Helper.setErrorMessage(e.getMessage());
        } catch (Exception exception) {
            logger.error(exception.getLocalizedMessage(), exception);
        } finally {
            show(true);
        }
    }

    /**
     * Save drag drop changes in case no conflicts with ruleset were found.
     * 
     * @param logicalMoved whether a logical division was moved
     * @param pageMoved whether a page was moved
     * @param physicalMoved whether a physical division was moved
     */
    private void preserveAfterDragDrop(boolean logicalMoved, boolean pageMoved, boolean physicalMoved) {
        if (logicalMoved || pageMoved) {
            if (logicalStructureTreeContainsMedia()) {
                preserveLogicalAndPhysical();
            } else {
                preserveLogical();
            }
            this.dataEditor.getGalleryPanel().updateStripes();
            this.dataEditor.getPaginationPanel().show();
        }
        if (physicalMoved) {
            preservePhysical();
        }
    }

    /**
     * Determine the LogicalDivision to which the given View is assigned.
     *
     * @param view
     *          View for which the LogicalDivision is determined
     * @return the LogicalDivision to which the given View is assigned
     */
    LogicalDivision getPageStructure(View view, LogicalDivision parent) {
        LogicalDivision resultElement = null;
        for (LogicalDivision child : parent.getChildren()) {
            if (child.getViews().contains(view)) {
                resultElement = child;
            } else {
                resultElement =  getPageStructure(view, child);
            }
            if (Objects.nonNull(resultElement)) {
                break;
            }
        }
        return resultElement;
    }

    /**
     * Move page encapsulated in given StructureTreeNode 'dragNode' to Structural Element encapsulated in given
     * StructureTreeNode 'dropNode' at index encoded in given TreeDragDropEvent 'event'.
     *
     * @param dragTreeNode
     *          TreeNode that is being dragged
     * @param dropStructureNode
     *          StructureTreeNode containing the Structural Element to which the page is moved
     * @param dragStructureNode
     *          StructureTreeNode containing the View/Page that is moved
     */
    private boolean movePageNode(
        TreeNode dragTreeNode, 
        StructureTreeNode dropStructureNode, 
        StructureTreeNode dragStructureNode
    ) throws IllegalArgumentException {
        TreeNode dragParent = dragTreeNode.getParent();
        if (dragParent.getData() instanceof StructureTreeNode) {
            StructureTreeNode dragParentTreeNode = (StructureTreeNode) dragParent.getData();
            if (dragParentTreeNode.getDataObject() instanceof LogicalDivision) {
                // FIXME waiting for PrimeFaces' tree drop index bug to be fixed.
                // Until fixed dropping nodes onto other nodes will produce random drop indices.
                return true;
            } else {
                throw new IllegalArgumentException(
                    Helper.getTranslation("dataEditor.dragNDropError", dragStructureNode.getLabel(), dropStructureNode.getLabel()));
            }
        } else {
            throw new IllegalArgumentException(
                Helper.getTranslation("dataEditor.dragNDropError", dragStructureNode.getLabel(), dropStructureNode.getLabel()));
        }
    }

    /**
     * Change the order of the PhysicalDivisions in the workpiece.
     * When structure is saved to METS this is represented by the order of DIV elements in the physical structMap.
     * @param toElement logical element where to which the PhysicalDivisions are assigned
     * @param elementsToBeMoved List of PhysicalDivisions which are moved
     * @param insertionIndex index at which the PhysicalDivisions are added to the existing List of PhysicalDivisions.
     *                       The value -1 represents the end of the list.
     */
    void reorderPhysicalDivisions(LogicalDivision toElement,
                           List<Pair<View, LogicalDivision>> elementsToBeMoved,
                           int insertionIndex) {
        int physicalInsertionIndex;
        List<PhysicalDivision> physicalDivisionsToBeMoved = elementsToBeMoved.stream()
                .map(e -> e.getLeft().getPhysicalDivision())
                .collect(Collectors.toList());

        if (insertionIndex > toElement.getViews().size()) {
            Helper.setErrorMessage("Unsupported drag'n'drop operation: Insertion index exceeds list.");
            insertionIndex = -1;
        }

        if (insertionIndex < 0 || toElement.getViews().isEmpty()) {
            // no insertion position was specified or the element does not contain any pages yet
            physicalInsertionIndex = toElement.getOrder() - 1;
        } else {
            // if 'insertionIndex' equals the size of the list, it means we want to append the moved pages _behind_ the physical division of
            // the last view in the list of views of the 'toElement'
            if (insertionIndex == toElement.getViews().size()) {
                physicalInsertionIndex = toElement.getViews().getLast().getPhysicalDivision().getOrder();
            } else if (insertionIndex == 0) {
                // insert at first position directly after logical element
                physicalInsertionIndex = toElement.getOrder() - 1;
            } else {
                // insert at given index
                physicalInsertionIndex = toElement.getViews().get(insertionIndex).getPhysicalDivision().getOrder() - 1;
            }
        }

        if (physicalInsertionIndex > physicalDivisionsToBeMoved.stream()
                .map(PhysicalDivision::getOrder)
                .collect(Collectors.summarizingInt(Integer::intValue))
                .getMin() - 1) {
            int finalInsertionIndex = physicalInsertionIndex;
            physicalInsertionIndex -= (int) physicalDivisionsToBeMoved.stream().filter(m -> m.getOrder() - 1 < finalInsertionIndex).count();
        }
        dataEditor.getWorkpiece().getPhysicalStructure().getChildren().removeAll(physicalDivisionsToBeMoved);
        int numberOfChildren = dataEditor.getWorkpiece().getPhysicalStructure().getChildren().size();
        if (physicalInsertionIndex < numberOfChildren) {
            dataEditor.getWorkpiece().getPhysicalStructure().getChildren().addAll(physicalInsertionIndex, physicalDivisionsToBeMoved);
        } else {
            dataEditor.getWorkpiece().getPhysicalStructure().getChildren().addAll(physicalDivisionsToBeMoved);
            if (physicalInsertionIndex > numberOfChildren) {
                Helper.setErrorMessage("Could not append media at correct position. Index exceeded list.");
            }
        }
    }

    /**
     * Change order fields of physical elements. When saved to METS this is represented by the physical structMap divs'
     * "ORDER" attribute.
     */
    void changePhysicalOrderFields() {
        ServiceManager.getFileService().renumberPhysicalDivisions(dataEditor.getWorkpiece(), false);
    }

    /**
     * Change the order attribute of the logical elements that are affected by pages around them being moved.
     * @param toElement logical element the pages will be assigned to
     * @param elementsToBeMoved physical elements which are moved
     */
    void changeLogicalOrderFields(LogicalDivision toElement, List<Pair<View, LogicalDivision>> elementsToBeMoved,
                                  int insertionIndex) {
        HashMap<Integer, List<LogicalDivision>> logicalElementsByOrder = new HashMap<>();
        for (LogicalDivision logicalElement : dataEditor.getWorkpiece().getAllLogicalDivisions()) {
            if (logicalElementsByOrder.containsKey(logicalElement.getOrder()))  {
                logicalElementsByOrder.get(logicalElement.getOrder()).add(logicalElement);
            } else {
                logicalElementsByOrder.put(logicalElement.getOrder(), new LinkedList<>(Collections.singletonList(logicalElement)));
            }
        }

        // Order values of moved pages and target element. Logical elements located between these Order values are affected.
        List<Integer> ordersAffectedByMove = getOrdersAffectedByMove(elementsToBeMoved, toElement);

        /* The new Order value for the logical elements can be calculated quite simple:
        The Order values of elements located before the target element have to be modified by -i - 1.
        The Order values of elements located after the target element have to be modified by the size of ordersAffectedByMove - i.
        (ordersAffectedByMove equals to the number of moved pages + the target element.)
         */

        for (Map.Entry<Integer, List<LogicalDivision>> entry : logicalElementsByOrder.entrySet()) {
            for (int i = 0; i < ordersAffectedByMove.size() - 1; i++) {
                if (ordersAffectedByMove.get(i) < entry.getKey() && entry.getKey() < ordersAffectedByMove.get(i + 1)) {
                    if (ordersAffectedByMove.get(i) < toElement.getOrder()) {
                        updateOrder(entry.getValue(), -i - 1);
                    } else if (ordersAffectedByMove.get(i) > toElement.getOrder()) {
                        updateOrder(entry.getValue(), ordersAffectedByMove.size() - i);
                    }
                }
            }
            // check if elements exist with the same order like toElement (the toElememt itself might be affected as well)
            if (entry.getKey() == toElement.getOrder()) {
                List<LogicalDivision> beforeToElement = entry.getValue().subList(0, entry.getValue().indexOf(toElement) + 1);
                List<LogicalDivision> afterToElement = entry.getValue().subList(entry.getValue().indexOf(toElement) + 1,
                        entry.getValue().size());
                /* toElement at index 0 means we're in an edge case: toElement is the first order which is affected (no pages with smaller
                order affected) and its order will not change, nor will other elements with the same order before it. */
                if (ordersAffectedByMove.indexOf(toElement.getOrder()) > 0) {
                    updateOrder(beforeToElement, -ordersAffectedByMove.indexOf(entry.getKey()));
                }
                /* Order of elements directly after toElement (with same order) only have to be update if the pages are inserted at the
                first position. If they are inserted after any pages, the order of elements in afterToElement will not change. */
                if (insertionIndex == 0) {
                    updateOrder(afterToElement, elementsToBeMoved.size() - ordersAffectedByMove.indexOf(toElement.getOrder()));
                }
            }
        }
    }

    private List<Integer> getOrdersAffectedByMove(List<Pair<View, LogicalDivision>> views, LogicalDivision toElement) {
        Set<Integer> ordersAffectedByMove = views.stream()
                .map(e -> e.getLeft().getPhysicalDivision().getOrder())
                .collect(Collectors.toSet());
        ordersAffectedByMove.add(toElement.getOrder());
        return ordersAffectedByMove.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    private void updateOrder(List<LogicalDivision> elementsToBeUpdated, int delta) {
        for (LogicalDivision element : elementsToBeUpdated) {
            element.setOrder(element.getOrder() + delta);
        }
    }

    /**
     * Move List of elements 'elementsToBeMoved' from LogicalDivision in each Pair to LogicalDivision
     * 'toElement'.
     *
     * @param toElement
     *          LogicalDivision to which View is moved
     * @param elementsToBeMoved
     *          List of elements to be moved as Pairs of View and LogicalDivision they are attached to
     * @param insertionIndex
     *          Index where views will be inserted into toElement's views
     */
    void moveViews(LogicalDivision toElement,
                   List<Pair<View, LogicalDivision>> elementsToBeMoved,
                   int insertionIndex) {
        List<View> views = elementsToBeMoved.stream()
                .map(Pair::getKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (insertionIndex < 0 || insertionIndex == toElement.getViews().size()) {
            toElement.getViews().addAll(views);
        } else {
            toElement.getViews().addAll(insertionIndex, views);
        }

        for (Pair<View, LogicalDivision> elementToBeMoved : elementsToBeMoved) {
            boolean removeLastOccurrenceOfView = toElement.equals(elementToBeMoved.getValue())
                    && insertionIndex < elementToBeMoved.getValue().getViews().lastIndexOf(elementToBeMoved.getKey());
            dataEditor.unassignView(elementToBeMoved.getValue(), elementToBeMoved.getKey(), removeLastOccurrenceOfView);
            elementToBeMoved.getKey().getPhysicalDivision().getLogicalDivisions().add(toElement);
        }
    }

    private boolean checkLogicalDragDrop(StructureTreeNode dragNode, StructureTreeNode dropNode) throws IllegalArgumentException {
        LogicalDivision dragStructure = (LogicalDivision) dragNode.getDataObject();
        LogicalDivision dropStructure = (LogicalDivision) dropNode.getDataObject();

        StructuralElementViewInterface divisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                dropStructure.getType(), dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());

        LinkedList<LogicalDivision> dragParents;
        if (divisionView.getAllowedSubstructuralElements().containsKey(dragStructure.getType())
                || Objects.nonNull(dragStructure.getLink())) {
            dragParents = MetadataEditor.getAncestorsOfLogicalDivision(dragStructure,
                    dataEditor.getWorkpiece().getLogicalStructure());
            if (!dragParents.isEmpty()) {
                LogicalDivision parentStructure = dragParents.get(dragParents.size() - 1);
                if (parentStructure.getChildren().contains(dragStructure)) {
                    return true;
                } else {
                    throw new IllegalArgumentException(Helper.getTranslation("dataEditor.childNotContainedError",
                        dragNode.getLabel()));
                }
            } else {
                throw new IllegalArgumentException(Helper.getTranslation("dataEditor.noParentsError",
                    dragNode.getLabel()));
            }
        } else {
            throw new IllegalArgumentException(Helper.getTranslation("dataEditor.forbiddenChildElement",
                dragNode.getLabel(), dropNode.getLabel()));
        }
    }

    private boolean checkPhysicalDragDrop(StructureTreeNode dragNode, StructureTreeNode dropNode) throws IllegalArgumentException {
        PhysicalDivision dragUnit = (PhysicalDivision) dragNode.getDataObject();
        PhysicalDivision dropUnit = (PhysicalDivision) dropNode.getDataObject();

        StructuralElementViewInterface divisionView = dataEditor.getRulesetManagement().getStructuralElementView(
                dropUnit.getType(), dataEditor.getAcquisitionStage(), dataEditor.getPriorityList());

        LinkedList<PhysicalDivision> dragParents;
        if (divisionView.getAllowedSubstructuralElements().containsKey(dragUnit.getType())) {
            dragParents = MetadataEditor.getAncestorsOfPhysicalDivision(dragUnit, dataEditor.getWorkpiece().getPhysicalStructure());
            if (dragParents.isEmpty()) {
                throw new IllegalArgumentException(Helper.getTranslation("dataEditor.noParentsError",
                    dragNode.getLabel()));
            } else {
                PhysicalDivision parentUnit = dragParents.get(dragParents.size() - 1);
                if (parentUnit.getChildren().contains(dragUnit)) {
                    return true;
                } else {
                    throw new IllegalArgumentException(Helper.getTranslation("dataEditor.childNotContainedError",
                        dragUnit.getType()));
                }
            }
        } else {
            throw new IllegalArgumentException(Helper.getTranslation("dataEditor.forbiddenChildElement",
                dragNode.getLabel(), dropNode.getLabel()));
        }
    }

    private void preserveLogicalAndPhysical() throws UnknownTreeNodeDataException {
        if (!this.logicalTree.getChildren().isEmpty()) {
            order = 1;
            for (PhysicalDivision physicalDivision : dataEditor.getWorkpiece().getPhysicalStructure().getChildren()) {
                physicalDivision.getLogicalDivisions().clear();
            }
            dataEditor.getWorkpiece().getPhysicalStructure().getChildren().clear();
            preserveLogicalAndPhysicalRecursive(this.logicalTree.getChildren().get(logicalTree.getChildCount() - 1));
        }
    }

    private LogicalDivision preserveLogicalAndPhysicalRecursive(TreeNode treeNode) throws UnknownTreeNodeDataException {
        StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
        if (Objects.isNull(structureTreeNode) || !(structureTreeNode.getDataObject() instanceof LogicalDivision)) {
            return null;
        }
        LogicalDivision structure = (LogicalDivision) structureTreeNode.getDataObject();
        structure.setOrder(order);
        structure.getViews().clear();
        structure.getChildren().clear();
        for (TreeNode child : treeNode.getChildren()) {
            if (!(child.getData() instanceof StructureTreeNode)) {
                throw new UnknownTreeNodeDataException(child.getData().getClass().getCanonicalName());
            }
            if (((StructureTreeNode) child.getData()).getDataObject() instanceof LogicalDivision) {
                LogicalDivision possibleChildStructure = preserveLogicalAndPhysicalRecursive(child);
                if (Objects.nonNull(possibleChildStructure)) {
                    structure.getChildren().add(possibleChildStructure);
                }
            } else if (((StructureTreeNode) child.getData()).getDataObject() instanceof View) {
                View view = (View) ((StructureTreeNode) child.getData()).getDataObject();
                structure.getViews().add(view);
                if (!dataEditor.getWorkpiece().getAllPhysicalDivisions().contains(view.getPhysicalDivision())) {
                    view.getPhysicalDivision().setOrder(order);
                    dataEditor.getWorkpiece().getPhysicalStructure().getChildren().add(view.getPhysicalDivision());
                    order++;
                }
                if (!view.getPhysicalDivision().getLogicalDivisions().contains(structure)) {
                    view.getPhysicalDivision().getLogicalDivisions().add(structure);
                }
            }
        }
        /*
            PhysicalDivisions assigned to multiple LogicalDivisions may lead to wrong order value. The order will be
            incremented for each occurrence and not just the last one. The LogicalDivisions containing those
            PhysicalDivisions must be set to the order value of their first PhysicalDivision.
         */
        if (!structure.getViews().isEmpty()) {
            structure.setOrder(structure.getViews().getFirst().getPhysicalDivision().getOrder());
        }
        return structure;
    }

    /**
     * Check and return whether the metadata of a process should be displayed in separate logical and physical
     * structure trees or in one unified structure tree.
     *
     * @return
     *          whether metadata structure should be displayed in separate structure trees or not
     */
    public boolean isSeparateMedia() {
        Process process = dataEditor.getProcess();
        if (Objects.nonNull(process)) {
            Template template = process.getTemplate();
            if ( Objects.nonNull(template) ) {
                return template.getWorkflow().isSeparateStructure();
            }
        }
        return false;
    }

    private void expandNode(TreeNode node) {
        if (Objects.nonNull(node)) {
            node.setExpanded(true);
            expandNode(node.getParent());
        }
    }

    private HashMap<LogicalDivision, Boolean> getLogicalTreeNodeExpansionStates(DefaultTreeNode tree) {
        if (Objects.nonNull(tree) && tree.getChildCount() == 1) {
            TreeNode treeRoot = tree.getChildren().get(0);
            LogicalDivision structuralElement = getTreeNodeStructuralElement(treeRoot);
            if (Objects.nonNull(structuralElement)) {
                return getLogicalTreeNodeExpansionStatesRecursively(treeRoot, new HashMap<>());
            }
        }
        return new HashMap<>();
    }

    private HashMap<LogicalDivision, Boolean> getLogicalTreeNodeExpansionStatesRecursively(TreeNode treeNode,
            HashMap<LogicalDivision, Boolean> expansionStates) {
        if (Objects.nonNull(treeNode)) {
            LogicalDivision structureData = getTreeNodeStructuralElement(treeNode);
            if (Objects.nonNull(structureData)) {
                expansionStates.put(structureData, treeNode.isExpanded());
                for (TreeNode childNode : treeNode.getChildren()) {
                    expansionStates.putAll(getLogicalTreeNodeExpansionStatesRecursively(childNode, expansionStates));
                }
            }
        }
        return expansionStates;
    }

    private HashMap<PhysicalDivision, Boolean> getPhysicalTreeNodeExpansionStates(DefaultTreeNode tree) {
        if (Objects.nonNull(tree) && tree.getChildCount() == 1) {
            TreeNode treeRoot = tree.getChildren().get(0);
            PhysicalDivision physicalDivision = getTreeNodePhysicalDivision(treeRoot);
            if (Objects.nonNull(physicalDivision)) {
                return getPhysicalTreeNodeExpansionStatesRecursively(treeRoot, new HashMap<>());
            }
        }
        return new HashMap<>();
    }

    private HashMap<PhysicalDivision, Boolean> getPhysicalTreeNodeExpansionStatesRecursively(TreeNode treeNode,
            HashMap<PhysicalDivision, Boolean> expansionStates) {
        if (Objects.nonNull(treeNode)) {
            PhysicalDivision physicalDivision = getTreeNodePhysicalDivision(treeNode);
            if (Objects.nonNull(physicalDivision)) {
                expansionStates.put(physicalDivision, treeNode.isExpanded());
                for (TreeNode childNode : treeNode.getChildren()) {
                    expansionStates.putAll(getPhysicalTreeNodeExpansionStatesRecursively(childNode, expansionStates));
                }
            }
        }
        return expansionStates;
    }

    private void updateLogicalNodeExpansionStates(DefaultTreeNode tree, HashMap<LogicalDivision, Boolean> expansionStates) {
        if (Objects.nonNull(tree) && Objects.nonNull(expansionStates) && !expansionStates.isEmpty()) {
            updateNodeExpansionStatesRecursively(tree, expansionStates);
        }
    }

    private void updateNodeExpansionStatesRecursively(TreeNode treeNode, HashMap<LogicalDivision, Boolean> expansionStates) {
        LogicalDivision element = getTreeNodeStructuralElement(treeNode);
        if (Objects.nonNull(element) && expansionStates.containsKey(element)) {
            treeNode.setExpanded(expansionStates.get(element));
        }
        for (TreeNode childNode : treeNode.getChildren()) {
            updateNodeExpansionStatesRecursively(childNode, expansionStates);
        }
    }

    private void updatePhysicalNodeExpansionStates(DefaultTreeNode tree, HashMap<PhysicalDivision, Boolean> expansionStates) {
        if (Objects.nonNull(tree) && Objects.nonNull(expansionStates) && !expansionStates.isEmpty()) {
            updatePhysicalNodeExpansionStatesRecursively(tree, expansionStates);
        }
    }

    private void updatePhysicalNodeExpansionStatesRecursively(TreeNode treeNode, HashMap<PhysicalDivision, Boolean> expansionStates) {
        PhysicalDivision physicalDivision = getTreeNodePhysicalDivision(treeNode);
        if (Objects.nonNull(physicalDivision) && expansionStates.containsKey(physicalDivision)) {
            treeNode.setExpanded(expansionStates.get(physicalDivision));
        }
        for (TreeNode childNode : treeNode.getChildren()) {
            updatePhysicalNodeExpansionStatesRecursively(childNode, expansionStates);
        }
    }

    private boolean logicalNodeStateUnknown(HashMap<LogicalDivision, Boolean> expansionStates, TreeNode treeNode) {
        LogicalDivision element = getTreeNodeStructuralElement(treeNode);
        return !Objects.nonNull(expansionStates) || (Objects.nonNull(element) && !expansionStates.containsKey(element));
    }

    private boolean physicalNodeStateUnknown(HashMap<PhysicalDivision, Boolean> expanionStates, TreeNode treeNode) {
        PhysicalDivision physicalDivision = getTreeNodePhysicalDivision(treeNode);
        return Objects.isNull(expanionStates) || (Objects.nonNull(physicalDivision) && !expanionStates.containsKey(physicalDivision));
    }

    private LogicalDivision getTreeNodeStructuralElement(TreeNode treeNode) {
        if (Objects.nonNull(treeNode) && treeNode.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
            if (structureTreeNode.getDataObject() instanceof LogicalDivision) {
                return (LogicalDivision) structureTreeNode.getDataObject();
            }
        }
        return null;
    }

    private PhysicalDivision getTreeNodePhysicalDivision(TreeNode treeNode) {
        if (Objects.nonNull(treeNode) && treeNode.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
            if (structureTreeNode.getDataObject() instanceof PhysicalDivision) {
                return (PhysicalDivision) structureTreeNode.getDataObject();
            }
        }
        return null;
    }

    private View getTreeNodeView(TreeNode treeNode) {
        if (Objects.nonNull(treeNode) && treeNode.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) treeNode.getData();
            if (structureTreeNode.getDataObject() instanceof View) {
                return (View) structureTreeNode.getDataObject();
            }
        }
        return null;
    }

    /**
     * Get List of PhysicalDivisions assigned to multiple LogicalDivisions.
     *
     * @return value of severalAssignments
     */
    List<PhysicalDivision> getSeveralAssignments() {
        return severalAssignments;
    }

    /**
     * Get activeTabs.
     *
     * @return value of activeTabs
     */
    public String getActiveTabs() {
        return activeTabs;
    }

    /**
     * Set activeTabs.
     *
     * @param activeTabs as java.lang.String
     */
    public void setActiveTabs(String activeTabs) {
        this.activeTabs = activeTabs;
    }

    /**
     * Get the index of this StructureTreeNode's PhysicalDivision out of all
     * PhysicalDivisions which are assigned to more than one LogicalDivision.
     *
     * @param treeNode
     *            object to find the index for
     * @return index of the StructureTreeNode's PhysicalDivision if present in
     *         the List of several assignments, or -1 if not present in the
     *         list.
     */
    public int getMultipleAssignmentsIndex(StructureTreeNode treeNode) {
        if (treeNode.getDataObject() instanceof View
                && Objects.nonNull(((View) treeNode.getDataObject()).getPhysicalDivision())) {
            return severalAssignments.indexOf(((View) treeNode.getDataObject()).getPhysicalDivision());
        }
        return -1;
    }

    /**
     * Check if the selected Node's PhysicalDivision is assigned to several LogicalDivisions.
     *
     * @return {@code true} when the PhysicalDivision is assigned to more than one logical element
     */
    public boolean isAssignedSeveralTimes() {
        TreeNode selected = getSelectedLogicalNodeIfSingle();
        if (Objects.nonNull(selected) && selected.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) selected.getData();
            if (structureTreeNode.getDataObject() instanceof View) {
                View view = (View) structureTreeNode.getDataObject();
                return view.getPhysicalDivision().getLogicalDivisions().size() > 1;
            }
        }
        return false;
    }

    /**
     * Find the next logical structure node that can be used to create a new link to the currently selected node. 
     * The node needs to be the last node amongst its siblings.
     * 
     * @param node the tree node of the currently selected physical devision node
     * @return the next logical tree node 
     */
    private TreeNode findNextLogicalNodeForViewAssignment(TreeNode node) {
        if (Objects.isNull(getTreeNodeView(node))) {
            // node is not a view
            return null;
        }

        List<TreeNode> viewSiblings = node.getParent().getChildren();
        if (viewSiblings.indexOf(node) != viewSiblings.size() - 1) {
            // view is not last view amongst siblings
            return null;
        }

        // pseudo-recursively find next logical node
        return findNextLogicalNodeForViewAssignmentRecursive(node.getParent());
    }

    /**
     * Find the next logical structure node that can be used to create a link by pseudo-recursively iterating over 
     * logical parent and logical children nodes. 
     * 
     * @param node the tree node of the logical division
     * @return the tree node of the next logical division
     */
    private TreeNode findNextLogicalNodeForViewAssignmentRecursive(TreeNode node) {
        TreeNode current = node;

        while (Objects.nonNull(current)) {
            if (Objects.isNull(getTreeNodeStructuralElement(current))) {
                // node is not a logical node
                return null;
            }

            // check whether next sibling is a logical node as well
            List<TreeNode> currentSiblings = current.getParent().getChildren();
            int currentIndex = currentSiblings.indexOf(current);

            if (currentSiblings.size() > currentIndex + 1) {
                TreeNode nextSibling = currentSiblings.get(currentIndex + 1);
                if (Objects.isNull(getTreeNodeStructuralElement(nextSibling))) {
                    // next sibling is not a logical node
                    return null;
                }

                // next sibling is a logical node and potential valid result, unless there are children
                TreeNode nextLogical = nextSibling;

                // check sibling has children (with first child being another logical node)
                while (!nextLogical.getChildren().isEmpty()) {
                    TreeNode firstChild = nextLogical.getChildren().get(0);
                    if (Objects.isNull(getTreeNodeStructuralElement(firstChild))) {
                        // first child is not a logical node
                        return nextLogical;
                    }
                    // iterate to child node
                    nextLogical = firstChild;
                }
                return nextLogical;
            }

            // node is last amongst siblings
            // iterate to parent node
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if the selected Node's PhysicalDivision can be assigned to the next logical element in addition to the 
     * current assignment.
     * 
     * @return {@code true} if the PhysicalDivision can be assigned to the next LogicalDivision
     */
    public boolean isAssignableSeveralTimes() {
        TreeNode selectedLogicalNode = getSelectedLogicalNodeIfSingle();
        if (Objects.isNull(selectedLogicalNode)) {
            return false;
        }
        TreeNode nextLogical = findNextLogicalNodeForViewAssignment(selectedLogicalNode);
        if (Objects.nonNull(nextLogical)) {
            // check whether first child is already view of current node (too avoid adding views multiple times)
            if (!nextLogical.getChildren().isEmpty()) {
                TreeNode childNode = nextLogical.getChildren().get(0);
                View childNodeView = getTreeNodeView(childNode);
                View selectedView = getTreeNodeView(selectedLogicalNode);
                if (Objects.nonNull(childNodeView) && Objects.nonNull(selectedView)) {
                    if (childNodeView.equals(selectedView)) {
                        // first child is already a view for the currently selected node
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Assign selected Node's PhysicalDivision to the next LogicalDivision.
     */
    public void assign() {
        TreeNode selectedLogicalNode = getSelectedLogicalNodeIfSingle();
        if (Objects.isNull(selectedLogicalNode)) {
            logger.error("assign called without selection or too many selected, should not happen");
            return;
        }
        TreeNode nextLogical = findNextLogicalNodeForViewAssignment(selectedLogicalNode);
        if (Objects.nonNull(nextLogical)) {
            View view = (View) ((StructureTreeNode) selectedLogicalNode.getData()).getDataObject();
            View viewToAssign = new View();
            viewToAssign.setPhysicalDivision(view.getPhysicalDivision());
            StructureTreeNode structureTreeNodeSibling = (StructureTreeNode) nextLogical.getData();
            LogicalDivision logicalDivision = (LogicalDivision) structureTreeNodeSibling.getDataObject();
            dataEditor.assignView(logicalDivision, viewToAssign, 0);
            severalAssignments.add(viewToAssign.getPhysicalDivision());
            show();
            dataEditor.getSelectedMedia().clear();
            dataEditor.getGalleryPanel().updateStripes();
        }
    }

    /**
     * Unassign the selected Node's PhysicalDivision from the LogicalDivision parent at the selected position.
     * This does not remove it from other LogicalDivisions.
     */
    public void unassign() {
        TreeNode selectedLogicalNode = getSelectedLogicalNodeIfSingle();
        if (Objects.isNull(selectedLogicalNode)) {
            logger.error("unassign called without selection or too many selected, should not happen");
            return;
        }
        if (isAssignedSeveralTimes()) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) selectedLogicalNode.getData();
            View view = (View) structureTreeNode.getDataObject();
            if (selectedLogicalNode.getParent().getData() instanceof StructureTreeNode) {
                StructureTreeNode structureTreeNodeParent = (StructureTreeNode) selectedLogicalNode.getParent().getData();
                if (structureTreeNodeParent.getDataObject() instanceof LogicalDivision) {
                    LogicalDivision logicalDivision =
                            (LogicalDivision) structureTreeNodeParent.getDataObject();
                    dataEditor.unassignView(logicalDivision, view, false);
                    if (view.getPhysicalDivision().getLogicalDivisions().size() <= 1) {
                        severalAssignments.remove(view.getPhysicalDivision());
                    }
                    show();
                    dataEditor.getGalleryPanel().updateStripes();
                }
            }
        }
    }

    /**
     * Get the node label option (either "type", "title" or "type+title").
     * @return value of node label option
     */
    public String getNodeLabelOption() {
        return nodeLabelOption;
    }

    /**
     * Set node label option.
     * @param nodeLabelOption as java.lang.String
     */
    public void setNodeLabelOption(String nodeLabelOption) {
        if (!Arrays.asList("type", "title", "type+title").contains(nodeLabelOption)) {
            throw new IllegalArgumentException("node label option must be either type, title or type+title");
        }
        this.nodeLabelOption = nodeLabelOption;
    }

    /**
     * Returns true if the logical structure tree is a combined tree of structure nodes and view nodes (media).
     *
     * @return true if logical structure tree contains media
     */
    public boolean logicalStructureTreeContainsMedia() {
        return !this.isSeparateMedia() && !this.isHideMediaInLogicalTree();
    }

    /**
     * Return true if the users selected the option to hide media in the logical structure tree.
     *
     * @return true if the user want to hide media in the logical structure tree
     */
    public boolean isHideMediaInLogicalTree() {
        return this.hideMediaInLogicalTree;
    }

    /**
     * Sets whether media should be shown in the logical structure tree.
     *
     * @param hideMediaInLogicalTree boolean
     */
    public void setHideMediaInLogicalTree(boolean hideMediaInLogicalTree) {
        this.hideMediaInLogicalTree = hideMediaInLogicalTree;
    }

    /**
     * Listener that is called when the user changes the UI option to show or hide
     * media in the logical structure tree.
     */
    public void onHideMediaInLogicalTreeChange() {
        this.show();
        this.dataEditor.getGalleryPanel().show();
    }

    /**
     * Returns whether the user selected to show the page range for each logical structure node.
     *
     * @return value of showPageRangeInLogicalTree
     */
    public boolean isShowPageRangeInLogicalTree() {
        return this.showPageRangeInLogicalTree;
    }

    /**
     * Set whether the page range should be shown for each logical structure node.
     * @param showPageRangeInLogicalTree boolean
     */
    public void setShowPageRangeInLogicalTree(boolean showPageRangeInLogicalTree) {
        this.showPageRangeInLogicalTree = showPageRangeInLogicalTree;
    }

    /**
     * Returns whether the user selected to show the hierarchy level of individual tree nodes in the structure tree.
     * @return value of showHierarchyLevel
     */
    public boolean isShowHierarchyLevel() {
        return showHierarchyLevel;
    }

    /**
     * Set whether the hierarchy level of individual tree nodes in the structure tree should be displayed.
     * @param showHierarchyLevel boolean
     */
    public void setShowHierarchyLevel(boolean showHierarchyLevel) {
        this.showHierarchyLevel = showHierarchyLevel;
    }

    /**
     * Expand all tree nodes in the logical structure tree.
     */
    public void expandAll() {
        toggleAll(this.logicalTree, true);
    }

    /**
     * Collapse all tree nodes in the logical structure tree.
     */
    public void collapseAll() {
        toggleAll(this.logicalTree, false);
    }

    private void toggleAll(TreeNode treeNode, boolean expanded) {
        for (TreeNode childNode : treeNode.getChildren()) {
            toggleAll(childNode, expanded);
        }
        treeNode.setExpanded(expanded);
    }
}
