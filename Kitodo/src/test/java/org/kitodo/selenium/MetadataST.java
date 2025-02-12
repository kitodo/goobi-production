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

package org.kitodo.selenium;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.MockDatabase;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.selenium.testframework.BaseTestSelenium;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.kitodo.selenium.testframework.pages.MetadataEditorPage;
import org.kitodo.test.utils.ProcessTestUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Tests for functions in the metadata editor.
 */
public class MetadataST extends BaseTestSelenium {

    private static final String TEST_MEDIA_REFERENCES_FILE = "testUpdatedMediaReferencesMeta.xml";
    private static final String TEST_METADATA_LOCK_FILE = "testMetadataLockMeta.xml";
    private static final String TEST_RENAME_MEDIA_FILE = "testRenameMediaMeta.xml";
    private static final String TEST_LINK_PAGE_TO_NEXT_DIVISION_MEDIA_FILE = "testLinkPageToNextDivisionMeta.xml";
    private static int mediaReferencesProcessId = -1;
    private static int metadataLockProcessId = -1;
    private static int parentProcessId = -1;
    private static int renamingMediaProcessId = -1;
    private static int dragndropProcessId = -1;
    private static int createStructureProcessId = -1;
    private static int linkPageToNextDivisionProcessId = -1;
    private static final String PARENT_PROCESS_TITLE = "Parent process";
    private static final String FIRST_CHILD_PROCESS_TITLE = "First child process";
    private static final String SECOND_CHILD_PROCESS_TITLE = "Second child process";
    private static final String LINK_PAGE_TO_NEXT_DIVISION_PROCESS_TITLE = "Link page to next division";
    private static final String TEST_PARENT_PROCESS_METADATA_FILE = "testParentProcessMeta.xml";
    private static final String FIRST_CHILD_ID = "FIRST_CHILD_ID";
    private static final String SECOND_CHILD_ID = "SECOND_CHILD_ID";
    private static List<Integer> processHierarchyTestProcessIds = new LinkedList<>();
    private static final String FIRST_STRUCTURE_TREE_NODE_LABEL = "1 : -";
    private static final String SECOND_STRUCTURE_TREE_NODE_LABEL = "2 : -";

    private static void prepareMediaReferenceProcess() throws DAOException, DataException, IOException {
        insertTestProcessForMediaReferencesTest();
        copyTestFilesForMediaReferences();
    }

    private static void prepareMetadataLockProcess() throws DAOException, DataException, IOException {
        insertTestProcessForMetadataLockTest();
        ProcessTestUtils.copyTestMetadataFile(metadataLockProcessId, TEST_METADATA_LOCK_FILE);
    }

    private static void prepareProcessHierarchyProcesses() throws DAOException, IOException, DataException {
        processHierarchyTestProcessIds = linkProcesses();
        copyTestParentProcessMetadataFile();
        updateChildProcessIdsInParentProcessMetadataFile();
    }

    private static void prepareMediaRenamingProcess() throws DAOException, DataException, IOException {
        insertTestProcessForRenamingMediaFiles();
        copyTestFilesForRenamingMediaFiles();
    }

    private static void prepareDragNDropProcess() throws DAOException, DataException, IOException {
        insertTestProcessForDragAndDrop();
        copyTestFilesForDragAndDrop();
    }

    private static void prepareCreateStructureProcess() throws DAOException, DataException, IOException {
        insertTestProcessForCreatingStructureElement();
        copyTestFilesForCreateStructure();
    }

    private static void prepareLinkPageToNextDivision() throws DAOException, DataException, IOException {
        linkPageToNextDivisionProcessId = MockDatabase.insertTestProcessIntoSecondProject(LINK_PAGE_TO_NEXT_DIVISION_PROCESS_TITLE);
        ProcessTestUtils.copyTestFiles(linkPageToNextDivisionProcessId, TEST_LINK_PAGE_TO_NEXT_DIVISION_MEDIA_FILE);
    }

