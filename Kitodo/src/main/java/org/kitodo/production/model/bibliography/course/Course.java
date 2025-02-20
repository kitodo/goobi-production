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

package org.kitodo.production.model.bibliography.course;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.MetadataException;
import org.kitodo.production.forms.createprocess.ProcessSimpleMetadata;
import org.kitodo.production.helper.XMLUtils;
import org.kitodo.production.model.bibliography.course.metadata.CountableMetadata;
import org.kitodo.production.model.bibliography.course.metadata.RecoveredMetadata;
import org.kitodo.production.services.data.ImportService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The class Course represents the course of appearance of a newspaper.
 *
 * <p>
 * A course of appearance consists of one or more blocks of time. Interruptions
 * in the course of appearance can be modeled by subsequent blocks.
 */
public class Course extends ArrayList<Block> {

    /**
     * Attribute {@code after="…"} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * Newspapers, especially bigger ones, can have several issues that, e.g.,
     * may differ in time of publication (morning issue, evening issue, …) or
     * geographic distribution (Edinburgh issue, London issue, …). Normally,
     * when parsing the XML file, the issues are created in their order of first
     * appearance. However, if you want to enforce a different order, you can
     * define it here.
     *
     * <p>
     * The attribute {@code after="…"} holds the names of issues that shall be
     * ordered before this issue. Several issues are to be separated by white
     * space. Issue names containing white space must be enclosed in double
     * replaced by two subsequent apostrophes ("{@code ''}", 2× U+0027).
     *
     * <p>
     * Not-yet-mentioned issues are created before, though maybe empty.
     */
    private static final String ATTRIBUTE_AFTER = "after";

    /**
     * Attribute {@code date="…"} used in the XML representation of a course of
     * appearance.
     */
    private static final String ATTRIBUTE_DATE = "date";

    /**
     * Attribute {@code increment="…"} used in the XML representation of a
     * course of appearance.
     *
     * <p>
     * The attribute {@code increment="…"} can have as value one of the
     * {@link Granularity} values, in lower case. It indicates when the counter
     * shall be incremented.
     */
    private static final String ATTRIBUTE_INCREMENT = "increment";

    /**
     * Attribute {@code issue="…"} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * The attribute {@code issue="…"} holds the name of the issue. Newspapers,
     * especially bigger ones, can have several issues that, e.g., may differ in
     * time of publication (morning issue, evening issue, …) or geographic
     * distribution (Edinburgh issue, London issue, …).
     */
    private static final String ATTRIBUTE_ISSUE_HEADING = "issue";

    /**
     * Attribute {@code metadataType="…"} used in the XML representation of a
     * course of appearance.
     *
     * <p>
     * The attribute {@code metadataType="…"} holds the name of the metadata
     * type that this counter will be written to.
     */
    private static final String ATTRIBUTE_METADATA_TYPE = "metadataType";

    /**
     * Attribute {@code value="…"} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * The attribute {@code value="…"} holds the counter start value.
     */
    private static final String ATTRIBUTE_VALUE = "value";

    /**
     * Attribute {@code index="…"} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * The attribute {@code index="…"} is optional. It may be used to
     * distinguish different blocks if needed and can be omitted if only one
     * block is used.
     */
    private static final String ATTRIBUTE_VARIANT = "index";

    /**
     * Attribute {@code yearBegin="…"} used in the XML representation of a
     * course of appearance.
     *
     * <p>
     * The attribute {@code yearBegin="…"} is optional. It may be used to
     * indicate a year begin different from the first of January, as it may be
     * used for school years, business years, or seasons.
     */
    private static final String ATTRIBUTE_YEAR_BEGIN = "yearBegin";

    /**
     * Attribute {@code yearTerm="…"} used in the XML representation of a course
     * of appearance.
     *
     * <p>
     * The attribute {@code yearTerm="…"} is optional. It may be used to
     * indicate the type of year that begins with a date different from the
     * first of January, values maybe like “business year”, “season”, or “school
     * year”.
     */
    private static final String ATTRIBUTE_YEAR_TERM = "yearTerm";

