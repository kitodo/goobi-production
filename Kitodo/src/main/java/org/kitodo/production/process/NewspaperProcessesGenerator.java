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

package org.kitodo.production.process;

import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.naming.ConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.ComplexMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.DatesSimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.FunctionalMetadata;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.LinkedMetsResource;
import org.kitodo.config.ConfigProject;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.DoctypeMissingException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.forms.createprocess.ProcessFieldedMetadata;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.model.bibliography.course.Course;
import org.kitodo.production.model.bibliography.course.IndividualIssue;
import org.kitodo.production.process.field.AdditionalField;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.data.RulesetService;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;

/**
 * A generator for newspaper processes.
 */
public class NewspaperProcessesGenerator extends ProcessGenerator {
    private static final Logger logger = LogManager.getLogger(NewspaperProcessesGenerator.class);

    /**
     * Language for the ruleset. Since we run headless here, English only.
     */
    public static final List<LanguageRange> ENGLISH = LanguageRange.parse("en");

    /**
     * Number of steps the long-running task has to go through for
     * initialization.
     */
    private static final int NUMBER_OF_INIT_STEPS = 1;

    /**
     * Number of steps the long-running task must complete to complete.
     */
    private static final int NUMBER_OF_COMPLETION_STEPS = 1;

    /**
     * Date format pattern indicating a double year. A double year is a time
     * span with the length of one year, starting on a day different from
     * January 1ˢᵗ and spanning two complementary parts of two subsequent
     * calendar years.
     */
    private static final String PATTERN_DOUBLE_YEAR = "yyyy/yyyy";

    /**
     * Acquisition stage of newspaper generator.
     */
    private final String acquisitionStage = "";

    /**
     * This class requires service for files.
     */
    private final FileService fileService = ServiceManager.getFileService();

    /**
     * This class requires service for METS.
     */
    private final MetsService metsService = ServiceManager.getMetsService();

    /**
     * This class requires service for process.
     */
    private final ProcessService processService = ServiceManager.getProcessService();

    /**
     * This class requires service for rule definitions.
     */
    private final RulesetService rulesetService = ServiceManager.getRulesetService();

    /**
     * This is the supreme process of the newspaper.
     */
    private final Process overallProcess;

    /**
     * The appearance history for which operations are to be created.
     */
    private final Course course;

    /**
     * The current step. This class operates step by step and the long running
     * task can always be paused between two steps in Task Manager.
     */
    private int currentStep = 0;

    /**
     * Specifies which year is currently being processed.
     */
    private String currentYear;

    /**
     * An interface view for simple metadata that maps the date of the day.
     */
    private DatesSimpleMetadataViewInterface daySimpleMetadataView;

    /**
     * Which logical division type are the days.
     */
    private String dayType;

    /**
     * Information from the rule set about the issue.
     */
    StructuralElementViewInterface issueDivisionView;

    /**
     * Views of metadata to add process title to issue processes.
     */
    private Collection<SimpleMetadataViewInterface> issueProcessTitleViews;

    /**
     * An interface view for simple metadata that maps the date of the month.
     */
    private DatesSimpleMetadataViewInterface monthSimpleMetadataView;

    /**
     * Which logical division type are the months.
     */
    private String monthType;

    /**
     * Views of metadata to add process title to the overall newspaper process.
     */
    private Collection<SimpleMetadataViewInterface> newspaperProcessTitleViews;

    /**
     * Uniform resource identifier of the location of the serialization of the
     * overall media presentation description.
     */
    private URI overallMetadataFileUri;

    /**
     * Object model of the overall media presentation description.
     */
    private Workpiece overallWorkpiece;

    /**
     * List of processes to be created. A process is characterized here only by
     * the issues contained therein.
     */
    private List<List<IndividualIssue>> processesToCreate;

    /**
     * Build statements for the process title, which can be interpreted by the
     * title generator.
     */
    private Optional<String> yearTitleDefinition;

    /**
     * The title generator is used to create the process titles.
     */
    private TitleGenerator titleGenerator;

    /**
     * Uniform resource identifier of the location of the serialization of the
     * annual media presentation description.
     */
    private URI yearMetadataFileUri;

    /**
     * This is the annual process which is currently being processed.
     */
    private Process yearProcess;

    /**
     * Views of metadata to add process title to year processes.
     */
    private Collection<SimpleMetadataViewInterface> yearProcessTitleViews;