    /**
     * Prepare tests by inserting dummy processes into database and index for sub-folders of test metadata resources.
     * @throws DAOException when saving of dummy or test processes fails.
     * @throws DataException when retrieving test project for test processes fails.
     * @throws IOException when copying test metadata or image files fails.
     */
    @BeforeAll
    public static void prepare() throws DAOException, DataException, IOException {
        MockDatabase.insertFoldersForSecondProject();
        prepareMetadataLockProcess();
        prepareMediaReferenceProcess();
        prepareProcessHierarchyProcesses();
        prepareMediaRenamingProcess();
        prepareDragNDropProcess();
        prepareCreateStructureProcess();
        prepareLinkPageToNextDivision();
    }

    /**
     * Tests whether structure tree is hidden when user lacks permission to see a process structure in metadata editor.
     * @throws Exception when page navigation fails
     */
    @Test
    public void hideStructureDataTest() throws Exception {
        login("verylast");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.METADATA_LOCK_TEST_PROCESS_TITLE);
        assertFalse(Pages.getMetadataEditorPage().isStructureTreeFormVisible());
        Pages.getMetadataEditorPage().save();
    }

    /**
     * Tests if process metadata lock is being removed when the user leaves the metadata editor
     * without clicking the close button.
     */
    @Test
    public void removeMetadataLockTest() throws Exception {
        // Open process in metadata editor by default user to set metadata lock for this process and user
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.METADATA_LOCK_TEST_PROCESS_TITLE);
        Pages.getMetadataEditorPage().save();
        // Leave metadata editor without explicitly clicking the 'close' button
        Pages.getMetadataEditorPage().clickPortalLogo();
        // Try to open metadata editor with separate user to check whether metadata lock is still in place
        Pages.getTopNavigation().logout();
        login("verylast");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.METADATA_LOCK_TEST_PROCESS_TITLE);
        assertTrue(Browser.getCurrentUrl().contains("metadataEditor.jsf"), "Unable to open metadata editor that was not closed by 'close' button");
    }

    /**
     * Verifies that linked child processes can be reordered via drag'n'drop in the metadata editor.
     * @throws Exception if processes cannot be saved or loaded
     */
    @Test
    public void changeProcessLinkOrderTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editParentProcessMetadata();
        assertTrue(Pages.getMetadataEditorPage().getNameOfFirstLinkedChildProcess().endsWith(FIRST_CHILD_PROCESS_TITLE), "Wrong initial order of linked child processes");
        assertTrue(Pages.getMetadataEditorPage().getSecondRootElementChildLabel().endsWith(SECOND_CHILD_PROCESS_TITLE), "Wrong initial order of linked child processes");
        Pages.getMetadataEditorPage().changeOrderOfLinkedChildProcesses();
        Pages.getMetadataEditorPage().saveAndExit();
        Pages.getProcessesPage().goTo().editParentProcessMetadata();
        assertTrue(Pages.getMetadataEditorPage().getNameOfFirstLinkedChildProcess().endsWith(SECOND_CHILD_PROCESS_TITLE), "Wrong resulting order of linked child processes");
        assertTrue(Pages.getMetadataEditorPage().getSecondRootElementChildLabel().endsWith(FIRST_CHILD_PROCESS_TITLE), "Wrong resulting order of linked child processes");
    }

    /**
     * Verifies that clicking the "collapse all" and "expand all" buttons in the structure panel of the metadata editor
     * does indeed collapse and expand all tree nodes correctly.
     * @throws Exception when page navigation fails
     */
    @Test
    public void toggleAllStructureNodesTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.METADATA_LOCK_TEST_PROCESS_TITLE);
        assertEquals(2, Pages.getMetadataEditorPage().getNumberOfDisplayedStructureElements(), "Number of visible nodes is wrong initially");
        Pages.getMetadataEditorPage().collapseAll();
        assertEquals(1, Pages.getMetadataEditorPage().getNumberOfDisplayedStructureElements(), "Number of visible nodes after collapsing all is wrong");
        Pages.getMetadataEditorPage().expandAll();
        assertEquals(2, Pages.getMetadataEditorPage().getNumberOfDisplayedStructureElements(), "Number of visible nodes after expanding all is wrong");
    }

    /**
     * Tests total number of scans.
     */
    @Test
    public void totalNumberOfScansTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        assertEquals("(3 Medien)", Pages.getMetadataEditorPage().getNumberOfScans(), "Total number of scans is not correct");
    }

    /**
     * Verifies that turning the "pagination panel switch" on in the user settings
     * results in pagination panel being displayed by default in the metadata editor.
     */
    @Test
    public void showPaginationByDefaultTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        assertFalse(Pages.getMetadataEditorPage().isPaginationPanelVisible());
        Pages.getMetadataEditorPage().closeEditor();
        Pages.getUserEditPage().togglePaginationToShowByDefault();
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        assertTrue(Pages.getMetadataEditorPage().isPaginationPanelVisible());
        // disable pagination again to prevent conflicts with other tests (when interacting with metadata table)
        Pages.getMetadataEditorPage().closeEditor();
        Pages.getUserEditPage().togglePaginationToShowByDefault();
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        assertFalse(Pages.getMetadataEditorPage().isPaginationPanelVisible());
    }

    /**
     * Verifies that changing process images on file system triggers information/warning dialog.
     * @throws Exception when page navigation fails
     */
    @Test
    public void updateMediaReferencesTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_REFERENCES_TEST_PROCESS_TITLE);
        assertTrue(Pages.getMetadataEditorPage()
                .isFileReferencesUpdatedDialogVisible(), "Media references updated dialog not visible");
        Pages.getMetadataEditorPage().acknowledgeFileReferenceChanges();
    }

    /**
     * Verifies that renaming media files shows 'renaming successful' dialog and renames files correctly according to
     * their corresponding physical divisions ORDER attribute.
     * @throws Exception when page navigation fails
     */
    @Test
    public void renameMediaFilesTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        assertEquals(FIRST_STRUCTURE_TREE_NODE_LABEL, Pages.getMetadataEditorPage().getSecondRootElementChildLabel(), "Second child node in structure tree has wrong label BEFORE renaming media files");
        Pages.getMetadataEditorPage().renameMedia();
        assertTrue(Pages.getMetadataEditorPage()
                .isRenamingMediaFilesDialogVisible(), "'Renaming media files was successful' dialog is not visible");
        Pages.getMetadataEditorPage().acknowledgeRenamingMediaFiles();
        assertEquals(SECOND_STRUCTURE_TREE_NODE_LABEL, Pages.getMetadataEditorPage().getSecondRootElementChildLabel(), "Second child node in structure tree has wrong label AFTER renaming media files");
    }

    /**
     * Verifies drag and drop functionality in gallery and structure tree.
     * 
     * @throws Exception when page navigation or process saving fails.
     */
    @Test
    public void dragAndDropPageTest() throws Exception {
        login("kowal");

        // open metadata editor
        MetadataEditorPage metaDataEditor = Pages.getMetadataEditorPage();
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.DRAG_N_DROP_TEST_PROCESS_TITLE);

        // wait until gallery is visible, page order should be 2-1-3
        WebElement unstructuredMedia = Browser.getDriver().findElement(By.id("imagePreviewForm:unstructuredMedia"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(unstructuredMedia::isDisplayed);

        // wait until first page in visible in unstructured media stripe
        WebElement firstThumbnail = Browser.getDriver()
                .findElement(By.id("imagePreviewForm:unstructuredMediaList:0:unstructuredMediaPanel"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(firstThumbnail::isDisplayed);

        // hover over second thumbnail to verify overlay text before drag'n'drop
        metaDataEditor.checkGalleryThumbnailOverlayText(
            "imagePreviewForm:unstructuredMediaList:1:unstructuredMediaPanel",
            "Bild 1, Seite -",
            "Second thumbnail has wrong overlay before drag'n'drop action"
        );

        // drop position for drag'n'drop action
        WebElement dropPosition = Browser.getDriver()
                .findElement(By.id("imagePreviewForm:unstructuredMediaList:2:unstructuredPageDropArea"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(dropPosition::isDisplayed);

        // drag'n'drop action
        Actions dragAndDropAction = new Actions(Browser.getDriver());
        dragAndDropAction.dragAndDrop(firstThumbnail, dropPosition).build().perform();
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id("buttonForm:saveExit"))::isEnabled);

        // page order should now be 1-2-3

        // save process
        Pages.getMetadataEditorPage().saveAndExit();

        // check whether new position has been saved correctly
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.DRAG_N_DROP_TEST_PROCESS_TITLE);
        metaDataEditor.checkGalleryThumbnailOverlayText(
            "imagePreviewForm:unstructuredMediaList:1:unstructuredMediaPanel",
            "Bild 2, Seite -",
            "Second thumbnail has wrong overlay after drag'n'drop action"
        );

        // select page 1 and 2 in structure tree
        metaDataEditor.selectStructureTreeNode("0_0", false, false);
        metaDataEditor.selectStructureTreeNode("0_1", true, false);

        // drag and drop them to last drop
        WebElement dragElement = Browser.getDriver()
            .findElement(By.cssSelector("#logicalTree\\:0_0 .ui-treenode-content"));
        dropPosition = Browser.getDriver()
                .findElement(By.cssSelector("#logicalTree\\:0_2 + li.ui-tree-droppoint"));
        new Actions(Browser.getDriver()).dragAndDrop(dragElement, dropPosition).build().perform();

        // page order should now be 3-1-2

        // save process
        Pages.getMetadataEditorPage().saveAndExit();
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.DRAG_N_DROP_TEST_PROCESS_TITLE);
        metaDataEditor.checkGalleryThumbnailOverlayText(
            "imagePreviewForm:unstructuredMediaList:0:unstructuredMediaPanel",
            "Bild 3, Seite -",
            "First thumbnail has wrong overlay after multi-select drag'n'drop action"
        );    
    }

    /**
     * Verifies functionality of creating structure elements in the metadata editor using the structure tree context
     * menu.
     */
    @Test
    public void createStructureElementTest() throws Exception {
        login("kowal");
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.CREATE_STRUCTURE_PROCESS_TITLE);
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id("logicalTree"))::isDisplayed);
        WebElement structureTree = Browser.getDriver().findElement(By.id("logicalTree"));
        WebElement logicalRoot = structureTree.findElement(By.className("ui-tree-selectable"));
        logicalRoot.click();
        await().ignoreExceptions().pollDelay(1000, TimeUnit.MILLISECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(logicalRoot::isDisplayed);
        // right click action
        Actions rightClickAction = new Actions(Browser.getDriver());
        rightClickAction.contextClick(logicalRoot).build().perform();
        // wait for loading screen to disappear
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).pollInterval(300, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS).until(Browser.getDriver()
                        .findElement(By.id("buttonForm:saveExit"))::isDisplayed);
        WebElement contextMenu = Browser.getDriver().findElement(By.id("contextMenuLogicalTree"));
        List<WebElement> menuItems = contextMenu.findElements(By.className("ui-menuitem"));
        assertEquals(3, menuItems.size(), "Wrong number of context menu items");
        // click "add element" option
        menuItems.get(0).click();
        // open "structure element type selection" menu
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection"), 1000, 1000, 5);
        // click first option
        WebElement firstOption = Browser.getDriver().findElement(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection_1"));
        String structureType = firstOption.getText();
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection_1"), 1000, 500, 3);
        WebDriverWait wait = new WebDriverWait(Browser.getDriver(), 3);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection_1")));
        // add structure element with selected type by clicking "accept"/"apply" button
        Thread.sleep(1000);
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:addDocStruc"), 500, 500, 5);
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id("buttonForm:saveExit"))::isEnabled);
        structureTree = Browser.getDriver().findElement(By.id("logicalTree"));
        WebElement firstChild = structureTree.findElement(By.id("logicalTree:0_0"));
        assertEquals(structureType, firstChild.getText(), "Added structure element has wrong type!");
    }

    /**
     * Tests that column layout can be saved to database and is loaded into hidden form inputs.
     * Does not test whether column layout is actually applied via Javascript (see resize.js).
     */
    @Test
    public void saveLayoutTest() throws Exception {
        login("kowal");

        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        
        String structureWithId = "metadataEditorLayoutForm:structureWidth";
        String metadataWidthId = "metadataEditorLayoutForm:metadataWidth";
        String galleryWithId = "metadataEditorLayoutForm:galleryWidth";

        Function<String, String> getValue = 
            (id) -> Browser.getDriver().findElement(By.id(id)).getAttribute("value");
        
        // by default, layout settings are all 0
        assertEquals("0.0", getValue.apply(structureWithId));
        assertEquals("0.0", getValue.apply(metadataWidthId));
        assertEquals("0.0", getValue.apply(galleryWithId));

        // open layout menu
        Browser.getDriver().findElement(By.id("metadataEditorLayoutButtonForm:open")).click();
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(Browser.getDriver().findElement(By.id("metadataEditorLayoutForm:saveDefault"))::isDisplayed);
        // save layout
        Browser.getDriver().findElement(By.id("metadataEditorLayoutForm:saveDefault")).click();

        // wait until success message is shown
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
            .until(Browser.getDriver().findElement(By.id("dataEditorSavingResultDialog_content"))::isDisplayed);
        
        // confirm success message
        Browser.getDriver().findElement(By.id("dataEditorSavingResultForm:reload")).click();

        // close metadata editor, wait until closed, and re-open
        Pages.getMetadataEditorPage().closeEditor();
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
            .until(() -> Pages.getProcessesPage().isAt());
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

        // verify that layout was saved
        assertNotEquals("0.0", getValue.apply(structureWithId));
        assertNotEquals("0.0", getValue.apply(metadataWidthId));
        assertNotEquals("0.0", getValue.apply(galleryWithId));
    }

    /**
     * Verifies that turning the "show physical page number below thumbnail switch" on in the user settings
     * results in thumbnail banner being displayed in the gallery of the metadata editor.
     */
    @Test
    public void showPhysicalPageNumberBelowThumbnailTest() throws Exception {
        login("kowal");
       
        // open the metadata editor
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);
        
        // verify that physical page number is not shown below thumbnail by default
        assertEquals(0, Browser.getDriver().findElements(By.cssSelector(".thumbnail-banner")).size());

        // change user setting
        Pages.getMetadataEditorPage().closeEditor();
        Pages.getUserEditPage().toggleShowPhysicalPageNumberBelowThumbnail();
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

        // verify physical page number is now shown below thumbnail
        assertFalse(Browser.getDriver().findElements(By.cssSelector(".thumbnail-banner")).isEmpty());
    }

    /** 
     * Verifies that the label of a node in the logical structure tree changes according to the 
     * "structureTreeTitle" option from the ruleset after switching to the "title" display option. 
     */
    @Test
    public void selectStructureTreeTitleTest() throws Exception {
        login("kowal");

        // open the metadata editor
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

        // wait until logical tree is shown
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(By.id("logicalTree")).isDisplayed());

        // check that first tree node label shows type label "Band"
        assertEquals("Band", 
            Browser.getDriver().findElement(By.cssSelector("#logicalTree\\:0 .ui-treenode-label")).getText());

        // open select menu
        Browser.getDriver().findElement(By.id("logicalStructureTitle")).click();

        // wait until select menu is open
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(By.id("logicalStructureTitle_panel")).isDisplayed());

        // click on title menu entry
        Browser.getDriver().findElement(By.id("logicalStructureTitle_1")).click();

        // wait until menu disappears
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> !Browser.getDriver().findElement(By.id("logicalStructureTitle_panel")).isDisplayed());
        
        // check that node title has changed to metadata value of "HauptTitel"
        assertEquals("Der Titel des Bandes", 
            Browser.getDriver().findElement(By.cssSelector("#logicalTree\\:0 .ui-treenode-label")).getText());
    }
    
    @Test
    public void linkPageToNextDivision() throws Exception {
        login("kowal");

        // open metadata editor
        Pages.getProcessesPage().goTo().editMetadata(LINK_PAGE_TO_NEXT_DIVISION_PROCESS_TITLE);

        MetadataEditorPage metaDataEditor = Pages.getMetadataEditorPage();

        // wait until structure tree is shown
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(metaDataEditor::isLogicalTreeVisible);       

        // check page "2" is not marked as "linked"
        assertFalse(metaDataEditor.isStructureTreeNodeAssignedSeveralTimes("0_0_0_0"));

        // open context menu for page "2"
        metaDataEditor.openContextMenuForStructureTreeNode("0_0_0_0");

        // click on 2nd menu entry "assign to next element"
        metaDataEditor.clickStructureTreeContextMenuEntry("assignToNextElement");

        // verify page "2" is now marked as "linked"
        assertTrue(metaDataEditor.isStructureTreeNodeAssignedSeveralTimes("0_0_0_0"));

        // verify linked page "2" was created at correct tree position
        assertTrue(metaDataEditor.isStructureTreeNodeAssignedSeveralTimes("0_1_0_0"));

        // check page "3" was moved to be 2nd sibling
        assertFalse(metaDataEditor.isStructureTreeNodeAssignedSeveralTimes("0_1_0_1"));

        // open context menu for linked page "2"
        metaDataEditor.openContextMenuForStructureTreeNode("0_1_0_0");

        // click on 2nd menu entry "remove assignment"
        metaDataEditor.clickStructureTreeContextMenuEntry("unassign");

        // check page "2" is not marked as "linked" any more
        assertFalse(metaDataEditor.isStructureTreeNodeAssignedSeveralTimes("0_0_0_0"));

        // check page "3" is now only child of folder again
        assertTrue(Browser.getDriver().findElements(By.cssSelector("#logicalTree\\:0_1_0_1")).isEmpty());
    }

    /**
     * Tests that a metadata row of the metadata table is highlighted as soon as a user adds a new
     * row via the add metadata dialog.
     */
    @Test
    public void focusRecentlyAddedMetadataRowTest() throws Exception {
        login("kowal");

        // open the metadata editor
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

        // wait until metadata table is shown
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(
            () -> Browser.getDriver().findElement(By.id("metadataAccordion:metadata:metadataTable")).isDisplayed()
        );

        // verify no metadata row is focused yet
        assertTrue(Browser.getDriver().findElements(
            By.cssSelector("#metadataAccordion\\:metadata\\:metadataTable tr.focusedRow")).isEmpty()
        );

        // click on add metadata button
        Browser.getDriver().findElement(By.id("metadataAccordion:addMetadataButton")).click();
        
        // wait until dialog is visible
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(
            () -> Browser.getDriver().findElement(By.id("addMetadataDialog")).isDisplayed()
        );

        // open select menu
        Browser.getDriver().findElement(By.id("addMetadataForm:metadataTypeSelection")).click();

        // wait until selection menu list is visible
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(
            () -> Browser.getDriver().findElement(By.id("addMetadataForm:metadataTypeSelection_items")).isDisplayed()
        );
        
        // select Person as new metadata row
        Browser.getDriver().findElement(By.cssSelector(
            "#addMetadataForm\\:metadataTypeSelection_items li[data-label='Person'].ui-selectonemenu-item"
        )).click();

        // confirm dialog
        Browser.getDriver().findElement(By.id("addMetadataForm:apply")).click();

        // wait until dialog disappears
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(
            () -> !Browser.getDriver().findElement(By.id("addMetadataDialog")).isDisplayed()
        );

        // verify metadata row with name "Person" is selected
        assertEquals("Person:", Browser.getDriver().findElement(
            By.cssSelector("#metadataAccordion\\:metadata\\:metadataTable tr.focusedRow label")
        ).getText());

        // verify accordion was scrolled down
        assertTrue(0 < (Long)Browser.getDriver().executeScript(
            "return document.getElementById('metadataAccordion:metadata:metadataTable').scrollTop;"
        ));
    }

    /*
     * Verifies that an image can be openend in a separate window by clicking on the corresponding 
     * context menu item of the first logical tree node.
     */
    @Test
    public void openPageInSeparateWindowTest() throws Exception {
        login("kowal");

        // remember current window handle
        String firstWindowHandle = Browser.getDriver().getWindowHandle();
       
        // open the metadata editor
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

         // wait until structure tree is shown
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(By.id("logicalTree")).isDisplayed());

        // open context menu for linked page "2"
        Pages.getMetadataEditorPage().openContextMenuForStructureTreeNode("0_0");

        // click second menu entry to open new tab
        Browser.getDriver().findElement(By.cssSelector("#contextMenuLogicalTree .viewPageInNewWindow")).click();

        // find handle of new tab window
        String newWindowHandle = Browser.getDriver().getWindowHandles().stream()
            .filter((h) -> !h.equals(firstWindowHandle)).findFirst().get();

        // switch to new window
        Browser.getDriver().switchTo().window(newWindowHandle);

        // wait until preview image is found
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> !Browser.getDriver().findElements(By.id("imagePreviewForm:mediaPreviewGraphicImage")).isEmpty());

        // check that title contains image number
        assertEquals("Bild 2", Browser.getDriver().findElement(By.id("externalViewTitle")).getText());

        // check that canvas is visible
        assertTrue(Browser.getDriver().findElement(By.cssSelector("#map canvas")).isDisplayed());

        // close tab
        Browser.getDriver().close();

        // switch back to previous window
        Browser.getDriver().switchTo().window(firstWindowHandle);
    }

    /**
     * Checks that multiple elements can be selected in the logical structure tree using the 
     * ctrl and shift keys. Verifies that selection is applied to pagination panel and gallery.
     */
    @Test
    public void multiSelectInLogicalStructureTreeTest() throws Exception {
        login("kowal");

        // open metadata editor
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.MEDIA_RENAMING_TEST_PROCESS_TITLE);

        // wait until logical structure tree is available
        MetadataEditorPage metaDataEditor = Pages.getMetadataEditorPage();
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(By.id("logicalTree")).isDisplayed());

        // select first page
        metaDataEditor.selectStructureTreeNode("0_0", false, false);

        // verify metadata shows of first page is displayed
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(
                By.id("metadataAccordion:metadata:metadataTable:0:inputText")
            ).getAttribute("value").equals("-"));

        // select second page with ctrl
        metaDataEditor.selectStructureTreeNode("0_1", true, false);

        // verify metadata panel is empty
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElement(
                By.id("metadataAccordion:metadata:metadataTable")
            ).getText().equals("No records found."));
        
        // verify both pages are selected in pagination panel
        metaDataEditor.checkPaginationSelection(2);

        // verify both pages are selected in gallery
        metaDataEditor.checkGallerySelection(2);

        // select last page
        metaDataEditor.selectStructureTreeNode("0_2", false, false);

        // select first page with shift (range select)
        metaDataEditor.selectStructureTreeNode("0_0", false, true);

        // verify all pages are selected in pagination panel
        metaDataEditor.checkPaginationSelection(3);

        // verify all pages are selected in gallery
        metaDataEditor.checkGallerySelection(3);
    }

    /**
     * Close metadata editor and logout after every test.
     * @throws Exception when page navigation fails
     */
    @AfterEach
    public void closeEditorAndLogout() throws Exception {
        Pages.getMetadataEditorPage().closeEditor();
        Pages.getTopNavigation().logout();
    }

    /**
     * Cleanup test environment by removing temporal dummy processes from database and index.
     * @throws DAOException when dummy process cannot be removed from database
     * @throws CustomResponseException when dummy process cannot be removed from index
     * @throws DataException when dummy process cannot be removed from index
     * @throws IOException when deleting test files fails.
     */
    @AfterAll
    public static void cleanup() throws DAOException, CustomResponseException, DataException, IOException {
        for (int processId : processHierarchyTestProcessIds) {
            ProcessService.deleteProcess(processId);
        }
        ProcessService.deleteProcess(mediaReferencesProcessId);
        ProcessService.deleteProcess(metadataLockProcessId);
        ProcessService.deleteProcess(renamingMediaProcessId);
        ProcessService.deleteProcess(dragndropProcessId);
        ProcessService.deleteProcess(createStructureProcessId);
        ProcessService.deleteProcess(linkPageToNextDivisionProcessId);
    }

    private void login(String username) throws InstantiationException, IllegalAccessException, InterruptedException {
        User metadataUser = ServiceManager.getUserService().getByLogin(username);
        Pages.getLoginPage().goTo().performLogin(metadataUser);
    }

    private static void insertTestProcessForMediaReferencesTest() throws DAOException, DataException {
        mediaReferencesProcessId = MockDatabase.insertTestProcessForMediaReferencesTestIntoSecondProject();
    }

    private static void insertTestProcessForMetadataLockTest() throws DAOException, DataException {
        metadataLockProcessId = MockDatabase.insertTestProcessForMetadataLockTestIntoSecondProject();
    }

    private static void insertTestProcessForRenamingMediaFiles() throws DAOException, DataException {
        renamingMediaProcessId = MockDatabase.insertTestProcessForRenamingMediaTestIntoSecondProject();
    }

    private static void insertTestProcessForDragAndDrop() throws DAOException, DataException {
        dragndropProcessId = MockDatabase.insertTestProcessForDragNDropTestIntoSecondProject();
    }

    private static void insertTestProcessForCreatingStructureElement() throws DAOException, DataException {
        createStructureProcessId = MockDatabase.insertTestProcessForCreatingStructureElementIntoSecondProject();
    }

    /**
     * Creates dummy parent process and links first two test processes as child processes to parent process.
     * @throws DAOException if loading of processes fails
     * @throws DataException if saving of processes fails
     */
    private static List<Integer> linkProcesses() throws DAOException, DataException {
        List<Integer> processIds = new LinkedList<>();
        List<Process> childProcesses = new LinkedList<>();
        childProcesses.add(ProcessTestUtils.addProcess(FIRST_CHILD_PROCESS_TITLE));
        childProcesses.add(ProcessTestUtils.addProcess(SECOND_CHILD_PROCESS_TITLE));
        Process parentProcess = ProcessTestUtils.addProcess(PARENT_PROCESS_TITLE);
        parentProcess.getChildren().addAll(childProcesses);
        ServiceManager.getProcessService().save(parentProcess);
        parentProcessId = parentProcess.getId();
        for (Process childProcess : childProcesses) {
            childProcess.setParent(parentProcess);
            ServiceManager.getProcessService().save(childProcess);
            processIds.add(childProcess.getId());
        }
        processIds.add(parentProcess.getId());
        return processIds;
    }

    private static void copyTestFilesForMediaReferences() throws IOException, DAOException, DataException {
        ProcessTestUtils.copyTestFiles(mediaReferencesProcessId, TEST_MEDIA_REFERENCES_FILE);
    }

    private static void copyTestFilesForRenamingMediaFiles() throws IOException, DAOException, DataException {
        ProcessTestUtils.copyTestFiles(renamingMediaProcessId, TEST_RENAME_MEDIA_FILE);
    }

    private static void copyTestFilesForDragAndDrop() throws IOException, DAOException, DataException {
        ProcessTestUtils.copyTestFiles(dragndropProcessId, TEST_RENAME_MEDIA_FILE);
    }

    private static void copyTestFilesForCreateStructure() throws DAOException, DataException, IOException {
        ProcessTestUtils.copyTestFiles(createStructureProcessId, TEST_RENAME_MEDIA_FILE);
    }

    private static void copyTestParentProcessMetadataFile() throws IOException, DAOException, DataException {
        ProcessTestUtils.copyTestMetadataFile(parentProcessId, TEST_PARENT_PROCESS_METADATA_FILE);
    }

    private static void updateChildProcessIdsInParentProcessMetadataFile() throws IOException, DAOException {
        Process parentProcess = ServiceManager.getProcessService().getById(parentProcessId);
        Path metaXml = Paths.get(ConfigCore.getKitodoDataDirectory(), parentProcessId + ProcessTestUtils.META_XML);
        String xmlContent = Files.readString(metaXml);
        String firstChildId = String.valueOf(parentProcess.getChildren().get(0).getId());
        String secondChildId = String.valueOf(parentProcess.getChildren().get(1).getId());
        xmlContent = xmlContent.replaceAll(FIRST_CHILD_ID, String.valueOf(firstChildId));
        xmlContent = xmlContent.replaceAll(SECOND_CHILD_ID, String.valueOf(secondChildId));
        Files.write(metaXml, xmlContent.getBytes());
    }

    private void clickItemWhenDisplayed(By selector, long delay, long intervall, long timeout) {
        await().ignoreExceptions().pollDelay(delay, TimeUnit.MILLISECONDS).pollInterval(intervall, TimeUnit.MILLISECONDS)
                .atMost(timeout, TimeUnit.SECONDS).until(Browser.getDriver().findElement(selector)::isDisplayed);
        Browser.getDriver().findElement(selector).click();
    }
}
