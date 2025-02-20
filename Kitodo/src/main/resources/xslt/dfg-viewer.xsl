<?xml version="1.0" encoding="utf-8"?>
<!--
 *
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 *
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:mets="http://www.loc.gov/METS/"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:mods="http://www.loc.gov/mods/v3"
                xmlns:dv="http://dfg-viewer.de/"
                xmlns:kitodo="http://meta.kitodo.org/v1/">
    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- mets:mets -->
    <xsl:template match="mets:mets">
        <mets:mets>
            <xsl:apply-templates select="@* | node()"/>
        </mets:mets>
    </xsl:template>

    <!-- mets:metsHdr -->
    <xsl:template match="mets:metsHdr">
        <mets:metsHdr>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="node()"/>
        </mets:metsHdr>
    </xsl:template>

    <!-- mets:fileSec -->
    <xsl:template match="mets:fileSec">
        <mets:metsHdr>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="node()"/>
        </mets:metsHdr>
    </xsl:template>

    <!-- mets:structMap -->
    <xsl:template match="mets:structMap">
        <mets:structMap>
            <xsl:copy-of select="@*"/>
            <!-- TYPE='physSequence' in the physical root div is required by Kitodo.Presentation -->
            <xsl:for-each select="mets:div">
                <xsl:element name="mets:div">
                    <xsl:attribute name="TYPE">physSequence</xsl:attribute>
                    <xsl:copy-of select="@*"/>
                    <xsl:copy-of select="node()"/>
                </xsl:element>
            </xsl:for-each>
        </mets:structMap>
    </xsl:template>

    <!-- sets TYPE='month' and TYPE='day' in the newspaper issue process, and
         adds the month's date -->
    <xsl:template match="mets:div[mets:div/mets:div[@TYPE='issue' and not(mets:mptr)]]">
        <xsl:copy>
            <xsl:apply-templates select="@*[name()!='TYPE']"/>
            <xsl:attribute name="TYPE">month</xsl:attribute>
            <xsl:attribute name="ORDERLABEL"><xsl:value-of select="substring(mets:div/@ORDERLABEL,1,7)"/></xsl:attribute>
            <mets:div>
                <xsl:attribute name="TYPE">day</xsl:attribute>
                <xsl:apply-templates select="mets:div/@*[name()!='TYPE'] | mets:div/node()"/>
            </mets:div>
        </xsl:copy>
    </xsl:template>

    <!-- mets:structLink -->
    <xsl:template match="mets:structLink">
        <mets:structLink>
            <xsl:copy-of select="@*"/>
            <xsl:copy-of select="node()"/>
        </mets:structLink>
    </xsl:template>

    <!-- mets:amdSec -->
    <xsl:template match="mets:amdSec">
        <mets:amdSec ID="AMD">
            <!-- mets:rightsMD -->
            <xsl:if test=".//@name='ownerSiteURL' or .//@name='owner' or .//@name='ownerLogo' or .//@name='ownerContact'">
                <mets:rightsMD ID="RIGHTS">
                    <mets:mdWrap MDTYPE="OTHER" MIMETYPE="text/xml" OTHERMDTYPE="DVRIGHTS">
                        <mets:xmlData>
                            <dv:rights>
                                <xsl:for-each select=".//kitodo:metadata[@name]">
                                    <xsl:element name="dv:{@name}"><xsl:value-of select="./text()"/></xsl:element>
                                </xsl:for-each>
                            </dv:rights>
                        </mets:xmlData>
                    </mets:mdWrap>
                </mets:rightsMD>
            </xsl:if>
            <!-- mets:digiprovMD -->
            <xsl:if test=".//@name='presentation' or .//@name='reference'">
                <mets:digiprovMD ID="DIGIPROV">
                    <mets:mdWrap MDTYPE="OTHER" MIMETYPE="text/xml" OTHERMDTYPE="DVLINKS">
                        <mets:xmlData>
                            <dv:links>
                                <xsl:for-each select=".//kitodo:metadata[@name]">
                                    <xsl:element name="dv:{@name}"><xsl:value-of select="./text()"/></xsl:element>
                                </xsl:for-each>
                            </dv:links>
                        </mets:xmlData>
                    </mets:mdWrap>
                </mets:digiprovMD>
            </xsl:if>
        </mets:amdSec>
    </xsl:template>

    <!-- mets:dmdSec -->
    <xsl:template match="mets:dmdSec">
        <mets:dmdSec>
            <xsl:copy-of select="@*"/>
            <mets:mdWrap MDTYPE="MODS">
                <mets:xmlData>
                    <mods:mods xmlns:mods="http://www.loc.gov/mods/v3">
                        <xsl:apply-templates />
                    </mods:mods>
                </mets:xmlData>
            </mets:mdWrap>
        </mets:dmdSec>
    </xsl:template>

    <!-- kitodo:metadata -->
    <xsl:template match="kitodo:metadata">
        <xsl:element name="mods:{@name}"><xsl:value-of select="normalize-space()"/></xsl:element>
    </xsl:template>

    <!-- kitodo:metadataGroup -->
    <xsl:template match="kitodo:metadataGroup">
        <xsl:element name="mods:{@name}">
            <xsl:if test="child::*[@name='value']">
                <xsl:for-each select="child::*">
                    <xsl:if test="./@name!='value'">
                        <xsl:attribute name="{./@name}"><xsl:value-of select="./text()"/></xsl:attribute>
                    </xsl:if>
                </xsl:for-each>
                <xsl:value-of select="normalize-space(child::*[@name='value'])"/>
            </xsl:if>
            <xsl:if test="not(child::*[@name='value'])">
                <xsl:apply-templates />
            </xsl:if>
        </xsl:element>
    </xsl:template>

    <!-- delete unmapped text -->
    <xsl:template match="text()"/>

    <!-- delete XML Schema locations -->
    <xsl:template match="@xsi:schemaLocation"/>
</xsl:stylesheet>
