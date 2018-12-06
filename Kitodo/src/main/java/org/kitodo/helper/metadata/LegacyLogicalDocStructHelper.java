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

package org.kitodo.helper.metadata;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.Domain;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.mets.AreaXmlElementAccessInterface;
import org.kitodo.api.dataformat.mets.DivXmlElementAccessInterface;
import org.kitodo.api.dataformat.mets.FileXmlElementAccessInterface;
import org.kitodo.api.dataformat.mets.MdSec;
import org.kitodo.api.dataformat.mets.MetadataAccessInterface;
import org.kitodo.api.dataformat.mets.MetadataXmlElementAccessInterface;
import org.kitodo.api.ugh.ContentFileInterface;
import org.kitodo.api.ugh.DigitalDocumentInterface;
import org.kitodo.api.ugh.DocStructInterface;
import org.kitodo.api.ugh.DocStructTypeInterface;
import org.kitodo.api.ugh.MetadataGroupInterface;
import org.kitodo.api.ugh.MetadataGroupTypeInterface;
import org.kitodo.api.ugh.MetadataInterface;
import org.kitodo.api.ugh.MetadataTypeInterface;
import org.kitodo.api.ugh.PersonInterface;
import org.kitodo.api.ugh.PrefsInterface;
import org.kitodo.api.ugh.ReferenceInterface;
import org.kitodo.api.ugh.exceptions.ContentFileNotLinkedException;
import org.kitodo.api.ugh.exceptions.MetadataTypeNotAllowedException;
import org.kitodo.api.ugh.exceptions.TypeNotAllowedAsChildException;
import org.kitodo.api.ugh.exceptions.TypeNotAllowedForParentException;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.dataformat.MetsService;

/**
 * Connects a legacy doc struct from the logical map to a structure. This is a
 * soldering class to keep legacy code operational which is about to be removed.
 * Do not use this class.
 */
public class LegacyLogicalDocStructHelper implements DocStructInterface {
    private static final Logger logger = LogManager.getLogger(LegacyLogicalDocStructHelper.class);

    private final ServiceManager serviceLoader = new ServiceManager();
    private final MetsService metsService = serviceLoader.getMetsService();

    private DivXmlElementAccessInterface structure;
    private StructuralElementViewInterface divisionView;

    private RulesetManagementInterface ruleset;

    private List<LanguageRange> priorityList;

    private LegacyLogicalDocStructHelper parent;

    public LegacyLogicalDocStructHelper() {
        logger.log(Level.TRACE, "new LogicalDocStructJoint()");
        // TODO Auto-generated method stub
        this.structure = metsService.createDiv();
    }

    LegacyLogicalDocStructHelper(DivXmlElementAccessInterface structure, LegacyLogicalDocStructHelper parent,
            RulesetManagementInterface ruleset, List<LanguageRange> priorityList) {
        this.structure = structure;
        this.ruleset = ruleset;
        this.priorityList = priorityList;
        this.parent = parent;
        this.divisionView = ruleset.getStructuralElementView(structure.getType(), "edit", priorityList);
    }

    @Override
    public void addChild(DocStructInterface child) throws TypeNotAllowedAsChildException {
        LegacyLogicalDocStructHelper legacyLogicalDocStructHelperChild = (LegacyLogicalDocStructHelper) child;
        legacyLogicalDocStructHelperChild.parent = this;
        structure.getChildren().add(legacyLogicalDocStructHelperChild.structure);
    }

    @Override
    public void addChild(Integer index, DocStructInterface child) throws TypeNotAllowedAsChildException {
        LegacyLogicalDocStructHelper legacyLogicalDocStructHelperChild = (LegacyLogicalDocStructHelper) child;
        legacyLogicalDocStructHelperChild.parent = this;
        structure.getChildren().add(index, legacyLogicalDocStructHelperChild.structure);
    }

