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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;

public class ProcessDateMetadata extends ProcessTextMetadata {
    private static final Logger logger = LogManager.getLogger(ProcessDateMetadata.class);
    private static final String NOW = "now";

    private Date date;

    ProcessDateMetadata(ProcessFieldedMetadata container, SimpleMetadataViewInterface settings, MetadataEntry value) {
        super(container, settings, value);
        if (Objects.isNull(value)) {
            this.setValue(NOW.equals(settings.getDefaultValue()) ? new SimpleDateFormat("yyyy-MM-dd").format(new Date()) : "");
        }
    }

    private ProcessDateMetadata(ProcessDateMetadata template) {
        super(template);
        this.date = Objects.isNull(template.date) ? null : new Date(template.date.getTime());
    }

    @Override
    public ProcessTextMetadata getClone() {
        return new ProcessDateMetadata(this);
    }


    /**
     * Get date.
     *
     * @return value of date
     */
    public Date getDate() {
        if (Objects.isNull(date) && StringUtils.isNotBlank(getValue())) {
            try {
                date = new SimpleDateFormat("yyyy-MM-dd").parse(getValue());
            } catch (ParseException e) {
                logger.error(e.getLocalizedMessage(), e);
            }
        }
        return date;
    }

    /**
     * Set date.
     *
     * @param date as java.util.Date
     */
    public void setDate(Date date) {
        this.date = date;
        if (Objects.nonNull(date)) {
            this.setValue(new SimpleDateFormat("yyyy-MM-dd").format(date));
        }
    }

}