    /**
     * Element {@code <appeared>} used in the XML representation of a
     * course of appearance.
     *
     * <p>
     * Each {@code <appeared>} element represents one issue that
     * physically appeared. It has the attributes {@code issue="…"}
     * (required, may be empty) and {@code date="…"} (required) and cannot
     * hold child elements.
     */
    private static final String ELEMENT_APPEARED = "appeared";

    /**
     * Element {@code <course>} used in the XML representation of a
     * course of appearance.
     *
     * <p>
     * {@code <course>} is the root element of the XML
     * representation. It can hold two children,
     * {@code <description>} (output only, optional) and
     * {@code <processes>} (required).
     */
    private static final String ELEMENT_COURSE = "course";

    /**
     * Element {@code <description>} used in the XML representation
     * of a course of appearance.
     *
     * <p>
     * {@code <description>} holds a verbal, human-readable
     * description of the course of appearance, which is generated only and
     * doesn’t have an effect on input.
     */
    private static final String ELEMENT_DESCRIPTION = "description";

    /**
     * Element {@code <metadata>} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * {@code <metadata>} declares an auto-counting metadata value assigned to
     * the issue it is used in. The counter will start counting until it is
     * replaced by another counter. A counter value of {@code ""} disables the
     * counter.
     */
    private static final String ELEMENT_METADATA = "metadata";

    /**
     * Element {@code <process>} used in the XML representation of a course of
     * appearance.
     *
     * <p>
     * Each {@code <process>} element represents one process to be generated in
     * Production. It can hold {@code <title>} elements (of any quantity).
     */
    private static final String ELEMENT_PROCESS = "process";

    /**
     * Element {@code <processes>} used in the XML representation of
     * a course of appearance.
     *
     * <p>
     * Each {@code <processes>} element represents the processes to
     * be generated in Production. It can hold
     * {@code <process>} elements (of any quantity).
     */
    private static final String ELEMENT_PROCESSES = "processes";

    /**
     * Element {@code <title>} used in the XML representation of a
     * course of appearance. Each {@code <title>} element represents
     * a block in time the appeared issues belong to. It has the optional
     * attribute {@code index="…"} and can hold
     * {@code <appeared>} elements (of any quantity).
     *
     * <p>
     * Note: In the original design, the element was intended to model title
     * name changes. This was given up later, but for historical reasons, the
     * XML element’s name is still “title”. For the original design, see
     * https://github.com/kitodo/kitodo-production/issues/51#issuecomment-38035674
     */
    private static final String ELEMENT_BLOCK = "title";

    /**
     * January the 1ˢᵗ.
     */
    public static final MonthDay FIRST_OF_JANUARY = MonthDay.of(1, 1);

    private static final int WEEKDAY_PAGES = 40;
    private static final int SUNDAY_PAGES = 240;

    /**
     * List of Lists of Issues, each representing a process.
     */
    private final transient List<List<IndividualIssue>> processes = new ArrayList<>();
    private final transient Map<String, Block> resolveByBlockVariantCache = new HashMap<>();

    private boolean processesAreVolatile = true;

    /**
     * The name of the year, such as “business year”, “fiscal year”, or
     * “season”.
     */
    private String yearName = "";

    /**
     * The first day of the year.
     */
    private MonthDay yearStart = MonthDay.of(1, 1);

    /**
     * Default constructor, creates an empty course. Must be made explicit since
     * we offer other constructors, too.
     */
    public Course() {
        super();
    }

