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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.primefaces.model.TreeNode;

/**
 * Common operations when working with Primefaces trees.
 */
public class StructureTreeOperations {

    private static final Logger logger = LogManager.getLogger(StructureTreeOperations.class);
    
    /** 
     * Set all tree nodes to unselected in a tree.
     * 
     * @param root the root tree node
     */
    public static void clearTreeNodeSelection(TreeNode root) {
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            node.setSelected(false);
            stack.addAll(node.getChildren());
        }
    }

    /**
     * Mark all provided tree nodes as selected.
     * 
     * @param nodes the set of nodes that are marked as selected
     */
    public static void selectTreeNodes(Set<TreeNode> nodes) {
        for (TreeNode node : nodes) {
            node.setSelected(true);
        }
    }

    /**
     * Return the number of selected tree nodes (e.g. for debugging purposes).
     * 
     * @param root the root tree node
     * @return the number of selected tree nodes
     */
    public static int getNumberOfSelectedTreeNodes(TreeNode root) {
        int count = 0;
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            if (node.isSelected()) {
                count += 1;
            }
            stack.addAll(node.getChildren());
        }

        return count;
    }

    /**
     * Return a set of tree nodes from the whole tree that match a given criteria.
     * 
     * @param root the root tree node of the tree
     * @param criteria the criteria function
     * @return the set of tree nodes that match the criteria function
     */
    public static Set<TreeNode> findTreeNodeMatchingCriteria(TreeNode root, Function<TreeNode, Boolean> criteria) {
        Set<TreeNode> matches = new HashSet<>();
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            if (criteria.apply(node)) {
                matches.add(node);
            }
            stack.addAll(node.getChildren());
        }

        return matches;
    }

    /**
     * Return physical divison for a tree node if the tree node represents a physical division.
     * 
     * @param node the tree node
     * @return the physical division or null
     */
    public static PhysicalDivision getPhysicalDivisionFromTreeNode(TreeNode node) {
        if (Objects.nonNull(node) && node.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) node.getData();

            if (structureTreeNode.getDataObject() instanceof View) {
                // tree node is a physical node
                View view = (View) structureTreeNode.getDataObject();
                return view.getPhysicalDivision();
            }

            if (structureTreeNode.getDataObject() instanceof PhysicalDivision) {
                return (PhysicalDivision) structureTreeNode.getDataObject();
            }
        }
        
        return null;
    }

    /**
     * Return pair of physical divison and its parent logical division for a tree node if the tree node represents a 
     * physical division.
     * 
     * @param node the tree node
     * @return the pair of physical division and its parent logical division or null
     */
    public static ImmutablePair<PhysicalDivision, LogicalDivision> getPhysicalDivisionPairFromTreeNode(TreeNode node) {
        if (Objects.nonNull(node) && node.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) node.getData();

            if (structureTreeNode.getDataObject() instanceof View) {
                // tree node is a view on physical node (as implemented in logical structure tree)
                View view = (View) structureTreeNode.getDataObject();

                // find logical division from tree node parent
                TreeNode parent = node.getParent();
                if (Objects.nonNull(parent) && parent.getData() instanceof StructureTreeNode) {
                    StructureTreeNode parentStructureTreeNode = (StructureTreeNode) parent.getData();
                    if (parentStructureTreeNode.getDataObject() instanceof LogicalDivision) {
                        LogicalDivision logicalDivision = (LogicalDivision) parentStructureTreeNode.getDataObject();
                        return new ImmutablePair<>(view.getPhysicalDivision(), logicalDivision);
                    }
                } else {
                    return new ImmutablePair<>(view.getPhysicalDivision(), null);
                }
            }

            if (structureTreeNode.getDataObject() instanceof PhysicalDivision) {
                // tree node is physical division (as implemented in physical structure tree)
                PhysicalDivision physicalDivision = (PhysicalDivision) structureTreeNode.getDataObject();

                if (!physicalDivision.getLogicalDivisions().isEmpty()) {
                    return new ImmutablePair<>(physicalDivision, physicalDivision.getLogicalDivisions().get(0));
                } else {
                    return new ImmutablePair<>(physicalDivision, null);
                }
            }
        }
        return null;
    }

    /**
     * Return the logical division of a tree node if the tree node represents a logical division.
     * 
     * @param node the tree node
     * @return the logical divison or null
     */
    public static LogicalDivision getLogicalDivisionFromTreeNode(TreeNode node) {
        if (Objects.nonNull(node) && node.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) node.getData();
            if (structureTreeNode.getDataObject() instanceof LogicalDivision) {
                return (LogicalDivision) structureTreeNode.getDataObject();
            }
        }
        return null;
    }

    /**
     * Return all tree nodes that match the physical divisions and logical divisons.
     * 
     * @param root the root tree node
     * @param physicalDivisions the physical divisions (as pair with their respective parent logical divisions)
     * @param logicalDivisions the logical divisions
     * @return the set of tree nodes matching the provided physical and logical divisions
     */
    public static Set<TreeNode> findTreeNodesMatchingDivisions(
        TreeNode root, 
        List<Pair<PhysicalDivision, LogicalDivision>> physicalDivisions, 
        List<LogicalDivision> logicalDivisions
    ) {
        Set<TreeNode> matchingTreeNodes = new HashSet<>();

        if (!logicalDivisions.isEmpty()) {
            matchingTreeNodes.addAll(findTreeNodeMatchingCriteria(root, (TreeNode node) -> {
                    // do not use List.contains operation, which uses content based "equals"-methods of Division class
                    // and there can be multiple divisions that are essentially equal (same properties)
                    // but each have their own tree node
                    LogicalDivision targetLogicalDivision = getLogicalDivisionFromTreeNode(node);
                    for (LogicalDivision logicalDivision : logicalDivisions) {
                        if (logicalDivision == targetLogicalDivision) {
                            return true;
                        }
                    }
                    return false;
                }
            ));
        }

        if (!physicalDivisions.isEmpty()) {
            matchingTreeNodes.addAll(findTreeNodeMatchingCriteria(root, (TreeNode node) -> {
                // do not use List.contains operation, which uses content based "equals"-methods of Division class
                // and there can be multiple logical divisions that are essentially equal (same properties)
                // but each have their own tree node
                Pair<PhysicalDivision, LogicalDivision> targetPair = getPhysicalDivisionPairFromTreeNode(node);
                if (Objects.nonNull(targetPair)) {
                    for (Pair<PhysicalDivision, LogicalDivision>  pair : physicalDivisions) {
                        if (Objects.nonNull(pair) 
                                && targetPair.getLeft() == pair.getLeft() 
                                && targetPair.getRight() == pair.getRight()) {
                            return true;
                        }
                    }
                }
                return false;
            }));
        }

        return matchingTreeNodes;
    }

    /**
     * Return the logical parent tree node of a node if it is a physical divison node, or 
     * itself if it is a logical node.
     */
    public static TreeNode getTreeNodeLogicalParentOrSelf(TreeNode node) {
        if (Objects.isNull(node)) {
            return null;
        }
        if (node.getData() instanceof StructureTreeNode) {
            StructureTreeNode structureTreeNode = (StructureTreeNode) node.getData();
            if (structureTreeNode.getDataObject() instanceof View) {
                // node is a physical division, try to find its parent logical division
                if (Objects.nonNull(getLogicalDivisionFromTreeNode(node.getParent()))) {
                    return node.getParent();
                }
            } else if (structureTreeNode.getDataObject() instanceof LogicalDivision) {
                // node is a logical divison, return self
                return node;
            }
        }
        return null;
    }
}
