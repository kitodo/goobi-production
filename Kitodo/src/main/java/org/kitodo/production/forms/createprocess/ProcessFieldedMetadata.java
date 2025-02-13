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

package org.kitodo.production.forms.createprocess;

import static org.kitodo.constants.StringConstants.CREATE;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataeditor.rulesetmanagement.ComplexMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.Domain;
import org.kitodo.api.dataeditor.rulesetmanagement.InputType;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.Division;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.production.services.dataeditor.DataEditorService;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;

public class ProcessFieldedMetadata extends ProcessDetail implements Serializable {
    private static final Logger logger = LogManager.getLogger(ProcessFieldedMetadata.class);

    /**
     * An empty metadata group for the empty metadata panel showing. The empty metadata panel can be displayed if the
     * element selected in the structure window isn’t a structure (has no metadata).
     */
    public static final ProcessFieldedMetadata EMPTY = new ProcessFieldedMetadata();
    public static final String METADATA_KEY_LABEL = "LABEL";
    public static final String METADATA_KEY_ORDERLABEL = "ORDERLABEL";
    public static final String METADATA_KEY_CONTENTIDS = "CONTENTIDS";

    /**
     * Fields the user has selected to show in addition, with no data yet.
     */
    private final Collection<String> additionallySelectedFields = new ArrayList<>();
    private static final Set<String> specialFields = Set.of(METADATA_KEY_LABEL, METADATA_KEY_ORDERLABEL,
            METADATA_KEY_CONTENTIDS);

    private boolean copy;

    /**
     * The division this panel is related to, if it isn’t a sub-panel.
     */
    private Division<?> division;

    /**
     * Metadata which is excluded by the rule set.
     */
    private Collection<Metadata> hiddenMetadata;

    /**
     * The metadata object with the content of this panel.
     */
    private final HashSet<Metadata> metadata;

    /**
     * The key of the metadata group displaying here.
     */
    private String metadataKey;

    /**
     * The definition of this panel in the rule set.
     */
    private ComplexMetadataViewInterface metadataView;

    /**
     * To access the ruleset functions.
     */
    private RulesetManagementInterface rulesetService;

    /**
     * The tree node that JSF has to display.
     */
    protected TreeNode treeNode;

    /**
     * Creates an empty metadata group.
     */
    public ProcessFieldedMetadata() {
        super(null, null);
        this.treeNode = new DefaultTreeNode();
        treeNode.setExpanded(true);
        this.metadata = new HashSet<>();
        this.hiddenMetadata = Collections.emptyList();
    }

    /**
     * Creates a new root metadata group representing the metadata table content
     * in the processMetadata.
     *
     * @param structure
     *            structure selected by the user
     * @param divisionView
     *            information about that structure from the rule set
     * @param rulesetService
     *            the ruleset used for displaying
     */
    public ProcessFieldedMetadata(Division<?> structure, StructuralElementViewInterface divisionView,
            RulesetManagementInterface rulesetService) {

        this(null, structure, divisionView, null, null, structure.getMetadata());
        this.rulesetService = rulesetService;
        buildTreeNodeAndCreateMetadataTable();
    }

    /**
     * Add the metadata of this fielded metadata.
     *
     * @param potentialMetadataItems
     *            metadata to add if not exist
     * @return returns count of added metadata
     */
    public int addMetadataIfNotExists(Collection<Metadata> potentialMetadataItems)
            throws InvalidMetadataValueException, NoSuchMetadataFieldException {

        preserve();
        int count = rulesetService.updateMetadata(division.getType(), metadata, CREATE, potentialMetadataItems);
        buildTreeNodeAndCreateMetadataTable();
        return count;
    }

    private void buildTreeNodeAndCreateMetadataTable() {
        treeNode = new DefaultTreeNode();
        treeNode.setExpanded(true);
        createMetadataTable();
    }

