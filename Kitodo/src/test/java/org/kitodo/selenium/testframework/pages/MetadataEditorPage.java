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

package org.kitodo.selenium.testframework.pages;

import org.kitodo.MockDatabase;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetadataEditorPage extends Page<MetadataEditorPage> {

    private static final String SAVE_AND_EXIT_BUTTON_ID = "buttonForm:saveExit";
    private static final String LOGICAL_TREE = "logicalTree";

    @SuppressWarnings("unused")
    @FindBy(id = "structureTreeForm")
    private WebElement structureTreeForm;

    @SuppressWarnings("unused")
    @FindBy(id = "numberOfScans")
    private WebElement numberOfScans;

    @FindBy(id = "paginationPanel")
    private WebElement paginationPanel;

    @FindBy(css = "#portal-logo a")
    private WebElement poralLogoLink;

    @FindBy(id = "fileReferencesUpdatedDialog")
    private WebElement fileReferencesUpdatedDialog;

    @FindBy(id = "ok")
    private WebElement okButton;

    @FindBy(id = "buttonForm:close")
    private WebElement closeButton;

    @FindBy(id = "buttonForm:save")
    private WebElement saveButton;

    @FindBy(id = SAVE_AND_EXIT_BUTTON_ID)
    private WebElement saveAndExitButton;

    @FindBy(id = "buttonForm:renameMedia")
    private WebElement renameMediaButton;

    @FindBy(id = LOGICAL_TREE + ":0")
    private WebElement logicalTree;

    @FindBy(id = LOGICAL_TREE + ":0_0")
    private WebElement firstChildProcess;

    @FindBy(id = LOGICAL_TREE + ":0_1")
    private WebElement secondChildNode;

    @FindBy(id = "expandAllButton")
    private WebElement expandAllButton;

    @FindBy(id = "collapseAllButton")
    private WebElement collapseAllButton;

    @FindBy(id = "renamingMediaSuccessDialog")
    private WebElement renamingMediaSuccessDialog;

    @FindBy(id = "renamingMediaResultForm:okSuccess")
    private WebElement okButtonRenameMediaFiles;

    @FindBy(id = "contextMenuLogicalTree")
    private WebElement contextMenuLogicalTree;

    @FindBy(id = "imagePreviewForm:previewButton")
    private WebElement imagePreviewButton;

    public MetadataEditorPage() {
        super("metadataEditor.jsf");
    }

    @Override
    public MetadataEditorPage goTo() {
        return null;
    }

    public boolean isStructureTreeFormVisible() {
        return structureTreeForm.isDisplayed();
    }

    public boolean isLogicalTreeVisible() {
        return logicalTree.isDisplayed();
    }

    /**
     * Gets numberOfScans.
     *
     * @return value of numberOfScans
     */
    public String getNumberOfScans() {
        return numberOfScans.getText();
    }

    /**
     * Return whether pagination panel is displayed or not.
     * @return whether pagination panel is displayed or not
     */
    public boolean isPaginationPanelVisible() {
        return paginationPanel.isDisplayed();
    }

    /**
     * Click Kitodo portal logo to return to desktop page.
     */
    public void clickPortalLogo() {
        poralLogoLink.click();
    }

    /**
     * Check and return whether information dialog about updated media references is displayed or not.
     * @return whether information dialog about updated media references is displayed or not
     */
    public boolean isFileReferencesUpdatedDialogVisible() {
        return fileReferencesUpdatedDialog.isDisplayed();
    }

    /**
     * Acknowledge file reference changes by clicking "OK" button on corresponding popup dialog.
     */
    public void acknowledgeFileReferenceChanges() {
        okButton.click();
    }

    /**
     * Check and return whether information dialog about renamed media files is displayed or not.
     * @return whether information dialog about renamed media files is displayed or not
     */
    public boolean isRenamingMediaFilesDialogVisible() {
        return renamingMediaSuccessDialog.isDisplayed();
    }

    /**
     * Acknowledge media files being successfully renamed dialog by clicking "OK" button on corresponding popup dialog.
     */
    public void acknowledgeRenamingMediaFiles() {
        okButtonRenameMediaFiles.click();
    }

    /**
     * Close Metadata editor to release metadata lock.
     */
    public void closeEditor() {
        closeButton.click();
    }

    /**
     * Click save button.
     */
    public void save() {
        saveButton.click();
    }

    /**
     * Click "rename media" button.
     */
    public void renameMedia() {
        renameMediaButton.click();
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(this::isRenamingMediaFilesDialogVisible);
    }

    /**
     * Change order of child processes in metadata editor by moving the second child process before
     * the first child process via drag and drop.
     */
    public void changeOrderOfLinkedChildProcesses() {
        secondChildNode.click();
        WebDriver webDriver = Browser.getDriver();
        Actions moveAction = new Actions(webDriver);
        WebElement dropArea = logicalTree.findElement(By.className("ui-tree-droppoint"));
        moveAction.dragAndDrop(secondChildNode, dropArea).build().perform();
    }

    public ProcessesPage saveAndExit() throws InstantiationException, IllegalAccessException {
        clickButtonAndWaitForRedirect(saveAndExitButton, Pages.getProcessesPage().getUrl());
        return Pages.getProcessesPage();
    }

    public String getNameOfFirstLinkedChildProcess() {
        return firstChildProcess.getText();
    }

    /**
     * Get label of second child tree node of structure tree root node.
     * @return label of second child tree node of structure tree root node
     */
    public String getSecondRootElementChildLabel() {
        return secondChildNode.getText();
    }
    /**
     * Click "expand all" button in structure panel of metadata editor.
     */
    public void expandAll() {
        expandAllButton.click();
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(() -> expandAllButton.isEnabled());
    }

    /**
     * Click "collapse all" button in structure panel of metadata editor.
     */
    public void collapseAll() {
        collapseAllButton.click();
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(() -> collapseAllButton.isEnabled());
    }

    /**
     * Retrieve and return number of currently displayed nodes in structure tree.
     * @return number of currently displayed nodes in structure tree
     */
    public long getNumberOfDisplayedStructureElements() {
        return Browser.getDriver().findElements(By.cssSelector(".ui-treenode")).stream().filter(WebElement::isDisplayed)
                .count();
    }

    /**
     * Open context menu (right click) for specific structure tree node.
     * 
     * @param nodeId the tree node id describing the node in the tree (e.g., "0_1_0_1")
     */
    public void selectStructureTreeNode(String nodeId, boolean withCtrl, boolean withShift) {
        WebElement treeNode = Browser.getDriver().findElement(By.cssSelector(
            "#logicalTree\\:" + nodeId +  " .ui-treenode-content"
        ));
        Actions actions = new Actions(Browser.getDriver());
        if (withCtrl) {
            actions = actions.keyDown(Keys.LEFT_CONTROL).click(treeNode).keyUp(Keys.LEFT_CONTROL); 
        } else if (withShift) {
            actions = actions.keyDown(Keys.LEFT_SHIFT).click(treeNode).keyUp(Keys.LEFT_SHIFT);
        } else {
            actions = actions.click(treeNode);
        }
        actions.build().perform();
    }

    /**
     * Check that the provided number of pages are selected in the pagination panel.
     * 
     * @param count the number of pages that should be selected in the pagination panel
     */
    public void checkPaginationSelection(int count) {
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElements(
                By.cssSelector("#paginationForm\\:paginationSelection .ui-chkbox-box.ui-state-active")
            ).size() == count);
    }

    /**
     * Check that the provided number of pages are selected in the gallery.
     * 
     * @param count the number of pages that should be selected in the gallery
     */
    public void checkGallerySelection(int count) {
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
            .until(() -> Browser.getDriver().findElements(
                By.cssSelector("#imagePreviewForm .thumbnail.selected")
            ).size() == count);
    }

    /**
     * Check that overlay text of a gallery thumbnail matches an expected text.
     * 
     * @param thumbnailId the id of the thumbnail
     * @param expectedText the expected overlay text
     * @param errorText an error message in case the overlay text does not match
     */
    public void checkGalleryThumbnailOverlayText(String thumbnailId, String expectedText, String errorText) {
        // find gallery thumbnail
        WebElement thumbnail = Browser.getDriver().findElement(By.id(thumbnailId));

        // move moues to thumbnail
        new Actions(Browser.getDriver()).moveToElement(thumbnail).build().perform();

        // retrieve thumbnail text
        WebElement overlay = thumbnail.findElement(By.className("thumbnail-overlay"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(overlay::isDisplayed);

        assertEquals(expectedText, overlay.getText().strip(), errorText);
    }

    /**
     * Open context menu (right click) for specific structure tree node.
     * 
     * @param nodeId the tree node id describing the node in the tree (e.g., "0_1_0_1")
     */
    public void openContextMenuForStructureTreeNode(String nodeId) {
        WebElement treeNode = Browser.getDriver().findElement(By.cssSelector(
            "#logicalTree\\:" + nodeId +  " .ui-treenode-content"
        ));
        new Actions(Browser.getDriver()).contextClick(treeNode).build().perform();
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(
            () -> contextMenuLogicalTree.isDisplayed()
        );
    }

    /**
     * Click on a menu entry in the structure tree context menu.
     * 
     * @param menuItemClassName the class name of the menu entry
     */
    public void clickStructureTreeContextMenuEntry(String menuItemClassName) {
        // click on menu entry
        contextMenuLogicalTree.findElement(By.className(menuItemClassName)).click();
        // wait for context menu to disappear
        await().ignoreExceptions().pollDelay(100, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> !contextMenuLogicalTree.isDisplayed());
    }

    /**
     * Check if a structure tree node is marked as "assigned several times".
     * 
     * @param nodeId the tree node id describing the node in the tree (e.g., "0_1_0_1")
     * @return true if "assigned several times"
     */
    public Boolean isStructureTreeNodeAssignedSeveralTimes(String nodeId) {
        return !Browser.getDriver().findElements(By.cssSelector(
            "#logicalTree\\:" + nodeId + " .assigned-several-times"
        )).isEmpty();
    }

    /*
     * Open detail view by clicking on image preview button.
     */
    public void openDetailView() {
        imagePreviewButton.click();
    }

    /**
     * Create new structure element and return corresponding WebElement.
     *
     * @return WebElement representing newly created structure element
     * @throws Exception when page navigation fails
     */
    public WebElement createStructureElement() throws Exception {
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id(LOGICAL_TREE))::isDisplayed);
        WebElement structureTree = Browser.getDriver().findElement(By.id(LOGICAL_TREE));
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
                        .findElement(By.id(SAVE_AND_EXIT_BUTTON_ID))::isDisplayed);
        WebElement contextMenu = Browser.getDriver().findElement(By.id("contextMenuLogicalTree"));
        List<WebElement> menuItems = contextMenu.findElements(By.className("ui-menuitem"));
        assertEquals(3, menuItems.size(), "Wrong number of context menu items");
        // click "add element" option
        menuItems.get(0).click();
        // open "structure element type selection" menu
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection"), 1000, 1000, 5);
        // click first option
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection_1"), 1000, 500, 3);
        WebDriverWait wait = new WebDriverWait(Browser.getDriver(), 3);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("dialogAddDocStrucTypeForm:docStructAddTypeSelection_1")));
        // add structure element with selected type by clicking "accept"/"apply" button
        Thread.sleep(1000);
        clickItemWhenDisplayed(By.id("dialogAddDocStrucTypeForm:addDocStruc"), 500, 500, 5);
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id(SAVE_AND_EXIT_BUTTON_ID))::isEnabled);
        structureTree = Browser.getDriver().findElement(By.id(LOGICAL_TREE));
        return structureTree.findElement(By.id(LOGICAL_TREE + ":0_0"));
    }

    /**
     * Perform drag'n'drop action and return thumbnail overlay of target page as WebElement.
     * @param dropPositionId ID of html element where dragged element should be dropped
     * @param targetId ID of html dragged element when drag'n'drop operation finished
     * @param expectedOverlayText String containing expected overlay text of page to move via drag'n'drop
     * @return WebElement containing thumbnail of target page.
     * @throws Exception when page navigation fails
     */
    public WebElement performDragAndDrop(String dropPositionId, String targetId, String expectedOverlayText)
            throws Exception {
        WebElement unstructuredMedia = Browser.getDriver().findElement(By.id("imagePreviewForm:unstructuredMedia"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(unstructuredMedia::isDisplayed);
        // first page in unstructured media
        WebElement firstThumbnail = Browser.getDriver()
                .findElement(By.id("imagePreviewForm:unstructuredMediaList:0:unstructuredMediaPanel"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(firstThumbnail::isDisplayed);
        // hover over second thumbnail to verify overlay text before drag'n'drop
        Actions hoverAction = new Actions(Browser.getDriver());
        WebElement secondThumbnail = Browser.getDriver()
                .findElement(By.id("imagePreviewForm:unstructuredMediaList:1:unstructuredMediaPanel"));
        hoverAction.moveToElement(secondThumbnail).build().perform();
        WebElement thumbnailOverlay = secondThumbnail.findElement(By.className("thumbnail-overlay"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(thumbnailOverlay::isDisplayed);
        assertEquals(expectedOverlayText, thumbnailOverlay.getText().strip(), "Last thumbnail has wrong overlay before drag'n'drop action");
        // drop position for drag'n'drop action
        WebElement dropPosition = Browser.getDriver()
                .findElement(By.id(dropPositionId));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.SECONDS)
                .until(dropPosition::isDisplayed);
        // drag'n'drop action
        Actions dragAndDropAction = new Actions(Browser.getDriver());
        dragAndDropAction.dragAndDrop(firstThumbnail, dropPosition).build().perform();
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(Browser.getDriver().findElement(By.id(SAVE_AND_EXIT_BUTTON_ID))::isEnabled);
        Pages.getMetadataEditorPage().saveAndExit();
        // check whether new position has been saved correctly
        Pages.getProcessesPage().goTo().editMetadata(MockDatabase.DRAG_N_DROP_TEST_PROCESS_TITLE);
        secondThumbnail = Browser.getDriver().findElement(By.id(targetId));
        hoverAction.moveToElement(secondThumbnail).build().perform();
        thumbnailOverlay = secondThumbnail.findElement(By.className("thumbnail-overlay"));
        await().ignoreExceptions().pollDelay(300, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(thumbnailOverlay::isDisplayed);
        return thumbnailOverlay;
    }

    private void clickItemWhenDisplayed(By selector, long delay, long intervall, long timeout) {
        await().ignoreExceptions().pollDelay(delay, TimeUnit.MILLISECONDS).pollInterval(intervall, TimeUnit.MILLISECONDS)
                .atMost(timeout, TimeUnit.SECONDS).until(Browser.getDriver().findElement(selector)::isDisplayed);
        Browser.getDriver().findElement(selector).click();
    }
}
