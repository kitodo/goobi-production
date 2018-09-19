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

package org.kitodo.config.enums;

public enum ParameterImageManagement implements ParameterInterface {

    DIR_TMP("ImageManagementModule.tmpDir"),
    SEARCH_PATH("ImageManagementModule.searchPath"),
    TIMEOUT_SEC("ImageManagementModule.timeoutSec"),
    SSH_HOST("ImageManagementModule.sshHosts");

    private String name;

    /**
     * Private constructor to hide the implicit public one.
     *
     * @param name
     *            of parameter
     */
    ParameterImageManagement(String name) {
        this.name = name;
    }

    @Override
    public java.lang.String toString() {
        return this.name;
    }
}