    /**
     * An interface view for simple metadata that maps the date of the year.
     */
    private DatesSimpleMetadataViewInterface yearSimpleMetadataView;

    /**
     * Which logical division type are the years.
     */
    private String yearType;

    /**
     * Object model of the year media presentation description.
     */
    private Workpiece yearWorkpiece;

    /**
     * Creates a new newspaper process generator.
     *
     * @param overallProcess
     *            Process that represents the entirety of the newspaper
     * @param course
     *            object model of the course of the issue
     */
    public NewspaperProcessesGenerator(Process overallProcess, Course course) {
        this.overallProcess = overallProcess;
        this.course = course;
    }

    /**
     * Returns the progress of the newspaper processes generator.
     *
     * @return the progress
     */
    public int getProgress() {
        return currentStep;
    }

    /**
     * Returns the number of steps of the newspaper processes generator. Note
     * that the number of steps may change in the future and needs to be
     * updated.
     *
     * @return the number of steps
     */
    public int getNumberOfSteps() {
        return NUMBER_OF_INIT_STEPS + (Objects.isNull(processesToCreate) ? 0 : processesToCreate.size())
                + NUMBER_OF_COMPLETION_STEPS;
    }

    /**
     * Works the next step of the long-running task.
     *
     * @throws ConfigurationException
     *             if the configuration is wrong
     * @throws DAOException
     *             if an error occurs while saving in the database
     * @throws DataException
     *             if an error occurs while saving in the database
     * @throws IOException
     *             if something goes wrong when reading or writing one of the
     *             affected files
     * @throws ProcessGenerationException
     *             if there is a "CurrentNo" item in the projects configuration,
     *             but its value cannot be evaluated to an integer
     */
    public boolean nextStep() throws ConfigurationException, DAOException, DataException, IOException,
            ProcessGenerationException, DoctypeMissingException, CommandException, CustomResponseException {

        if (currentStep == 0) {
            initialize();
            if (isDuplicatedTitles()) {
                Helper.setErrorMessage("duplicatedTitles");
                return false;
            }
        } else if (currentStep - NUMBER_OF_INIT_STEPS < processesToCreate.size()) {
            createProcess(currentStep - NUMBER_OF_INIT_STEPS);
        } else {
            finish();
        }
        currentStep++;
        return true;
    }

