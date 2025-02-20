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

package org.kitodo.production.model.bibliography.course.metadata;

import java.io.IOException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.forms.createprocess.ProcessSimpleMetadata;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.metadata.pagination.Paginator;
import org.kitodo.production.model.bibliography.course.Block;
import org.kitodo.production.model.bibliography.course.Granularity;
import org.kitodo.production.model.bibliography.course.IndividualIssue;
import org.kitodo.production.model.bibliography.course.Issue;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.calendar.CalendarService;

/**
 * Generic metadata that is created using a counter.
 */
public class CountableMetadata {
    private static final Logger logger = LogManager.getLogger(CountableMetadata.class);

    /**
     * Block this metadata counter belongs to. The block is needed to have
     * access to the other issues, which—together with the start value and step
     * size—define the value of the counter on a given day.
     */
    private final Block block;

    /**
     * Date and issue this counter appears the first time,
     * boolean to check if this metadata counter is only associated to issue or not.
     */
    private final Triple<LocalDate, Issue, Boolean> create;

    /**
     * Date and issue this counter does no longer appear on. May be null, indicating that no end issue has been set.
     */
    private Pair<LocalDate, Issue> delete;

    /**
     * Metadata type to create with this counter.
     */
    private String metadataType;

    /**
     * Value to start counting with.
     */
    private String startValue = "";

    /**
     * Step size of the counter (for each issue, or for each day, or for each
     * week, etc.) May be null for metadata constants which are not
     * incremented.
     */
    private Granularity stepSize;

    private ProcessDetail metadataDetail;

    private List<ProcessDetail> allMetadataTypes = null;

    /**
     * Creates a new countable metadata.
     *
     * @param block
     *            Block this metadata counter is defined in
     * @param create as a Pair
     *            first issue to be counted
     */
    public CountableMetadata(Block block, Pair<LocalDate, Issue> create) {
        this.block = block;
        this.create = Triple.of(create.getLeft(), create.getRight(), false);
    }

    /**
     * Creates a new countable metadata.
     *
     * @param block
     *            Block this metadata counter is defined in
     * @param create as a Triple
     *            first issue to be counted
     */
    public CountableMetadata(Block block, Triple<LocalDate, Issue, Boolean> create) {
        this.block = block;
        this.create = create;
    }

    /**
     * Returns the edit mode for the given issue, or {@code null} if the
     * metadata is not at this issue.
     *
     * @param selectedIssue
     *            issue to return the edit mode for
     * @return the edit mode for that issue
     */
    public MetadataEditMode getEditMode(Pair<LocalDate, Issue> selectedIssue) {
        int creation = new IssueComparator(block).compare(selectedIssue, Pair.of(this.create.getLeft(), this.create.getMiddle()));
        if (creation < 0) {
            return MetadataEditMode.HIDDEN;
        }
        if (creation == 0) {
            return MetadataEditMode.DEFINE;
        }
        int deletion = new IssueComparator(block).compare(selectedIssue, delete);
        if (deletion < 0) {
            return MetadataEditMode.CONTINUE;
        } else if (deletion == 0) {
            if (Objects.isNull(block.getMetadata(metadataType, selectedIssue, true))) {
                return MetadataEditMode.DELETE;
            } else {
                return MetadataEditMode.HIDDEN;
            }
        } else {
            return MetadataEditMode.HIDDEN;
        }
    }

    /**
     * Gets create.
     *
     * @return value of create
     */
    public Triple<LocalDate, Issue, Boolean> getCreate() {
        return create;
    }

    /**
     * Get create.
     *
     * @return value of create as java.lang.String
     */
    public String getCreateAsString() {
        return CalendarService.dateIssueToString(Pair.of(this.create.getLeft(), this.create.getMiddle()));
    }

    /**
     * Get delete.
     *
     * @return value of delete as java.lang.String
     */
    public String getDelete() {
        if (Objects.isNull(delete)) {
            return DateTimeFormatter.ISO_DATE.format(block.getLastAppearance());
        }
        return CalendarService.dateIssueToString(delete);
    }

    /**
     * Returns the metadata type of this metadata.
     *
     * @return the metadata type of this metadata
     */
    public String getMetadataType() {
        return metadataType;
    }

    /**
     * Returns the start value of the countable metadata.
     *
     * @return the start value
     */
    public String getStartValue() {
        return startValue;
    }

    /**
     * Returns the step size for counting this metadata.
     *
     * @return the step size
     */
    public Granularity getStepSize() {
        return stepSize;
    }

    /**
     * Returns the counter value for a given issue.
     *
     * @param selectedIssue
     *            issue to return the counter value for
     * @param yearStart
     *            first day of the year
     * @return the counter value for that issue
     */
    public String getValue(Pair<LocalDate, Issue> selectedIssue, MonthDay yearStart) {
        assert new IssueComparator(block).compare(selectedIssue, Pair.of(this.create.getLeft(), this.create.getMiddle())) >= 0;
        Paginator values = new Paginator(startValue);
        int breakMark = 0;
        for (LocalDate i = create.getLeft(); !i.isAfter(selectedIssue.getLeft()); i = i.plusDays(1)) {
            boolean first = i.equals(create.getLeft());
            for (IndividualIssue issue : block.getIndividualIssues(i)) {
                if (first && block.getIssueIndex(issue.getIssue()) < block.getIssueIndex(create.getMiddle())) {
                    continue;
                } else if (first) {
                    if (stepSize != null) {
                        first = false;
                        breakMark = issue.getBreakMark(stepSize, yearStart);
                    }
                } else if (stepSize != null) {
                    int currentBreakMark = issue.getBreakMark(stepSize, yearStart);
                    if (breakMark != currentBreakMark) {
                        values.next();
                        breakMark = currentBreakMark;
                    }
                }
                if (new IssueComparator(block).compare(selectedIssue, Pair.of(i, issue.getIssue())) == 0) {
                    return values.next();
                }
            }
        }
        throw new IllegalStateException("Issue “" + selectedIssue.getRight().getHeading() + "” not found on "
                + DateTimeFormatter.ISO_LOCAL_DATE.format(selectedIssue.getLeft()));
    }

