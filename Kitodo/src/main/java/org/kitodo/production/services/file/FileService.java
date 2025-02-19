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

package org.kitodo.production.services.file;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.command.CommandResult;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.filemanagement.FileManagementInterface;
import org.kitodo.api.filemanagement.ProcessSubType;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.exceptions.MediaNotFoundException;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.file.BackupFileRotation;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.metadata.ImageHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.pagination.Paginator;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.metadata.MetadataLock;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.command.CommandService;
import org.kitodo.production.services.data.RulesetService;
import org.kitodo.production.thread.RenameMediaThread;
import org.kitodo.serviceloader.KitodoServiceLoader;
import org.kitodo.utils.MediaUtil;

public class FileService {

    private static final Logger logger = LogManager.getLogger(FileService.class);

    private final MetadataImageComparator metadataImageComparator = new MetadataImageComparator(this);

    /**
     * Attachment to filename for the overall anchor file in Production v. 2.
     */
    private static final String APPENDIX_ANCOR = "_anchor";
    /**
     * Attachment to filename for the year anchor file in Production v. 2.
     */
    private static final String APPENDIX_YEAR = "_year";
    private static final String TEMPORARY_FILENAME_PREFIX = "temporary_";
    private final FileManagementInterface fileManagementModule = new KitodoServiceLoader<>(
            FileManagementInterface.class).loadModule();

    private static final String ARABIC = "arabic";
    private static final String ROMAN = "roman";
    private static final String ARABIC_DEFAULT_VALUE = "1";
    private static final String ROMAN_DEFAULT_VALUE = "I";
    private static final String UNCOUNTED_DEFAULT_VALUE = " - ";
    private static final String TEMP_EXTENSION = ".tmp";

    private static final String SLASH = "/";


    /**
     * Adds a slash to a URI to mark it as a directory, if it does not already
     * end with one.
     *
     * @param uri
     *            URI that you know to name a directory
     * @return URI, which definitely ends in a slash
     */
    public URI asDirectory(URI uri) {
        String uriString = uri.toString();
        return uriString.endsWith("/") ? uri : URI.create(uriString.concat("/"));
    }

    /**
     * Creates a MetaDirectory.
     *
     * @param parentFolderUri
     *            The URI, where the
     * @param directoryName
     *            the name of the directory
     * @return true or false
     * @throws IOException
     *             an IOException
     */
    URI createMetaDirectory(URI parentFolderUri, String directoryName) throws IOException, CommandException {
        String encodedPath = "";
        try {
            encodedPath = (new URI(null, null, directoryName, null)).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        URI directoryUri = asDirectory(parentFolderUri).resolve(encodedPath);
        if (fileExist(directoryUri)) {
            logger.info("Metadata directory: {} already existed! No new directory was created", directoryName);
        } else {
            CommandService commandService = ServiceManager.getCommandService();
            String path = FileSystems.getDefault()
                    .getPath(ConfigCore.getKitodoDataDirectory(), parentFolderUri.getRawPath(), directoryName)
                    .normalize().toAbsolutePath().toString();
            List<String> commandParameter = Collections.singletonList(path);
            File script = new File(ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_DIR_META));
            if (!script.exists()) {
                throw new CommandException(Helper.getTranslation("fileNotFound", script.getName()));
            }
            CommandResult commandResult = commandService.runCommand(script, commandParameter);
            if (!commandResult.isSuccessful()) {
                String message = MessageFormat.format(
                    "Could not create directory {0} in {1}! No new directory was created", directoryName,
                    parentFolderUri.getPath());
                logger.warn(message);
                throw new CommandException(message);
            }
        }
        return directoryUri;
    }

    /**
     * Generates the URI to the anchor file for Production v. 2 hierarchical
     * processes. This should not be used except for migration of legacy data.
     *
     * @param metadataFilePath
     *            path URI to meta XML file
     * @return path URI to anchor XML file
     */
    public URI createAnchorFile(URI metadataFilePath) {
        return insertIntoURI(metadataFilePath, ".xml", APPENDIX_ANCOR);
    }

