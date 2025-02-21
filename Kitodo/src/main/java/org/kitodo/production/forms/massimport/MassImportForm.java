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

package org.kitodo.production.forms.massimport;

import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.faces.view.ViewScoped;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.exceptions.ConfigException;
import org.kitodo.exceptions.ImportException;
import org.kitodo.production.forms.BaseForm;
import org.kitodo.production.forms.CsvRecord;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ImportService;
import org.kitodo.production.services.data.MassImportService;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named("MassImportForm")
@ViewScoped
public class MassImportForm extends BaseForm {

    private static final Logger logger = LogManager.getLogger(MassImportForm.class);

    private int projectId;
    private int templateId;
    private String templateTitle;
    private Ruleset ruleset;
    private ImportConfiguration importConfiguration;
    private UploadedFile file;
    private String csvSeparator = ";";
    private List<String> metadataKeys = new LinkedList<>(Collections.singletonList("ID"));
    private List<CsvRecord> records = new LinkedList<>();
    private String importedCsvHeaderLine = "";
    private List<String> importedCsvLines = new LinkedList<>();
    private final List<Character> csvSeparatorCharacters = Arrays.asList(',', ';');
    private final MassImportService massImportService = ServiceManager.getMassImportService();
    private final AddMetadataDialog addMetadataDialog = new AddMetadataDialog(this);
    private HashMap<String, String> importSuccessMap = new HashMap<>();
    private Integer progress = 0;
    private Boolean rulesetConfigurationForOpacImportComplete = null;
    private String configurationError = null;

    /**
     * Prepare mass import.
     *
     * @param templateId ID of template used to create processes during mass import
     * @param projectId ID of project for which processes are created
     */
    public void prepareMassImport(int templateId, int projectId) {
        this.projectId = projectId;
        this.templateId = templateId;
        try {
            Template template = ServiceManager.getTemplateService().getById(templateId);
            templateTitle = template.getTitle();
            ruleset = template.getRuleset();
            RulesetManagementInterface ruleset = ServiceManager.getRulesetService().openRuleset(template.getRuleset());
            addMetadataDialog.setRulesetManagement(ruleset);
            checkRecordIdentifierConfigured(ruleset);
        } catch (DAOException | IOException e) {
            Helper.setErrorMessage(e);
        }
    }

    private void checkRecordIdentifierConfigured(RulesetManagementInterface ruleset) {
        if (Objects.isNull(rulesetConfigurationForOpacImportComplete)) {
            rulesetConfigurationForOpacImportComplete = ServiceManager.getImportService()
                    .isRecordIdentifierMetadataConfigured(ruleset);
        }
        if (!rulesetConfigurationForOpacImportComplete) {
            PrimeFaces.current().executeScript("PF('recordIdentifierMissingDialog').show();");
        }
    }

    /**
     * Handle file upload.
     *
     * @param event FileUploadEvent to handle
     */
    public void handleFileUpload(FileUploadEvent event) {
        file = event.getFile();
        try {
            List<String> csvLines = massImportService.getLines(file);
            resetValues();
            if (!csvLines.isEmpty()) {
                importedCsvHeaderLine = csvLines.get(0);
                updateMetadataKeys();
                if (csvLines.size() > 1) {
                    importedCsvLines = csvLines.subList(1, csvLines.size());
                    records = massImportService.parseLines(importedCsvLines, csvSeparator);
                }
            }
        } catch (IOException | CsvException e) {
            Helper.setErrorMessage(e);
        }
    }

    private void resetValues() {
        metadataKeys = new LinkedList<>();
        records = new LinkedList<>();
        importSuccessMap = new HashMap<>();
        importedCsvHeaderLine = "";
        importedCsvLines = new LinkedList<>();
    }

    /**
     * Event listender function called when user switches CSV separator character used to split text lines into cells.
     */
    public void changeSeparator() {
        updateMetadataKeys();
        try {
            records = massImportService.parseLines(importedCsvLines, csvSeparator);
        } catch (IOException | CsvException e) {
            Helper.setErrorMessage(e);
        }
    }

