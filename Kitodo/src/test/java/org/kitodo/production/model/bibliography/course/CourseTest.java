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

package org.kitodo.production.model.bibliography.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.kitodo.production.helper.XMLUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class CourseTest {

    @Test
    public void testCloneMethod() throws Exception {
        String xmlString = new String(Files.readAllBytes(new File("src/test/resources/newspaper-course.xml").toPath()));
        Document xmlDocument = XMLUtils.parseXMLString(xmlString);
        Course course = new Course(xmlDocument, Collections.emptyMap());

        // assert / check some data from the xml file
        assertEquals(23L, course.getIndividualIssues().size());
        assertEquals(23, course.countIndividualIssues());
        assertEquals("", course.getYearName());

        // clone course
        Course clonedCourse = course.clone();
        // courses should have same data
        assertEquals(course.countIndividualIssues(), clonedCourse.countIndividualIssues());
        assertEquals(course.getNumberOfProcesses(), clonedCourse.getNumberOfProcesses());
        assertEquals(course.getYearName(), clonedCourse.getYearName());

        // change year name of not cloned course
        course.setYearName("Year 2024");
        // year name should now differ
        assertEquals("", clonedCourse.getYearName());
        assertNotEquals(course.getYearName(), clonedCourse.getYearName());
    }
}