    /**
     * Creates a sub-panel for a metadata group.
     *
     * @param metadataView
     *            information about that group from the rule set
     * @param metadata
     *            data of the group, may be empty but must be modifiable
     */
    private ProcessFieldedMetadata(ProcessFieldedMetadata parent, ComplexMetadataViewInterface metadataView,
            HashSet<Metadata> metadata) {
        this(parent, null, metadataView, metadataView.getLabel(), metadataView.getId(), metadata);
    }

    /**
     * Creates a new fielded metadata. This constructor is called from one of
     * the above ones and does the work.
     *
     * @param structure
     *            structure selected by the user, null in case of a sub-panel
     * @param metadataView
     *            information about that structure or group from the rule set
     * @param metadata
     *            metadata, may be empty but must be modifiable
     */
    private ProcessFieldedMetadata(ProcessFieldedMetadata parent, Division<?> structure,
            ComplexMetadataViewInterface metadataView, String label, String metadataKey,
                                   HashSet<Metadata> metadata) {
        super(parent, label);
        this.division = structure;
        this.metadata = metadata;
        this.metadataView = metadataView;
        this.metadataKey = metadataKey;
    }

    private ProcessFieldedMetadata(ProcessFieldedMetadata parent, MetadataGroup group) {
        this(parent, null, null, group.getKey(), group.getKey(), group.getMetadata());
    }

    private ProcessFieldedMetadata(ProcessFieldedMetadata template) {
        this(template.container, null, template.metadataView, template.label, template.metadataKey,
                new HashSet<>(template.metadata));
        copy = true;
        hiddenMetadata = template.hiddenMetadata;
        treeNode = new DefaultTreeNode(this, template.getTreeNode().getParent());
        createMetadataTable();
        treeNode.setExpanded(true);
    }

    /**
     * The method for building the metadata table.
     */
    private void createMetadataTable() {
        // the existing metadata is passed to the rule set, which sorts it
        Collection<Metadata> entered = addLabels(new HashSet<>(metadata));
        if (Objects.nonNull(treeNode) && !treeNode.getChildren().isEmpty()) {
            try {
                entered = entered.stream().filter(metadataElem -> !(metadataElem instanceof MetadataGroup))
                        .collect(Collectors.toSet());
                entered.addAll(DataEditorService.getExistingMetadataRows(treeNode.getChildren()));
            } catch (InvalidMetadataValueException e) {
                logger.error(e.getLocalizedMessage());
            }
        }
        List<MetadataViewWithValuesInterface> tableData = metadataView.getSortedVisibleMetadata(entered, additionallySelectedFields);
        if (Objects.nonNull(treeNode) && Objects.nonNull(treeNode.getChildren())) {
            treeNode.getChildren().clear();
        }
        hiddenMetadata = Collections.emptyList();
        for (MetadataViewWithValuesInterface rowData : tableData) {
            Optional<MetadataViewInterface> optionalMetadataView = rowData.getMetadata();
            Collection<Metadata> values = rowData.getValues();
            if (optionalMetadataView.isPresent()) {
                MetadataViewInterface metadataView = optionalMetadataView.get();
                if (metadataView.isComplex()) {
                    createMetadataGroupPanel((ComplexMetadataViewInterface) metadataView, values);
                } else {
                    if (!createMetadataEntryEdit((SimpleMetadataViewInterface) metadataView, values)) {
                        /*
                         * If a conditional metadata was set automatically,
                         * start over. This is necessary to update dependent
                         * fields above or below.
                         */
                        logger.debug("Metadata was changed. Restarting.");
                        createMetadataTable();
                        break;
                    }
                }
            } else {
                hiddenMetadata = values;
            }
        }
    }

    /**
     * The method for building the metadata table if the group and thus
     * everything in it is undefined in the ruleset.
     */
    private void createUndefinedMetadataTable() {
        treeNode.getChildren().clear();
        hiddenMetadata = Collections.emptyList();
        for (Metadata entry : metadata) {
            createMetadataEntryEdit(null, Collections.singletonList(entry));
        }
    }