    /**
     * Constructor to create a course from an XML source.
     *
     * @param xml
     *            XML document data structure
     * @param possibleProcessDetails
     *            possible process details from the process creation form
     * @throws InvalidMetadataValueException
     *             if an invalid value was given for a select-type metadata
     * @throws NoSuchElementException
     *             if ELEMENT_COURSE or ELEMENT_PROCESSES cannot be found
     * @throws IllegalArgumentException
     *             if the dates of two blocks do overlap
     * @throws NullPointerException
     *             if a mandatory element is absent
     */
    public Course(Document xml, Map<String, ProcessSimpleMetadata> possibleProcessDetails) throws InvalidMetadataValueException {
        super();
        processesAreVolatile = false;
        Element rootNode = XMLUtils.getFirstChildWithTagName(xml, ELEMENT_COURSE);
        String yearBegin = rootNode.getAttribute(ATTRIBUTE_YEAR_BEGIN);
        if (!yearBegin.isEmpty()) {
            LocalDate dateTime = LocalDate.parse(yearBegin,
                    DateTimeFormatter.ofPattern("--MM-dd").withLocale(DateTimeFormatter.ISO_DATE.getLocale()));
            yearStart = MonthDay.of(dateTime.getMonthValue(), dateTime.getDayOfMonth());
        }
        yearName = rootNode.getAttribute(ATTRIBUTE_YEAR_TERM);
        Element processesNode = XMLUtils.getFirstChildWithTagName(rootNode, ELEMENT_PROCESSES);
        int initialCapacity = 10;
        Map<LocalDate, IndividualIssue> lastIssueForDate = new HashMap<>();
        List<RecoveredMetadata> recoveredMetadata = new LinkedList<>();
        for (Node processNode = processesNode.getFirstChild(); processNode != null; processNode = processNode
                .getNextSibling()) {
            if (!(processNode instanceof Element) || !processNode.getNodeName().equals(ELEMENT_PROCESS)) {
                continue;
            }
            initialCapacity = processProcessNode(initialCapacity, lastIssueForDate, recoveredMetadata, processNode);
        }
        processRecoveredMetadata(recoveredMetadata, possibleProcessDetails);
        recalculateRegularityOfIssues();
        processesAreVolatile = true;
    }

    private int processProcessNode(int initialCapacity, Map<LocalDate, IndividualIssue> lastIssueForDate,
                                   List<RecoveredMetadata> recoveredMetadata, Node processNode) {
        List<IndividualIssue> process = new ArrayList<>(initialCapacity);
        for (Node blockNode = processNode.getFirstChild(); blockNode != null; blockNode = blockNode
                .getNextSibling()) {
            if (!(blockNode instanceof Element) || !blockNode.getNodeName().equals(ELEMENT_BLOCK)) {
                continue;
            }
            processBlockNode(lastIssueForDate, recoveredMetadata, process, blockNode);
        }
        processes.add(process);
        initialCapacity = (int) Math.round(1.1 * process.size());
        return initialCapacity;
    }

    private void processBlockNode(Map<LocalDate, IndividualIssue> lastIssueForDate,
                                  List<RecoveredMetadata> recoveredMetadata, List<IndividualIssue> process,
                                  Node blockNode) {
        String variant = ((Element) blockNode).getAttribute(ATTRIBUTE_VARIANT);
        for (Node issueNode = blockNode.getFirstChild(); issueNode != null; issueNode = issueNode
                .getNextSibling()) {
            if (!(issueNode instanceof Element) || !issueNode.getNodeName().equals(ELEMENT_APPEARED)) {
                continue;
            }
            String issue = ((Element) issueNode).getAttribute(ATTRIBUTE_ISSUE_HEADING);
            String date = ((Element) issueNode).getAttribute(ATTRIBUTE_DATE);
            if (date == null) {
                throw new NullPointerException(ATTRIBUTE_DATE);
            }
            String after = ((Element) issueNode).getAttribute(ATTRIBUTE_AFTER);
            List<String> before = Objects.isNull(after) ? Collections.emptyList()
                    : splitAtSpaces(after);
            LocalDate localDate = LocalDate.parse(date);
            IndividualIssue individualIssue = addAddition(variant, before, issue, localDate);
            IndividualIssue previousIssue = lastIssueForDate.get(localDate);
            if (previousIssue != null) {
                Integer sortingNumber = previousIssue.getSortingNumber();
                if (sortingNumber == null) {
                    sortingNumber = 1;
                    previousIssue.setSortingNumber(sortingNumber);
                }
                individualIssue.setSortingNumber(sortingNumber + 1);
            }
            lastIssueForDate.put(localDate, individualIssue);
            process.add(individualIssue);
            findToBeRecoveredMetadata(recoveredMetadata, issueNode, issue, date);
        }
    }