    @Override
    public void addContentFile(ContentFileInterface contentFile) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void addMetadata(MetadataInterface metadata) throws MetadataTypeNotAllowedException {
        Map<MetadataAccessInterface, String> metadataEntriesMappedToKeyNames = structure.getMetadata().parallelStream()
                .collect(Collectors.toMap(Function.identity(), MetadataAccessInterface::getType));
        Optional<MetadataViewInterface> zz = divisionView
                .getAddableMetadata(metadataEntriesMappedToKeyNames, Collections.emptyList()).parallelStream()
                .filter(x -> x.getId().equals(metadata.getMetadataType().getName())).findFirst();
        Optional<Domain> optionalDomain = zz.isPresent() ? zz.get().getDomain() : Optional.empty();
        if (!optionalDomain.isPresent() || !optionalDomain.get().equals(Domain.METS_DIV)) {
            MetadataXmlElementAccessInterface metadataEntry = metsService.createMetadata();
            metadataEntry.setType(metadata.getMetadataType().getName());
            metadataEntry.setDomain(domainToMdSec(optionalDomain.orElse(Domain.DESCRIPTION)));
            metadataEntry.setValue(metadata.getValue());
            structure.getMetadata().add(metadataEntry);
        } else {
            try {
                structure.getClass().getMethod("set".concat(metadata.getMetadataType().getName()), String.class)
                        .invoke(structure, metadata.getValue());
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    @Override
    public DocStructInterface addMetadata(String metadataType, String value) throws MetadataTypeNotAllowedException {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
        // return: this
    }

    @Override
    public void addMetadataGroup(MetadataGroupInterface metadataGroup) throws MetadataTypeNotAllowedException {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void addPerson(PersonInterface person) throws MetadataTypeNotAllowedException {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public ReferenceInterface addReferenceTo(DocStructInterface docStruct, String type) {
        AreaXmlElementAccessInterface view = metsService.createArea();
        LegacyInnerPhysicalDocStructHelper target = (LegacyInnerPhysicalDocStructHelper) docStruct;
        view.setFile(target.getMediaUnit());
        structure.getAreas().add(view);
        return new LegacyReferenceHelper(target);
    }

    @Override
    public DocStructInterface copy(boolean copyMetaData, Boolean recursive) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public DocStructInterface createChild(String docStructType, DigitalDocumentInterface digitalDocument,
            PrefsInterface prefs) throws TypeNotAllowedAsChildException, TypeNotAllowedForParentException {

        throw andLog(new UnsupportedOperationException("Not yet implemented"));
        // return the child
    }

    @Override
    public void deleteUnusedPersonsAndMetadata() {
        Iterator<MetadataAccessInterface> metadataAccessInterfaceIterator = structure.getMetadata().iterator();
        while (metadataAccessInterfaceIterator.hasNext()) {
            MetadataAccessInterface metadataAccessInterface = metadataAccessInterfaceIterator.next();
            if (((MetadataXmlElementAccessInterface) metadataAccessInterface).getValue().isEmpty()) {
                metadataAccessInterfaceIterator.remove();
            }
        }
    }

    private MdSec domainToMdSec(Domain domain) {
        switch (domain) {
            case DESCRIPTION:
                return MdSec.DMD_SEC;
            case DIGITAL_PROVENANCE:
                return MdSec.DIGIPROV_MD;
            case RIGHTS:
                return MdSec.RIGHTS_MD;
            case SOURCE:
                return MdSec.SOURCE_MD;
            case TECHNICAL:
                return MdSec.TECH_MD;
            default:
                throw new IllegalArgumentException(domain.name());
        }
    }

    @Override
    public List<MetadataGroupTypeInterface> getAddableMetadataGroupTypes() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<MetadataTypeInterface> getAddableMetadataTypes() {
        Map<MetadataAccessInterface, String> metadataEntriesMappedToKeyNames = structure.getMetadata().parallelStream()
                .collect(Collectors.toMap(Function.identity(), MetadataAccessInterface::getType));
        Collection<MetadataViewInterface> addableKeys = divisionView.getAddableMetadata(metadataEntriesMappedToKeyNames,
            Collections.emptyList());
        ArrayList<MetadataTypeInterface> result = new ArrayList<>(addableKeys.size());
        for (MetadataViewInterface key : addableKeys) {
            result.add(new LegacyMetadataTypeHelper(key));
        }
        return result;
    }

    @Override
    public List<DocStructInterface> getAllChildren() {
        List<DocStructInterface> wrappedChildren = new ArrayList<>();
        for (DivXmlElementAccessInterface child : structure.getChildren()) {
            wrappedChildren.add(new LegacyLogicalDocStructHelper(child, this, ruleset, priorityList));
        }
        return wrappedChildren;
    }

    @Override
    public List<DocStructInterface> getAllChildrenByTypeAndMetadataType(String docStructType, String metaDataType) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<ContentFileInterface> getAllContentFiles() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<ReferenceInterface> getAllFromReferences() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<MetadataInterface> getAllIdentifierMetadata() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<MetadataInterface> getAllMetadata() {
        List<MetadataInterface> result = new LinkedList<>();
        // sortieren
        Map<MetadataAccessInterface, String> metadataEntriesMappedToKeyNames = structure.getMetadata().parallelStream()
                .collect(Collectors.toMap(Function.identity(), MetadataAccessInterface::getType));
        List<MetadataViewWithValuesInterface<MetadataAccessInterface>> a = divisionView
                .getSortedVisibleMetadata(metadataEntriesMappedToKeyNames, Collections.emptyList());

        // ausgabe

        for (MetadataViewWithValuesInterface<MetadataAccessInterface> x : a) {
            if (x.getMetadata().isPresent()) {
                MetadataViewInterface key = x.getMetadata().get();
                for (MetadataAccessInterface value : x.getValues()) {
                    if (value instanceof MetadataXmlElementAccessInterface) {
                        result.add(new LegacyMetadataHelper(null, new LegacyMetadataTypeHelper(key),
                                ((MetadataXmlElementAccessInterface) value).getValue()));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<? extends MetadataInterface> getAllMetadataByType(MetadataTypeInterface metadataType) {
        List<MetadataInterface> result = new LinkedList<>();
        // sortieren
        Map<MetadataAccessInterface, String> metadataEntriesMappedToKeyNames = structure.getMetadata().parallelStream()
                .collect(Collectors.toMap(Function.identity(), MetadataAccessInterface::getType));
        List<MetadataViewWithValuesInterface<MetadataAccessInterface>> a = divisionView
                .getSortedVisibleMetadata(metadataEntriesMappedToKeyNames, Collections.emptyList());

        // ausgabe

        for (MetadataViewWithValuesInterface<MetadataAccessInterface> x : a) {
            if (x.getMetadata().isPresent()) {
                MetadataViewInterface key = x.getMetadata().get();
                if (key.getId().equals(metadataType.getName())) {
                    for (MetadataAccessInterface value : x.getValues()) {
                        if (value instanceof MetadataXmlElementAccessInterface) {
                            result.add(new LegacyMetadataHelper(null, new LegacyMetadataTypeHelper(key),
                                    ((MetadataXmlElementAccessInterface) value).getValue()));
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<MetadataGroupInterface> getAllMetadataGroups() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<PersonInterface> getAllPersons() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<PersonInterface> getAllPersonsByType(MetadataTypeInterface metadataType) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<ReferenceInterface> getAllReferences(String direction) {
        switch (direction) {
            case "to":
                List<AreaXmlElementAccessInterface> views = structure.getAreas();
                ArrayList<ReferenceInterface> allReferences = new ArrayList<>(views.size());
                for (AreaXmlElementAccessInterface view : views) {
                    FileXmlElementAccessInterface mediaUnit = view.getFile();
                    allReferences.add(new LegacyReferenceHelper(new LegacyInnerPhysicalDocStructHelper(mediaUnit)));
                }
                return allReferences;
            default:
                throw new IllegalArgumentException("Unknown reference direction: " + direction);
        }
    }

    @Override
    public Collection<ReferenceInterface> getAllToReferences() {
        return getAllReferences("to");
    }

    @Override
    public Collection<ReferenceInterface> getAllToReferences(String type) {
        switch (type) {
            case "logical_physical":
                List<AreaXmlElementAccessInterface> views = structure.getAreas();
                ArrayList<ReferenceInterface> allReferences = new ArrayList<>(views.size());
                for (AreaXmlElementAccessInterface view : views) {
                    FileXmlElementAccessInterface mediaUnit = view.getFile();
                    allReferences.add(new LegacyReferenceHelper(new LegacyInnerPhysicalDocStructHelper(mediaUnit)));
                }
                return allReferences;
            default:
                throw new IllegalArgumentException("Unknown reference type: " + type);
        }
    }

    @Override
    public Object getAllVisibleMetadata() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
        // return: null -> false, new Object() -> true
    }

    @Override
    public String getAnchorClass() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
        // return: null (none)
    }

    @Override
    public DocStructInterface getChild(String type, String identifierField, String identifier) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public List<MetadataTypeInterface> getDisplayMetadataTypes() {
        List<MetadataTypeInterface> result = new LinkedList<>();
        // sortieren
        Map<MetadataAccessInterface, String> metadataEntriesMappedToKeyNames = structure.getMetadata().parallelStream()
                .collect(Collectors.toMap(Function.identity(), MetadataAccessInterface::getType));
        List<MetadataViewWithValuesInterface<MetadataAccessInterface>> a = divisionView
                .getSortedVisibleMetadata(metadataEntriesMappedToKeyNames, Collections.emptyList());

        // ausgabe

        for (MetadataViewWithValuesInterface<MetadataAccessInterface> x : a) {
            if (x.getMetadata().isPresent()) {
                MetadataViewInterface key = x.getMetadata().get();
                result.add(new LegacyMetadataTypeHelper(key));
            }
        }
        return result;
    }

    @Override
    public String getImageName() {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public DocStructInterface getNextChild(DocStructInterface predecessor) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public DocStructInterface getParent() {
        return parent;
    }

    @Override
    public List<MetadataTypeInterface> getPossibleMetadataTypes() {
        // The method is a doublet (in the interface, as well as doubled code in
        // the legacy implementation)
        return getAddableMetadataTypes();
    }

    @Override
    public DocStructTypeInterface getDocStructType() {
        return new LegacyLogicalDocStructTypeHelper(divisionView);
    }

    /**
     * This method is not part of the interface, but the JSF code digs in the
     * depths of the UGH and uses it on the guts.
     * 
     * @return Method delegated to {@link #getDocStructType()}
     */
    public DocStructTypeInterface getType() {
        if (!logger.isTraceEnabled()) {
            StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
            logger.log(Level.WARN, "Method {}.{}() invokes {}.{}(), bypassing the interface!",
                stackTrace[1].getClassName(), stackTrace[1].getMethodName(), stackTrace[0].getClassName(),
                stackTrace[0].getMethodName());
        }
        return getDocStructType();
    }

    @Override
    public boolean isDocStructTypeAllowedAsChild(DocStructTypeInterface type) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void removeChild(DocStructInterface docStruct) {
        LegacyLogicalDocStructHelper legacyLogicalDocStructHelperChild = (LegacyLogicalDocStructHelper) docStruct;
        legacyLogicalDocStructHelperChild.parent = null;
        structure.getChildren().remove(legacyLogicalDocStructHelperChild.structure);
    }

    @Override
    public void removeContentFile(ContentFileInterface contentFile) throws ContentFileNotLinkedException {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void removeMetadata(MetadataInterface metaDatum) {
        Iterator<MetadataAccessInterface> entries = structure.getMetadata().iterator();
        String metadataTypeName = metaDatum.getMetadataType().getName();
        while (entries.hasNext()) {
            MetadataAccessInterface entry = entries.next();
            if (entry.getType().equals(metadataTypeName)
                    && ((MetadataXmlElementAccessInterface) entry).getValue().equals(metaDatum.getValue())) {
                entries.remove();
                break;
            }
        }
    }

    @Override
    public void removeMetadataGroup(MetadataGroupInterface metadataGroup) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void removePerson(PersonInterface person) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void removeReferenceTo(DocStructInterface target) {
        FileXmlElementAccessInterface mediaUnit = ((LegacyInnerPhysicalDocStructHelper) target).getMediaUnit();
        Iterator<AreaXmlElementAccessInterface> areaXmlElementAccessInterfaceIterator = structure.getAreas().iterator();
        while (areaXmlElementAccessInterfaceIterator.hasNext()) {
            FileXmlElementAccessInterface fileXmlElementAccessInterface = areaXmlElementAccessInterfaceIterator.next()
                    .getFile();
            if (fileXmlElementAccessInterface.equals(mediaUnit))
                areaXmlElementAccessInterfaceIterator.remove();
        }
    }

    @Override
    public void setImageName(String imageName) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    @Override
    public void setType(DocStructTypeInterface docStructType) {
        throw andLog(new UnsupportedOperationException("Not yet implemented"));
    }

    /**
     * This method generates a comprehensible log message in case something was
     * overlooked and one of the unimplemented methods should ever be called in
     * operation. The name was chosen deliberately short in order to keep the
     * calling code clear. This method must be implemented in every class
     * because it uses the logger tailored to the class.
     * 
     * @param exception
     *            created {@code UnsupportedOperationException}
     * @return the exception
     */
    private static RuntimeException andLog(UnsupportedOperationException exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        StringBuilder buffer = new StringBuilder(255);
        buffer.append(stackTrace[1].getClassName());
        buffer.append('.');
        buffer.append(stackTrace[1].getMethodName());
        if (stackTrace[1].getLineNumber() > -1) {
            buffer.append(" line ");
            buffer.append(stackTrace[1].getLineNumber());
        }
        buffer.append(" unexpectedly called unimplemented ");
        buffer.append(stackTrace[0].getMethodName());
        if (exception.getMessage() != null) {
            buffer.append(": ");
            buffer.append(exception.getMessage());
        }
        logger.error(buffer.toString());
        return exception;
    }
}
