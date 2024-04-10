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

package org.kitodo.production.dto;

import java.util.ArrayList;
import java.util.List;

import org.kitodo.data.interfaces.ClientInterface;
import org.kitodo.data.interfaces.RoleInterface;
import org.kitodo.data.interfaces.UserInterface;

/**
 * Role DTO object.
 */
public class RoleDTO extends BaseDTO implements RoleInterface {

    private String title;
    private List<UserInterface> users = new ArrayList<>();
    private Integer usersSize;
    private ClientInterface client;

    /**
     * Get title.
     *
     * @return title as String
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set title.
     *
     * @param title
     *            as String
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get list of users.
     *
     * @return list of users as UserInterface
     */
    public List<UserInterface> getUsers() {
        return users;
    }

    /**
     * Set list of users.
     *
     * @param users
     *            list of users as UserInterface
     */
    public void setUsers(List<UserInterface> users) {
        this.users = users;
    }

    /**
     * Get size of users.
     *
     * @return size of users as Integer
     */
    public Integer getUsersSize() {
        return usersSize;
    }

    /**
     * Set size of users.
     *
     * @param usersSize
     *            as Integer
     */
    public void setUsersSize(Integer usersSize) {
        this.usersSize = usersSize;
    }

    /**
     * Get client FTO object.
     *
     * @return the client Interface object
     */
    public ClientInterface getClient() {
        return client;
    }

    /**
     * Set client Interface object.
     *
     * @param client as Interface object.
     */
    public void setClient(ClientInterface client) {
        this.client = client;
    }
}