    private void processRecoveredMetadata(List<RecoveredMetadata> recoveredMetadata,
            Map<String, ProcessSimpleMetadata> possibleProcessDetails) throws InvalidMetadataValueException {
        Map<Pair<Block, String>, CountableMetadata> last = new HashMap<>();
        for (RecoveredMetadata metaDatum : recoveredMetadata) {
            Block foundBlock = null;
            Issue foundIssue = null;
            BLOCK: for (Block block : this) {
                for (IndividualIssue individualIssue : block.getIndividualIssues(metaDatum.getDate())) {
                    if (individualIssue.getHeading().equals(metaDatum.getIssue())) {
                        foundBlock = block;
                        foundIssue = individualIssue.getIssue();
                        break BLOCK;
                    }
                }
            }
            CountableMetadata previousMetadata = last.get(Pair.of(foundBlock, metaDatum.getMetadataType()));
            if (previousMetadata != null) {
                previousMetadata.setDelete(Pair.of(metaDatum.getDate(), foundIssue));
            }
            CountableMetadata metadata = new CountableMetadata(foundBlock, Pair.of(metaDatum.getDate(), foundIssue));
            metadata.setMetadataType(metaDatum.getMetadataType());
            ProcessSimpleMetadata processSimpleMetadata = possibleProcessDetails.get(metaDatum.getMetadataType());
            if (Objects.isNull(processSimpleMetadata)) {
                throw new MetadataException("Cannot add metadata key " + metaDatum.getMetadataType(),
                        new NoSuchElementException(metaDatum.getMetadataType()));
            }
            metadata.setMetadataDetail(processSimpleMetadata.getClone());
            metadata.setStartValue(metaDatum.getValue());
            metadata.setStepSize(metaDatum.getStepSize());
            if (Objects.nonNull(foundBlock)) {
                foundBlock.addMetadata(metadata);
                last.put(Pair.of(foundBlock, metaDatum.getMetadataType()), metadata);
            }
        }
    }

    private void findToBeRecoveredMetadata(List<RecoveredMetadata> recoveredMetadata, Node issueNode,
                                           String issue, String date) {
        for (Node metadataNode = issueNode
                .getFirstChild(); metadataNode != null; metadataNode = metadataNode.getNextSibling()) {
            if (!(metadataNode instanceof Element)
                    || !metadataNode.getNodeName().equals(ELEMENT_METADATA)) {
                continue;
            }
            RecoveredMetadata recovered = new RecoveredMetadata(LocalDate.parse(date), issue);
            recovered.setMetadataType(((Element) metadataNode).getAttribute(ATTRIBUTE_METADATA_TYPE));
            if (recovered.getMetadataType() == null) {
                throw new NullPointerException(ATTRIBUTE_METADATA_TYPE);
            }
            recovered.setValue(((Element) metadataNode).getAttribute(ATTRIBUTE_VALUE));
            if (recovered.getValue() == null) {
                throw new NullPointerException(ATTRIBUTE_VALUE);
            }
            String increment = ((Element) metadataNode).getAttribute(ATTRIBUTE_INCREMENT);
            try {
                recovered.setStepSize(Granularity.valueOf(increment.toUpperCase()));
            } catch (IllegalArgumentException e) {
                recovered.setStepSize(null);
            }
            recoveredMetadata.add(recovered);
        }
    }

    /**
     * Appends the specified block to the end of this course.
     *
     * @param block
     *            block to be appended to this course
     * @return true (as specified by Collection.add(E))
     * @see java.util.ArrayList#add(java.lang.Object)
     */
    @Override
    public boolean add(Block block) {
        super.add(block);
        if (block.countIndividualIssues() > 0) {
            processes.clear();
        }
        return true;
    }