    /**
     * Creates a directory including any missing parent directories.
     *
     * @param pathToCreate
     *            path to create
     */
    public void createDirectories(URI pathToCreate) throws IOException {
        if (fileManagementModule.isDirectory(pathToCreate)) {
            return;
        }
        String path = pathToCreate.toString();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            createDirectory(null, path);
        } else {
            URI before = URI.create(path.substring(0, lastSlash));
            String after = path.substring(lastSlash + 1);
            createDirectories(before);
            createDirectory(before, after);
        }
    }

    /**
     * Creates a directory at a given URI with a given name.
     *
     * @param parentFolderUri
     *            the uri, where the directory should be created
     * @param directoryName
     *            the name of the directory.
     * @return the URI of the new directory or URI of parent directory if
     *         directoryName is null or empty
     */
    public URI createDirectory(URI parentFolderUri, String directoryName) throws IOException {
        if (Objects.nonNull(directoryName)) {
            return fileManagementModule.create(parentFolderUri, directoryName, false);
        }
        return URI.create("");
    }

    /**
     * Creates a directory with a name given and assigns permissions to the
     * given user. Under Linux a script is used to set the file system
     * permissions accordingly. This cannot be done from within java code before
     * version 1.7.
     *
     * @param dirName
     *            Name of directory to create
     * @throws IOException
     *             If an I/O error occurs.
     */
    public void createDirectoryForUser(URI dirName, String userName) throws IOException {
        if (!fileExist(dirName)) {
            CommandService commandService = ServiceManager.getCommandService();
            List<String> commandParameter = Arrays.asList(userName, new File(dirName).getAbsolutePath());
            commandService.runCommand(new File(ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_DIR_USER_HOME)),
                commandParameter);
        }
    }

    /**
     * Creates the folder structure needed for a process.
     *
     * @param process
     *            the process
     * @return the URI to the process location
     */
    public URI createProcessLocation(Process process) throws IOException, CommandException {
        URI processLocationUri = fileManagementModule.createProcessLocation(process.getId().toString());
        createProcessFolders(process, processLocationUri);
        return processLocationUri;
    }

    /**
     * Creates the folders inside a process location.
     *
     * @param process
     *            the process
     */
    public void createProcessFolders(Process process) throws IOException, CommandException {
        createProcessFolders(process, fileManagementModule.createUriForExistingProcess(process.getId().toString()));
    }

    private void createProcessFolders(Process process, URI processLocationUri) throws IOException, CommandException {
        for (Folder folder : process.getProject().getFolders()) {
            if (folder.isCreateFolder()) {
                URI parentFolderUri = processLocationUri;
                for (String singleFolder : new Subfolder(process, folder).getRelativeDirectoryPath()
                        .split(Pattern.quote(File.separator))) {
                    parentFolderUri = createMetaDirectory(parentFolderUri, singleFolder);
                }
            }
        }
    }

    /**
     * Creates a new File.
     *
     * @param fileName
     *            the name of the new file
     * @return the uri of the new file
     */
    public URI createResource(String fileName) throws IOException {
        return fileManagementModule.create(null, fileName, true);
    }

    /**
     * Creates a resource at a given URI with a given name.
     *
     * @param targetFolder
     *            the URI of the target folder
     * @param name
     *            the name of the new resource
     * @return the URI of the created resource
     */
    public URI createResource(URI targetFolder, String name) throws IOException {
        return fileManagementModule.create(targetFolder, name, true);
    }

    /**
     * Generates the URI to the year file for Production v. 2 newspaper
     * processes. This should not be used except for migration of legacy data.
     *
     * @param metadataFilePath
     *            path URI to meta XML file
     * @return path URI to year XML file
     */
    public URI createYearFile(URI metadataFilePath) {
        return insertIntoURI(metadataFilePath, ".xml", APPENDIX_YEAR);
    }

    /**
     * Inserts a string before another substring in an URI.
     *
     * @param uri
     *            URI to insert the string into
     * @param tail
     *            part of the URI before which the string is to be inserted
     * @param insert
     *            string to insert
     * @return URI with string inserted
     */
    private static URI insertIntoURI(URI uri, String tail, String insert) {
        String data = uri.toASCIIString();
        int dataLength = data.length();
        int questionMark = data.indexOf('?');
        int resourceLength = questionMark >= 0 ? questionMark : dataLength;
        if (questionMark < 0) {
            int hash = data.indexOf('#');
            resourceLength = hash >= 0 ? hash : dataLength;
        }
        int beforeTail = data.lastIndexOf(tail, resourceLength);
        int cutPosition = beforeTail >= 0 ? beforeTail : resourceLength;
        String buffer = data.substring(0, cutPosition)
                + insert
                + data.substring(cutPosition, dataLength);
        return URI.create(buffer);
    }

    /**
     * Writes to a file at a given URI.
     *
     * @param uri
     *            the URI, to write to.
     * @return an output stream to the file at the given URI or null
     */
    public OutputStream write(URI uri) throws IOException {
        return fileManagementModule.write(uri);
    }

    /**
     * Reads a file at a given URI.
     *
     * @param uri
     *            the uri to read
     * @return an InputStream to read from or null
     */
    public InputStream read(URI uri) throws IOException {
        return fileManagementModule.read(uri);
    }

    /**
     * Read metadata file (meta.xml).
     *
     * @param process
     *            for which file should be read
     * @return InputStream with metadata file
     */
    public InputStream readMetadataFile(Process process) throws IOException {
        return read(getMetadataFilePath(process));
    }

    /**
     * Read metadata file (meta.xml).
     *
     * @param process
     *            for which file should be read
     * @return InputStream with metadata file
     */
    public InputStream readMetadataFile(Process process, boolean forIndexingAll) throws IOException {
        return read(getMetadataFilePath(process, true, forIndexingAll));
    }

    /**
     * This function implements file renaming. Renaming of files is full of
     * mischief under Windows which unaccountably holds locks on files.
     * Sometimes running the JVM’s garbage collector puts things right.
     *
     * @param fileUri
     *            File to rename
     * @param newFileName
     *            New file name / destination
     * @throws IOException
     *             is thrown if renaming the file fails permanently
     */
    public URI renameFile(URI fileUri, String newFileName) throws IOException {
        return fileManagementModule.rename(fileUri, newFileName);
    }

    /**
     * Calculate all files with given file extension at specified directory
     * recursively.
     *
     * @param directory
     *            the directory to run through
     * @return number of files as Integer
     */
    public Integer getNumberOfFiles(URI directory) {
        return fileManagementModule.getNumberOfFiles(null, directory);
    }

    /**
     * Calculate all files with given file extension at specified directory
     * recursively.
     *
     * @param directory
     *            the directory to run through
     * @return number of files as Integer
     */
    public Integer getNumberOfImageFiles(URI directory) {
        return fileManagementModule.getNumberOfFiles(ImageHelper.imageNameFilter, directory);
    }

    /**
     * Get size of directory.
     *
     * @param directory
     *            URI to get size
     * @return size of directory as Long
     */
    public Long getSizeOfDirectory(URI directory) throws IOException {
        return fileManagementModule.getSizeOfDirectory(directory);
    }

    /**
     * Copy directory.
     *
     * @param sourceDirectory
     *            source file as uri
     * @param targetDirectory
     *            destination file as uri
     */
    public void copyDirectory(URI sourceDirectory, URI targetDirectory) throws IOException {
        fileManagementModule.copy(sourceDirectory, targetDirectory);
    }

    /**
     * Copies a file from a given URI to a given URI.
     *
     * @param sourceUri
     *            the uri to copy from
     * @param destinationUri
     *            the uri to copy to
     * @throws IOException
     *             if copying fails
     */
    public void copyFile(URI sourceUri, URI destinationUri) throws IOException {
        fileManagementModule.copy(sourceUri, destinationUri);
    }

    /**
     * Copies a file to a directory.
     *
     * @param sourceDirectory
     *            The source directory
     * @param targetDirectory
     *            the target directory
     * @throws IOException
     *             if copying fails.
     */
    public void copyFileToDirectory(URI sourceDirectory, URI targetDirectory) throws IOException {
        String target = targetDirectory.toString();
        if (!target.endsWith("/")) {
            targetDirectory = URI.create(target.concat("/"));
        }
        fileManagementModule.copy(sourceDirectory, targetDirectory);
    }

    /**
     * Deletes a resource at a given URI.
     *
     * @param uri
     *            the uri to delete
     * @return true, if successful, false otherwise
     * @throws IOException
     *             if get of module fails
     */
    public boolean delete(URI uri) throws IOException {
        return fileManagementModule.delete(uri);
    }

    /**
     * Checks, if a file exists.
     *
     * @param uri
     *            the URI, to check, if there is a file
     * @return true, if the file exists
     */
    public boolean fileExist(URI uri) {
        return fileManagementModule.fileExist(uri);
    }

    /**
     * Checks if a resource at a given URI is a file.
     *
     * @param uri
     *            the URI to check, if there is a file
     * @return true, if it is a file, false otherwise
     */
    public boolean isFile(URI uri) {
        return fileManagementModule.isFile(uri);
    }

    /**
     * checks, if a URI leads to a directory.
     *
     * @param dir
     *            the uri to check.
     * @return true, if it is a directory.
     */
    public boolean isDirectory(URI dir) {
        return fileManagementModule.isDirectory(dir);
    }

    /**
     * Checks if an uri is readable.
     *
     * @param uri
     *            the uri to check.
     * @return true, if it's readable, false otherwise.
     */
    public boolean canRead(URI uri) {
        return fileManagementModule.canRead(uri);
    }

    /**
     * Returns the name of a file at a given URI.
     *
     * @param uri
     *            the URI, to get the filename from.
     * @return the name of the file
     */
    public String getFileName(URI uri) {
        return FilenameUtils.getBaseName(fileManagementModule.getFileNameWithExtension(uri));
    }

    /**
     * Returns the name of a file at a given uri.
     *
     * @param uri
     *            the URI, to get the filename from
     * @return the name of the file
     */
    public String getFileNameWithExtension(URI uri) {
        return fileManagementModule.getFileNameWithExtension(uri);
    }

    /**
     * Moves a directory from a given URI to a given URI.
     *
     * @param sourceUri
     *            the source URI
     * @param targetUri
     *            the target URI
     * @throws IOException
     *             if get of module fails
     */
    public void moveDirectory(URI sourceUri, URI targetUri) throws IOException {
        fileManagementModule.move(sourceUri, targetUri);
    }

    /**
     * Moves a file from a given URI to a given URI.
     *
     * @param sourceUri
     *            the source URI
     * @param targetUri
     *            the target URI
     * @throws IOException
     *             if get of module fails
     */
    public void moveFile(URI sourceUri, URI targetUri) throws IOException {
        fileManagementModule.move(sourceUri, targetUri);
    }

    /**
     * Process owns anchor XML.
     *
     * @param process
     *            whose metadata file path to use
     * @return whether an anchor file was found
     * @throws IOException
     *             if Io failed
     */
    public boolean processOwnsAnchorXML(Process process) throws IOException {
        URI yearFile = createAnchorFile(getMetadataFilePath(process, false, true));
        return fileExist(yearFile);
    }

    /**
     * Process owns year XML.
     *
     * @param process
     *            whose metadata file path to use
     * @return whether a year file was found
     * @throws IOException
     *             if Io failed
     */
    public boolean processOwnsYearXML(Process process) throws IOException {
        URI yearFile = createYearFile(getMetadataFilePath(process, false, true));
        return fileExist(yearFile);
    }

    /**
     * Get all sub URIs of an URI.
     *
     * @param uri
     *            the URI, to get the sub URIs from
     * @return a List of sub URIs
     */
    public List<URI> getSubUris(URI uri) {
        return fileManagementModule.getSubUris(null, uri);
    }

    /**
     * Get all sub URIs of an URI with a given filter.
     *
     * @param filter
     *            the filter to filter the sub URIs
     * @param uri
     *            the URI, to get the sub URIs from
     * @return a List of sub URIs
     */
    public List<URI> getSubUris(FilenameFilter filter, URI uri) {
        return fileManagementModule.getSubUris(filter, uri);
    }

    /**
     * Lists all Files at the given Path.
     *
     * @param file
     *            the directory to get the Files from
     * @return an Array of Files.
     */
    private File[] listFiles(File file) {
        File[] unchecked = file.listFiles();
        return Objects.nonNull(unchecked) ? unchecked : new File[0];
    }

    /**
     * Writes a metadata file.
     *
     * @param gdzfile
     *            the file format
     * @param process
     *            the process
     * @throws IOException
     *             if error occurs
     */
    public void writeMetadataFile(LegacyMetsModsDigitalDocumentHelper gdzfile, Process process) throws IOException {
        RulesetService rulesetService = ServiceManager.getRulesetService();
        LegacyMetsModsDigitalDocumentHelper ff;

        Ruleset ruleset = process.getRuleset();
        ff = new LegacyMetsModsDigitalDocumentHelper(rulesetService.getPreferences(ruleset).getRuleset());

        // createBackupFile();
        URI metadataFileUri = getMetadataFilePath(process, false, false);
        String temporaryMetadataFileName = getTemporaryMetadataFileName(metadataFileUri);

        ff.setDigitalDocument(gdzfile.getDigitalDocument());
        // ff.write(getMetadataFilePath());
        ff.write(temporaryMetadataFileName);
        File temporaryMetadataFile = new File(temporaryMetadataFileName);
        boolean backupCondition = temporaryMetadataFile.exists() && temporaryMetadataFile.length() > 0;
        if (backupCondition) {
            createBackupFile(process);
            renameFile(Paths.get(temporaryMetadataFileName).toUri(), metadataFileUri.getRawPath());
            removePrefixFromRelatedMetsAnchorFilesFor(Paths.get(temporaryMetadataFileName).toUri());
        }
    }

    private void removePrefixFromRelatedMetsAnchorFilesFor(URI temporaryMetadataFilename) throws IOException {
        File temporaryFile = new File(temporaryMetadataFilename);
        File directoryPath = new File(temporaryFile.getParentFile().getPath());
        for (File temporaryAnchorFile : listFiles(directoryPath)) {
            String temporaryAnchorFileName = temporaryAnchorFile.toString();
            if (temporaryAnchorFile.isFile()
                    && FilenameUtils.getBaseName(temporaryAnchorFileName).startsWith(TEMPORARY_FILENAME_PREFIX)) {
                String anchorFileName = FilenameUtils.concat(FilenameUtils.getFullPath(temporaryAnchorFileName),
                    temporaryAnchorFileName.replace(TEMPORARY_FILENAME_PREFIX, ""));
                temporaryAnchorFileName = FilenameUtils.concat(FilenameUtils.getFullPath(temporaryAnchorFileName),
                    temporaryAnchorFileName);
                renameFile(Paths.get(temporaryAnchorFileName).toUri(), new File(anchorFileName).toURI().getRawPath());
            }
        }
    }

    /**
     * Creates a backup of {@code meta.xml}.
     *
     * @param process
     *            process whose {@code meta.xml} shall be created a backup of.
     */
    public void createBackupFile(Process process) throws IOException {
        int numberOfBackups;

        numberOfBackups = ConfigCore.getIntParameter(ParameterCore.NUMBER_OF_META_BACKUPS);

        if (numberOfBackups != ConfigCore.INT_PARAMETER_NOT_DEFINED_OR_ERRONEOUS) {
            BackupFileRotation bfr = new BackupFileRotation();
            bfr.setNumberOfBackups(numberOfBackups);
            bfr.setFormat("meta.*\\.xml");
            bfr.setProcess(process);
            bfr.performBackup();
        } else {
            logger.warn("No backup configured for meta data files.");
        }
    }

    /**
     * Gets the URI of the metadata.xml of a given process.
     *
     * @param process
     *            the process to get the metadata.xml for.
     * @return The URI to the metadata.xml
     */
    public URI getMetadataFilePath(Process process) throws IOException {
        return getMetadataFilePath(process, true, false);
    }

    /**
     * Gets the URI of the metadata.xml of a given processDTO.
     *
     * @param processDTO
     *            the process to get the metadata.xml for.
     * @return The URI to the metadata.xml
     */
    public URI getMetadataFilePath(ProcessDTO processDTO) throws IOException {
        return getMetadataFilePath(processDTO, true);
    }

    /**
     * Gets the URI of the metadata.xml of a given process.
     *
     * @param process
     *            the process to get the metadata.xml for.
     * @param mustExist
     *            whether the file must exist
     * @return The URI to the metadata.xml
     */
    public URI getMetadataFilePath(Process process, boolean mustExist, boolean forIndexingAll) throws IOException {
        URI metadataFilePath = getProcessSubTypeURI(process, ProcessSubType.META_XML, null, forIndexingAll);
        if (mustExist && !fileExist(metadataFilePath)) {
            throw new IOException(Helper.getTranslation("metadataFileNotFound", metadataFilePath.getPath()));
        }
        return metadataFilePath;
    }

    /**
     * Gets the URI of the metadata.xml of a given processDTO.
     *
     * @param processDTO
     *            the process to get the metadata.xml for.
     * @param mustExist
     *            whether the file must exist
     * @return The URI to the metadata.xml
     */
    public URI getMetadataFilePath(ProcessDTO processDTO, boolean mustExist) throws IOException {
        URI metadataFilePath = getProcessSubTypeURI(processDTO, ProcessSubType.META_XML, null);
        if (mustExist && !fileExist(metadataFilePath)) {
            throw new IOException(Helper.getTranslation("metadataFileNotFound", metadataFilePath.getPath()));
        }
        return metadataFilePath;
    }

    private String getTemporaryMetadataFileName(URI fileName) {
        File temporaryFile = getFile(fileName);
        String directoryPath = temporaryFile.getParentFile().getPath();
        String temporaryFileName = TEMPORARY_FILENAME_PREFIX + temporaryFile.getName();

        return directoryPath + File.separator + temporaryFileName;
    }

    /**
     * Gets the specific IMAGE sub type.
     *
     * @param process
     *            the process to get the imageDirectory for.
     * @return The uri of the Image Directory.
     */
    public URI getImagesDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);
    }

    /**
     * Gets the URI to the ocr directory.
     *
     * @param process
     *            the process tog et the ocr directory for.
     * @return the uri to the ocr directory.
     */
    public URI getOcrDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR, null);
    }

    /**
     * Gets the URI to the import directory.
     *
     * @param process
     *            the process to get the import directory for.
     * @return the uri of the import directory
     */
    public URI getImportDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMPORT, null);
    }

    /**
     * Gets the URI to the text directory.
     *
     * @param process
     *            the process to get the text directory for.
     * @return the uri of the text directory
     */
    public URI getTxtDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_TXT, null);
    }

    /**
     * Gets the URI to the pdf directory.
     *
     * @param process
     *            the process to get the pdf directory for.
     * @return the uri of the pdf directory
     */
    public URI getPdfDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_PDF, null);
    }

    /**
     * Gets the URI to the alto directory.
     *
     * @param process
     *            the process to get the alto directory for.
     * @return the uri of the alto directory
     */
    public URI getAltoDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_ALTO, null);
    }

    /**
     * Gets the URI to the word directory.
     *
     * @param process
     *            the process to get the word directory for.
     * @return the uri of the word directory
     */
    public URI getWordDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.OCR_WORD, null);
    }

    /**
     * Gets the URI to the template file.
     *
     * @param process
     *            the process to get the template file for.
     * @return the uri of the template file
     */
    public URI getTemplateFile(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null);
    }

    /**
     * Returns the URI of the resource in the file management module for a URI
     * relative to the process directory.
     *
     * @param process
     *            Process in whose possession the URI should be resolved
     * @param processRelativeUri
     *            URI relative to the specified process
     * @return URI of the resource
     */
    public URI getResourceUriForProcessRelativeUri(Process process, URI processRelativeUri) {
        URI processBaseUri = getProcessBaseUriForExistingProcess(process);
        return asDirectory(processBaseUri).resolve(processRelativeUri);
    }

    /**
     * This method is needed for migration purposes. It maps existing filePaths
     * to the correct URI. File.separator doesn't work because on Windows it
     * appends backslash to URI.
     *
     * @param process
     *            the process, the uri is needed for.
     * @return the URI.
     */
    public URI getProcessBaseUriForExistingProcess(Process process) {
        URI processBaseUri = process.getProcessBaseUri();
        if (Objects.isNull(processBaseUri) && Objects.nonNull(process.getId())) {
            process.setProcessBaseUri(fileManagementModule.createUriForExistingProcess(process.getId().toString()));
        }
        return process.getProcessBaseUri();
    }

    /**
     * This method is needed for migration purposes. It maps existing filePaths
     * to the correct URI. File.separator doesn't work because on Windows it
     * appends backslash to URI.
     *
     * @param processDTO
     *            the process, the uri is needed for.
     * @return the URI.
     */
    public String getProcessBaseUriForExistingProcess(ProcessDTO processDTO) {
        String processBaseUri = processDTO.getProcessBaseUri();
        if (Objects.isNull(processBaseUri) && Objects.nonNull(processDTO.getId())) {
            processDTO.setProcessBaseUri(fileManagementModule.createUriForExistingProcess(processDTO.getId().toString()).toString());
        }
        return processDTO.getProcessBaseUri();
    }

    /**
     * Get the URI for a process sub-location. Possible locations are listed in
     * ProcessSubType.
     *
     * @param processId
     *            the id of process to get the sublocation for
     * @param processTitle
     *            the title of process to get the sublocation for
     * @param processDataDirectory
     *            the base URI of process to get the sublocation for
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    public URI getProcessSubTypeURI(Integer processId, String processTitle, URI processDataDirectory,
            ProcessSubType processSubType, String resourceName) {

        if (Objects.isNull(processDataDirectory)) {
            try {
                Process process = ServiceManager.getProcessService().getById(processId);
                processDataDirectory = ServiceManager.getProcessService().getProcessDataDirectory(process);
            } catch (DAOException e) {
                processDataDirectory = URI.create(String.valueOf(processId));
            }
        }

        if (Objects.isNull(resourceName)) {
            resourceName = "";
        }
        return fileManagementModule.getProcessSubTypeUri(processDataDirectory, processTitle, processSubType,
            resourceName);
    }

    /**
     * Gets the URI for a Process Sub-location. Possible Locations are listed
     * in ProcessSubType
     *
     * @param process
     *            the process to get the sublocation for.
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    public URI getProcessSubTypeURI(Process process, ProcessSubType processSubType, String resourceName) {
        return getProcessSubTypeURI(process, processSubType, resourceName, false);
    }

    /**
     * Gets the URI for a Process Sub-location. Possible Locations are listed
     * in ProcessSubType
     *
     * @param process
     *            the process to get the sublocation for.
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    private URI getProcessSubTypeURI(Process process, ProcessSubType processSubType, String resourceName,
            boolean forIndexingAll) {

        URI processDataDirectory = ServiceManager.getProcessService().getProcessDataDirectory(process, forIndexingAll);

        if (Objects.isNull(resourceName)) {
            resourceName = "";
        }
        return fileManagementModule.getProcessSubTypeUri(processDataDirectory,
            Helper.getNormalizedTitle(process.getTitle()), processSubType, resourceName);
    }

    /**
     * Gets the URI for a Process Sub-location. Possible Locations are listed
     * in ProcessSubType
     *
     * @param processDTO
     *            the process to get the sublocation for.
     * @param processSubType
     *            The subType.
     * @param resourceName
     *            the name of the single object (e.g. image) if null, the root
     *            folder of the sublocation is returned
     * @return The URI of the requested location
     */
    private URI getProcessSubTypeURI(ProcessDTO processDTO, ProcessSubType processSubType, String resourceName) {

        String processDataDirectory = ServiceManager.getProcessService().getProcessDataDirectory(processDTO);

        if (Objects.isNull(resourceName)) {
            resourceName = "";
        }
        return fileManagementModule.getProcessSubTypeUri(URI.create(processDataDirectory),
                Helper.getNormalizedTitle(processDTO.getTitle()), processSubType, resourceName);
    }

    /**
     * Get part of the URI for specific process.
     *
     * @param filter
     *            FilenameFilter object
     * @param processId
     *            the id of process
     * @param processTitle
     *            the title of process
     * @param processDataDirectory
     *            the base URI of process
     * @param processSubType
     *            object
     * @param resourceName
     *            as String
     * @return unmapped URI
     */
    public List<URI> getSubUrisForProcess(FilenameFilter filter, Integer processId, String processTitle,
            URI processDataDirectory, ProcessSubType processSubType, String resourceName) {
        URI processSubTypeURI = getProcessSubTypeURI(processId, processTitle, processDataDirectory, processSubType,
            resourceName);
        return getSubUris(filter, processSubTypeURI);
    }

    /**
     * Get part of the URI for specific process.
     *
     * @param filter
     *            FilenameFilter object
     * @param process
     *            object
     * @param processSubType
     *            object
     * @param resourceName
     *            as String
     * @return unmapped URI
     */
    public List<URI> getSubUrisForProcess(FilenameFilter filter, Process process, ProcessSubType processSubType,
            String resourceName) {
        URI processSubTypeURI = getProcessSubTypeURI(process, processSubType, resourceName);
        return getSubUris(filter, processSubTypeURI);
    }

    /**
     * Deletes all process directories and their content.
     *
     * @param process
     *            the process to delete the directories for.
     * @return true, if deletion was successful.
     */
    public boolean deleteProcessContent(Process process) throws IOException {
        for (ProcessSubType processSubType : ProcessSubType.values()) {
            URI processSubTypeURI = getProcessSubTypeURI(process, processSubType, null);
            if (!fileManagementModule.delete(processSubTypeURI)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the image source directory.
     *
     * @param process
     *            the process, to get the source directory for
     * @return the source directory as an URI
     */
    public URI getSourceDirectory(Process process) {
        return getProcessSubTypeURI(process, ProcessSubType.IMAGE_SOURCE, null);
    }

    /**
     * Searches for new media and adds them to the media list of the workpiece, if any are found.
     *
     * @param process
     *         Process in which folders should be searched for media
     * @param workpiece
     *         Workpiece to which the media are to be added
     */
    public boolean searchForMedia(Process process, Workpiece workpiece)
            throws InvalidImagesException, MediaNotFoundException {
        final long begin = System.nanoTime();
        List<Folder> folders = process.getProject().getFolders();
        int mapCapacity = (int) Math.ceil(folders.size() / 0.75);
        Map<String, Subfolder> subfolders = new HashMap<>(mapCapacity);
        for (Folder folder : folders) {
            subfolders.put(folder.getFileGroup(), new Subfolder(process, folder));
        }
        Map<String, Map<Subfolder, URI>> currentMedia = new TreeMap<>(metadataImageComparator);
        for (Subfolder subfolder : subfolders.values()) {
            for (Entry<String, URI> element : subfolder.listContents(false).entrySet()) {
                currentMedia.computeIfAbsent(element.getKey(), any -> new HashMap<>(mapCapacity));
                currentMedia.get(element.getKey()).put(subfolder, element.getValue());
            }
        }

        List<String> canonicals = getCanonicalFileNamePartsAndSanitizeAbsoluteURIs(workpiece, subfolders,
                process.getProcessBaseUri());

        if (currentMedia.isEmpty() && !canonicals.isEmpty()) {
            throw new MediaNotFoundException();
        }

        addNewURIsToExistingPhysicalDivisions(currentMedia,
                workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack(), canonicals);
        Map<String, Map<Subfolder, URI>> mediaToAdd = new TreeMap<>(currentMedia);
        List<String> mediaToRemove = new LinkedList<>(canonicals);

        mediaToRemove.removeAll(mediaToAdd.keySet());
        canonicals.forEach(mediaToAdd.keySet()::remove);
        removeMissingMediaFromWorkpiece(mediaToRemove, workpiece, subfolders.values());
        List<PhysicalDivision> children = workpiece.getPhysicalStructure().getChildren();
        boolean orderedChildren = (!children.isEmpty() && children.get(0).getOrder() > 0);
        addNewMediaToWorkpiece(canonicals, mediaToAdd, workpiece, orderedChildren);
        renumberPhysicalDivisions(workpiece, true);
        if (ConfigCore.getBooleanParameter(ParameterCore.WITH_AUTOMATIC_PAGINATION)) {
            repaginatePhysicalDivisions(workpiece);
        }
        if (Workpiece.treeStream(workpiece.getLogicalStructure())
                .allMatch(logicalDivision -> logicalDivision.getViews().isEmpty())) {
            automaticallyAssignPhysicalDivisionsToEffectiveRootRecursive(workpiece, workpiece.getLogicalStructure());
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Searching for media took {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
        return orderedChildren && !(mediaToAdd.isEmpty() && mediaToRemove.isEmpty());
    }

    private void automaticallyAssignPhysicalDivisionsToEffectiveRootRecursive(Workpiece workpiece,
            LogicalDivision logicalDivision) {

        if (Objects.nonNull(logicalDivision.getType())) {
            Workpiece.treeStream(workpiece.getPhysicalStructure()).filter(physicalDivision -> !physicalDivision.getMediaFiles().isEmpty())
                    .map(View::of).forEachOrdered(logicalDivision.getViews()::add);
        } else if (logicalDivision.getChildren().size() == 1) {
            automaticallyAssignPhysicalDivisionsToEffectiveRootRecursive(workpiece,
                logicalDivision.getChildren().get(0));
        }
    }

    /**
     * Parses the canonical part of the filename from the URIs of the media
     * units. Because we need to do this to be able to parse correctly, old
     * absolute URIs are converted to relative URIs.
     */
    private List<String> getCanonicalFileNamePartsAndSanitizeAbsoluteURIs(Workpiece workpiece,
            Map<String, Subfolder> subfolders, URI processBaseUri) throws InvalidImagesException {

        List<String> canonicals = new LinkedList<>();
        String baseUriString = processBaseUri.toString();
        if (!baseUriString.endsWith("/")) {
            baseUriString = baseUriString.concat("/");
        }
        for (PhysicalDivision physicalDivision : workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack()) {
            String unitCanonical = "";
            for (Entry<MediaVariant, URI> entry : physicalDivision.getMediaFiles().entrySet()) {
                Subfolder subfolder = subfolders.get(entry.getKey().getUse());
                if (Objects.isNull(subfolder)) {
                    logger.warn("Missing subfolder for USE {}", entry.getKey().getUse());
                    continue;
                }
                URI mediaFile = entry.getValue();
                String fileUriString = mediaFile.toString();
                if (fileUriString.startsWith(baseUriString)) {
                    mediaFile = URI.create(fileUriString.substring(baseUriString.length()));
                    physicalDivision.getMediaFiles().put(entry.getKey(), mediaFile);
                }
                String fileCanonical = subfolder.getCanonical(mediaFile);
                if (StringUtils.isBlank(unitCanonical)) {
                    unitCanonical = fileCanonical;
                } else if (!unitCanonical.equals(fileCanonical)) {
                    throw new InvalidImagesException("Ambiguous canonical file name part in the same physical division: \""
                            + unitCanonical + "\" and \"" + fileCanonical + "\"!");
                }
            }
            if (!physicalDivision.getMediaFiles().isEmpty() && StringUtils.isBlank(unitCanonical)) {
                throw new InvalidImagesException("Missing canonical file name part in physical division " + physicalDivision);
            }
            canonicals.add(unitCanonical);
        }
        return canonicals;
    }

    /**
     * Adds new media variants found to existing physical divisions.
     */
    private void addNewURIsToExistingPhysicalDivisions(Map<String, Map<Subfolder, URI>> mediaToAdd,
            List<PhysicalDivision> physicalDivisions, List<String> canonicals) {

        for (int i = 0; i < canonicals.size(); i++) {
            String canonical = canonicals.get(i);
            PhysicalDivision physicalDivision = physicalDivisions.get(i);
            if (mediaToAdd.containsKey(canonical)) {
                for (Entry<Subfolder, URI> entry : mediaToAdd.get(canonical).entrySet()) {
                    physicalDivision.getMediaFiles().put(createMediaVariant(entry.getKey().getFolder()), entry.getValue());
                }
            }
        }
    }

    private void removeMissingMediaFromWorkpiece(List<String> mediaToRemove, Workpiece workpiece,
                                                 Collection<Subfolder> subfolders) {
        List<PhysicalDivision> pages = workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack();
        for (String removal : mediaToRemove) {
            if (StringUtils.isNotBlank(removal)) {
                for (PhysicalDivision page : pages) {
                    if (removal.equals(getCanonical(subfolders, page))) {
                        workpiece.getPhysicalStructure().getChildren().remove(page);
                        for (LogicalDivision structuralElement : page.getLogicalDivisions()) {
                            structuralElement.getViews().removeIf(view -> view.getPhysicalDivision().equals(page));
                        }
                        page.getLogicalDivisions().clear();
                        LinkedList<PhysicalDivision> ancestors = MetadataEditor
                                .getAncestorsOfPhysicalDivision(page, workpiece.getPhysicalStructure());
                        if (!ancestors.isEmpty()) {
                            PhysicalDivision parent = ancestors.getLast();
                            parent.getChildren().remove(page);
                        }
                    }
                }
            }
        }
        if (!mediaToRemove.isEmpty()) {
            int i = 1;
            for (PhysicalDivision division : workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack()) {
                division.setOrder(i);
                i++;
            }
        }
    }

    private String getCanonical(Collection<Subfolder> folders, PhysicalDivision division) {
        Map<MediaVariant, URI> mediaFiles = division.getMediaFiles();
        for (Subfolder folder : folders) {
            for (URI mediaUri : mediaFiles.values()) {
                if (mediaUri.getPath().startsWith(folder.getFolder().getRelativePath())) {
                    String canonical = folder.getCanonical(mediaUri);
                    if (Objects.nonNull(canonical)) {
                        return canonical;
                    }
                }
            }
        }
        return "";
    }

    /**
     * Adds the new media to the workpiece. The media are sorted in according to
     * the canonical part of the file name.
     */
    private void addNewMediaToWorkpiece(List<String> canonicals, Map<String, Map<Subfolder, URI>> mediaToAdd,
            Workpiece workpiece, boolean orderedChildren) {

        LogicalDivision actualLogicalRoot = workpiece.getLogicalStructure();
        while (Objects.isNull(actualLogicalRoot.getType()) && actualLogicalRoot.getChildren().size() == 1) {
            actualLogicalRoot = actualLogicalRoot.getChildren().get(0);
        }
        // If the newspaper has multiple issues in the process, then everything stays as it was
        if (Objects.isNull(actualLogicalRoot.getType()) && actualLogicalRoot.getChildren().size() != 1) {
            actualLogicalRoot = workpiece.getLogicalStructure();
        }

        for (Entry<String, Map<Subfolder, URI>> entry : mediaToAdd.entrySet()) {
            PhysicalDivision physicalDivision = createPhysicalDivision(entry.getValue());
            View view = new View();
            view.setPhysicalDivision(physicalDivision);
            // do not use canonical filename parts if existing physical structures already have "order" values > 0!
            if (orderedChildren) {
                physicalDivision.setOrder(workpiece.getPhysicalStructure().getChildren().size());
                workpiece.getPhysicalStructure().getChildren().add(physicalDivision);
                actualLogicalRoot.getViews().add(view);
                view.getPhysicalDivision().getLogicalDivisions().add(actualLogicalRoot);
                canonicals.add(entry.getKey());
            } else {
                // only use canonical filename parts if no ordered physical structures exist in the workpiece, yet
                int insertionPoint = 0;
                for (String canonical : canonicals) {
                    if (metadataImageComparator.compare(entry.getKey(), canonical) > 0) {
                        insertionPoint++;
                    } else {
                        break;
                    }
                }
                workpiece.getPhysicalStructure().getChildren().add(insertionPoint, physicalDivision);
                actualLogicalRoot.getViews().add(insertionPoint, view);
                view.getPhysicalDivision().getLogicalDivisions().add(actualLogicalRoot);
                canonicals.add(insertionPoint, entry.getKey());
            }
        }
    }

    /**
     * Creates a new physical division with the given uses and URIs.
     */
    private PhysicalDivision createPhysicalDivision(Map<Subfolder, URI> data) {
        PhysicalDivision physicalDivision = new PhysicalDivision();
        if (!data.entrySet().isEmpty()) {
            physicalDivision.setType(PhysicalDivision.TYPE_PAGE);
        }
        for (Entry<Subfolder, URI> entry : data.entrySet()) {
            Folder folder = entry.getKey().getFolder();
            MediaVariant mediaVariant = createMediaVariant(folder);

            // overwrite physical division type if mime type is audio or video
            if (!PhysicalDivision.TYPE_TRACK.equals(physicalDivision.getType()) && MediaUtil.isAudioOrVideo(
                    mediaVariant.getMimeType())) {
                physicalDivision.setType(PhysicalDivision.TYPE_TRACK);
            }

            physicalDivision.getMediaFiles().put(mediaVariant, entry.getValue());
        }
        return physicalDivision;
    }

    /**
     * Creates a new media variant for the given use.
     */
    private MediaVariant createMediaVariant(Folder folder) {
        MediaVariant mediaVariant = new MediaVariant();
        mediaVariant.setUse(folder.getFileGroup());
        mediaVariant.setMimeType(folder.getMimeType());
        return mediaVariant;
    }

    /**
     * Renumbers the order of the physical divisions.
     */
    public void renumberPhysicalDivisions(Workpiece workpiece, boolean sortByOrder) {
        int order = 1;
        for (PhysicalDivision physicalDivision : sortByOrder
                ? workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack()
                : workpiece.getPhysicalStructure().getAllChildren()) {
            physicalDivision.setOrder(order++);
        }
    }

    /**
     * Adds a count to media that do not yet have a count. But only at the end,
     * or if none of the media has been counted yet at all. New media found in
     * intermediate places are marked uncounted.
     */
    private void repaginatePhysicalDivisions(Workpiece workpiece) {
        List<PhysicalDivision> physicalDivisions = workpiece.getAllPhysicalDivisionChildrenSortedFilteredByPageAndTrack();
        int first = 0;
        String value;
        switch (ConfigCore.getParameter(ParameterCore.METS_EDITOR_DEFAULT_PAGINATION)) {
            case ARABIC:
                value = ARABIC_DEFAULT_VALUE;
                break;
            case ROMAN:
                value = ROMAN_DEFAULT_VALUE;
                break;
            default:
                value = UNCOUNTED_DEFAULT_VALUE;
                break;
        }
        if (!UNCOUNTED_DEFAULT_VALUE.equals(value)) {
            for (int i = physicalDivisions.size() - 1; i >= 0; i--) {
                PhysicalDivision physicalDivision = physicalDivisions.get(i);
                String orderlabel = physicalDivision.getOrderlabel();
                if (Objects.nonNull(orderlabel) && !physicalDivision.getMediaFiles().isEmpty()) {
                    first = i + 1;
                    value = orderlabel;
                    physicalDivisions.get(i).setType(PhysicalDivision.TYPE_PAGE);
                    break;
                }
            }
        }
        Paginator paginator = new Paginator(value);
        if (first > 0) {
            paginator.next();
            for (int i = first; i < physicalDivisions.size(); i++) {
                physicalDivisions.get(i).setOrderlabel(paginator.next());
            }
        }
        for (PhysicalDivision physicalDivision : physicalDivisions) {
            if (Objects.isNull(physicalDivision.getOrderlabel())) {
                physicalDivision.setOrderlabel(UNCOUNTED_DEFAULT_VALUE);
            }
        }
    }

    public void writeMetadataAsTemplateFile(LegacyMetsModsDigitalDocumentHelper inFile, Process process)
            throws IOException {
        inFile.write(getTemplateFile(process).toString());
    }

    /**
     * Creates a symbolic link.
     *
     * @param targetUri
     *            the target URI for the link
     * @param homeUri
     *            the home URI
     * @return true, if link creation was successful
     */
    public boolean createSymLink(URI homeUri, URI targetUri, boolean onlyRead, User user) {
        return fileManagementModule.createSymLink(homeUri, targetUri, onlyRead, user.getLogin());
    }

    /**
     * Delete a symbolic link.
     *
     * @param homeUri
     *            the URI of the home folder, where the link should be deleted
     * @return true, if deletion was successful
     */
    public boolean deleteSymLink(URI homeUri) {
        return fileManagementModule.deleteSymLink(homeUri);
    }

    public File getFile(URI uri) {
        return fileManagementModule.getFile(uri);
    }

    /**
     * Deletes the slash as first character from an uri object.
     *
     * @param uri
     *            The uri object.
     * @return The new uri object without first slash.
     */
    public URI deleteFirstSlashFromPath(URI uri) {
        String uriString = uri.getPath();
        if (uriString.startsWith("/")) {
            uriString = uriString.replaceFirst("/", "");
        }
        return URI.create(uriString);
    }

    /**
     * Check and return whether given process has an empty generator folder or not.
     *
     * @param process Process
     * @param generatorSource Folder
     * @return whether given URI points to empty directory or not
     */
    public static boolean hasImages(Process process, Folder generatorSource) {
        Subfolder sourceFolder = new Subfolder(process, generatorSource);
        return !sourceFolder.isFolderEmpty();
    }

    /**
     * Returns the comparator for metadata images.
     *
     * @return comparator for metadata images
     */
    public MetadataImageComparator getMetadataImageComparator() {
        return metadataImageComparator;
    }

    /**
     * Rename media files of current process according to their corresponding media units order attribute. Given Map
     * "filenameMapping" is altered via side effect and does not need to be returned. Instead, the number of acutally
     * changed filenames is returned to the calling method.
     *
     * @param process Process object for which media files are renamed.
     * @param workpiece Workpiece object of process
     * @param filenameMapping Bidirectional map containing current filename mapping; empty until first renaming
     * @return number of renamed media files
     * @throws IOException when renaming files fails
     * @throws URISyntaxException when creating URI for new filenames fails
     */
    public int renameMediaFiles(Process process, Workpiece workpiece, DualHashBidiMap<URI, URI> filenameMapping)
            throws IOException, URISyntaxException {
        int filenameLength = process.getProject().getFilenameLength();
        URI processDataUri = ServiceManager.getProcessService().getProcessDataDirectory(process);

        if (!processDataUri.toString().endsWith(SLASH)) {
            processDataUri = URI.create(processDataUri + SLASH);
        }

        int numberOfRenamedMedia = 0;

        // first, rename all files to new filenames plus "tmp" extension to avoid filename collisions
        for (PhysicalDivision page : workpiece.getAllPhysicalDivisionChildrenFilteredByTypes(PhysicalDivision.TYPES)) {
            String newFilename = StringUtils.leftPad(String.valueOf(page.getOrder()), filenameLength, '0');
            for (Entry<MediaVariant, URI> variantURIEntry : page.getMediaFiles().entrySet()) {
                URI fileUri = processDataUri.resolve(variantURIEntry.getValue());
                String newFilepath = newFilename + "." + FilenameUtils.getExtension(fileUri.getPath()) + TEMP_EXTENSION;
                // skip files that already have the correct target name
                if (!newFilename.equals(FilenameUtils.getBaseName(variantURIEntry.getValue().toString()))) {
                    URI tmpUri = fileManagementModule.rename(fileUri, processDataUri + newFilepath);
                    if (filenameMapping.containsValue(fileUri)) {
                        // update existing mapping of files that are renamed multiple times
                        filenameMapping.replace(filenameMapping.getKey(fileUri), tmpUri);
                    } else {
                        // add new mapping otherwise
                        filenameMapping.put(fileUri, tmpUri);
                    }
                    URI targetUri = new URI(StringUtils.removeStart(StringUtils.removeEnd(tmpUri.toString(),
                            TEMP_EXTENSION), process.getId() + SLASH));
                    page.getMediaFiles().put(variantURIEntry.getKey(), targetUri);
                    numberOfRenamedMedia++;
                }
            }
        }

        // then remove "tmp" extension from all filenames
        for (Entry<URI, URI> renamingEntry : filenameMapping.entrySet()) {
            URI tempFilename = renamingEntry.getValue();
            String tempFilenameString = tempFilename.toString();
            // skip filename mappings from last renaming round that have not been renamed again
            if (tempFilenameString.endsWith(TEMP_EXTENSION)) {
                String newFilepath = StringUtils.removeEnd(tempFilename.toString(), TEMP_EXTENSION);
                filenameMapping.put(renamingEntry.getKey(), fileManagementModule.rename(tempFilename, newFilepath));
            }
        }
        return numberOfRenamedMedia;
    }

    /**
     * Check whether too many processes are selected for media renaming and return corresponding error message.
     * @param numberOfProcesses number of processes for media renaming
     * @return error message if too many processes are selected; otherwise return empty string
     */
    public String tooManyProcessesSelectedForMediaRenaming(int numberOfProcesses) {
        int limit = ConfigCore.getIntParameterOrDefaultValue(ParameterCore.MAX_NUMBER_OF_PROCESSES_FOR_MEDIA_RENAMING);
        if (0 < limit && limit < numberOfProcesses) {
            return Helper.getTranslation("tooManyProcessesSelectedForMediaRenaming", String.valueOf(limit),
                    String.valueOf(numberOfProcesses));
        } else {
            return "";
        }
    }

    /**
     * Rename media files of given processes.
     * @param processes Processes whose media is renamed
     */
    public void renameMedia(List<Process> processes) {
        processes = lockAndSortProcessesForRenaming(processes);
        TaskManager.addTask(new RenameMediaThread(processes));
    }

    private List<Process> lockAndSortProcessesForRenaming(List<Process> processes) {
        processes.sort(Comparator.comparing(Process::getId));
        List<Integer> lockedProcesses = new LinkedList<>();
        for (Process process : processes) {
            int processId = process.getId();
            if (MetadataLock.isLocked(processId)) {
                lockedProcesses.add(processId);
                if (ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.ANONYMIZE)) {
                    logger.error("Unable to lock process " + processId + " for media renaming because it is currently "
                            + "being worked on by another user");
                } else {
                    User currentUser = MetadataLock.getLockUser(processId);
                    logger.error("Unable to lock process " + processId + " for media renaming because it is currently "
                            + "being worked on by another user (" + currentUser.getFullName() + ")");
                }
            } else {
                MetadataLock.setLocked(processId, ServiceManager.getUserService().getCurrentUser());
            }
        }
        return processes.stream().filter(p -> !lockedProcesses.contains(p.getId())).collect(Collectors.toList());
    }

    /**
     * Revert renaming of media files when the user leaves the metadata editor without saving. This method uses a
     * provided map object to rename media files identified by the map entries values to the corresponding map entries
     * keys.
     *
     * @param filenameMappings Bidirectional map containing original filenames as keys and new filenames as values.
     * @param workpiece Workpiece of current process
     */
    public void revertRenaming(BidiMap<URI, URI> filenameMappings, Workpiece workpiece) {
        // revert media variant URIs for all media files in workpiece to previous, original values
        logger.info("Reverting to original media filenames of process " + workpiece.getId());
        for (PhysicalDivision physicalDivision : workpiece
                .getAllPhysicalDivisionChildrenFilteredByTypes(PhysicalDivision.TYPES)) {
            for (Entry<MediaVariant, URI> mediaVariantURIEntry : physicalDivision.getMediaFiles().entrySet()) {
                physicalDivision.getMediaFiles().put(mediaVariantURIEntry.getKey(),
                        filenameMappings.get(mediaVariantURIEntry.getValue()));
            }
        }
        // revert filenames of media files to previous, original values
        try {
            List<URI> tempUris = new LinkedList<>();
            for (Entry<URI, URI> mapping : filenameMappings.entrySet()) {
                if (mapping.getKey().toString().endsWith(TEMP_EXTENSION)) {
                    // if current URI has '.tmp' extension, directly revert to original name (without '.tmp' extension)
                    tempUris.add(fileManagementModule.rename(mapping.getKey(), mapping.getValue().toString()));
                } else {
                    // rename to new filename with '.tmp' extension otherwise
                    tempUris.add(fileManagementModule.rename(mapping.getKey(), mapping.getValue().toString()
                            + TEMP_EXTENSION));
                }
            }
            for (URI tempUri : tempUris) {
                fileManagementModule.rename(tempUri, StringUtils.removeEnd(tempUri.toString(), TEMP_EXTENSION));
            }
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * Remove given map entry with whose value URI ends with given URI "unsavedMediaUri" from map
     * and return updated map.
     * @param unsavedMediaUri URI for which corresponding map entry is removed
     * @param mappingMap bidirectional map from URIs to URIs
     * @return updated bidirectional map
     */
    public DualHashBidiMap<URI, URI> removeUnsavedUploadMediaUriFromFileMapping(URI unsavedMediaUri,
                                                                                 DualHashBidiMap<URI, URI> mappingMap) {
        DualHashBidiMap<URI, URI> updatedMap = new DualHashBidiMap<>();
        for (Map.Entry<URI, URI> mapEntry : mappingMap.entrySet()) {
            if (!mapEntry.getValue().toString().endsWith(unsavedMediaUri.toString())) {
                updatedMap.put(mapEntry.getKey(), mapEntry.getValue());
            }
        }
        return updatedMap;
    }
}
