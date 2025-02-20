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

package org.kitodo.production.forms.createprocess;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.Domain;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.exceptions.InvalidMetadataValueException;

/**
 * A row on the metadata panel that contains an on/off switch.
 */
public class ProcessBooleanMetadata extends ProcessSimpleMetadata implements Serializable {

    /**
     * Whether the switch is on or off.
     */
    private boolean active;

    /**
     * Creates a new metadata panel row with an on/off switch.
     *
     * @param settings
     *            configuration settings from the rule set
     * @param data
     *            data to display
     */
    ProcessBooleanMetadata(ProcessFieldedMetadata container, SimpleMetadataViewInterface settings, MetadataEntry data) {
        super(container, settings, settings.getLabel());
        if (Objects.isNull(data)) {
            this.active = settings.getBooleanDefaultValue();
        } else {
            this.active = StringUtils.isNotBlank(data.getValue());
        }
    }

    private ProcessBooleanMetadata(ProcessBooleanMetadata template) {
        super(template.container, template.settings, template.label);
        this.active = template.active;
    }

    @Override
    public ProcessBooleanMetadata getClone() {
        return new ProcessBooleanMetadata(this);
    }

    @Override
    public String getMetadataID() {
        return settings.getId();
    }

    @Override
    public String extractSimpleValue() {
        return settings.convertBoolean(active).orElse(null);
    }

    @Override
    public String getInput() {
        return "toggleSwitch";
    }

    @Override
    public Collection<Metadata> getMetadataWithFilledValues() throws InvalidMetadataValueException {
        return getMetadata(true);
    }

    @Override
    public Collection<Metadata> getMetadata(boolean skipEmpty) throws InvalidMetadataValueException {
        if (!isValid()) {
            throw new InvalidMetadataValueException(label, settings.convertBoolean(active).orElse(""));
        }
        Optional<String> value = settings.convertBoolean(active);
        if (!skipEmpty || value.isPresent()) {
            MetadataEntry entry = new MetadataEntry();
            entry.setKey(settings.getId());
            entry.setDomain(DOMAIN_TO_MDSEC.get(settings.getDomain().orElse(Domain.DESCRIPTION)));
            entry.setValue(value.isPresent() ? value.get() : "");
            return Collections.singletonList(entry);
        }
        return Collections.emptyList();
    }


    /**
     * Returns whether the switch is on.
     *
     * @return whether the switch is on
     */
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isValid() {
        Optional<String> value = settings.convertBoolean(active);
        return !value.isPresent() || settings.isValid(value.get(), container.getListForLeadingMetadataFields());
    }

    /**
     * Setter if the user changes the switch.
     *
     * @param active
     *            whether the switch is on
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void setValue(String value) {
        setActive(StringUtils.isNotBlank(value));
    }
}
