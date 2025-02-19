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

package org.kitodo.production.model;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.xml.fileformats.FileFormat;
import org.kitodo.config.xml.fileformats.FileFormatsConfig;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.production.helper.VariableReplacer;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.file.FileService;

/**
 * A subfolder is a folder in the file system below the process folder that
 * contains a variant of each file of the digital representation of the work
 * intended for a particular use. In this approach, a file is a fragment of the
 * digital representation of the artwork, a conceptual entity that can have
 * different variants. The individual variants of this file are then each stored
 * as computer-readable files in a folder for that use on the volume. They can
 * have different formats, both in terms of the data type and their form of
 * representation. The computer files, which are located in different use
 * folders and which are conceptually the same file, share a part of their
 * filename. This is referred to here as the canonical part of the file name.
 *
 * <p>
 * For historical reasons, in some institutions using this software, there is a
 * situation where the different variants of a file are in the same folder and
 * differ in filename suffix. We do not want to advertise this setup, but we
 * will continue to support it because it would be difficult for these
 * institutions to migrate the files.
 */
public class Subfolder {
    private final FileService fileService = ServiceManager.getFileService();
    /**
     * The general metrics of this kind of subfolder. So to say its type.
     */
    private final Folder folder;

    /**
     * The process this subfolder belongs to. That is the process whose process
     * directory this sub-folder is below on the file storage.
     */
    private final Process process;

    /**
     * The variable replacer used to replace variables in the folder path to the
     * task folder.
     */
    private final VariableReplacer variableReplacer;

    /**
     * Creates a new subfolder.
     *
     * @param process
     *            the process this subfolder belongs to
     * @param folder
     *            the general metrics of this kind of subfolder
     */
    public Subfolder(Process process, Folder folder) {
        this.process = process;
        this.folder = folder;
        this.variableReplacer = new VariableReplacer(null, process, null);
    }

    /**
     * Returns a key mapper for a compiled regular expression.
     *
     * @param pattern
     *            compiled regular expression to determine the key
     * @return a mapping function for the key of the map
     */
    private static Function<URI, String> createKeyMapperForPattern(final Pattern pattern) {
        return uri -> {
            if (Objects.nonNull(uri)) {
                Matcher matcher = pattern.matcher(FilenameUtils.getName(uri.getPath()));
                if (!matcher.find()) {
                    throw new IllegalStateException(
                            "Regular expression \"" + pattern + "\" didn't match on \"" + uri + '"');
                }
                return matcher.group(1);
            }
            return null;
        };
    }

    /**
     * Computes the search patterns in the complicated case that the path
     * contains a search pattern.
     *
     * @param lastSeparator
     *            position of the last File.separatorChar in the path
     * @param lastSegment
     *            everything after the last File.separatorChar in the path
     * @param firstStar
     *            position of the first star after the last File.separatorChar
     *            in the path
     * @return search request consisting of an indication of the folder to be
     *         searched and a pattern to which the file names must correspond
     */
    private Pair<URI, Pattern> determineComplicatedSearchParameters(int lastSeparator, String lastSegment,
            int firstStar) {
        String realPath = variableReplacer.replace(folder.getPath().substring(0, lastSeparator));
        String processId = process.getId().toString();
        final URI directory = (realPath.isEmpty() ? Paths.get(ConfigCore.getKitodoDataDirectory(), processId)
                : Paths.get(ConfigCore.getKitodoDataDirectory(), processId, realPath)).toUri();
        StringBuilder patternBuilder = new StringBuilder();
        if (firstStar > 0) {
            patternBuilder.append(Pattern.quote(lastSegment.substring(0, firstStar)));
        }
        patternBuilder.append("(.*)");
        if (firstStar < lastSegment.length() - 1) {
            patternBuilder.append(Pattern.quote(
                lastSegment.substring(firstStar + 1).replaceFirst("\\*$", getFileFormat().getExtension(false))));
        }
        return Pair.of(directory, Pattern.compile(patternBuilder.toString()));
    }

