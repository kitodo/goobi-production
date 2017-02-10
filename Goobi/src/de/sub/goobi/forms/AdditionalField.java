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

package de.sub.goobi.forms;

import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.lang.StringUtils;

public class AdditionalField {
	private String titel;
	private String wert = "";
	private boolean required = false;
	private String from = "prozess";
	private List<SelectItem> selectList;
	private boolean ughbinding = false;
	private String docstruct;
	private String metadata;
	private String isdoctype = "";
	private String isnotdoctype = "";
	private String initStart = ""; // defined in kitodo_projects.xml
	private String initEnd = "";
	private boolean autogenerated = false;
	private ProzesskopieForm pkf;

	public AdditionalField(ProzesskopieForm inPkf) {
		this.pkf = inPkf;
	}

	public String getInitStart() {
		return this.initStart;
	}

	public void setInitStart(String newValue) {
		this.initStart = newValue;
		if (this.initStart == null) {
			this.initStart = "";
		}
		this.wert = this.initStart + this.wert;
	}

	public String getInitEnd() {
		return this.initEnd;
	}

	public void setInitEnd(String newValue) {
		this.initEnd = newValue;
		if (this.initEnd == null) {
			this.initEnd = "";
		}
		this.wert = this.wert + this.initEnd;
	}

	public String getTitel() {
		return this.titel;
	}

	public void setTitel(String titel) {
		this.titel = titel;
	}

	public String getWert() {
		return this.wert;
	}

	public void setWert(String newValue) {
		if (newValue == null || newValue.equals(this.initStart)) {
			newValue = "";
		}
		if (newValue.startsWith(this.initStart)) {
			this.wert = newValue + this.initEnd;
		} else {
			this.wert = this.initStart + newValue + this.initEnd;
		}
	}

	public String getFrom() {
		return this.from;
	}

	public void setFrom(String infrom) {
		if (infrom != null && infrom.length() != 0) {
			this.from = infrom;
		}
	}

	public List<SelectItem> getSelectList() {
		return this.selectList;
	}

	public void setSelectList(List<SelectItem> selectList) {
		this.selectList = selectList;
	}

	public boolean isRequired() {
		return this.required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public boolean isUghbinding() {
		return this.ughbinding;
	}

	public void setUghbinding(boolean ughbinding) {
		this.ughbinding = ughbinding;
	}

	public String getDocstruct() {
		return this.docstruct;
	}

	public void setDocstruct(String docstruct) {
		this.docstruct = docstruct;
		if (this.docstruct == null) {
			this.docstruct = "topstruct";
		}
	}

	public String getMetadata() {
		return this.metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	public String getIsdoctype() {
		return this.isdoctype;
	}

	public void setIsdoctype(String isdoctype) {
		this.isdoctype = isdoctype;
		if (this.isdoctype == null) {
			this.isdoctype = "";
		}
	}

	public String getIsnotdoctype() {
		return this.isnotdoctype;
	}

	public void setIsnotdoctype(String isnotdoctype) {
		this.isnotdoctype = isnotdoctype;
		if (this.isnotdoctype == null) {
			this.isnotdoctype = "";
		}
	}

	public boolean getShowDependingOnDoctype() {

		/* wenn nix angegeben wurde, dann anzeigen */
		if (this.isdoctype.equals("") && this.isnotdoctype.equals("")) {
			return true;
		}

		/* wenn pflicht angegeben wurde */
		if (!this.isdoctype.equals("") && !StringUtils.containsIgnoreCase(isdoctype, this.pkf.getDocType())) {
			return false;
		}

		/* wenn nur "darf nicht" angegeben wurde */
		if (!this.isnotdoctype.equals("") && StringUtils.containsIgnoreCase(isnotdoctype, this.pkf.getDocType())) {
			return false;
		}

		return true;
	}

	/**
	 * @param autogenerated the autogenerated to set
	 */
	public void setAutogenerated(boolean autogenerated) {
		this.autogenerated = autogenerated;
	}

	/**
	 * @return the autogenerated
	 */
	public boolean getAutogenerated() {
		return this.autogenerated;
	}
}

/* =============================================================== */