    /**
     * Reads the labels from the structure (if any) and adds them to the
     * returned metadata collection.
     *
     * @param metadata
     *            available metadata
     * @return metadata with labels, if any
     */
    private Collection<Metadata> addLabels(Collection<Metadata> metadata) {
        Collection<Metadata> displayMetadata = metadata;
        if (Objects.nonNull(division)) {
            displayMetadata = new HashSet<>(metadata);
            for (URI contentId : division.getContentIds()) {
                MetadataEntry contentIdEntry = new MetadataEntry();
                contentIdEntry.setKey("CONTENTIDS");
                contentIdEntry.setValue(contentId.toString());
                displayMetadata.add(contentIdEntry);
            }
            if (Objects.nonNull(division.getLabel())) {
                MetadataEntry label = new MetadataEntry();
                label.setKey(METADATA_KEY_LABEL);
                label.setValue(division.getLabel());
                displayMetadata.add(label);
            }
            if (Objects.nonNull(division.getOrderlabel())) {
                MetadataEntry label = new MetadataEntry();
                label.setKey(METADATA_KEY_ORDERLABEL);
                label.setValue(division.getOrderlabel());
                displayMetadata.add(label);
            }
        }
        return displayMetadata;
    }

    /**
     * Creates an object to represent a metadata group. This is done by
     * creating a {@code FieldedMetadataGroup} recursively.
     *
     * @param complexMetadataView
     *            information about that group from the rule set
     * @param values
     *            data for that group, must contain at most one element
     */
    public void createMetadataGroupPanel(ComplexMetadataViewInterface complexMetadataView,
                                                           Collection<Metadata> values) {
        HashSet<Metadata> value;

        switch (values.size()) {
            case 0:
                value = new HashSet<>();
                break;
            case 1:
                Metadata nextMetadata = values.iterator().next();
                if (nextMetadata instanceof MetadataGroup) {
                    MetadataGroup metadataGroup = (MetadataGroup) nextMetadata;
                    value = metadataGroup.getMetadata();
                } else {
                    throw new IllegalStateException("Got simple metadata entry with key \"" + nextMetadata.getKey()
                            + "\" which is declared as substructured key in the rule set.");
                }
                break;
            default:
                throw new IllegalStateException("Too many (" + values.size() + ") complex metadata of type \""
                        + metadataKey + "\" in a single row. Must be 0 or 1 per row.");
        }
        ProcessFieldedMetadata metadata = new ProcessFieldedMetadata(this, complexMetadataView, value);
        metadata.treeNode = new DefaultTreeNode(metadata, treeNode);
        metadata.createMetadataTable();
        metadata.treeNode.setExpanded(true);
    }

    /**
     * Creates an object to represent a single-row metadata input.
     *
     * @param simpleMetadataView
     *            presentation information about the metadata entry from the
     *            ruleset
     * @param values
     *            the value(s) to be displayed
     * @return whether the input field could be generated. If an automatic
     *         preset has been added to the metadata, false is returned. In that
     *         case, the rule set must be invoked again to update the options
     *         for dependent fields.
     */
    public boolean createMetadataEntryEdit(SimpleMetadataViewInterface simpleMetadataView,
                                                 Collection<Metadata> values) {

        ProcessDetail data;
        try {
            InputType inputType = Objects.isNull(simpleMetadataView) ? InputType.ONE_LINE_TEXT
                    : simpleMetadataView.getInputType();
            switch (inputType) {
                case MULTIPLE_SELECTION:
                case MULTI_LINE_SINGLE_SELECTION:
                case ONE_LINE_SINGLE_SELECTION:
                    List<Map<MetadataEntry, Boolean>> leadingFields = getListForLeadingMetadataFields();
                    Map<String, String> options = simpleMetadataView.getSelectItems(leadingFields);
                    boolean dependent = leadingFields.parallelStream().flatMap(map -> map.entrySet().parallelStream())
                            .anyMatch(entry -> Boolean.TRUE.equals(entry.getValue()));
                    if (dependent && !options.isEmpty() && addAutoPresetForConditionalMetadata(simpleMetadataView, options, values)) {
                        return false;
                    }
                    data = new ProcessSelectMetadata(this, simpleMetadataView, simpleValues(values), dependent);
                    break;
                case BOOLEAN:
                    data = new ProcessBooleanMetadata(this, simpleMetadataView, oneValue(values, MetadataEntry.class));
                    break;
                case DATE:
                    data = new ProcessDateMetadata(this, simpleMetadataView, oneValue(values, MetadataEntry.class));
                    break;
                case INTEGER:
                case MULTI_LINE_TEXT:
                case ONE_LINE_TEXT:
                    data = new ProcessTextMetadata(this, simpleMetadataView, oneValue(values, MetadataEntry.class));
                    break;
                default:
                    throw new IllegalStateException("complete switch");
            }
            new DefaultTreeNode(data, treeNode).setExpanded(true);
        } catch (IllegalStateException e) {
            logger.catching(Level.WARN, e);
            ProcessFieldedMetadata metadata = new ProcessFieldedMetadata(this, oneValue(values, MetadataGroup.class));
            metadata.treeNode = new DefaultTreeNode(metadata, treeNode);
            metadata.createUndefinedMetadataTable();
            metadata.treeNode.setExpanded(true);
        }
        return true;
    }

