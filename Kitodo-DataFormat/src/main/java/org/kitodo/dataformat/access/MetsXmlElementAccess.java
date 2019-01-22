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

package org.kitodo.dataformat.access;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.api.dataformat.LinkedStructure;
import org.kitodo.api.dataformat.MediaUnit;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.ProcessingNote;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.InputStreamProviderInterface;
import org.kitodo.api.dataformat.mets.MetsXmlElementAccessInterface;
import org.kitodo.dataformat.metskitodo.DivType;
import org.kitodo.dataformat.metskitodo.DivType.Mptr;
import org.kitodo.dataformat.metskitodo.FileType;
import org.kitodo.dataformat.metskitodo.Mets;
import org.kitodo.dataformat.metskitodo.MetsType;
import org.kitodo.dataformat.metskitodo.MetsType.FileSec;
import org.kitodo.dataformat.metskitodo.MetsType.FileSec.FileGrp;
import org.kitodo.dataformat.metskitodo.MetsType.MetsHdr;
import org.kitodo.dataformat.metskitodo.MetsType.MetsHdr.Agent;
import org.kitodo.dataformat.metskitodo.MetsType.MetsHdr.MetsDocumentID;
import org.kitodo.dataformat.metskitodo.MetsType.StructLink;
import org.kitodo.dataformat.metskitodo.StructLinkType.SmLink;
import org.kitodo.dataformat.metskitodo.StructMapType;

/**
 * The administrative structure of the product of an element that passes through
 * a Production workflow. The file format for this management structure is METS
 * XML after the ZVDD DFG Viewer Application Profile.
 *
 * <p>
 * A {@code Workpiece} has two essential characteristics: {@link FileXmlElementAccess}s and
 * an outline {@link DivXmlElementAccess}. {@code MediaUnit}s are the types of every
 * single digital medium on a conceptual level, such as the individual pages of
 * a book. Each {@code MediaUnit} can be in different {@link UseXmlAttributeAccess}s (for
 * example, in different resolutions or file formats). Each {@code MediaVariant}
 * of a {@code MediaUnit} resides in a {@link FLocatXmlElementAccess} in the data store.
 *
 * <p>
 * The {@code Structure} is a tree structure that can be finely subdivided, e.g.
 * a book, in which the chapters, in it individual elements such as tables or
 * figures. Each outline level points to the {@code MediaUnit}s that belong to
 * it via {@link AreaXmlElementAccess}s. Currently, a {@code View} always contains exactly one
 * {@code MediaUnit} unit, here a simple expandability is provided, so that in a
 * future version excerpts from {@code MediaUnit}s can be described. Each
 * outline level can be described with any {@link MetadataXmlElementsAccess}.
 *
 * @see "https://www.zvdd.de/fileadmin/AGSDD-Redaktion/METS_Anwendungsprofil_2.0.pdf"
 */
public class MetsXmlElementAccess implements MetsXmlElementAccessInterface {
    /**
     * The data object of this mets XML element access.
     */
    private final Workpiece workpiece;

    /**
     * Creates an empty workpiece. This is the default state when the editor
     * starts. You can either load a file or create a new one.
     */
    public MetsXmlElementAccess() {
        workpiece = new Workpiece();
    }

    private MetsXmlElementAccess(Workpiece workpiece) {
        this.workpiece = workpiece;
    }

    /**
     * Converts a mets to a workpiece.
     *
     * @param mets
     *            mets to convert
     * @param inputStreamProvider
     *            a functional interface that returns an input stream for an URI
     * @return the workpiece
     */
    public static final Workpiece toWorkpiece(Mets mets,
            InputStreamProviderInterface inputStreamProvider) {

        Workpiece workpiece = new Workpiece();
        readEditHistory(mets, workpiece);
        Map<String, FileXmlElementAccess> divIDsToMediaUnits = readMediaUnits(mets, workpiece);
        Map<String, Set<FileXmlElementAccess>> mediaUnitsMap = readViews(mets, divIDsToMediaUnits);
        readStructure(mets, mediaUnitsMap, workpiece, inputStreamProvider);
        return workpiece;
    }

