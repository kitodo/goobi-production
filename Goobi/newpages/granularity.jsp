<%@ page session="false" contentType="text/html;charset=utf-8"%>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f"%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h"%>
<%@ taglib uri="http://jsftutorials.net/htmLib" prefix="htm"%>
<%@ taglib uri="http://myfaces.apache.org/tomahawk" prefix="t"%>
<%@ taglib uri="http://sourceforge.net/projects/jsf-comp/easysi"
	prefix="si"%>
<%-- 
	This file is part of the Goobi Application - a Workflow tool for the support
	of mass digitization.
	
	(c) 2013 Goobi. Digialisieren im Verein e.V. &lt;contact@goobi.org&gt;
	
	Visit the websites for more information.
	    		- http://www.goobi.org/en/
	    		- https://github.com/goobi
	
	This program is free software; you can redistribute it and/or modify it under
	the terms of the GNU General Public License as published by the Free Software
	Foundation; either version 2 of the License, or (at your option) any later
	version.
	
	This program is distributed in the hope that it will be useful, but WITHOUT
	ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
	FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
	details.
	
	You should have received a copy of the GNU General Public License along with
	this program; if not, write to the Free Software Foundation, Inc., 59 Temple
	Place, Suite 330, Boston, MA 02111-1307 USA
	
	Linking this library statically or dynamically with other modules is making a
	combined work based on this library. Thus, the terms and conditions of the
	GNU General Public License cover the whole combination. As a special
	exception, the copyright holders of this library give you permission to link
	this library with independent modules to produce an executable, regardless of
	the license terms of these independent modules, and to copy and distribute
	the resulting executable under terms of your choice, provided that you also
	meet, for each linked independent module, the terms and conditions of the
	license of that module. An independent module is a module which is not
	derived from or based on this library. If you modify this library, you may
	extend this exception to your version of the library, but you are not obliged
	to do so. If you do not wish to do so, delete this exception statement from
	your version.
--%>

<%--  Granularity selector for multiple process generation --%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<f:view locale="#{SpracheForm.locale}">
	<%@include file="/newpages/inc/head.jsp"%>
	<body>
		<script type="text/javascript">
			
		<%--
		 * The function numberOfPagesValid() validates content of the form field
		 * numberOfPages to make sure it consists of digits only.
		 * 
		 * @return whether the title data is valid
		 --%>
			function numberOfPagesValid() {
				if (!document.getElementById("form1:numberOfPages").value
						.match(/^\d+$/)) {
					alert("${msgs['granularity.numberOfPages.invalid']}");
					document.getElementById("form1:numberOfPages").focus();
					return false;
				}
				if (!document.getElementById("form1:lastAppearance").value
						.match(/^[0-3]\d\.[01]\d.\d{4}$/)) {
					alert("${msgs['calendar.title.lastAppearance.invalid']}");
					document.getElementById("form1:lastAppearance").focus();
					return false;
				}
				return true;
			}
		<%--
		 * The function showApplyLink() shows a link to apply an entered value.
		 * 
		 * @return always true
		 --%>
			function showApplyLink() {
				document.getElementById("form1:applyLink").style.display = "inline";
				return true;
			}
		</script>
		<htm:table cellspacing="5" cellpadding="0" styleClass="layoutTable"
			align="center">
			<%@include file="/newpages/inc/tbl_Kopf.jsp"%>
			<htm:tr>
				<%@include file="/newpages/inc/tbl_Navigation.jsp"%>
				<htm:td valign="top" styleClass="layoutInhalt">

					<%-- ===================== Page main frame ===================== --%>

					<h:form id="form1" onsubmit="return numberOfPagesValid()">

						<%-- Bread crumbs --%>

						<h:panelGrid width="100%" columns="1"
							styleClass="layoutInhaltKopf">
							<h:panelGroup>
								<h:commandLink value="#{msgs.startseite}" action="newMain" />
								<f:verbatim> &#8250;&#8250; </f:verbatim>
								<h:commandLink value="#{msgs.prozessverwaltung}"
									action="ProzessverwaltungAlle" />
								<f:verbatim> &#8250;&#8250; </f:verbatim>
								<h:commandLink value="#{msgs.einenNeuenProzessAnlegen}"
									action="#{ProzesskopieForm.GoToSeite1}" />
								<f:verbatim> &#8250;&#8250; </f:verbatim>
								<h:commandLink value="#{msgs['calendar.header']}"
									action="ShowCalendarEditor" />
								<f:verbatim> &#8250;&#8250; </f:verbatim>
								<h:outputText value="#{msgs['granularity.header']}" />
							</h:panelGroup>
						</h:panelGrid>

						<htm:table border="0" align="center" width="100%" cellpadding="15">
							<htm:tr>
								<htm:td>
									<htm:h3>
										<h:outputText value="#{msgs['granularity.header']}" />
									</htm:h3>

									<%-- Global warnings and error messages --%>

									<h:messages globalOnly="true" errorClass="text_red"
										infoClass="text_blue" showDetail="true" showSummary="true"
										tooltip="true" />

									<%-- ===================== Page main content ====================== --%>

									<htm:fieldset>
										<htm:div>
											<h:outputText value="#{msgs['granularity.issueCount']} " />
											<h:outputText value="#{GranularityForm.issueCount}">
												<f:convertNumber/>
											</h:outputText>
										</htm:div>
										<htm:div>
											<h:outputLabel for="numberOfPages"
												value="#{msgs['granularity.numberOfPages']}"
												styleClass="fullWideLabel" />
											<h:commandLink value="#{msgs['granularity.apply']}"
												id="applyLink" styleClass="deleteIssue"
												style="display: none;" />
											<htm:span styleClass="fullWideBox">
												<h:inputText value="#{GranularityForm.numberOfPages}"
													id="numberOfPages" onkeydown="showApplyLink();"
													styleClass="fullWideInput" />
											</htm:span>
										</htm:div>

									</htm:fieldset>

									<%-- ===================== End page main content ====================== --%>

								</htm:td>
							</htm:tr>
						</htm:table>
					</h:form>

					<%-- ===================== End page main frame ===================== --%>

				</htm:td>
			</htm:tr>
			<%@include file="/newpages/inc/tbl_Fuss.jsp"%>
		</htm:table>
	</body>
</f:view>
</html>