    /**
     * Calculates the search pattern in the ordinary case that the path is
     * simply a path.
     *
     * @return search request consisting of an indication of the folder to be
     *         searched and a pattern to which the file names must correspond
     */
    private Pair<URI, Pattern> determineCommonSearchParameters() {
        String processId = process.getId().toString();
        URI directory = Paths
                .get(ConfigCore.getKitodoDataDirectory(), processId, variableReplacer.replace(folder.getPath()))
                .toUri();
        String pattern = "(.*)\\." + Pattern.quote(getFileFormat().getExtension(false));
        return Pair.of(directory, Pattern.compile(pattern));
    }

    /**
     * Determines the directory to search and the search pattern.
     *
     * @return search request consisting of an indication of the folder to be
     *         searched and a pattern to which the file names must correspond
     */
    private Pair<URI, Pattern> determineDirectoryAndFileNamePattern() {
        int lastSeparator = folder.getPath().lastIndexOf(File.separatorChar);
        String lastSegment = folder.getPath().substring(lastSeparator + 1);
        int firstStar = lastSegment.indexOf('*');
        if (firstStar == -1) {
            return determineCommonSearchParameters();
        } else {
            return determineComplicatedSearchParameters(lastSeparator, lastSegment, firstStar);
        }
    }

    /**
     * Returns the canonical part of the file name for a given URI.
     *
     * @param uri
     *            URI for which the canonical part of the file name should be
     *            returned
     * @return the canonical part of the file name
     */
    public String getCanonical(URI uri) {
        return createKeyMapperForPattern(determineDirectoryAndFileNamePattern().getRight()).apply(uri);
    }