    /**
     * Adds a LocalDate to the set of additions of the issue identified by
     * issueHeading in the block optionally identified by a variant. Note that
     * in case that the date is outside the time range of the described block,
     * the time range will be expanded. Do not use this function in contexts
     * where there is one or more issues in the block that have a regular
     * appearance set, because in this case the regularly appeared issues in the
     * expanded block will show up later, too, which is probably not what you
     * want.
     *
     * @param variant
     *            block identifier (may be null)
     * @param beforeIssues
     *            issues to be existing before this one
     * @param issueHeading
     *            heading of the issue this issue is of
     * @param date
     *            date to add
     * @return an IndividualIssue representing the added issue
     * @throws IllegalArgumentException
     *             if the date would cause the block to overlap with another
     *             block
     */
    private IndividualIssue addAddition(String variant, List<String> beforeIssues, String issueHeading,
            LocalDate date) {

        Block block = get(variant);
        if (block == null) {
            block = new Block(this, variant);
            try {
                block.setFirstAppearance(date);
                block.setLastAppearance(date);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + ", (" + variant + ") " + date, e);
            }
            add(block);
        } else {
            if (block.getFirstAppearance().isAfter(date)) {
                block.setFirstAppearance(date);
            }
            if (block.getLastAppearance().isBefore(date)) {
                block.setLastAppearance(date);
            }
        }
        for (String issueBefore : beforeIssues) {
            Issue issue = block.getIssue(issueBefore);
            if (issue == null) {
                issue = new Issue(this, issueBefore);
                block.addIssue(issue);
            }
        }
        Issue issue = block.getIssue(issueHeading);
        if (issue == null) {
            issue = new Issue(this, issueHeading);
            block.addIssue(issue);
        }
        issue.addAddition(date);
        return new IndividualIssue(block, issue, date, null);
    }

    /**
     * Deletes the process list. This is
     * necessary if the processes must be regenerated because the data structure
     * they will be derived from has changed, or if they only had been added
     * temporarily to be able to retrieve an XML file containing values.
     */
    public void clearProcesses() {
        if (processesAreVolatile) {
            processes.clear();
        }
    }

    /**
     * Determines how many stampings of
     * issues physically appeared without generating a list of IndividualIssue
     * objects.
     *
     * @return the count of issues
     */
    public long countIndividualIssues() {
        long numberOfIndividualIssues = 0;
        for (Block block : this) {
            numberOfIndividualIssues += block.countIndividualIssues();
        }
        return numberOfIndividualIssues;
    }

    /**
     * Returns the block identified by the optionally given variant, or null if
     * no block with the given variant can be found.
     *
     * @param variant
     *            the variant of the block (may be null)
     * @return the block identified by the given variant, or null if no block
     *         can be found
     */
    private Block get(String variant) {
        if (resolveByBlockVariantCache.containsKey(variant)) {
            Block potentialResult = resolveByBlockVariantCache.get(variant);
            if (potentialResult.isIdentifiedBy(variant)) {
                return potentialResult;
            } else {
                resolveByBlockVariantCache.remove(variant);
            }
        }
        for (Block candidate : this) {
            if (candidate.isIdentifiedBy(variant)) {
                resolveByBlockVariantCache.put(variant, candidate);
                return candidate;
            }
        }
        return null;
    }

    /**
     * Generates a list of IndividualIssue
     * objects, each of them representing a stamping of one physically appeared
     * issue.
     *
     * @return a LinkedHashSet of IndividualIssue objects, each of them
     *         representing one physically appeared issue
     */
    public Set<IndividualIssue> getIndividualIssues() {
        LinkedHashSet<IndividualIssue> individualIssues = new LinkedHashSet<>();
        LocalDate lastAppearance = getLastAppearance();
        LocalDate firstAppearance = getFirstAppearance();
        if (Objects.nonNull(firstAppearance)) {
            for (LocalDate day = firstAppearance; !day.isAfter(lastAppearance); day = day.plusDays(1)) {
                for (Block block : this) {
                    individualIssues.addAll(block.getIndividualIssues(day));
                }
            }
        }
        return individualIssues;
    }

    /**
     * Returns the date the regularity of this
     * course of appearance starts with.
     *
     * @return the date of first appearance
     */
    public LocalDate getFirstAppearance() {
        if (super.isEmpty()) {
            return null;
        }
        LocalDate firstAppearance = super.get(0).getFirstAppearance();
        for (int index = 1; index < super.size(); index++) {
            LocalDate otherFirstAppearance = super.get(index).getFirstAppearance();
            if (otherFirstAppearance.isBefore(firstAppearance)) {
                firstAppearance = otherFirstAppearance;
            }
        }
        return firstAppearance;
    }

    /**
     * Returns the date the regularity of this
     * course of appearance ends with.
     *
     * @return the date of last appearance
     */
    public LocalDate getLastAppearance() {
        if (super.isEmpty()) {
            return null;
        }
        LocalDate lastAppearance = super.get(0).getLastAppearance();
        for (int index = 1; index < super.size(); index++) {
            LocalDate otherLastAppearance = super.get(index).getLastAppearance();
            if (otherLastAppearance.isAfter(lastAppearance)) {
                lastAppearance = otherLastAppearance;
            }
        }
        return lastAppearance;
    }

    /**
     * Returns the number of processes into
     * which the course of appearance will be split.
     *
     * @return the number of processes
     */
    public int getNumberOfProcesses() {
        return processes.size();
    }

    /**
     * Returns the name of the year. The name of the year is optional and maybe
     * empty. Typical values are “Business year”, “Fiscal year”, or “Season”.
     *
     * @return the name of the year
     */
    public String getYearName() {
        return yearName;
    }

    /**
     * Returns the beginning of the year. Typically, this is the 1ˢᵗ of January,
     * but it can be changed here to other days as well. The beginning of the
     * year must parse and must not not be empty.
     *
     * @return the beginning of the year
     */
    public MonthDay getYearStart() {
        return yearStart;
    }

    /**
     * Calculates a guessed number of pages for a course of appearance of a
     * newspaper, presuming each issue having 40 pages and Sunday issues having
     * six times that size because most people buy the Sunday issue most often
     * and therefore advertisers buy the most space on that day.
     *
     * @return a guessed total number of pages for the full course of appearance
     */
    public long guessTotalNumberOfPages() {
        long totalNumberOfPages = 0;
        for (Block block : this) {
            LocalDate lastAppearance = block.getLastAppearance();
            for (LocalDate day = block.getFirstAppearance(); !day.isAfter(lastAppearance); day = day.plusDays(1)) {
                for (Issue issue : block.getIssues()) {
                    if (issue.isMatch(day)) {
                        totalNumberOfPages += day.getDayOfWeek() != DayOfWeek.SUNDAY ? WEEKDAY_PAGES
                                : SUNDAY_PAGES;
                    }
                }
            }
        }
        return totalNumberOfPages;
    }

    /**
     * Returns the processes to create from the
     * course of appearance.
     *
     * @return the processes
     */
    public List<List<IndividualIssue>> getProcesses() {
        return processes;
    }

    /**
     * Iterates over the array of blocks and returns the
     * first one that matches a given date. Since there shouldn’t be overlapping
     * blocks, there should be at most one block for which this is true. If no
     * matching block is found, it will return null.
     *
     * @param date
     *            a LocalDate to examine
     * @return the block on which this date is represented, if any
     */
    public Block isMatch(LocalDate date) {
        for (Block block : this) {
            if (block.isMatch(date)) {
                return block;
            }
        }
        return null;
    }

    /**
     * Joins a list of strings to a string of whitespace-separated tokens,
     * surrounding tokens containing spaces with quotes.
     *
     * @param input
     *            string to tokenize
     * @return list of split strings
     */
    private static String joinQuoting(Collection<String> input) {
        StringBuilder result = new StringBuilder(16 * input.size());
        boolean first = true;
        for (String item : input) {
            if (first) {
                first = false;
            } else {
                result.append(' ');
            }
            boolean hasSpace = item.indexOf(' ') > -1;
            if (hasSpace) {
                result.append('"');
            }
            result.append(item.replaceAll("\"", "''"));
            if (hasSpace) {
                result.append('"');
            }
        }
        return result.toString();
    }

    /**
     * Recalculates for all blocks of this Course for each Issue the daysOfWeek
     * of its regular appearance within the interval of time of the block. This
     * is especially sensible to detect the underlying regularity after lots of
     * issues whose existence is known have been added one by one as additions
     * to the underlying issue(s).
     */
    public void recalculateRegularityOfIssues() {
        for (Block block : this) {
            block.recalculateRegularityOfIssues();
        }
    }

    /**
     * Removes the element at the specified position in
     * this list. Shifts any subsequent elements to the left (subtracts one from
     * their indices). Additionally, any references to the object held in the
     * map used for resolving are being removed so that the object can be
     * garbage-collected.
     *
     * @param index
     *            the index of the element to be removed
     * @return the element that was removed from the list
     * @throws IndexOutOfBoundsException
     *             if the index is out of range (index < 0 || index >= size())
     * @see java.util.ArrayList#remove(int)
     */
    @Override
    public Block remove(int index) {
        Block block = super.remove(index);
        resolveByBlockVariantCache.entrySet().removeIf(entry -> entry.getValue() == block);
        if (block.countIndividualIssues() > 0) {
            processes.clear();
        }
        return block;
    }

    /**
     * Splits a string of whitespace-separated tokens, considering tokens
     * surrounded by quotes as one.
     *
     * @param input
     *            string to tokenize
     * @return list of split strings
     */
    private static List<String> splitAtSpaces(String input) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(input);
        while (matcher.find()) {
            result.add(matcher.group(1).replaceFirst("^\"(.*)\"$", "$1").replaceAll("''", "\""));
        }
        return result;
    }

    /**
     * Calculates the processes depending on the given BreakMode.
     *
     * @param mode
     *            how the course shall be broken into issues
     */

    public void splitInto(Granularity mode) {
        int initialCapacity = 10;
        Integer lastMark = null;
        List<IndividualIssue> process = null;

        processes.clear();
        for (IndividualIssue issue : getIndividualIssues()) {
            Integer mark = issue.getBreakMark(mode, yearStart);
            if (!mark.equals(lastMark) && process != null) {
                initialCapacity = (int) Math.round(1.1 * process.size());
                processes.add(process);
                process = null;
            }
            if (process == null) {
                process = new ArrayList<>(initialCapacity);
            }
            process.add(issue);
            lastMark = mark;
        }
        if (process != null) {
            processes.add(process);
        }
    }

    /**
     * Transforms a course of appearance to XML.
     *
     * @return XML as String
     */
    public Document toXML() throws IOException {
        Document xml = XMLUtils.newDocument();
        Element courseNode = xml.createElement(ELEMENT_COURSE);
        if (!yearStart.equals(FIRST_OF_JANUARY)) {
            courseNode.setAttribute(ATTRIBUTE_YEAR_BEGIN, yearStart.toString());
        }
        if (!yearName.isEmpty()) {
            courseNode.setAttribute(ATTRIBUTE_YEAR_TERM, yearName);
        }

        Element description = xml.createElement(ELEMENT_DESCRIPTION);
        description.appendChild(xml.createTextNode(StringUtils.join(CourseToGerman.asReadableText(this), "\n\n")));
        courseNode.appendChild(description);

        courseNode.appendChild(processesToXml(xml));
        xml.appendChild(courseNode);
        return xml;
    }

    private Element processesToXml(Document xml) {
        Element processesNode = xml.createElement(ELEMENT_PROCESSES);
        Set<Pair<Integer, String>> afterDeclarations = new HashSet<>();
        for (List<IndividualIssue> process : processes) {
            Element processNode = xml.createElement(ELEMENT_PROCESS);
            Element blockNode = null;
            int previous = -1;
            for (IndividualIssue issue : process) {
                blockNode = issueToXml(xml, afterDeclarations, processNode, blockNode, previous, issue);
            }
            if (blockNode != null) {
                processNode.appendChild(blockNode);
            }
            processesNode.appendChild(processNode);
        }
        return processesNode;
    }

    private Element issueToXml(Document xml, Set<Pair<Integer, String>> afterDeclarations, Element processNode,
            Element blockNode, int previous, IndividualIssue issue) {
        int index = issue.indexIn(this);
        if (index != previous && blockNode != null) {
            processNode.appendChild(blockNode);
            blockNode = null;
        }
        if (blockNode == null) {
            blockNode = xml.createElement(ELEMENT_BLOCK);
            blockNode.setAttribute(ATTRIBUTE_VARIANT, Integer.toString(index + 1));
        }
        Element issueNode = xml.createElement(ELEMENT_APPEARED);
        if (!StringUtils.isBlank(issue.getHeading())) {
            issueNode.setAttribute(ATTRIBUTE_ISSUE_HEADING, issue.getHeading());
        }
        issueNode.setAttribute(ATTRIBUTE_DATE, issue.getDate().toString());
        addMetadataToIssue(xml, issue, issueNode);
        Pair<Integer, String> afterDeclaration = Pair.of(index, issue.getHeading());
        if (!afterDeclarations.contains(afterDeclaration)) {
            List<String> issuesBefore = issue.getIssuesBefore();
            if (!issuesBefore.isEmpty()) {
                issueNode.setAttribute(ATTRIBUTE_AFTER, joinQuoting(issuesBefore));
            }
            afterDeclarations.add(afterDeclaration);
        }
        blockNode.appendChild(issueNode);
        previous = index;
        return blockNode;
    }

    private void addMetadataToIssue(Document xml, IndividualIssue issue, Element issueNode) {
        Pair<LocalDate, Issue> issueId = Pair.of(issue.getDate(), issue.getIssue());
        Map<String, CountableMetadata> metadata = new HashMap<>();
        for (Block block : this) {
            for (CountableMetadata metaDatum : block.getMetadata(issueId, false)) {
                metadata.put(metaDatum.getMetadataType(), metaDatum);
            }
        }
        for (Block block : this) {
            for (CountableMetadata metaDatum : block.getMetadata(issueId, true)) {
                metadata.put(metaDatum.getMetadataType(), metaDatum);
            }
        }
        for (Entry<String, CountableMetadata> entry : metadata.entrySet()) {
            Element metadataNode = xml.createElement(ELEMENT_METADATA);
            metadataNode.setAttribute(ATTRIBUTE_METADATA_TYPE, entry.getKey());
            CountableMetadata metaDatum = entry.getValue();
            if (metaDatum.matches(metaDatum.getMetadataType(), issueId, false)) {
                metadataNode.setAttribute(ATTRIBUTE_VALUE, "");
            } else {
                metadataNode.setAttribute(ATTRIBUTE_VALUE, ImportService.getProcessDetailValue(metaDatum.getMetadataDetail()));
                if (metaDatum.getStepSize() != null) {
                    metadataNode.setAttribute(ATTRIBUTE_INCREMENT, metaDatum.getStepSize().toString().toLowerCase());
                }
            }
            issueNode.appendChild(metadataNode);
        }
    }

    /**
     * Sets the year name of the course.
     *
     * @param yearName
     *            the yearName to set
     */
    public void setYearName(String yearName) {
        this.yearName = yearName;
    }

    /**
     * Sets the year start of the course.
     *
     * @param yearStart
     *            the yearStart to set
     */
    public void setYearStart(MonthDay yearStart) {
        this.yearStart = yearStart;
    }

    /**
     * Returns a shallow copy of this Course instance.
     *
     * @return a clone of this Course instance
     */
    @Override
    public Course clone() {
        return (Course) super.clone();
    }
}
