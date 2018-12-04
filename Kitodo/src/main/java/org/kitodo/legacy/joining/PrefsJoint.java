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

package org.kitodo.legacy.joining;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.ugh.DocStructTypeInterface;
import org.kitodo.api.ugh.MetadataTypeInterface;
import org.kitodo.api.ugh.PrefsInterface;
import org.kitodo.api.ugh.exceptions.PreferencesException;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.dataeditor.RulesetManagementService;

public class PrefsJoint implements PrefsInterface {
    private static final Logger logger = LogManager.getLogger(PrefsJoint.class);
    private final ServiceManager serviceLoader = new ServiceManager();
    private final RulesetManagementService rums = serviceLoader.getRulesetManagementService();

    RulesetManagementInterface ruleset;

    @Override
    public List<DocStructTypeInterface> getAllDocStructTypes() {
        logger.log(Level.TRACE, "getAllDocStructTypes()");
        // TODO Auto-generated method stub
        return Collections.emptyList();
    }

    @Override
    public DocStructTypeInterface getDocStrctTypeByName(String identifier) {
        switch (identifier) {
            case "page":
                return DocStructTypeJoint.SPECIAL_TYPE_PAGE;
            default:
                logger.log(Level.TRACE, "getDocStrctTypeByName(identifier: \"{}\")", identifier);
                // TODO Auto-generated method stub
                return new DocStructTypeJoint();
        }
    }

    @Override
    public MetadataTypeInterface getMetadataTypeByName(String identifier) {
        switch (identifier) {
            case "logicalPageNumber":
                return MetadataTypeJoint.SPECIAL_TYPE_ORDERLABEL;
            case "physPageNumber":
                return MetadataTypeJoint.SPECIAL_TYPE_ORDER;
            default:
                logger.log(Level.TRACE, "getMetadataTypeByName(identifier: \"{}\")", identifier);
                // TODO Auto-generated method stub
                return new MetadataTypeJoint();
        }
    }

    @Override
    public void loadPrefs(String fileName) throws PreferencesException {
        logger.log(Level.TRACE, "loadPrefs(fileName: {})", fileName);
        RulesetManagementInterface rum = rums.getRulesetManagement();
        try {
            rum.load(new File(fileName));
        } catch (IOException e) {
            throw new PreferencesException(e.getMessage(), e);
        }
        this.ruleset = rum;
    }

}
