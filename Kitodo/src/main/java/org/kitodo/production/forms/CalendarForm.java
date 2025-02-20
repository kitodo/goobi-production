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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import javax.naming.ConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.DoctypeMissingException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.forms.createprocess.ProcessSimpleMetadata;
import org.kitodo.production.forms.createprocess.ProcessTextMetadata;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.XMLUtils;
import org.kitodo.production.helper.tasks.GeneratesNewspaperProcessesThread;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.model.bibliography.course.Block;
import org.kitodo.production.model.bibliography.course.Cell;
import org.kitodo.production.model.bibliography.course.Course;
import org.kitodo.production.model.bibliography.course.Granularity;
import org.kitodo.production.model.bibliography.course.IndividualIssue;
import org.kitodo.production.model.bibliography.course.Issue;
import org.kitodo.production.model.bibliography.course.metadata.CountableMetadata;
import org.kitodo.production.process.NewspaperProcessesGenerator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.calendar.CalendarService;
import org.kitodo.production.services.data.ImportService;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The class CalendarForm provides the screen logic for a JSF calendar editor to
 * enter the course of appearance of a newspaper.
 */
@Named("CalendarForm")
@ViewScoped
public class CalendarForm implements Serializable {
    private static final Logger logger = LogManager.getLogger(CalendarForm.class);

    private static final String BLOCK = "calendar.block.";
    private static final String BLOCK_NEGATIVE = BLOCK + "negative";
    private static final String UPLOAD_ERROR = "calendar.upload.error";
    private static final String REDIRECT_PARAMETER = "faces-redirect=true";
    private static final String DEFAULT_REFERER = "processes?" + REDIRECT_PARAMETER;
    private static final String TASK_MANAGER_REFERER = "system.jsf?tabIndex=0&" + REDIRECT_PARAMETER;
    private static final Integer[] MONTHS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    /**
     * The constant field issueColours holds the colors used to represent the
     * issues in the calendar editor.
     */
    private static String[] issueColours;

    /**
     * The constant field START_RELATION hold the date the course of publication
     * of the the German-language “Relation aller Fürnemmen und gedenckwürdigen
     * Historien”, which is often recognized as the first newspaper, began. If
     * the user tries to create a block before that date, a hint will be shown.
     */
    private static final LocalDate START_RELATION = LocalDate.of(1605, 9, 12);

    private String referer = DEFAULT_REFERER;
    private Granularity granularity = Granularity.ISSUES;
    private int numberOfPagesPerIssue = 0;
    protected int yearShowing = 1979;
    private UploadedFile uploadedFile;
    private LocalDate selectedDate;
    private Block selectedBlock = null;

    /**
     * The field course holds the course of appearance currently under edit by
     * this calendar form instance.
     */
    protected Course course;

    /**
     * The constant field today hold the date of today. Reading the system clock
     * requires much synchronisation throughout the JVM and is therefore only
     * done once on form creation.
     */
    private final LocalDate today = LocalDate.now();
    private Integer parentId;

    private String activeIndexes = "0";

    /**
     * Empty constructor. Creates a new form without yet any data.
     *
     * <p>
     * The issue color presets are samples which have been chosen to provide
     * distinguishability also for users with red-green color vision deficiency.
     * Arbitrary colors can be defined in kitodo_config.properties by setting
     * the property “issue.colours”.
     */
    public CalendarForm() {
        issueColours = ConfigCore.getParameterOrDefaultValue(ParameterCore.ISSUE_COLOURS).split(";");
        course = new Course();

    }

    /**
     * Gets activeIndexes.
     *
     * @return value of activeIndexes
     */
    public String getActiveIndexes() {
        return activeIndexes;
    }

    /**
     * Sets activeIndexes.
     *
     * @param activeIndexes value of activeIndexes
     */
    public void setActiveIndexes(String activeIndexes) {
        this.activeIndexes = activeIndexes;
    }

