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

import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.Domain;
import org.kitodo.api.dataeditor.rulesetmanagement.InputType;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;

public class ProcessTextMetadata extends ProcessSimpleMetadata implements Serializable {

    private String value;

    public ProcessTextMetadata(ProcessFieldedMetadata container, SimpleMetadataViewInterface settings,
            MetadataEntry value) {
        super(container, settings, Objects.isNull(settings) ? value.getKey() : settings.getLabel());
        this.value = addLeadingZeros(Objects.isNull(value) ? settings.getDefaultValue() : value.getValue());
    }

    public ProcessTextMetadata(ProcessTextMetadata template) {
        super(template.container, template.settings, template.label);
        this.value = template.value;
    }

    private String addLeadingZeros(String value) {
        if (Objects.nonNull(super.settings) && InputType.INTEGER.equals(super.settings.getInputType())) {
            int valueLength = value.length();
            int minDigits = super.settings.getMinDigits();
            return valueLength >= minDigits ? value : "0".repeat(minDigits - valueLength).concat(value);
        } else {
            return value;
        }
    }

    @Override
    ProcessTextMetadata getClone() {
        return new ProcessTextMetadata(this);
    }

    @Override
    public String getMetadataID() {
        return settings.getId();
    }

    @Override
    public String getInput() {
        InputType inputType = Objects.isNull(settings) ? InputType.ONE_LINE_TEXT : settings.getInputType();
        switch (inputType) {
            case DATE:
                return "calendar";
            case INTEGER:
                return "spinner";
            case MULTI_LINE_TEXT:
                return "inputTextarea";
            case ONE_LINE_TEXT:
                return "inputText";
            default:
                return "";
        }
    }

    /**
     * Returns the metadata from this row.
     * @param skipEmpty boolean
     * @return the metadata from this row
     */
    @Override
    public Collection<Metadata> getMetadata(boolean skipEmpty) {
        value = value.trim();
        if (skipEmpty && value.isEmpty()) {
            return Collections.emptyList();
        }
        /* if (!settings.isValid(value)) {
            throw new InvalidMetadataValueException(label, value);
        }*/
        MetadataEntry entry = new MetadataEntry();
        entry.setKey(settings.getId());
        entry.setDomain(DOMAIN_TO_MDSEC.get(settings.getDomain().orElse(Domain.DESCRIPTION)));
        entry.setValue(value);
        return Collections.singletonList(entry);
    }

    @Override
    public Collection<Metadata> getMetadataWithFilledValues() {
        return getMetadata(true);
    }

    @Override
    public boolean isValid() {
        if (Objects.isNull(value) || value.isEmpty()) {
            return false;
        }
        return settings.isValid(value, container.getListForLeadingMetadataFields());
    }

    /**
     * Returns the contents of the text input field of this process metadata.
     *
     * @return the contents of the input field
     */
    public String getValue() {
        return value;
    }

    @Override
    public String extractSimpleValue() {
        return getValue();
    }

    /**
     * Sets the contents of the text input field of this process metadata.
     *
     * @param value
     *            value to be set
     */
    public void setValue(String value) {
        this.value = addLeadingZeros(value);
    }
}