    private static void readEditHistory(Mets mets, Workpiece workpiece) {
        MetsHdr metsHdr = mets.getMetsHdr();
        if (Objects.nonNull(metsHdr)) {
            workpiece.setCreationDate(metsHdr.getCREATEDATE().toGregorianCalendar());
            for (Agent agent : metsHdr.getAgent()) {
                workpiece.getEditHistory().add(new AgentXmlElementAccess(agent).getProcessingNote());
            }
            MetsDocumentID metsDocumentID = metsHdr.getMetsDocumentID();
            if (Objects.nonNull(metsDocumentID)) {
                workpiece.setId(metsDocumentID.getID());
            }
        }
    }

    private static Map<String, FileXmlElementAccess> readMediaUnits(Mets mets, Workpiece workpiece) {
        FileSec fileSec = mets.getFileSec();
        Map<String, MediaVariant> useXmlAttributeAccess = fileSec != null
                ? fileSec.getFileGrp().parallelStream().map(UseXmlAttributeAccess::new)
                        .collect(Collectors.toMap(
                            newUseXmlAttributeAccess -> newUseXmlAttributeAccess.getMediaVariant().getUse(),
                            UseXmlAttributeAccess::getMediaVariant))
                : new HashMap<>();
        Optional<StructMapType> optionalPhysicalStructMap = getStructMapsStreamByType(mets, "PHYSICAL").findFirst();
        Map<String, FileXmlElementAccess> divIDsToMediaUnits = new HashMap<>();
        if (optionalPhysicalStructMap.isPresent()) {
            DivType div = optionalPhysicalStructMap.get().getDiv();
            FileXmlElementAccess fileXmlElementAccess = new FileXmlElementAccess(div, mets, useXmlAttributeAccess);
            MediaUnit mediaUnit = fileXmlElementAccess.getMediaUnit();
            workpiece.setMediaUnit(mediaUnit);
            divIDsToMediaUnits.put(div.getID(), fileXmlElementAccess);
            readMeadiaUnitsTreeRecursive(div, mets, useXmlAttributeAccess, mediaUnit, divIDsToMediaUnits);
        }
        return divIDsToMediaUnits;
    }

    private static Map<String, Set<FileXmlElementAccess>> readViews(Mets mets,
            Map<String, FileXmlElementAccess> divIDsToMediaUnits) {
        StructLink structLink = mets.getStructLink();
        if (structLink == null) {
            structLink = new StructLink();
        }
        Map<String, Set<FileXmlElementAccess>> mediaUnitsMap = new HashMap<>();
        for (Object smLinkOrSmLinkGrp : structLink.getSmLinkOrSmLinkGrp()) {
            if (smLinkOrSmLinkGrp instanceof SmLink) {
                SmLink smLink = (SmLink) smLinkOrSmLinkGrp;
                mediaUnitsMap.computeIfAbsent(smLink.getFrom(), any -> new HashSet<>());
                mediaUnitsMap.get(smLink.getFrom()).add(divIDsToMediaUnits.get(smLink.getTo()));
            }
        }
        return mediaUnitsMap;
    }

    /**
     * If the topmost {@code <mets:div>} contains a {@code <mets:mptr>}, then it
     * is a holder {@code <div>} for that {@code <mptr>} and must be skipped.
     */
    private static void readStructure(Mets mets, Map<String, Set<FileXmlElementAccess>> mediaUnitsMap,
            Workpiece workpiece, InputStreamProviderInterface inputStreamProvider) {

        workpiece.setStructure(
            getStructMapsStreamByType(mets, "LOGICAL").map(structMap -> structMap.getDiv())
                .map(div -> div.getMptr().isEmpty() ? div : div.getDiv().get(0))
                    .map(div -> new DivXmlElementAccess(div, mets, mediaUnitsMap, inputStreamProvider))
                .collect(Collectors.toList()).iterator().next());
        workpiece.getUplinks().addAll(readUplinks(mets, inputStreamProvider));
    }