    /**
     * Returns true, if the given metadataType and time point of creation or
     * deletion match.
     *
     * @param metadataType
     *            metadata type to compare, null will be true with any
     *            metadata type
     * @param issue
     *            issue time point
     * @param create
     *            if true, compare create time, else compare delete time
     * @return true, if the given metadataType and time point of creation match
     */
    public boolean matches(String metadataType, Pair<LocalDate, Issue> issue, Boolean create) {
        if (Objects.isNull(metadataType) || metadataType.equals(this.metadataType)) {
            if (this.create.getRight()) {
                return  Objects.nonNull(issue) && Objects.equals(this.create.getMiddle(), issue.getRight());
            } else {
                return (Objects.isNull(create)
                        && new IssueComparator(block).compare(Pair.of(this.create.getLeft(), this.create.getMiddle()), issue) <= 0
                        && (Objects.isNull(delete) || new IssueComparator(block).compare(issue, delete) < 0)
                        || Boolean.TRUE.equals(create) && Pair.of(this.create.getLeft(), this.create.getMiddle()).equals(issue)
                        || Boolean.FALSE.equals(create)
                        && (Objects.isNull(issue) && Objects.isNull(this.delete)
                            || (Objects.nonNull(issue) && issue.equals(this.delete))));
            }
        }
        return false;
    }

    /**
     * Sets the delete of the countable metadata.
     *
     * @param delete
     *            the delete to set
     */
    public void setDelete(Pair<LocalDate, Issue> delete) {
        this.delete = delete;
    }

    /**
     * Sets the metadata type of the countable metadata.
     *
     * @param metadataView
     *            the metadata type to set
     */
    public void setMetadataType(String metadataView) {
        this.metadataType = metadataView;
    }

    /**
     * Sets the step size of the countable metadata.
     *
     * @param stepSize
     *            the step size to set
     */
    public void setStepSize(Granularity stepSize) {
        this.stepSize = stepSize;
    }

    /**
     * Sets the start value of the countable metadata.
     *
     * @param startValue
     *            the start value to set
     */
    public void setStartValue(String startValue) throws InvalidMetadataValueException {
        ((ProcessSimpleMetadata) metadataDetail).setValue(startValue);
        this.startValue = startValue;
    }

    /**
     * Gets metadataDetail.
     *
     * @return value of metadataDetail
     */
    public ProcessDetail getMetadataDetail() {
        return metadataDetail;
    }

    /**
     * Sets metadataDetail.
     *
     * @param metadataDetail value of metadataDetail
     */
    public void setMetadataDetail(ProcessDetail metadataDetail) {
        this.metadataDetail = metadataDetail;
        this.metadataType = this.metadataDetail.getMetadataID();
    }

    /**
     * Returns the list of selectable metadata types.
     *
     * @param processId ID of process for which metadata types are determined
     *
     * @return the list of metadata types
     */
    public List<ProcessDetail> getAllMetadataTypes(Integer processId) {
        if (Objects.isNull(allMetadataTypes)) {
            try {
                Process process = ServiceManager.getProcessService().getById(processId);
                allMetadataTypes = new ArrayList<>(CalendarService.getAddableMetadataTable(process));

            } catch (DAOException | DataException | IOException e) {
                Helper.setErrorMessage("Unable to load metadata types: " + e.getMessage());
            }
        }
        if (Objects.nonNull(metadataDetail) && Objects.nonNull(allMetadataTypes)) {
            for (int i = 0; i < allMetadataTypes.size(); i++) {
                if (allMetadataTypes.get(i).getMetadataID().equals(metadataDetail.getMetadataID())) {
                    allMetadataTypes.set(i, metadataDetail);
                    break;
                }
            }
        }

        return allMetadataTypes;
    }

    /**
     * Returns a human-readable concise description of this countable metadata.
     *
     * @return a human-readable description of this metadata
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            for (Metadata metadata : metadataDetail.getMetadataWithFilledValues()) {
                stringBuilder.append(metadata);
            }
        } catch (InvalidMetadataValueException e) {
            logger.error(e.getMessage());
        }
        stringBuilder.append(" from ");
        stringBuilder.append(DateTimeFormatter.ISO_DATE.format(create.getLeft()));
        stringBuilder.append(", ");
        stringBuilder.append(create.getMiddle().getHeading());
        if (delete == null) {
            stringBuilder.append(" infinitely");
        } else {
            stringBuilder.append(" to ");
            stringBuilder.append(DateTimeFormatter.ISO_DATE.format(delete.getLeft()));
            stringBuilder.append(", ");
            stringBuilder.append(delete.getRight().getHeading());
        }
        if (Objects.nonNull(stepSize)) {
            stringBuilder.append(", step size ");
            stringBuilder.append(stepSize);
        }
        return stringBuilder.toString();
    }
}