    /**
     * Returns a file format by its MIME type, if any.
     *
     * @return the file format, if any
     */
    public FileFormat getFileFormat() {
        try {
            Optional<FileFormat> optionalFileFormat = FileFormatsConfig.getFileFormat(folder.getMimeType());
            if (optionalFileFormat.isPresent()) {
                return optionalFileFormat.get();
            } else {
                throw new NoSuchElementException(
                        "kitodo_fileFormats.xml has no <fileFormat mimeType=\"" + folder.getMimeType() + "\">");
            }
        } catch (JAXBException e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Returns the folder database bean. The folder bean contains the settings
     * for the subfolder.
     *
     * @return the folder
     */
    public Folder getFolder() {
        return folder;
    }

    /**
     * Returns the relative path to a (fictitious) media file in the folder,
     * relative to the process dierctory.
     *
     * @return relative path to the file
     */
    public URI getRelativeFilePath(String canonical) {
        List<String> components = getUriComponents(canonical);
        return URI.create(components.size() > 2 ? components.get(1) + '/' + components.get(2) : components.get(1));
    }

    /**
     * Returns the relative path to the directory storing the files.
     *
     * @return relative path to the directory
     */
    public String getRelativeDirectoryPath() {
        String pathWithVariables = folder.getPath();
        int lastSeparator = pathWithVariables.lastIndexOf(File.separator);
        if (pathWithVariables.indexOf('*', lastSeparator) > -1) {
            pathWithVariables = pathWithVariables.substring(0, lastSeparator);
        }
        return variableReplacer.replace(pathWithVariables);
    }

    /**
     * Returns the URI to a file in this folder.
     *
     * @param canonical
     *            the canonical part of the file name. The canonical part is the
     *            part that is different from one file to another in the folder,
     *            but equal between two files representing the same content in
     *            two different folders. A typical canonical part could be
     *            “00000001”.
     * @return composed URI
     */
    public URI getUri(String canonical) {
        List<String> uriComponents = getUriComponents(canonical);
        return Paths.get(ConfigCore.getKitodoDataDirectory(), uriComponents.toArray(new String[uriComponents.size()]))
                .toUri();
    }

    private List<String> getUriComponents(String canonical) {
        int lastSeparator = folder.getPath().lastIndexOf(File.separator);
        String lastSegment = folder.getPath().substring(lastSeparator + 1);
        List<String> components = new ArrayList<>(3);
        components.add(process.getId().toString());

        if (lastSegment.indexOf('*') == -1) {
            String localName = canonical + getFileFormat().getExtension(true);
            components.add(variableReplacer.replace(folder.getPath()));
            components.add(localName);
        } else {
            String realPath = folder.getPath().substring(0, lastSeparator);
            String localName = lastSegment.replaceFirst("\\*", canonical).replaceFirst("\\*$",
                getFileFormat().getExtension(false));
            if (realPath.isEmpty()) {
                components.add(localName);
            } else {
                components.add(variableReplacer.replace(realPath));
                components.add(localName);
            }
        }
        return components;
    }

    /**
     * Returns the URI to a file in this folder if that file does exist.
     *
     * @param canonical
     *            the canonical part of the file name. The canonical part is the
     *            part that is different from one file to another in the folder,
     *            but equal between two files representing the same content in
     *            two different folders. A typical canonical part could be
     *            “00000001”.
     * @return URI, or empty
     */
    public Optional<URI> getURIIfExists(String canonical) {
        URI uri = getUri(canonical);
        return new File(uri.getPath()).isFile() ? Optional.of(uri) : Optional.empty();
    }

    /**
     * Checks if the folder determined by determineDirectoryAndFileNamePattern() is empty.
     * Retrieve the directory URI and lists its contents.
     * It stops checking as soon as the first file is found.
     *
     * @return true if the folder is empty, false if it contains at least one file
     */
    public boolean isFolderEmpty() {
        URI dir = determineDirectoryAndFileNamePattern().getLeft();
        try (Stream<Path> entries = Files.list(Paths.get(dir))) {
            return entries.findFirst().isEmpty(); // Stop after first file is found
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * Returns a map of canonical file name parts to URIs with all files
     * actually contained in this folder. The canonical part is the part that is
     * different from one file to another in the folder, but equal between two
     * files representing the same content in two different folders. A typical
     * canonical part could be “00000001”.
     *
     * @return map of canonical file name parts to URIs
     */
    public Map<String, URI> listContents() {
        return listContents(true);
    }

    /**
     * Returns a map of canonical file name parts to URIs with all files
     * actually contained in this folder. The canonical part is the part that is
     * different from one file to another in the folder, but equal between two
     * files representing the same content in two different folders. A typical
     * canonical part could be “00000001”.
     *
     * @param absolute
     *            whether to return absolute URIs, else URIs relative to the
     *            process base directory
     * @return map of canonical file name parts to URIs
     */
    public Map<String, URI> listContents(boolean absolute) {
        return listDirectory(determineDirectoryAndFileNamePattern(), absolute);
    }

    /**
     * Search for files with the file management interface.
     *
     * @param query
     *            search request consisting of an indication of the folder to be
     *            searched and a pattern to which the file names must correspond
     * @param absolute
     *            whether to return absolute URIs, else URIs relative to the
     *            process base directory
     * @return a map from the canonical file name part to the URI
     */
    private Map<String, URI> listDirectory(Pair<URI, Pattern> query, boolean absolute) {
        FilenameFilter filter = (dir, name) -> query.getRight().matcher(name).matches();
        try (Stream<URI> relativeURIs = fileService.getSubUris(filter, query.getLeft()).parallelStream()) {
            Stream<URI> resultURIs = absolute ? relativeURIs.map(
                uri -> new File(ConfigCore.getKitodoDataDirectory().concat(uri.getPath())).toURI())
                    : relativeURIs.map(uri -> URI.create(uri.toString().replaceFirst("^[^/]+/", "")));
            Function<URI, String> keyMapper = createKeyMapperForPattern(query.getRight());
            return resultURIs.collect(Collectors.toMap(keyMapper, Function.identity(), (previous, latest) -> latest,
                () -> new TreeMap<>(fileService.getMetadataImageComparator())));
        }
    }

    /**
     * Returns a string that textually represents this object.
     */
    @Override
    public String toString() {
        return Objects.toString(process) + '/' + folder;
    }
}