    private static void readMeadiaUnitsTreeRecursive(DivType div, Mets mets,
            Map<String, MediaVariant> useXmlAttributeAccess,
            MediaUnit mediaUnit, Map<String, FileXmlElementAccess> divIDsToMediaUnits) {

        for (DivType child : div.getDiv()) {
            FileXmlElementAccess fileXmlElementAccess = new FileXmlElementAccess(child, mets, useXmlAttributeAccess);
            MediaUnit childMediaUnit = fileXmlElementAccess.getMediaUnit();
            mediaUnit.getChildren().add(childMediaUnit);
            divIDsToMediaUnits.put(child.getID(), fileXmlElementAccess);
            readMeadiaUnitsTreeRecursive(child, mets, useXmlAttributeAccess, childMediaUnit, divIDsToMediaUnits);
        }
    }

    /**
     * Locates the pointer to the current METS document within the parent
     * documents and return a list of hierarchy levels to that pointer within
     * the parent METS documents.
     *
     * @param div
     *            substructure of the structure tree of the immediately superior
     *            METS document to be examined (to be called recursively)
     * @param current
     *            content of the current METS file to be found
     * @param parentUri
     *            URI of the parent METS document to be returned within the link
     * @param inputStreamProvider
     *            a function that opens an input stream
     * @return a list of the parent structures of the document
     */
    private static final LinkedList<LinkedStructure> findCurrentStructureInParent(DivType div, Mets current, URI parentUri,
            InputStreamProviderInterface inputStreamProvider) {

        if (!div.getMptr().isEmpty()) {
            boolean found = div.getMptr().stream().map(Mptr::getHref)
                    .map(href -> Objects.deepEquals(readMets(inputStreamProvider, hrefToUri(href), false), current))
                    .reduce(Boolean::logicalOr).get();
            return found ? new LinkedList<>() : null;
        } else {
            Optional<Pair<DivType, LinkedList<LinkedStructure>>> optionalResult = div.getDiv().stream().map(child -> {
                LinkedList<LinkedStructure> links = findCurrentStructureInParent(child, current, parentUri, inputStreamProvider);
                return links != null ? Pair.of(child, links) : null;
            }).filter(Objects::nonNull).reduce((one, another) -> {
                if (one.getRight().equals(another.getRight())) {
                    return one;
                }
                throw new IllegalStateException("Child is referenced from parent multiple times");
            });
            if (optionalResult.isPresent()) {
                Pair<DivType, LinkedList<LinkedStructure>> found = optionalResult.get();
                LinkedStructure linkedStructure = new LinkedStructure();
                linkedStructure.setLabel(div.getLABEL());
                linkedStructure.setType(div.getTYPE());
                linkedStructure.setOrder(found.getLeft().getORDER());
                linkedStructure.setUri(parentUri);
                LinkedList<LinkedStructure> result = found.getRight();
                result.addFirst(linkedStructure);
                return result;
            }
        }
        return null;
    }

    /**
     * The method helps to read {@code <structMap>}s from METS.
     *
     * @param mets
     *            METS that can be read from
     * @param type
     *            type of the {@code <structMap>} to read
     * @return a stream of {@code <structMap>}s
     */
    private static final Stream<StructMapType> getStructMapsStreamByType(Mets mets, String type) {
        return mets.getStructMap().parallelStream().filter(structMap -> structMap.getTYPE().equals(type));
    }