    /**
     * A value must be set for conditional metadata, as there is no no-selection
     * option. In this case, the first possible value is already displayed as
     * selected, even if it is not yet reflected in the metadata before saving.
     * In this case, no options are offered in selection fields that depend on
     * this field, because their options are based on the metadata. Therefore,
     * in this case, the metadata must be set explicitly. Then, a termination
     * must take place and the rule set must be invoked again in order to
     * correctly populate the dependent fields with options.
     *
     * @param view
     *            view that gives the access the select items
     * @param metadataForInput
     *            list of metadata, to which a preset will be added if necessary
     * @return true, if the current tree building process must be restarted,
     *         because the metadata was changed
     */
    private boolean addAutoPresetForConditionalMetadata(SimpleMetadataViewInterface view, Map<String, String> options,
            Collection<Metadata> metadataForInput) {

        if (metadataForInput.isEmpty()) {
            MetadataEntry autoPreset = new MetadataEntry();
            autoPreset.setKey(view.getId());
            if (Objects.isNull(container)) {
                autoPreset.setDomain(DOMAIN_TO_MDSEC.get(view.getDomain().orElse(Domain.DESCRIPTION)));
            }
            autoPreset.setValue(options.entrySet().iterator().next().getKey());
            metadata.add(autoPreset);

            logger.debug("Added metadata {} to {}", autoPreset, metadataKey);
            return true;
        }
        return false;
    }

    /**
     * Returns the collection of simple metadata entries. Throws an
     * IllegalStateException if a cannot be casted.
     *
     * @param values
     *            values obtained
     * @return a collection of simple metadata entries
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private Collection<MetadataEntry> simpleValues(Collection<Metadata> values) {
        Optional<Metadata> fault = values.parallelStream().filter(entry -> !(entry instanceof MetadataEntry)).findAny();
        if (fault.isPresent()) {
            throw new IllegalStateException("Got complex metadata entry with key \"" + fault.get().getKey()
                    + "\" which isn't declared as substructured key in the rule set.");
        }
        return (Collection) values;
    }

    /**
     * Returns the only metadata entry or null. Throws an IllegalStateException
     * if the value is ambiguous or cannot be cast.
     *
     * @param values
     *            values obtained
     * @return the only entry or null
     */
    @SuppressWarnings("unchecked")
    private <T extends Metadata> T oneValue(Collection<Metadata> values, Class<T> subclass) {
        switch (values.size()) {
            case 0:
                return null;
            case 1:
                Metadata nextMetadata = values.iterator().next();
                if (subclass.isAssignableFrom(nextMetadata.getClass())) {
                    return (T) nextMetadata;
                } else {
                    throw new IllegalStateException("Got complex metadata entry with key \"" + nextMetadata.getKey()
                            + "\" which isn't declared as substructured key in the rule set.");
                }
            default:
                throw new IllegalStateException("Too many (" + values.size() + ") metadata of type \""
                        + values.iterator().next().getKey() + "\" in a single row. Must be 0 or 1 per row.");
        }
    }