    private void updateMetadataKeys() {
        metadataKeys = Arrays.stream(importedCsvHeaderLine.split(csvSeparator, -1)).map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * Add new CSV lines.
     */
    public void addRecord() {
        records.add(new CsvRecord(metadataKeys.size()));
    }

    /**
     * Remove CSV record.
     *
     * @param csvRecord CSV record to remove
     */
    public void removeLine(CsvRecord csvRecord) {
        records.remove(csvRecord);
    }

    /**
     * Import all records from list.
     */
    public void startMassImport() {
        importSuccessMap = new HashMap<>();
        PrimeFaces.current().ajax().update("massImportResultDialog");
        try {
            Map<String, Map<String, List<String>>> presetMetadata = massImportService.prepareMetadata(metadataKeys, records);
            importRecords(presetMetadata);
            PrimeFaces.current().executeScript("PF('massImportResultDialog').show();");
            PrimeFaces.current().ajax().update("massImportResultDialog");
        } catch (ImportException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        } catch (ConfigException e) {
            configurationError = e.getLocalizedMessage();
            PrimeFaces.current().executeScript("PF('massImportProgressBar').cancel();");
            PrimeFaces.current().executeScript("PF('configErrorDialog').show();");
            PrimeFaces.current().ajax().update("configErrorDialog");
        }
    }

    /**
     * Prepare massimport by resetting progress and import success map.
     */
    public void prepare() {
        progress = 0;
        importSuccessMap = new HashMap<>();
        PrimeFaces.current().ajax().update("massImportProgressForm:massImportProgress");
    }

    /**
     * Import records by ID and add preset metadata.
     *
     * @param processMetadata Map containing record IDs as keys and preset metadata lists as values
     */
    private void importRecords(Map<String, Map<String, List<String>>> processMetadata) {
        ImportService importService = ServiceManager.getImportService();
        PrimeFaces.current().ajax().update("massImportProgressDialog");
        for (Map.Entry<String, Map<String, List<String>>> entry : processMetadata.entrySet()) {
            try {
                importService.importProcess(entry.getKey(), projectId, templateId, importConfiguration,
                        entry.getValue());
                importSuccessMap.put(entry.getKey(), null);
            } catch (ImportException e) {
                importSuccessMap.put(entry.getKey(), e.getLocalizedMessage());
            }
            PrimeFaces.current().ajax().update("massImportProgressDialog");
        }
    }

    /**
     * Get column header for column with index "columnIndex".
     *
     * @param columnIndex index of column for which column header is returned
     * @return column header
     */
    public String getColumnHeader(Integer columnIndex) {
        if (columnIndex < metadataKeys.size()) {
            String metadataKey = metadataKeys.get(columnIndex);
            try {
                return ServiceManager.getImportService().getMetadataTranslation(ruleset, metadataKey);
            } catch (IOException e) {
                Helper.setErrorMessage(e);
                return metadataKey;
            }
        }
        return "";
    }

    /**
     * Get projectId.
     *
     * @return value of projectId
     */
    public int getProjectId() {
        return projectId;
    }

    /**
     * Set projectId.
     *
     * @param projectId
     *            as int
     */
    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    /**
     * Get templateId.
     *
     * @return value of templateId
     */
    public int getTemplateId() {
        return templateId;
    }

    /**
     * Set templateId.
     *
     * @param templateId
     *            as int
     */
    public void setTemplateId(int templateId) {
        this.templateId = templateId;
    }

    /**
     * Get importConfiguration.
     *
     * @return value of importConfiguration
     */
    public ImportConfiguration getImportConfigurationId() {
        return importConfiguration;
    }

    /**
     * Set importConfiguration.
     *
     * @param importConfiguration
     *            as ImportConfiguration
     */
    public void setImportConfigurationId(ImportConfiguration importConfiguration) {
        this.importConfiguration = importConfiguration;
    }

    /**
     * Get file.
     *
     * @return value of file
     */
    public UploadedFile getFile() {
        return file;
    }

    /**
     * Set file.
     *
     * @param file
     *            as org.primefaces.model.UploadedFile
     */
    public void setFile(UploadedFile file) {
        this.file = file;
    }

    /**
     * Get csvSeparator.
     *
     * @return value of csvSeparator
     */
    public String getCsvSeparator() {
        return csvSeparator;
    }

    /**
     * Set csvSeparator.
     *
     * @param csvSeparator as java.lang.String
     */
    public void setCsvSeparator(String csvSeparator) {
        this.csvSeparator = csvSeparator;
    }

    /**
     * Get metadataKeys.
     *
     * @return value of metadataKeys
     */
    public List<String> getMetadataKeys() {
        return metadataKeys;
    }

    /**
     * Set metadataKeys.
     *
     * @param metadataKeys as List of String
     */
    public void setMetadataKeys(List<String> metadataKeys) {
        this.metadataKeys = metadataKeys;
    }

    /**
     * Get records.
     *
     * @return value of records
     */
    public List<CsvRecord> getRecords() {
        return records;
    }

    /**
     * Set records.
     *
     * @param records as List of CsvRecord
     */
    public void setRecords(List<CsvRecord> records) {
        this.records = records;
    }

    /**
     * Get csvSeparatorCharacters.
     *
     * @return value of csvSeparatorCharacters
     */
    public List<Character> getCsvSeparatorCharacters() {
        return csvSeparatorCharacters;
    }

    /**
     * Get templateTitle.
     *
     * @return value of templateTitle
     */
    public String getTemplateTitle() {
        return templateTitle;
    }

    /**
     * Gets addMetadataDialog.
     *
     * @return value of addMetadataDialog
     */
    public AddMetadataDialog getAddMetadataDialog() {
        return addMetadataDialog;
    }

    /**
     * Get list IDs of successfully imported processes.
     *
     * @return list of IDs of successfully import processes
     */
    public List<String> getSuccessfulImports() {
        if (Objects.nonNull(importSuccessMap)) {
            return importSuccessMap.entrySet().stream().filter(entry -> Objects.isNull(entry.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Get list of IDs failed imports.
     *
     * @return list of IDs of failed imports
     */
    public List<String> getFailedImports() {
        if (Objects.nonNull(importSuccessMap)) {
            return importSuccessMap.entrySet().stream().filter(entry -> Objects.nonNull(entry.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Get error message of import with ID 'recordId'. Return 'null' if record was imported without error.
     *
     * @param recordId ID of record for which error message is returned
     * @return error message of import for ID 'recordId'; returns 'null' if no error occurred
     */
    public String getImportErrorMessage(String recordId) {
        return importSuccessMap.get(recordId);
    }


    /**
     * Remove metadata key and CsvCells with given index from list of metadata keys and all current CsvRecords.
     *
     * @param index index of metadata key and CsvCells to remove
     */
    public void removeMetadata(int index) {
        if (index < metadataKeys.size()) {
            metadataKeys.remove(index);
            for (CsvRecord csvRecord : records) {
                csvRecord.getCsvCells().remove(index);
            }
        }
    }

    /**
     * Get mass import progress.
     *
     * @return mass import progress
     */
    public int getProgress() {
        if (records.isEmpty()) {
            progress = 0;
        } else {
            progress = (importSuccessMap.size() * 100) / records.size();
        }
        PrimeFaces.current().ajax().update("massImportProgressForm:massImportProgress");
        return progress;
    }

    /**
     * Get number of already imported records.
     *
     * @return number of imported records
     */
    public int getNumberOfProcessesRecords() {
        return importSuccessMap.size();
    }

    /**
     * Get rulesetConfigurationForOpacImportComplete.
     *
     * @return value of rulesetConfigurationForOpacImportComplete
     */
    public Boolean getRulesetConfigurationForOpacImportComplete() {
        return rulesetConfigurationForOpacImportComplete;
    }

    /**
     * Get ConfigurationError value.
     * @return configuration error value
     */
    public String getConfigurationError() {
        return configurationError;
    }
}