    /**
     * Initializes the newspaper process generator.
     *
     * @throws ConfigurationException
     *             if the configuration is wrong
     * @throws IOException
     *             if something goes wrong when reading or writing one of the
     *             affected files
     */
    public void initialize() throws ConfigurationException, IOException, DoctypeMissingException {
        final long begin = System.nanoTime();

        overallMetadataFileUri = processService.getMetadataFileUri(overallProcess);
        overallWorkpiece = metsService.loadWorkpiece(overallMetadataFileUri);

        initializeRulesetFields(overallWorkpiece.getLogicalStructure().getType());

        ConfigProject configProject = new ConfigProject(overallProcess.getProject().getTitle());

        Collection<MetadataViewInterface> allowedMetadata = rulesetService.openRuleset(overallProcess.getRuleset())
                .getStructuralElementView(overallWorkpiece.getLogicalStructure().getType(), acquisitionStage, ENGLISH)
                .getAllowedMetadata();

        titleGenerator = initializeTitleGenerator(configProject, overallWorkpiece, allowedMetadata);

        processesToCreate = course.getProcesses();

        if (logger.isTraceEnabled()) {
            logger.trace("Initialization took {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Initializes the class fields related that hold information to be obtained
     * from the ruleset.
     *
     * @param newspaperType
     *            ruleset type of the overall newspaper process
     * @throws ConfigurationException
     *             if the configuration is wrong
     * @throws IOException
     *             if something goes wrong when reading or writing one of the
     *             affected files
     */
    private void initializeRulesetFields(String newspaperType) throws ConfigurationException, IOException {
        RulesetManagementInterface ruleset = rulesetService.openRuleset(overallProcess.getRuleset());
        StructuralElementViewInterface newspaperView = ruleset.getStructuralElementView(newspaperType, acquisitionStage, ENGLISH);
        StructuralElementViewInterface yearDivisionView = nextSubView(ruleset, newspaperView, acquisitionStage);
        yearSimpleMetadataView = yearDivisionView.getDatesSimpleMetadata().orElseThrow(ConfigurationException::new);
        yearTitleDefinition = yearDivisionView.getProcessTitle();
        yearType = yearDivisionView.getId();
        StructuralElementViewInterface monthDivisionView = nextSubView(ruleset, yearDivisionView, acquisitionStage);
        monthSimpleMetadataView = monthDivisionView.getDatesSimpleMetadata().orElseThrow(ConfigurationException::new);
        monthType = monthDivisionView.getId();
        StructuralElementViewInterface dayDivisionView = nextSubView(ruleset, monthDivisionView, acquisitionStage);
        daySimpleMetadataView = dayDivisionView.getDatesSimpleMetadata().orElseThrow(ConfigurationException::new);
        dayType = dayDivisionView.getId();
        issueDivisionView = nextSubView(ruleset, dayDivisionView, acquisitionStage);

        final Collection<String> processTitleKeys = ruleset.getFunctionalKeys(FunctionalMetadata.PROCESS_TITLE);
        newspaperProcessTitleViews = newspaperView
                .getAllowedMetadata()
                .parallelStream().filter(SimpleMetadataViewInterface.class::isInstance)
                .map(SimpleMetadataViewInterface.class::cast)
                .filter(metadataView -> processTitleKeys.contains(metadataView.getId())).collect(Collectors.toList());
        yearProcessTitleViews = yearDivisionView.getAllowedMetadata()
                .parallelStream().filter(SimpleMetadataViewInterface.class::isInstance)
                .map(SimpleMetadataViewInterface.class::cast)
                .filter(metadataView -> processTitleKeys.contains(metadataView.getId())).collect(Collectors.toList());
        issueProcessTitleViews = issueDivisionView.getAllowedMetadata()
                .parallelStream().filter(SimpleMetadataViewInterface.class::isInstance)
                .map(SimpleMetadataViewInterface.class::cast)
                .filter(metadataView -> processTitleKeys.contains(metadataView.getId())).collect(Collectors.toList());
    }

    /**
     * Returns the next sub-view relative to given view from the ruleset.
     *
     * @param ruleset
     *            ruleset to return the sub-view from
     * @param superiorView
     *            relative superior view
     * @return the sub-view
     */
    public static StructuralElementViewInterface nextSubView(RulesetManagementInterface ruleset,
            StructuralElementViewInterface superiorView, String acquisitionStage) {

        Map<String, String> allowedSubstructuralElements = superiorView.getAllowedSubstructuralElements();
        String subType = allowedSubstructuralElements.entrySet().iterator().next().getKey();
        return ruleset.getStructuralElementView(subType, acquisitionStage, ENGLISH);
    }

    /**
     * Initializes the title generator.
     *
     * @param configProject
     *            the config project
     * @param allowedMetadata
     *            allowed Metadata views
     * @return the initialized title generator
     */
    private static TitleGenerator initializeTitleGenerator(ConfigProject configProject, Workpiece workpiece,
            Collection<MetadataViewInterface> allowedMetadata)
            throws DoctypeMissingException {

        LogicalDivision logicalStructure = workpiece.getLogicalStructure();
        Map<String, Map<String, String>> metadata = new HashMap<>(4);
        Map<String, String> topstruct = getMetadataEntries(logicalStructure.getMetadata());
        metadata.put("topstruct", topstruct);
        List<LogicalDivision> children = logicalStructure.getChildren();
        metadata.put("firstchild",
            children.isEmpty() ? Collections.emptyMap() : getMetadataEntries(children.get(0).getMetadata()));
        metadata.put("physSequence", getMetadataEntries(workpiece.getPhysicalStructure().getMetadata()));

        String docType = null;
        for (ConfigOpacDoctype configOpacDoctype : ConfigOpac.getAllDoctypes()) {
            if (configOpacDoctype.getRulesetType().equals(logicalStructure.getType())) {
                docType = configOpacDoctype.getTitle();
                break;
            }
        }

        List<AdditionalField> projectAdditionalFields = configProject.getAdditionalFields();
        ProcessFieldedMetadata table = new ProcessFieldedMetadata();
        for (AdditionalField additionalField : projectAdditionalFields) {
            if (isDocTypeAndNotIsNotDoctype(additionalField, docType)) {
                String value = metadata.getOrDefault(additionalField.getDocStruct(), Collections.emptyMap())
                        .get(additionalField.getMetadata());
                List<MetadataViewInterface> filteredViews = allowedMetadata
                        .stream()
                        .filter(v -> v.getId().equals(additionalField.getMetadata()))
                        .collect(Collectors.toList());
                if (!filteredViews.isEmpty()) {
                    MetadataEntry metadataEntry = new MetadataEntry();
                    metadataEntry.setValue(value);
                    if (filteredViews.get(0).isComplex()) {
                        table.createMetadataGroupPanel((ComplexMetadataViewInterface) filteredViews.get(0),
                            Collections.singletonList(metadataEntry));
                    } else {
                        table.createMetadataEntryEdit((SimpleMetadataViewInterface) filteredViews.get(0),
                            Collections.singletonList(metadataEntry));
                    }
                }
            }
        }
        return new TitleGenerator(topstruct.getOrDefault("TSL_ATS", ""), table.getRows());
    }

    /**
     * Returns whether an additional field is assigned to the doc type and not
     * excluded from it. The {@code isDocType}s and {@code isNotDoctype}s are a
     * list as a string, separated by a horizontal line ({@code |}, U+007C).
     *
     * @param additionalField
     *            the field in question
     * @param docType
     *            the doc type used
     * @return whether the field is assigned and not excluded
     */
    private static boolean isDocTypeAndNotIsNotDoctype(AdditionalField additionalField, String docType) {
        boolean isDocType = false;
        boolean isNotDoctype = false;
        String isDocTypes = additionalField.getIsDocType();
        if (Objects.nonNull(isDocTypes)) {
            for (String isDocTypeOption : isDocTypes.split("\\|")) {
                if (isDocTypeOption.equals(docType)) {
                    isDocType = true;
                    break;
                }
            }
        }
        String isNotDoctypes = additionalField.getIsNotDoctype();
        if (Objects.nonNull(isNotDoctypes)) {
            for (String isNotDoctypeOption : isNotDoctypes.split("\\|")) {
                if (isNotDoctypeOption.equals(docType)) {
                    isNotDoctype = true;
                    break;
                }
            }
        }
        return isDocType ^ !isNotDoctype;
    }

    /**
     * Reduces the metadata to metadata entries and returns them as a map.
     *
     * @param metadata
     *            all the metadata
     * @return the metadata entries as map
     */
    private static Map<String, String> getMetadataEntries(Collection<Metadata> metadata) {
        return metadata.parallelStream().filter(MetadataEntry.class::isInstance)
                .map(MetadataEntry.class::cast).collect(Collectors.toMap(Metadata::getKey, MetadataEntry::getValue,
                    (one, another) -> one + ", " + another));
    }

    private void createProcess(int index) throws DAOException, DataException, IOException, ProcessGenerationException,
            CommandException, CustomResponseException {
        final long begin = System.nanoTime();

        List<IndividualIssue> individualIssuesForProcess = processesToCreate.get(index);
        if (individualIssuesForProcess.isEmpty()) {
            return;
        }

        IndividualIssue firstIssue = individualIssuesForProcess.get(0);
        Map<String, String> genericFields = firstIssue.getGenericFields();
        prepareTheAppropriateYearProcess(dateMark(yearSimpleMetadataView.getScheme(), firstIssue.getDate()),
            genericFields);

        generateProcess(overallProcess.getTemplate().getId(), overallProcess.getProject().getId());

        String title = makeTitle(issueDivisionView.getProcessTitle().orElse("+'_'+#YEAR+#MONTH+#DAY+#ISSU"), genericFields);
        getGeneratedProcess().setTitle(title);
        getGeneratedProcess().setParent(yearProcess);
        yearProcess.getChildren().add(getGeneratedProcess());
        processService.save(getGeneratedProcess(), true);
        createMetadataFileForProcess(individualIssuesForProcess, title);
        processService.saveToIndex(getGeneratedProcess(), false);

        if (logger.isTraceEnabled()) {
            logger.trace("Creating newspaper process {} took {} ms", title,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Generate process title.
     * @param definition as String
     * @param genericFields a map with generic fields that can be configured for process
     * @return process title as String
     */
    public String makeTitle(String definition, Map<String, String> genericFields) throws ProcessGenerationException {
        String title;
        boolean prefixWithProcessTitle = definition.startsWith("+");
        if (prefixWithProcessTitle) {
            definition = definition.substring(1);
        }
        title = titleGenerator.generateTitle(definition, genericFields);
        if (prefixWithProcessTitle) {
            title = overallProcess.getTitle().concat(title);
        }
        return title;
    }

    private void createMetadataFileForProcess(List<IndividualIssue> individualIssues, String title)
            throws IOException, CommandException {

        LogicalDivision logicalStructure = new LogicalDivision();
        MetadataEntry dateMetadataEntry = new MetadataEntry();
        dateMetadataEntry.setKey(monthSimpleMetadataView.getId());
        dateMetadataEntry.setValue(dateMark(monthSimpleMetadataView.getScheme(), individualIssues.get(0).getDate()));
        logicalStructure.getMetadata().add(dateMetadataEntry);

        for (IndividualIssue individualIssue : individualIssues) {

            String monthMark = dateMark(monthSimpleMetadataView.getScheme(), individualIssue.getDate());
            LogicalDivision yearMonth = getOrCreateLogicalDivision(yearWorkpiece.getLogicalStructure(),
                monthType, monthSimpleMetadataView, monthMark);
            String dayMark = dateMark(daySimpleMetadataView.getScheme(), individualIssue.getDate());
            final LogicalDivision processDay = getOrCreateLogicalDivision(logicalStructure, null,
                daySimpleMetadataView, dayMark);
            final LogicalDivision yearDay = getOrCreateLogicalDivision(yearMonth, dayType,
                daySimpleMetadataView, dayMark);

            LogicalDivision processIssue = new LogicalDivision();
            processIssue.setType(issueDivisionView.getId());
            for (SimpleMetadataViewInterface issueProcessTitleView : issueProcessTitleViews) {
                MetadataEditor.writeMetadataEntry(processIssue, issueProcessTitleView, title);
            }
            addCustomMetadata(individualIssue, processIssue);
            processDay.getChildren().add(processIssue);

            LogicalDivision yearIssue = new LogicalDivision();
            yearIssue.setType(issueDivisionView.getId());
            LinkedMetsResource linkToProcess = new LinkedMetsResource();
            linkToProcess.setLoctype("Kitodo.Production");
            linkToProcess.setUri(processService.getProcessURI(getGeneratedProcess()));
            yearIssue.setLink(linkToProcess);
            yearDay.getChildren().add(yearIssue);
        }

        Workpiece workpiece = new Workpiece();
        workpiece.setLogicalStructure(logicalStructure);
        workpiece.setId(getGeneratedProcess().getId().toString());
        fileService.createProcessLocation(getGeneratedProcess());
        final URI metadataFileUri = processService.getMetadataFileUri(getGeneratedProcess());
        metsService.saveWorkpiece(workpiece, metadataFileUri);
    }

    private void addCustomMetadata(IndividualIssue definition, LogicalDivision issue) {
        Collection<Metadata> entered = new ArrayList<>();
        MonthDay yearBegin = yearSimpleMetadataView.getYearBegin();
        for (Metadata metadata : definition.getMetadata(yearBegin.getMonthValue(),
            yearBegin.getDayOfMonth())) {
            entered.add(metadata);
        }
        List<MetadataViewWithValuesInterface> viewsWithValues = issueDivisionView
                .getSortedVisibleMetadata(entered, Collections.emptyList());
        for (MetadataViewWithValuesInterface viewWithValues : viewsWithValues) {
            for (Metadata metadata : viewWithValues.getValues()) {
                if (viewWithValues.getMetadata().isPresent()) {
                    MetadataViewInterface view = viewWithValues.getMetadata().get();
                    if (metadata instanceof MetadataEntry && view instanceof SimpleMetadataViewInterface) {
                        MetadataEditor.writeMetadataEntry(issue, (SimpleMetadataViewInterface) view,
                            ((MetadataEntry) metadata).getValue());
                    } else {
                        logger.warn("Cannot add metadata value \"{}\" of type {} to {}: {} is a metadata group",
                            ((MetadataEntry) metadata).getValue(), metadata.getKey(), issue.getType(), view.getId());
                    }
                } else {
                    logger.warn(
                        "Cannot add metadata value \"{}\" of type {} to NewspaperIssue: type is hidden in acquisition stage \"{}\".",
                        ((MetadataEntry) metadata).getValue(), metadata.getKey(), issue.getType(), acquisitionStage);
                }
            }
        }
    }

    /**
     * Creates a date indicator according to the specified schema for the given
     * date. Normally, the date indicator is generated with the date formatter.
     * Exception is the indication of a double year. In this case, the
     * information depends on the beginning of the year and must first be
     * determined.
     *
     * @param scheme
     *            scheme for the date indicator
     * @param date
     *            date for the date mark
     * @return the formatted date indicator
     */
    private String dateMark(String scheme, LocalDate date) {
        if (PATTERN_DOUBLE_YEAR.equals(scheme)) {
            int firstYear = date.getYear();
            MonthDay yearBegin = yearSimpleMetadataView.getYearBegin();
            LocalDate yearStartThisYear = LocalDate.of(firstYear, yearBegin.getMonth(), yearBegin.getDayOfMonth());
            if (date.isBefore(yearStartThisYear)) {
                firstYear--;
            }
            return String.format("%04d/%04d", firstYear, firstYear + 1);
        } else {
            DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern(scheme);
            return yearFormatter.format(date);
        }
    }

    private void prepareTheAppropriateYearProcess(String yearMark, Map<String, String> genericFields)
            throws DAOException, DataException, ProcessGenerationException, IOException, CommandException {

        if (yearMark.equals(currentYear)) {
            return;
        } else if (Objects.nonNull(currentYear)) {
            saveAndCloseCurrentYearProcess();
        }
        if (!openExistingYearProcess(yearMark)) {
            createNewYearProcess(yearMark, genericFields);
        }
    }

    private void saveAndCloseCurrentYearProcess() throws DataException, IOException {
        final long begin = System.nanoTime();

        metsService.saveWorkpiece(yearWorkpiece, yearMetadataFileUri);
        ProcessService.checkTasks(yearProcess, yearWorkpiece.getLogicalStructure().getType());
        processService.save(yearProcess, true);

        this.yearProcess = null;
        this.yearWorkpiece = null;
        this.yearMetadataFileUri = null;
        String year = currentYear;
        this.currentYear = null;

        if (logger.isTraceEnabled()) {
            logger.trace("Saving year process for {} took {} ms", year,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    private boolean openExistingYearProcess(String yearMark)
            throws DAOException, IOException {
        final long begin = System.nanoTime();

        boolean couldOpenExistingProcess = false;
        for (LogicalDivision firstLevelChild : overallWorkpiece.getLogicalStructure().getChildren()) {
            LinkedMetsResource firstLevelChildLink = firstLevelChild.getLink();
            if (Objects.isNull(firstLevelChildLink)) {
                continue;
            }
            Process linkedProcess = processService
                    .getById(processService.processIdFromUri(firstLevelChildLink.getUri()));
            URI metadataFileUri = processService.getMetadataFileUri(linkedProcess);
            Workpiece workpiece = metsService.loadWorkpiece(metadataFileUri);
            String yearMetadataEntry = null;
            if (yearSimpleMetadataView.getId().equals("ORDERLABEL")) {
                yearMetadataEntry = workpiece.getLogicalStructure().getOrderlabel();
            }
            for (Metadata metadata : workpiece.getLogicalStructure().getMetadata()) {
                if (metadata.getKey().equals(yearSimpleMetadataView.getId()) && metadata instanceof MetadataEntry) {
                    yearMetadataEntry = ((MetadataEntry) metadata).getValue();
                    break;
                }
            }
            if (Objects.isNull(yearMetadataEntry)) {
                continue;
            }
            couldOpenExistingProcess = yearMetadataEntry.equals(yearMark);
            if (couldOpenExistingProcess) {
                this.yearProcess = linkedProcess;
                this.yearWorkpiece = workpiece;
                this.yearMetadataFileUri = metadataFileUri;
                this.currentYear = yearMark;
                break;
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Searching year process for {} took {} ms", yearMark,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
        return couldOpenExistingProcess;
    }

    private void createNewYearProcess(String yearMark, Map<String, String> genericFields)
            throws ProcessGenerationException, DataException, IOException, CommandException {
        final long begin = System.nanoTime();

        generateProcess(overallProcess.getTemplate().getId(), overallProcess.getProject().getId());

        String title = makeTitle(yearTitleDefinition.orElse("+'_'+#YEAR"), genericFields);
        getGeneratedProcess().setTitle(title);
        ProcessService.checkTasks(getGeneratedProcess(), yearType);
        processService.save(getGeneratedProcess(), true);

        getGeneratedProcess().setParent(overallProcess);
        overallProcess.getChildren().add(getGeneratedProcess());
        processService.save(getGeneratedProcess(), true);

        fileService.createProcessLocation(getGeneratedProcess());
        final URI metadataFileUri = processService.getMetadataFileUri(getGeneratedProcess());

        LogicalDivision newYearChild = new LogicalDivision();
        LinkedMetsResource link = new LinkedMetsResource();
        link.setLoctype("Kitodo.Production");
        link.setUri(processService.getProcessURI(getGeneratedProcess()));
        newYearChild.setLink(link);
        overallWorkpiece.getLogicalStructure().getChildren().add(newYearChild);

        LogicalDivision logicalStructure = new LogicalDivision();
        logicalStructure.setType(yearType);
        MetadataEditor.writeMetadataEntry(logicalStructure, yearSimpleMetadataView, yearMark);
        for (SimpleMetadataViewInterface yearProcessTitleView : yearProcessTitleViews) {
            MetadataEditor.writeMetadataEntry(logicalStructure, yearProcessTitleView, title);
        }
        Workpiece workpiece = new Workpiece();
        workpiece.setLogicalStructure(logicalStructure);
        workpiece.setId(getGeneratedProcess().getId().toString());

        this.yearProcess = getGeneratedProcess();
        this.yearWorkpiece = workpiece;
        this.yearMetadataFileUri = metadataFileUri;
        this.currentYear = yearMark;

        if (logger.isTraceEnabled()) {
            logger.trace("Creating year process for {} took {} ms", yearMark,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    private LogicalDivision getOrCreateLogicalDivision(
            LogicalDivision logicalDivision, String childType,
            SimpleMetadataViewInterface identifierMetadata,
            String identifierMetadataValue) {

        for (LogicalDivision child : logicalDivision.getChildren()) {
            if (MetadataEditor.readSimpleMetadataValues(child, identifierMetadata).contains(identifierMetadataValue)) {
                return child;
            }
        }

        LogicalDivision createdChild = new LogicalDivision();
        createdChild.setType(childType);
        MetadataEditor.writeMetadataEntry(createdChild, identifierMetadata, identifierMetadataValue);
        logicalDivision.getChildren().add(createdChild);
        logicalDivision.getChildren().sort(Comparator.comparing(LogicalDivision::getOrderlabel,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return createdChild;
    }

    private void finish() throws DataException, IOException {
        final long begin = System.nanoTime();

        saveAndCloseCurrentYearProcess();
        for (SimpleMetadataViewInterface newspaperProcessTitleView : newspaperProcessTitleViews) {
            MetadataEditor.writeMetadataEntry(overallWorkpiece.getLogicalStructure(), newspaperProcessTitleView,
                overallProcess.getTitle());
        }
        metsService.saveWorkpiece(overallWorkpiece, overallMetadataFileUri);
        ProcessService.checkTasks(overallProcess, overallWorkpiece.getLogicalStructure().getType());
        processService.save(overallProcess,true);

        if (logger.isTraceEnabled()) {
            logger.trace("Finish took {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Check if process with the same processtitle already exists.
     * @return 'true' if Duplicated titles are found and 'false' if not
     */
    public boolean isDuplicatedTitles() throws ProcessGenerationException, DataException {
        List<List<IndividualIssue>> processes = course.getProcesses();
        List<String> issueTitles = new ArrayList<>();
        boolean check = false;
        for (List<IndividualIssue> individualProcess : processes) {
            for (IndividualIssue individualIssue : individualProcess) {
                Map<String, String> genericFields = individualIssue.getGenericFields();
                String title = makeTitle(issueDivisionView.getProcessTitle().orElse("+'_'+#YEAR+#MONTH+#DAY+#ISSU"),
                    genericFields);
                if (!ServiceManager.getProcessService().findByTitle(title).isEmpty() || issueTitles.contains(title)) {
                    Helper.setErrorMessage("duplicatedTitles", individualIssue.toString());
                    check = true;
                }
                issueTitles.add(title);
            }
        }
        return check;
    }
}