    /**
     * Converts a URI given as a string to a URI object.
     *
     * @param href
     *            URI to be converted
     * @return URI as {@code URI} object
     */
    public static final URI hrefToUri(String href) {
        try {
            return new URI(href);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Erroneous URI: \"" + href + "\" (" + e.getMessage() + ')', e);
        }
    }

    /**
     * Reads METS from an InputStream. JAXB is used to parse the XML.
     *
     * @param in
     *            InputStream to read from
     * @param inputStreamProvider
     *            a function that opens an input stream
     */
    @Override
    public Workpiece read(InputStream in, InputStreamProviderInterface inputStreamProvider)
            throws IOException {

        return toWorkpiece(readMets(in), inputStreamProvider);
    }

    /**
     * Reads METS from an InputStream. JAXB is used to parse the XML.
     *
     * @param in
     *            InputStream to read from
     * @return the parsed METS file
     * @throws IOException
     *             if the reading fails
     */
    public static Mets readMets(InputStream in) throws IOException {
        try {
            JAXBContext jc = JAXBContext.newInstance(Mets.class);
            Unmarshaller unmarshaller = jc.createUnmarshaller();
            return (Mets) unmarshaller.unmarshal(in);
        } catch (JAXBException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    /**
     * Use the {@code inputStreamProvider} function to read in a METS file.
     *
     * @param inputStreamProvider
     *            a function that opens an input stream
     * @param uri
     *            URI to read
     * @param couldHaveToBeWrittenInTheFuture
     *            Whether the file may still need to be written in the future.
     *            That would be the case, if it is a subordinate METS file and
     *            the link to this is to be removed. This is never the case for
     *            higher-level METS files. Depending on this, the
     *            {@code inputStreamProvider} function must request the lock so
     *            that a later change may still be possible, but no documents
     *            are unnecessarily locked for other users.
     * @return the parsed METS document
     * @throws UncheckedIOException
     *             if the reading fails (to be used in lambda expressions)
     */
    public static final Mets readMets(InputStreamProviderInterface inputStreamProvider, URI uri,
            boolean couldHaveToBeWrittenInTheFuture) {

        try (InputStream in = inputStreamProvider.getInputStream(uri, couldHaveToBeWrittenInTheFuture)) {
            return readMets(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads the superordinate structures of this workpiece from a METS
     * document.
     *
     * @param current
     *            content of the current METS file
     * @param inputStreamProvider
     *            a function that opens an input stream
     * @return a list of the parent structures of the document
     */
    private static final List<LinkedStructure> readUplinks(Mets current,
            InputStreamProviderInterface inputStreamProvider) {

        Optional<List<LinkedStructure>> result = getStructMapsStreamByType(current, "LOGICAL")
                .map(structMap -> structMap.getDiv()).filter(div -> !div.getMptr().isEmpty())
                .flatMap(div -> div.getMptr().parallelStream()).map(mptr -> mptr.getHref()).map(href -> {
                    URI parentUri = hrefToUri(href);
                    Mets parent = readMets(inputStreamProvider, parentUri, false);

                    LinkedList<LinkedStructure> found = null;
                    for (StructMapType structMap : getStructMapsStreamByType(parent, "LOGICAL")
                            .collect(Collectors.toList())) {
                        DivType div = structMap.getDiv();
                        if (!div.getMptr().isEmpty()) {
                            div = div.getDiv().get(0);
                        }
                        LinkedList<LinkedStructure> maybeFound = findCurrentStructureInParent(div, current, parentUri,
                            inputStreamProvider);
                        if (maybeFound != null) {
                            if (found == null) {
                                found = maybeFound;
                            } else {
                                throw new IllegalStateException("Child is referenced from parent multiple times");
                            }
                        }
                    }
                    if (found == null) {
                        throw new IllegalStateException("Child not referenced from parent");
                    }
                    found.addAll(0, readUplinks(parent, inputStreamProvider));
                    return found;
                }).reduce((one, another) -> {
                    one.addAll(another);
                    return one;
                }).map(linkedList -> (List<LinkedStructure>) linkedList);
        return result.orElse(Collections.emptyList());
    }

    /**
     * Writes the contents of this workpiece as a METS file into an output
     * stream.
     *
     * @param out
     *            writable output stream
     * @throws IOException
     *             if the output device has an error
     */
    @Override
    public void save(Workpiece workpiece, OutputStream out) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(Mets.class);
            Marshaller marshal = context.createMarshaller();
            marshal.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshal.marshal(new MetsXmlElementAccess(workpiece).toMets(), out);
        } catch (JAXBException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    /**
     * Generates a METS XML structure from this workpiece in the form of Java
     * objects in the main memory.
     *
     * @return a METS XML structure from this workpiece
     */
    private Mets toMets() {
        Mets mets = new Mets();
        mets.setMetsHdr(generateMetsHdr());

        Map<URI, FileType> mediaFilesToIDFiles = new HashMap<>();
        mets.setFileSec(generateFileSec(mediaFilesToIDFiles));

        Map<MediaUnit, String> mediaUnitIDs = new HashMap<>();
        mets.getStructMap().add(generatePhysicalStructMap(mediaFilesToIDFiles, mediaUnitIDs, mets));

        LinkedList<Pair<String, String>> smLinkData = new LinkedList<>();
        StructMapType logical = new StructMapType();
        logical.setTYPE("LOGICAL");
        DivType structureRoot = new DivXmlElementAccess(workpiece.getStructure()).toDiv(mediaUnitIDs, smLinkData, mets);
        List<LinkedStructure> uplinks = workpiece.getUplinks();
        if (uplinks.isEmpty()) {
            logical.setDiv(structureRoot);
        } else {
            DivType uplinkHolder = new DivType();
            Mptr uplink = new Mptr();
            uplink.setHref(uplinks.get(uplinks.size() - 1).getUri().getPath());
            uplinkHolder.getMptr().add(uplink);
            uplinkHolder.getDiv().add(structureRoot);
            logical.setDiv(uplinkHolder);
        }

        mets.getStructMap().add(logical);

        mets.setStructLink(createStructLink(smLinkData));
        return mets;
    }

    /**
     * Creates the header of the METS file. The header area stores the time
     * stamp, the ID and the processing notes.
     *
     * @return the header of the METS file
     */
    private MetsHdr generateMetsHdr() {
        MetsHdr metsHdr = new MetsHdr();
        metsHdr.setCREATEDATE(convertDate(workpiece.getCreationDate()));
        metsHdr.setLASTMODDATE(convertDate(new GregorianCalendar()));
        if (workpiece.getId() != null) {
            MetsDocumentID id = new MetsDocumentID();
            id.setValue(workpiece.getId());
            metsHdr.setMetsDocumentID(id);
        }
        for (ProcessingNote processingNote : workpiece.getEditHistory()) {
            metsHdr.getAgent().add(new AgentXmlElementAccess(processingNote).toAgent());
        }
        return metsHdr;
    }

    /**
     * Creates an object of class XMLGregorianCalendar. Creating this
     * JAXB-specific class is quite complicated and has therefore been
     * outsourced to a separate method.
     *
     * @param gregorianCalendar
     *            value of the calendar
     * @return an object of class XMLGregorianCalendar
     */
    private static XMLGregorianCalendar convertDate(GregorianCalendar gregorianCalendar) {
        DatatypeFactory datatypeFactory;
        try {
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            String message = e.getMessage();
            throw new NoClassDefFoundError(message != null ? message
                    : "Implementation of DatatypeFactory not available or cannot be instantiated.");
        }
        return datatypeFactory.newXMLGregorianCalendar(gregorianCalendar);
    }

    /**
     * Creates the file section. In the file section of a METS file after the
     * ZVDD DFG Viewer Application Profile, the files are declared in exactly
     * the opposite way as they are managed in Production. That is, there are
     * file groups, each file group accommodating the files of a media variant.
     * Therefore, the media units are first resolved according to their media
     * variants, then the corresponding XML elements are generated.
     *
     * @param mediaFilesToIDFiles
     *            In this map, for each media unit, the corresponding XML file
     *            element is added, so that it can be used for linking later.
     * @return
     */
    private FileSec generateFileSec(Map<URI, FileType> mediaFilesToIDFiles) {
        FileSec fileSec = new FileSec();

        Map<UseXmlAttributeAccess, Set<URI>> useToMediaUnits = new HashMap<>();
        Map<Pair<UseXmlAttributeAccess, URI>, String> fileIds = new HashMap<>();
        generateFileSecRecursive(workpiece.getMediaUnit(), useToMediaUnits, fileIds);

        for (Entry<UseXmlAttributeAccess, Set<URI>> fileGrpData : useToMediaUnits.entrySet()) {
            FileGrp fileGrp = new FileGrp();
            UseXmlAttributeAccess useXmlAttributeAccess = fileGrpData.getKey();
            fileGrp.setUSE(useXmlAttributeAccess.getMediaVariant().getUse());
            String mimeType = useXmlAttributeAccess.getMediaVariant().getMimeType();
            Map<URI, FileType> files = fileGrpData.getValue().parallelStream()
                    .map(uri -> Pair.of(uri,
                        new FLocatXmlElementAccess(uri).toFile(mimeType,
                            fileIds.get(Pair.of(useXmlAttributeAccess, uri)))))
                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
            mediaFilesToIDFiles.putAll(files);
            fileGrp.getFile().addAll(files.values());
            fileSec.getFileGrp().add(fileGrp);
        }
        return fileSec;
    }

    private void generateFileSecRecursive(MediaUnit mediaUnit, Map<UseXmlAttributeAccess, Set<URI>> useToMediaUnits,
            Map<Pair<UseXmlAttributeAccess, URI>, String> fileIds) {

        for (Entry<MediaVariant, URI> variantEntry : mediaUnit.getMediaFiles().entrySet()) {
            UseXmlAttributeAccess use = new UseXmlAttributeAccess(variantEntry.getKey());
            useToMediaUnits.computeIfAbsent(use, any -> new HashSet<>());
            URI uri = variantEntry.getValue();
            useToMediaUnits.get(use).add(uri);
            if (mediaUnit instanceof MediaUnitMetsReferrerStorage) {
                fileIds.put(Pair.of(use, uri), ((MediaUnitMetsReferrerStorage) mediaUnit).getFileId(uri));
            }
        }
        for (MediaUnit child : mediaUnit.getChildren()) {
            generateFileSecRecursive(child, useToMediaUnits, fileIds);
        }
    }

    /**
     * Creates the physical struct map. In the physical struct map, the
     * individual files with their variants are enumerated and labeled.
     *
     * @param mediaFilesToIDFiles
     *            A map of the media files to the XML file elements used to
     *            declare them in the file section. To output a link to the ID,
     *            the XML element must be passed to JAXB.
     * @param mediaUnitIDs
     *            In this map, the function returns the assigned identifier for
     *            each media unit so that the link pairs of the struct link
     *            section can be formed later.
     * @param mets
     *            the METS structure in which the meta-data is added
     * @return the physical struct map
     */
    private StructMapType generatePhysicalStructMap(
            Map<URI, FileType> mediaFilesToIDFiles, Map<MediaUnit, String> mediaUnitIDs, MetsType mets) {
        StructMapType physical = new StructMapType();
        physical.setTYPE("PHYSICAL");
        physical.setDiv(
            generatePhysicalStructMapRecursive(workpiece.getMediaUnit(), mediaFilesToIDFiles, mediaUnitIDs, mets));
        return physical;
    }

    private DivType generatePhysicalStructMapRecursive(MediaUnit mediaUnit, Map<URI, FileType> mediaFilesToIDFiles,
            Map<MediaUnit, String> mediaUnitIDs, MetsType mets) {
        DivType div = new FileXmlElementAccess(mediaUnit).toDiv(mediaFilesToIDFiles, mediaUnitIDs, mets);
        for (MediaUnit child : mediaUnit.getChildren()) {
            div.getDiv().add(generatePhysicalStructMapRecursive(child, mediaFilesToIDFiles, mediaUnitIDs, mets));
        }
        return div;
    }

    /**
     * Creates the struct link section. The struct link section stores which
     * files are attached to which nodes and leaves of the description
     * structure.
     *
     * @param smLinkData
     *            The list of related IDs
     * @return the struct link section
     */
    private StructLink createStructLink(LinkedList<Pair<String, String>> smLinkData) {
        StructLink structLink = new StructLink();
        structLink.getSmLinkOrSmLinkGrp().addAll(smLinkData.parallelStream().map(entry -> {
            SmLink smLink = new SmLink();
            smLink.setFrom(entry.getLeft());
            smLink.setTo(entry.getRight());
            return smLink;
        }).collect(Collectors.toList()));
        return structLink;
    }
}