    /**
     * Get referer.
     *
     * @return value of referer
     */
    public String getReferer() {
        return referer;
    }

    /**
     * Set referer.
     *
     * @param referer as java.lang.String
     */
    public void setReferer(String referer) {
        if (Objects.nonNull(referer) && !referer.isEmpty()) {
            this.referer = referer;
        }
    }

    /**
     * Set parent processId.
     *
     * @param parentId as java.lang.Integer
     */
    public void setParentId(Integer parentId) {
        if (Objects.nonNull(parentId)) {
            this.parentId = parentId;
        }
    }

    /**
     * Gets parentId.
     *
     * @return value of parentId
     */
    public Integer getParentId() {
        return parentId;
    }

    /**
     * Get all possible granularities.
     *
     * @return list of Granularity objects
     */
    public List<Granularity> getGranularities() {
        return Arrays.asList(Granularity.values());
    }

    /**
     * Get granularity.
     *
     * @return value of granularity
     */
    public Granularity getGranularity() {
        return granularity;
    }

    /**
     * Set granularity.
     *
     * @param granularity as org.kitodo.production.model.bibliography.course.Granularity
     */
    public void setGranularity(Granularity granularity) {
        this.granularity = granularity;
        course.splitInto(granularity);
        if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
            PrimeFaces.current().ajax().update("createProcessesConfirmDialog");
        }
    }

    /**
     * Get array representing the months of a year.
     *
     * @return value of MONTHS
     */
    public static Integer[] getMonths() {
        return MONTHS;
    }

    /**
     * Get the currently displayed year.
     *
     * @return the year to be displayed as java.lang.String
     */
    public String getYear() {
        return Integer.toString(yearShowing);
    }

    /**
     * Set the currently displayed year.
     *
     * @param year to be displayed as java.lang.String
     */
    public void setYear(String year) {
        yearShowing = Integer.parseInt(year);
    }

    /**
     * Display the previous year in the calendar.
     */
    public void previousYear() {
        yearShowing -= 1;
    }

    /**
     * Display the next year in the calendar.
     */
    public void nextYear() {
        yearShowing += 1;
    }

    /**
     * Get estimated number of pages per issue.
     *
     * @return value of numberOfPagesPerIssue
     */
    public int getNumberOfPagesPerIssue() {
        return numberOfPagesPerIssue;
    }

    /**
     * Set estimated number of pages per issue.
     *
     * @param numberOfPagesPerIssue as int
     */
    public void setNumberOfPagesPerIssue(int numberOfPagesPerIssue) {
        this.numberOfPagesPerIssue = numberOfPagesPerIssue;
    }

    /**
     * Get the number of pages of every process for the chosen granularity.
     * Formatted as String with one decimal place.
     *
     * @return number of images as java.lang.String
     */
    public String getNumberOfPagesPerProcessFormatted() {
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        return decimalFormat.format(getNumberOfPagesPerProcess());
    }

    /**
     * Get the number of pages of every process for the chosen granularity.
     *
     * @return number of pages as long
     */
    public double getNumberOfPagesPerProcess() {
        return course.countIndividualIssues() / ((double) Math.max(course.getNumberOfProcesses(), 1)) * numberOfPagesPerIssue;
    }

    /**
     * The function checkBlockPlausibility compares the dates entered against
     * some plausibility assumptions and sets hints otherwise.
     */
    public void checkBlockPlausibility(Block block) {
        LocalDate firstAppearance = block.getFirstAppearance();
        LocalDate lastAppearance = block.getLastAppearance();
        if (Objects.nonNull(firstAppearance) && Objects.nonNull(lastAppearance)) {
            if (firstAppearance.plusYears(100).isBefore(lastAppearance)) {
                Helper.setMessage(BLOCK + "long");
            }
            if (firstAppearance.isAfter(lastAppearance)) {
                Helper.setErrorMessage(BLOCK_NEGATIVE);
            }
            if (firstAppearance.isBefore(START_RELATION)) {
                Helper.setMessage(BLOCK + "firstAppearance.early");
            }
            if (firstAppearance.isAfter(today)) {
                Helper.setMessage(BLOCK + "firstAppearance.fiction");
            }
            if (lastAppearance.isBefore(START_RELATION)) {
                Helper.setMessage(BLOCK + "lastAppearance.early");
            }
            if (lastAppearance.isAfter(today)) {
                Helper.setMessage(BLOCK + "lastAppearance.fiction");
            }
            this.setYear(String.valueOf(firstAppearance.getYear()));
            if (Objects.nonNull(PrimeFaces.current()) && Objects.nonNull(FacesContext.getCurrentInstance())) {
                PrimeFaces.current().ajax().update("editForm:calendarTabView:calendarDetailsLayout");
            }
        }
    }

    /**
     * Change whether the selected issue appeared on the selected date.
     * Depending on the regular interval of appearance this will change the additions and exclusions for this issue.
     *
     * @param selectedIssue issue to be modified
     * @param selectedDate date for which the issue will be modified
     */
    public void changeMatch(Issue selectedIssue, LocalDate selectedDate) {
        if (selectedIssue.isMatch(selectedDate) && selectedIssue.getAdditions().contains(selectedDate)) {
            selectedIssue.removeAddition(selectedDate);
        } else if (selectedIssue.isMatch(selectedDate) && !selectedIssue.getAdditions().contains(selectedDate)) {
            selectedIssue.addExclusion(selectedDate);
        } else if (!selectedIssue.isMatch(selectedDate) && selectedIssue.getExclusions().contains(selectedDate)) {
            selectedIssue.removeExclusion(selectedDate);
        } else if (!selectedIssue.isMatch(selectedDate) && !selectedIssue.getExclusions().contains(selectedDate)) {
            selectedIssue.addAddition(selectedDate);
        }
    }

    /**
     * Creates and adds a copy of the currently
     * showing block.
     */
    public void copyBlock(Block block) {
        Block copy = block.clone(course);
        LocalDate lastAppearance = course.getLastAppearance();
        if (Objects.nonNull(lastAppearance)) {
            LocalDate firstAppearance = lastAppearance.plusDays(1);
            copy.setFirstAppearance(firstAppearance);
            copy.setLastAppearance(firstAppearance);
            course.add(copy);
            navigate(copy);
        }
    }

    /**
     * The function is executed if the user clicks the action
     * link to “export” the calendar data. If the course of appearance doesn’t
     * yet contain generated processes—which is always the case, except that the
     * user just came from uploading a data file and didn’t change anything
     * about it—process data will be generated. Then an XML file will be made
     * out of it and sent to the user’s browser. If the granularity was
     * temporarily added, it will be removed afterwards so that the user will
     * not be presented with the option to generate processes “as imported” if
     * he or she never ran an import before.
     *
     * <p>
     * Note: The process data will be generated with a granularity of “days”
     * (each day forms one process). This setting can be changed later after the
     * data has been re-imported, but it will remain if the user uploads the
     * saved data and then proceeds right to the next page and creates processes
     * with the granularity “as imported”. However, since this is possible
     * and—as to our knowledge in late 2014, when this was written—this is the
     * best option of all, this default has been chosen here.
     */
    public StreamedContent download() {
        boolean granularityWasTemporarilyAdded = false;
        try {
            if (Objects.isNull(course) || course.countIndividualIssues() == 0) {
                Helper.setErrorMessage("errorDataIncomplete", "calendar.isEmpty");
                return null;
            }
            if (course.getNumberOfProcesses() == 0) {
                granularityWasTemporarilyAdded = true;
                course.splitInto(Granularity.DAYS);
            }

            byte[] data = XMLUtils.documentToByteArray(course.toXML(), 4);
            return DefaultStreamedContent.builder().stream(() -> new ByteArrayInputStream(data))
                    .contentType("application/xml").name("newspaper.xml").build();
        } catch (TransformerException e) {
            Helper.setErrorMessage("granularity.download.error", "errorTransformerException", logger, e);
        } catch (IOException e) {
            Helper.setErrorMessage("granularity.download.error", e.getLocalizedMessage(), logger, e);
        } finally {
            if (granularityWasTemporarilyAdded) {
                course.clearProcesses();
            }
        }
        return null;
    }

    /**
     * Returns whether the calendar editor is in mint
     * condition, i.e. there is no block defined yet, as read-only property
     * “blank”.
     *
     * <p>
     * Side note: “empty” is a reserved word in JSP and cannot be used as
     * property name.
     *
     * @return whether there is no block yet
     */
    public boolean getBlank() {
        return course.isEmpty();
    }

    /**
     * Returns the data required to build the
     * calendar sheet as read-only property "calendarSheet". The outer list
     * contains 31 entries, each representing a row of the calendar (the days
     * 1−31), each line then contains 12 cells representing the months. This is
     * due to HTML table being produced line by line.
     *
     * @return the table cells to build the calendar sheet
     */
    public List<List<Cell>> getCalendarSheet() {
        List<List<Cell>> calendarSheet = getEmptySheet();
        populateByCalendar(calendarSheet);
        return calendarSheet;
    }

    /**
     * The function will return the course created with this editor
     * as read-only property "course" to pass it to the next form.
     *
     * @return the course of appearance data model
     */
    public Course getCourse() {
        return course;
    }

    /**
     * Builds the empty calendar sheet with 31 rows
     * of twelve cells with empty objects of type Cell().
     *
     * @return an empty calendar sheet
     */
    private List<List<Cell>> getEmptySheet() {
        List<List<Cell>> emptySheet = new ArrayList<>(31);
        for (int day = 1; day <= 31; day++) {
            ArrayList<Cell> row = new ArrayList<>(12);
            for (int month = 1; month <= 12; month++) {
                row.add(new Cell());
            }
            emptySheet.add(row);
        }
        return emptySheet;
    }

    /**
     * The function is the getter method for the property
     * "uploadedFile" which is write-only, however Faces requires is.
     *
     * @return always null
     */
    public UploadedFile getUploadedFile() {
        return null;
    }

    /**
     * Alters the year the calendar sheet is shown for so
     * that something of the current block is visible to prevent the user from
     * needing to click through centuries manually to get there.
     */
    protected void navigate(Block block) {
        try {
            if (yearShowing > block.getLastAppearance().getYear()) {
                yearShowing = block.getLastAppearance().getYear();
            }
            if (yearShowing < block.getFirstAppearance().getYear()) {
                yearShowing = block.getFirstAppearance().getYear();
            }
        } catch (NullPointerException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Populates an empty calendar sheet by
     * iterating on LocalDate.
     *
     * @param sheet
     *            calendar sheet to populate
     */
    protected void populateByCalendar(List<List<Cell>> sheet) {
        Map<Integer, List<Issue>> issuesMap = new HashMap<>();
        Block currentBlock = null;
        LocalDate nextYear = LocalDate.of(yearShowing + 1, Month.JANUARY, 1);
        for (LocalDate date = LocalDate.of(yearShowing, Month.JANUARY, 1); date
                .isBefore(nextYear); date = date.plusDays(1)) {
            Cell cell = sheet.get(date.getDayOfMonth() - 1).get(date.getMonthValue() - 1);
            cell.setDate(date);
            if (Objects.isNull(currentBlock) || !currentBlock.isMatch(date)) {
                currentBlock = course.isMatch(date);
            }
            if (Objects.isNull(currentBlock)) {
                cell.setOnBlock(false);
            } else {
                Integer hashCode = currentBlock.hashCode();
                if (!issuesMap.containsKey(hashCode)) {
                    issuesMap.put(hashCode, currentBlock.getIssues());
                }
                cell.setIssues(issuesMap.get(hashCode));
            }
        }
    }

    /**
     * Add a block to the course.
     */
    public void addBlock() {
        course.add(new Block(course));
    }

    /**
     * Remove block.
     *
     * @param block
     *          The Block to be removed from the course.
     */
    public void removeBlock(Block block) {
        int index = course.indexOf(block);
        course.remove(block);
        if (index > 0) {
            index--;
        }
        if (!course.isEmpty()) {
            navigate(course.get(index));
        }
    }

    /**
     * Add issue to given block.
     *
     * @param block block to add a new issue to
     */
    public void addIssue(Block block) {
        if (Objects.nonNull(block)) {
            block.addIssue();
            block.checkIssuesWithSameHeading();
        }
    }

    /**
     * Get the color from the list of defined colors for the given index.
     * These colors are used to highlight and distinguish the different issues in the calendar.
     *
     * @param index index to retrieve color for from list of colors
     * @return The color represented by a String containing the color's hex value
     */
    public String getIssueColor(int index) {
        if (index >= 0 && index < issueColours.length) {
            return issueColours[index];
        }
        return "";
    }

    /**
     * The method will be called by Faces to store the new
     * value of the read-write property "uploadedFile", which is a reference to
     * the binary data the user provides for upload.
     *
     * @param data
     *            the UploadedFile object generated by the Tomahawk library
     */
    public void setUploadedFile(UploadedFile data) {
        uploadedFile = data;
    }

    /**
     * Upload an XML file to import a course of appearance.
     * Overrides the existing contents of course with the contents
     * of the XML file.
     */
    public void upload() {
        try {
            if (Objects.isNull(uploadedFile)) {
                Helper.setMessage(UPLOAD_ERROR, "calendar.upload.isEmpty");
                return;
            }
            Document xml = XMLUtils.load(uploadedFile.getInputStream());
            List<ProcessDetail> processDetails = CalendarService
                    .getAddableMetadataTable(ServiceManager.getProcessService().getById(parentId));
            Map<String, ProcessSimpleMetadata> processDetailsByMetadataID = processDetails.stream()
                    .filter(ProcessSimpleMetadata.class::isInstance).map(ProcessSimpleMetadata.class::cast)
                    .collect(Collectors.toMap(ProcessDetail::getMetadataID, Function.identity()));
            course = new Course(xml, processDetailsByMetadataID);
            Helper.removeManagedBean("GranularityForm");
            navigate(course.get(0));
        } catch (SAXException e) {
            Helper.setErrorMessage(UPLOAD_ERROR, "errorSAXException", logger, e);
        } catch (IOException | DataException | DAOException e) {
            Helper.setErrorMessage(UPLOAD_ERROR, e.getLocalizedMessage(), logger, e);
        } catch (IllegalArgumentException e) {
            Helper.setErrorMessage("calendar.upload.overlappingDateRanges", logger, e);
        } catch (NoSuchElementException e) {
            Helper.setErrorMessage(UPLOAD_ERROR, "calendar.upload.missingMandatoryElement", logger, e);
        } catch (NullPointerException e) {
            Helper.setErrorMessage("calendar.upload.missingMandatoryValue", logger, e);
        } catch (InvalidMetadataValueException e) {
            Helper.setErrorMessage("calendar.upload.invalidMetadata", logger, e);
        } finally {
            uploadedFile = null;
        }
    }

    /**
     * Create processes for the modelled course of appearance and chosen granularity.
     */
    public String createProcesses() throws DAOException {
        Process process = ServiceManager.getProcessService().getById(parentId);
        TaskManager.addTask(new GeneratesNewspaperProcessesThread(process, course));
        if (ServiceManager.getSecurityAccessService().hasAuthorityToViewTaskManagerPage()) {
            return TASK_MANAGER_REFERER;
        }
        return DEFAULT_REFERER;
    }

    public String formatString(String messageKey, String... replacements) {
        return Helper.getTranslation(messageKey, replacements);
    }

    /**
     * Get the first day of the year.
     * This might differ from January 1st as business years might have a different range of time.
     * The used PrimeFaces component requires a Date object including a specific year,
     * however the year is irrelevant for yearStart itself.
     *
     * @return Date representing the first day of the year
     */
    public Date getYearStart() {
        Calendar calendar = new GregorianCalendar(
                today.getYear(), course.getYearStart().getMonth().ordinal(), course.getYearStart().getDayOfMonth());
        return calendar.getTime();
    }

    /**
     * Set the first day of the year.
     * This might differ from January 1st as business years might have a different range of time.
     * The used PrimeFaces component passes a Date object including a specific year,
     * however the year is irrelevant for yearStart itself.
     *
     * @param date Date representing the first day of the year
     */
    public void setYearStart(Date date) {
        if (Objects.nonNull(date)) {
            course.setYearStart(MonthDay.of(date.getMonth() + 1, date.getDate()));
        }
    }

    /**
     * Returns the name of the year. The name of the year is optional and maybe
     * empty. Typical values are “Business year”, “Fiscal year”, or “Season”.
     *
     * @return the name of the year
     */
    public String getYearName() {
        return course.getYearName();
    }

    /**
     * Sets the year name of the course.
     *
     * @param yearName
     *            the yearName to set
     */
    public void setYearName(String yearName) {
        course.setYearName(yearName);
    }

    /**
     * Get today.
     *
     * @return value of today
     */
    public LocalDate getToday() {
        return today;
    }

    /**
     * Add new metadata for the selected Block and with the selected date and Issue.
     */
    public void addMetadata(Issue issue, boolean onlyThisIssue) {
        IndividualIssue selectedIssue = null;
        for (IndividualIssue individualIssue : getIndividualIssues(selectedBlock)) {
            if (Objects.nonNull(issue)
                    && Objects.equals(individualIssue.getIssue(), issue)
                    && Objects.equals(selectedDate, individualIssue.getDate())) {
                selectedIssue = individualIssue;
                break;
            }
        }
        if (!selectedBlock.getIssues().isEmpty() && Objects.nonNull(selectedIssue)) {
            CountableMetadata metadata = new CountableMetadata(selectedBlock,
                    Triple.of(selectedIssue.getDate(), selectedIssue.getIssue(), onlyThisIssue));
            List<ProcessDetail> metadataTypes = metadata.getAllMetadataTypes(getParentId());
            if (!metadataTypes.isEmpty()) {
                metadata.setMetadataDetail(metadataTypes.get(0));
            }
            selectedBlock.addMetadata(metadata);
        } else {
            Helper.setErrorMessage("Selected issue or list of issues must not be empty for selectedBlock: " + selectedBlock.toString());
        }
    }

    /**
     * Get selectedDate.
     *
     * @return value of selectedDate
     */
    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    /**
     * Set selectedDate.
     *
     * @param selectedDate as java.time.LocalDate
     */
    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    /**
     * Get selectedBlock.
     *
     * @return value of selectedBlock
     */
    public Block getSelectedBlock() {
        return selectedBlock;
    }

    /**
     * Set the selected block based on the selected date.
     */
    public void setSelectedBlock() {
        if (Objects.nonNull(selectedDate)) {
            for (Block block : course) {
                if ((block.getFirstAppearance().isBefore(selectedDate) || block.getFirstAppearance().isEqual(selectedDate))
                        && (block.getLastAppearance().isAfter(selectedDate) || block.getLastAppearance().isEqual(selectedDate))) {
                    selectedBlock = block;
                    break;
                }
            }
        }
    }

    /**
     * Set selectedBlock.
     *
     * @param selectedBlock as org.kitodo.production.model.bibliography.course.Block
     */
    public void setSelectedBlock(Block selectedBlock) {
        this.selectedBlock = selectedBlock;
    }

    /**
     * Return a list of all individual issues for this block.
     *
     * @return the list of issues
     */
    public List<IndividualIssue> getIndividualIssues(Block block) {
        return CalendarService.getIndividualIssues(block);
    }

    /**
     * Get list of metadata for given block on a specific date and issue.
     *
     * @param block the block to get the metadata from
     * @param date the date to get the metadata for
     * @param issue the issue to get the metadata for
     * @return list of matching metadata
     */
    public List<CountableMetadata> getMetadata(Block block, LocalDate date, Issue issue) {
        return CalendarService.getMetadata(block, date, issue);
    }

    /**
     * Set the issue and date where the given metadata occurred last.
     *
     * @param metadata the metadata to set the end date and issue for
     * @param date date where the metadata occurred last
     * @param issue issue where the metadata occurred last
     */
    public void setLastIssue(CountableMetadata metadata, LocalDate date, Issue issue) {
        if (Objects.nonNull(metadata)) {
            metadata.setDelete(new ImmutablePair<>(date, issue));
        }
    }

    /**
     * Get value of countable metadata for the given date and issue.
     *
     * @param metadata the metadata to calculate the the value for
     * @param date the date to calculate the value for
     * @param issue the issue to calculate the metadata for
     * @return the metadata value as java.lang.String
     */
    public String getTextMetadataValue(CountableMetadata metadata, LocalDate date, Issue issue) {
        if (Objects.nonNull(metadata) && metadata.getMetadataDetail() instanceof ProcessTextMetadata) {
            return metadata.getValue(new ImmutablePair<>(date, issue), course.getYearStart());
        }
        return "";
    }

    /**
     * Get all metadata of the given block as summary.
     * Each type of metadata is only listed once with its earliest occurrence.
     *
     * @param block the block to get the metadata for
     * @return list of pairs containing the metadata type and the date of its earliest occurrence
     */
    public List<Pair<ProcessDetail, LocalDate>> getMetadataSummary(Block block) {
        return CalendarService.getMetadataSummary(block);
    }

    /**
     * Get the value of the given processDetail.
     *
     * @param processDetail
     *            as ProcessDetail
     * @return the value as a java.lang.String
     */
    public String getMetadataValue(ProcessDetail processDetail) {
        return ImportService.getProcessDetailValue(processDetail);
    }

    /**
     * Check if process with the same processtitle already exists.
     */
    public void checkDuplicatedTitles() throws ProcessGenerationException, DataException, DAOException,
            ConfigurationException, IOException, DoctypeMissingException {
        if (course.parallelStream().noneMatch(block -> Objects.equals(block.checkIssuesWithSameHeading(), true))) {
            Process process = ServiceManager.getProcessService().getById(parentId);
            NewspaperProcessesGenerator newspaperProcessesGenerator = new NewspaperProcessesGenerator(process, course);
            newspaperProcessesGenerator.initialize();
            if (!newspaperProcessesGenerator.isDuplicatedTitles()) {
                PrimeFaces.current().executeScript("PF('createProcessesConfirmDialog').show();");
            }
        }
    }

    /**
     * Get first issue that's appear on the selected Date.
     * @return issue
     */
    public Issue getFirstMatchIssue() {
        if (selectedDate != null) {
            return getCalendarSheet().get(selectedDate.getDayOfMonth() - 1).get(selectedDate.getMonthValue() - 1).getIssues()
                    .parallelStream()
                    .filter(issue -> issue.isMatch(selectedDate))
                    .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * add Metadata to all Issues that's appear on the selected Date.
     */
    public void addMetadataToAllMatchIssues() {
        if (getFirstMatchIssue() != null) {
            addMetadata(getFirstMatchIssue(), false);
        }
    }
}
