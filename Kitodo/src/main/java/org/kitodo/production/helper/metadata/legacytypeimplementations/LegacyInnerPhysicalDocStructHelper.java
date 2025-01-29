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

package org.kitodo.production.helper.metadata.legacytypeimplementations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.PhysicalDivision;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.dataformat.MetsService;

/**
 * Connects a legacy doc struct from the physical map to a physical division. This is a
 * soldering class to keep legacy code operational which is about to be removed.
 * Do not use this class.
 */
public class LegacyInnerPhysicalDocStructHelper implements LegacyDocStructHelperInterface {
    private static final Logger logger = LogManager.getLogger(LegacyInnerPhysicalDocStructHelper.class);

    private static final MetsService metsService = ServiceManager.getMetsService();

    /**
     * A constant for local use.
     */
    @Deprecated
    public static final MediaVariant LOCAL = new MediaVariant();

    static {
        LOCAL.setUse("LOCAL");
        LOCAL.setMimeType("image/tiff");
    }

    /**
     * The physical division accessed via this soldering class.
     */
    private PhysicalDivision physicalDivision;

    @Deprecated
    public LegacyInnerPhysicalDocStructHelper(PhysicalDivision physicalDivision) {
        this.physicalDivision = physicalDivision;
    }

    @Override
    @Deprecated
    public void addMetadata(LegacyMetadataHelper metadata) {
        if (LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDER.equals(metadata.getMetadataType())) {
            physicalDivision.setOrder(Integer.parseInt(metadata.getValue()));
        } else if (LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDERLABEL.equals(metadata.getMetadataType())) {
            physicalDivision.setOrderlabel(metadata.getValue());
        } else {
            logger.log(Level.TRACE, "addMetadata(metadata: {})", metadata);
        }
    }

    @Override
    @Deprecated
    public List<LegacyDocStructHelperInterface> getAllChildren() {
        /*
         * Although the method is called because the same loop is used for
         * logical and physical structure elements, it must come back empty.
         */
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadata() {
        return Arrays.asList(
            new LegacyMetadataHelper(this, LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDER,
                    Integer.toString(physicalDivision.getOrder())),
            new LegacyMetadataHelper(this, LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDERLABEL,
                    physicalDivision.getOrderlabel()));
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadataByType(LegacyMetadataTypeHelper metadataType) {
        if (metadataType == LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDER) {
            return Objects.nonNull(physicalDivision)
                    ? List.of(
                            new LegacyMetadataHelper(this, metadataType, Integer.toString(physicalDivision.getOrder())))
                    : Collections.emptyList();
        } else if (metadataType == LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDERLABEL) {
            return Objects.nonNull(physicalDivision) && Objects.nonNull(physicalDivision.getOrderlabel())
                    ? List.of(new LegacyMetadataHelper(this, metadataType, physicalDivision.getOrderlabel()))
                    : Collections.emptyList();
        } else {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }

    PhysicalDivision getPhysicalDivision() {
        return physicalDivision;
    }

    @Override
    @Deprecated
    public LegacyLogicalDocStructTypeHelper getDocStructType() {
        return LegacyInnerPhysicalDocStructTypePageHelper.INSTANCE;
    }
}
