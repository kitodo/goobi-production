package org.kitodo.selenium;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URI;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.WorkflowService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.selenium.testframework.BaseTestSelenium;
import org.kitodo.selenium.testframework.Pages;
import org.kitodo.selenium.testframework.pages.SystemPage;
import org.kitodo.selenium.testframework.pages.WorkflowEditPage;

public class MigrationST extends BaseTestSelenium {
    @Before
    public void login() throws Exception {
        Pages.getLoginPage().goTo().performLoginAsAdmin();
    }

    @After
    public void logout() throws Exception {
        FileService fileService = ServiceManager.getFileService();
        String diagramDirectory = ConfigCore.getKitodoDiagramDirectory();
        URI svgDiagramURI = new File(diagramDirectory + "FinishedClosedProgressOpenLocked.svg").toURI();
        URI xmlDiagramURI = new File(diagramDirectory + "FinishedClosedProgressOpenLocked.bpmn20.xml").toURI();
        fileService.delete(svgDiagramURI);
        fileService.delete(xmlDiagramURI);
        Pages.getTopNavigation().logout();
    }

    @Test
    public void testMigration() throws Exception {
        SystemPage systemPage = Pages.getSystemPage().goTo();
        long processTemplateId = ServiceManager.getProcessService().getById(1).getTemplate().getId();

        Assert.assertEquals("wrong template", 1, processTemplateId);
        systemPage.startWorkflowMigration();
        systemPage.selectProjects();
        Assert.assertEquals("FinishedClosedProgressOpenLocked", systemPage.getAggregatedTasks(3));
        WorkflowEditPage workflowEditPage = systemPage.createNewWorkflow();
        workflowEditPage.changeWorkflowStatusToActive();
        Assert.assertEquals("FinishedClosedProgressOpenLocked", workflowEditPage.getWorkflowTitle());
        systemPage = workflowEditPage.saveForMigration();
        String newTemplateTitle = "newTemplate";
        systemPage.createNewTemplateFromPopup(newTemplateTitle);
        await().untilAsserted(() -> Assert.assertEquals("template of process should have changed", 4,(long) ServiceManager.getProcessService().getById(1).getTemplate().getId()));
        WorkflowService workflowService = ServiceManager.getWorkflowService();
        Workflow workflow = workflowService.getById(4);
        final long numberOfTemplates = workflow.getTemplates().size();
        final long workflowTemplateId = workflow.getTemplates().get(0).getId();
        String processTemplateTitle = ServiceManager.getProcessService().getById(1).getTemplate().getTitle();

        Assert.assertEquals("only one template should be assigned", 1, numberOfTemplates);
        Assert.assertEquals("wrong template", 4, workflowTemplateId);
        Assert.assertEquals("wrong title for template", newTemplateTitle, processTemplateTitle);
    }
}
