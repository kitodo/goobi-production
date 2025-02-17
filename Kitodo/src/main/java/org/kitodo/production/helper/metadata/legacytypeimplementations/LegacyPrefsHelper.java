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

import static org.kitodo.constants.StringConstants.EDIT;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.data.database.beans.User;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.dataeditor.RulesetManagementService;

/**
 * Connects a legacy prefs to a ruleset. This is a soldering class to keep
 * legacy code operational which is about to be removed. Do not use this class.
 */
public class LegacyPrefsHelper {
    private static final Logger logger = LogManager.getLogger(LegacyPrefsHelper.class);

    private static final RulesetManagementService rulesetManagementService = ServiceManager
            .getRulesetManagementService();

    /**
     * The ruleset accessed via this soldering class.
     */
    private RulesetManagementInterface ruleset;

    /**
     * MetadataType aus Preferences ermitteln.
     *
     * @param inPrefs
     *            Prefs object
     * @param inName
     *            String
     * @return MetadataType
     */
    @Deprecated
    public static LegacyMetadataTypeHelper getMetadataType(LegacyPrefsHelper inPrefs, String inName) {
        LegacyMetadataTypeHelper mdt = inPrefs.getMetadataTypeByName(inName);
        if (mdt == null) {
            throw new IllegalArgumentException("MetadataType does not exist in current Preferences: " + inName);
        }
        return mdt;
    }

    /**
     * Needs string as parameter and returns MetadataType object with this name.
     *
     * @param identifier
     *            parameter
     * @return MetadataType object with this name
     */
    @Deprecated
    public LegacyMetadataTypeHelper getMetadataTypeByName(String identifier) {
        switch (identifier) {
            case "logicalPageNumber":
                return LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDERLABEL;
            case "physPageNumber":
                return LegacyMetadataTypeHelper.SPECIAL_TYPE_ORDER;
            default:
                List<LanguageRange> priorityList;

                try {
                    User user = ServiceManager.getUserService().getAuthenticatedUser();
                    String metadataLanguage = user != null ? user.getMetadataLanguage()
                            : Helper.getRequestParameter("Accept-Language");
                    priorityList = LanguageRange.parse(! metadataLanguage.isEmpty() ? metadataLanguage : "en");
                } catch (NullPointerException e) {
                    /*
                     * new Metadaten() throws a NullPointerException in
                     * asynchronous export because there is no Faces context
                     * then.
                     */
                    logger.catching(Level.TRACE, e);
                    priorityList = LanguageRange.parse("en");
                }
                StructuralElementViewInterface divisionView = ruleset.getStructuralElementView("", EDIT,
                    priorityList);
                List<MetadataViewWithValuesInterface> entryViews = divisionView
                        .getSortedVisibleMetadata(Collections.emptyList(), List.of(identifier));
                MetadataViewInterface resultKeyView = entryViews.parallelStream()
                        .map(MetadataViewWithValuesInterface::getMetadata).filter(Optional::isPresent).map(Optional::get)
                        .filter(keyView -> keyView.getId().equals(identifier)).findFirst()
                        .orElseThrow(IllegalStateException::new);
                return new LegacyMetadataTypeHelper(resultKeyView);
        }
    }

    /**
     * Returns the ruleset of the legacy prefs helper.
     *
     * @return the ruleset
     */
    @Deprecated
    public RulesetManagementInterface getRuleset() {
        return ruleset;
    }

    /**
     * Loads all known DocStruct types from the prefs XML file.
     *
     * @param fileName
     *            file to load
     */
    @Deprecated
    public void loadPrefs(String fileName) throws IOException {
        File rulesetFile = new File(fileName);
        RulesetManagementInterface loadRuleset = rulesetManagementService.getRulesetManagement();
        loadRuleset.load(rulesetFile);
        this.ruleset = loadRuleset;
    }
}