    /**
     * Adds an additionally selected field.
     *
     * @param additionallySelectedField
     *            additionally selected field to add
     * @throws NoSuchMetadataFieldException
     *             if the method has to save the entries in order to rebuild the
     *             display with the new field, but when saving an attempt is
     *             made to write a non-existing &lt;mets:div> attribute
     */
    public void addAdditionallySelectedField(String additionallySelectedField) throws NoSuchMetadataFieldException {
        additionallySelectedFields.add(additionallySelectedField);
        try {
            preserve();
        } catch (InvalidMetadataValueException e) {
            logger.info(e.getLocalizedMessage(), e);
        }
        createMetadataTable();
    }

    /**
     * Duplicates a process detail.
     *
     * @param processDetail
     *            process detail to copy
     */
    public void copy(ProcessDetail processDetail) {
        if (Objects.isNull(division)) {
            container.copy(processDetail);
        } else {
            searchRecursiveAndCopy(treeNode, processDetail);
        }
    }

    private static boolean searchRecursiveAndCopy(TreeNode treeNode, ProcessDetail processDetail) {
        List<TreeNode> children = treeNode.getChildren();
        for (int index = 0; index < children.size(); index++) {
            TreeNode child = children.get(index);
            Object childData = child.getData();
            if (Objects.equals(childData, processDetail)) {
                TreeNode copy = null;
                if (childData instanceof ProcessSimpleMetadata) {
                    ProcessSimpleMetadata copyData = ((ProcessSimpleMetadata) childData).getClone();
                    copy = new DefaultTreeNode(copyData, treeNode);
                    copy.setExpanded(child.isExpanded());
                } else if (childData instanceof ProcessFieldedMetadata) {
                    ProcessFieldedMetadata copyData = new ProcessFieldedMetadata((ProcessFieldedMetadata) childData);
                    copy = copyData.treeNode;
                }
                treeNode.getChildren().add(index + 1, copy);
            } else if (searchRecursiveAndCopy(child, processDetail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the division.
     * @return the division
     */
    public Division<?> getDivision() {
        return division;
    }

    @Override
    public String getMetadataID() {
        return metadataKey;
    }

    @Override
    public String getInput() {
        return "dataTable";
    }

    List<Map<MetadataEntry, Boolean>> getListForLeadingMetadataFields() {
        List<Map<MetadataEntry, Boolean>> result = Objects.isNull(container) ? new ArrayList<>()
                : container.getListForLeadingMetadataFields();
        Map<String, MetadataEntry> metadataEntryMap = new HashMap<>();
        treeNode.getChildren().stream().map(TreeNode::getData).filter(ProcessSimpleMetadata.class::isInstance)
                .map(ProcessSimpleMetadata.class::cast).map(ProcessDetail::getMetadataID).forEachOrdered(key -> {
                    MetadataEntry metadataEntry = new MetadataEntry();
                    metadataEntry.setKey(key);
                    metadataEntryMap.put(key, metadataEntry);
                });
        metadata.stream().filter(MetadataEntry.class::isInstance).map(MetadataEntry.class::cast)
                .forEachOrdered(key -> metadataEntryMap.put(key.getKey(), key));
        result.add(metadataEntryMap.entrySet().stream()
            .collect(Collectors.toMap(Entry::getValue, all -> Boolean.FALSE)));
        return result;
    }

    /**
     * Returns the metadata of a metadata group, when used recursively.
     *
     * @return the metadata of the metadata group
     * @throws InvalidMetadataValueException
     *             if some value is invalid
     */
    @Override
    public Collection<Metadata> getMetadataWithFilledValues() throws InvalidMetadataValueException {
        return getMetadata(true);
    }

    @Override
    public Collection<Metadata> getMetadata(boolean skipEmpty) throws InvalidMetadataValueException {
        assert division == null;
        MetadataGroup result = new MetadataGroup();
        result.setKey(metadataKey);
        if (Objects.nonNull(metadataView)) {
            result.setDomain(DOMAIN_TO_MDSEC.get(metadataView.getDomain().orElse(Domain.DESCRIPTION)));
            try {
                this.preserve();
            } catch (NoSuchMetadataFieldException e) {
                throw new IllegalStateException("never happening exception");
            }
            if (skipEmpty) {
                result.setMetadata(metadata instanceof List ? metadata : new HashSet<>(metadata));
            } else {
                result.setMetadata(new HashSet<>(DataEditorService.getExistingMetadataRows(treeNode.getChildren())));
            }
        }
        return result.getMetadata().isEmpty() ? Collections.emptyList() : Collections.singletonList(result);
    }

    /**
     * Return this ProcessFieldedMetadata as MetadataGroup.
     *
     * @return MetadataGroup representing this ProcessFieldedMetadata
     */
    public Collection<Metadata> getChildMetadata() {
        return metadata;
    }

    /**
     * Returns the rows that JSF has to display.
     *
     * @return the rows that JSF has to display
     */
    public List<ProcessDetail> getRows() {
        List<ProcessDetail> rows = new ArrayList<>();
        for (TreeNode child : treeNode.getChildren()) {
            rows.add((ProcessDetail) child.getData());
        }
        return new UnmodifiableList<>(rows);
    }

    public TreeNode getTreeNode() {
        return treeNode;
    }

    @Override
    public boolean isUndefined() {
        return Objects.isNull(metadataView) || metadataView.isUndefined();
    }

    @Override
    public boolean isRequired() {
        return Objects.nonNull(metadataView) && metadataView.getMinOccurs() > 0;
    }

    @Override
    public boolean isValid() {
        for (ProcessDetail row : getRows()) {
            if (!row.isValid()) {
                return false;
            }
        }
        return true;
    }

    void markLeadingMetadataFields(List<Map<MetadataEntry, Boolean>> leadingMetadataFields) {
        int lastIndex = leadingMetadataFields.size() - 1;
        if (lastIndex > 0) {
            container.markLeadingMetadataFields(leadingMetadataFields.subList(0, lastIndex));
        }
        final List<String> leadingMetadataKeys = leadingMetadataFields.get(lastIndex).entrySet().parallelStream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue())).map(entry -> entry.getKey().getKey())
                .collect(Collectors.toList());
        treeNode.getChildren().parallelStream().map(TreeNode::getData).map(ProcessDetail.class::cast)
                .filter(processDetail -> leadingMetadataKeys.contains(processDetail.getMetadataID()))
                .forEach(ProcessDetail::setLeading);
    }

    /**
     * Reads the contents of the processMetadata and stores the values in the
     * appropriate place. If the line is used to edit a field of the METS
     * structure, this field is set, otherwise the metadata will be stored in
     * the list. The hidden metadata is also written back there again.
     *
     * @throws InvalidMetadataValueException
     *             if the content of a metadata input field is syntactically
     *             wrong
     * @throws NoSuchMetadataFieldException
     *             if an input shall be saved to a field of the structure, but
     *             there is no setter corresponding to the name configured in
     *             the rule set
     */
    @Override
    public void preserve() throws InvalidMetadataValueException, NoSuchMetadataFieldException {
        try {
            if (Objects.nonNull(division)) {
                division.getContentIds().clear();
                division.setOrderlabel(null);
                division.setLabel(null);
            }
            metadata.clear();

            for (TreeNode child : treeNode.getChildren()) {
                ProcessDetail row = (ProcessDetail) child.getData();
                String id = row.getMetadataID();
                if (row instanceof ProcessSimpleMetadata && specialFields.contains(id)
                        && ((ProcessSimpleMetadata) row).getSettings()
                        .getDomain().orElse(Domain.DESCRIPTION).equals(Domain.METS_DIV)) {
                    updateDivisionFromProcessDetail(id, (ProcessSimpleMetadata) row);
                } else {
                    metadata.addAll(row.getMetadataWithFilledValues());
                }
            }
            if (Objects.nonNull(hiddenMetadata) && !hiddenMetadata.isEmpty()) {
                for (Metadata hidden : hiddenMetadata) {
                    if (hidden instanceof MetadataEntry) {
                        MetadataEntry entry = (MetadataEntry) hidden;
                        if (specialFields.contains(entry.getKey())) {
                            updateDivision(entry.getKey(), entry.getValue());
                        }
                    }
                }
                metadata.addAll(hiddenMetadata);
            }
        } catch (InvalidMetadataValueException invalidValueException) {
            if (Objects.isNull(division)) {
                invalidValueException.addParent(metadataKey);
            }
            throw invalidValueException;
        }
        if (copy) {
            MetadataGroup metadataGroup = new MetadataGroup();
            metadataGroup.setKey(metadataKey);
            Optional<Domain> optionalDomain = metadataView.getDomain();
            optionalDomain.ifPresent(domain -> metadataGroup.setDomain(DOMAIN_TO_MDSEC.get(domain)));
            metadataGroup.setMetadata(metadata);
            container.metadata.add(metadataGroup);
            copy = false;
        }
    }

    private void updateDivisionFromProcessDetail(String key, ProcessSimpleMetadata processDetail) throws InvalidMetadataValueException {
        String simpleValue = processDetail.extractSimpleValue();
        if (!processDetail.getSettings().isValid(simpleValue, getListForLeadingMetadataFields())) {
            throw new InvalidMetadataValueException(key, simpleValue);
        };
        if (simpleValue == null) {
            return;
        }
        updateDivision(key, simpleValue);
    }

    private void updateDivision(String key, String value) {
        switch (key) {
            case METADATA_KEY_LABEL:
                division.setLabel(value);
                break;
            case METADATA_KEY_ORDERLABEL:
                division.setOrderlabel(value);
                break;
            case METADATA_KEY_CONTENTIDS:
                division.getContentIds().add(URI.create(value));
                break;
            default:
                break;
        }
    }

    /**
     * Removes a process detail.
     *
     * @param toDelete
     *            process detail to delete
     */
    public void remove(ProcessDetail toDelete) throws InvalidMetadataValueException, NoSuchMetadataFieldException {
        Iterator<TreeNode> treeNodesIterator = treeNode.getChildren().iterator();
        while (treeNodesIterator.hasNext()) {
            TreeNode treeNode = treeNodesIterator.next();
            if (treeNode.getData().equals(toDelete)) {
                treeNodesIterator.remove();
                preserve();
                break;
            }
        }
    }

    /**
     * Overwrites the metadata of this process fielded metadata.
     *
     * @param metadata
     *            metadata to overwrite with
     */
    public void setMetadata(Collection<Metadata> metadata) {
        this.metadata.clear();
        this.metadata.addAll(metadata);
        buildTreeNodeAndCreateMetadataTable();
    }

    /**
     * Get metadataView.
     *
     * @return value of metadataView
     */
    public ComplexMetadataViewInterface getMetadataView() {
        return metadataView;
    }

    /**
     * Get additionallySelectedFields.
     *
     * @return value of additionallySelectedFields
     */
    public Collection<String> getAdditionallySelectedFields() {
        return additionallySelectedFields;
    }

    /**
     * Get occurrences of a metadata in the treeNode.
     * @param metadataKey as String
     * @return occurrences
     */
    public int getOccurrences(String metadataKey) {
        int occ = 0;
        for (TreeNode treeNode : treeNode.getChildren()) {
            if (((ProcessDetail) treeNode.getData()).getMetadataID().equals(metadataKey)) {
                occ++;
            }
        }
        return occ;
    }

    @Override
    public int getMinOccurs() {
        return metadataView.getMinOccurs();
    }
}
