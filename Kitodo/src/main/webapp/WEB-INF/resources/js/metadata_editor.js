/**
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */
/* globals sendGallerySelect, setGalleryViewMode, destruct, initialize, scrollToSelectedThumbnail, changeToMapView, PF,
   scrollToStructureThumbnail, scrollToPreviewThumbnail, expandMetadata, preserveMetadata,
   activateButtons, PF, remoteCommandSetMembersByRequestParameter */
/*eslint new-cap: ["error", { "capIsNewExceptionPattern": "^PF" }]*/
/*eslint complexity: ["error", 10]*/

var metadataEditor = metadataEditor || {};

metadataEditor.metadataTree = {

    /**
     * Key down event for all inputText elements in the metadata tree.
     * If the key is "enter", handle it like a "tab"-event.
     * @param event the corresponding key down event
     * @param element the corresponding element
     */
    handleKeyDown(event, element) {
        // Check if the pressed key is 'Enter'
        if (event.keyCode === 13 || event.key === "Enter") {
            event.preventDefault();
            let form = element.form;
            let focusableElements = Array.from(form.elements).filter((element) => {
                return element.tabIndex >= 0 && !element.disabled;
            });
            let index = focusableElements.indexOf(element);
            let nextInput = focusableElements[index + 1];
            if (nextInput) {
                // Focus on the next input element
                nextInput.focus();
            }
        }
    },

};

/**
 * Methods and events related to the gallery section of the meta data editor.
 */
metadataEditor.gallery = {

    /** 
     * Mouse down event for all of the gallery, including both stripes and thumbnails.
     * 
     * @param event the corresponding mouse event
     */
    handleMouseDown(event) {
        $(document.activeElement).blur();
        let target = $(event.target);
        if (target.closest(".stripe").length === 1) {
            this.stripes.handleMouseDown(event);
        } else if (target.closest(".thumbnail-container").length === 1) {
            this.pages.handleMouseDown(event, target.closest(".thumbnail-container"));
        }
        // set focus for gallery (used for keyboard shortcuts)
        $(".focusable").removeClass("focused");
        $("#imagePreviewForm").addClass("focused");
    },

    /**
     * Handler for moues up event for all of the gallery, including both stripes and thumbnails.
     * 
     * @param event the corresponding mouse event
     */
    handleMouseUp(event) {
        let target = $(event.target);
        if (target.closest(".thumbnail-container").length === 1) {
            this.pages.handleMouseUp(event, target.closest(".thumbnail-container"));
        }
    },

    /**
     * Handler for drag start event for any element in the gallery.
     * 
     * @param event the standard drag start event
     */
    handleDragStart(event) {
        // call thumbnail drag start handler, since only thumbnails are draggable
        this.pages.handleDragStart(event);
    },

    mediaPartial: {
        addLeadingZeros(num, totalLength) {
            return String(num).padStart(totalLength, '0');
        },
        addTrailingZeros(num, totalLength) {
            return String(num).padEnd(totalLength, '0');
        },
        truncWithoutRound(number, decimal) {
            return Math.trunc(number * Math.pow(10, decimal)) / Math.pow(10, decimal);
        },
        convertSecondsToFormattedTime(seconds) {
            let hours = parseInt(parseInt(seconds) / 3600); // 3,600 seconds in 1 hour
            seconds = seconds % 3600; // seconds remaining after extracting hours
            let minutes = parseInt(seconds / 60); // 60 seconds in 1 minute
            seconds = seconds % 60;
            let formattedTime = this.addLeadingZeros(hours, 2) + ":" + this.addLeadingZeros(minutes, 2) + ":";
            seconds = this.truncWithoutRound(Number.parseFloat(seconds), 3); // trunc remaining digits without rounding
            seconds = seconds.toString();
            if (seconds.includes(".")) {
                let splittedSeconds = seconds.split(".");
                formattedTime += this.addLeadingZeros(splittedSeconds[0], 2) + "." + this.addTrailingZeros(splittedSeconds[1], 3);
            } else {
                formattedTime += this.addLeadingZeros(seconds, 2) + ".000";
            }
            return formattedTime;
        },
        convertFormattedTimeToMilliseconds(formattedTime) {
            let milliseconds = 0;
            if (formattedTime.includes(".")) {
                let splittedFormattedTime = formattedTime.split(".");
                formattedTime = splittedFormattedTime[0];
                milliseconds = parseInt(splittedFormattedTime[1]);
            }
            let time = formattedTime.split(":");
            return ((+time[0]) * 60 * 60 + (+time[1]) * 60 + (+time[2]))  * 1000 + milliseconds;
        },
        setBeginIfEmpty() {
            let begin = document.getElementById("mediaPartialForm:beginInput");
            if(!begin.value) {
                begin.value = this.convertSecondsToFormattedTime(document.querySelector('#imagePreviewForm\\:mediaDetailMediaContainer video, #imagePreviewForm\\:mediaDetailMediaContainer audio').currentTime);
            }
        },
        setMediaDuration() {
            let mediaDuration = this.convertSecondsToFormattedTime(document.querySelector('#imagePreviewForm\\:mediaDetailMediaContainer video, #imagePreviewForm\\:mediaDetailMediaContainer audio').duration);
            remoteCommandSetMembersByRequestParameter([{name: "mediaDuration", value: mediaDuration}]);
        },
        stopPlayEvent: new CustomEvent("mediaPartialStopPlay"),
        togglePlay(button, formattedTimeBegin, formattedTimeExtent) {
            let mediaElement = document.querySelector('#imagePreviewForm\\:mediaDetailMediaContainer video, #imagePreviewForm\\:mediaDetailMediaContainer audio');
            let timerElement = document.querySelector('#imagePreviewForm\\:mediaDetailMediaContainer #mediaFormattedTime');
            let isPaused = mediaElement.paused;
            let isAnotherMediaPartialTimeBegin = formattedTimeBegin !== mediaElement.dataset.mediaPartialTimeBegin;
            mediaElement.dispatchEvent(this.stopPlayEvent); // stop already running media partial

            let startTime = this.convertFormattedTimeToMilliseconds(formattedTimeBegin);
            let durationTime = this.convertFormattedTimeToMilliseconds(formattedTimeExtent);
            let stopTime = (startTime + durationTime) / 1000;

            if (isPaused || isAnotherMediaPartialTimeBegin) {
                let self = this;
                let icon = button.querySelector(".ui-icon");
                icon.classList.remove("fa-play");
                icon.classList.add("fa-stop");

                let onTimeUpdate = function () {
                    if (mediaElement.currentTime >= stopTime) {
                        self.stopPlay(mediaElement, icon);
                        mediaElement.removeEventListener("timeupdate", onTimeUpdate);
                        mediaElement.currentTime = stopTime;
                    }
                    timerElement.innerHTML = self.convertSecondsToFormattedTime(mediaElement.currentTime);
                };

                let onMediaPartialStopPlay = function () {
                    self.stopPlay(mediaElement, icon);
                    mediaElement.removeEventListener("timeupdate", onTimeUpdate);
                    timerElement.innerHTML = self.convertSecondsToFormattedTime(mediaElement.currentTime);
                };

                mediaElement.currentTime = startTime / 1000;
                mediaElement.addEventListener("timeupdate", onTimeUpdate);
                mediaElement.addEventListener("mediaPartialStopPlay", onMediaPartialStopPlay);
                mediaElement.addEventListener("pause", onMediaPartialStopPlay);
                mediaElement.dataset.mediaPartialTimeBegin = formattedTimeBegin;
                mediaElement.play();
            }
        },
        stopPlay(mediaElement, icon) {
            mediaElement.pause();
            delete mediaElement.dataset.mediaPartialTimeBegin;
            icon.classList.remove("fa-stop");
            icon.classList.add("fa-play");
        }
    },

    /**
     * Event handlers and methods related to individual pages or thumbnails.
     */
    pages: {

        /**
         * Remember the current selection and its order by keeping a list of logical tree node ids.
         */
        currentSelection: [],

        /**
         * Remembers the logical tree node id for the thumbnail that was last selected by the user.
         */
        lastSelectedItem: null,

        /**
         * Populates currentSelection and lastSelectedItem when meta data editor is started.
         */
        init() {
            this.currentSelection = this.findSelectedTreeNodeIds();
            this.lastSelectedItem = this.findLastSelectedTreeNodeId();
        },

        /**
         * Handler for a mouse down event if it specially occurred above a thumbnail.
         * 
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object
         */
        handleMouseDown(event, target) {
            if (target.closest(".thumbnail-parent").find(".selected").length === 0) {
                // do not trigger selection, if thumbnail was previously selected, such that 
                // drag and drop for multiple selected thumbnails is possible
                // otherwise, this event would select this thumbnail as only selection before dragging starts
                this.handleSelect(event, target);
            }
        },

        /**
         * Handler for a mouse up event if it specially occurred above a thumbnail.
         * 
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object
         */
        handleMouseUp(event, target) {
            metadataEditor.gallery.dragdrop.removeDragAmountIcon();
            if (metadataEditor.gallery.dragdrop.dragging) {
                metadataEditor.gallery.dragdrop.dragging = false;
            } else if (event.button !== 2 || target.closest(".thumbnail-parent").find(".selected").length === 0) {
                this.handleSelect(event, target);
            }
        },

        /**
         * Handler that is called when dragging of a thumbnail starts.
         * 
         * @param event the drag start event
         */
        handleDragStart(event) {
            metadataEditor.gallery.dragdrop.dragging = true;
            metadataEditor.gallery.dragdrop.addDragAmountIcon(event);
        },

        /**
         * Is called when a thumbnail is selected (via click).
         * 
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object
         */
        handleSelect(event, target) {
            // get the logical tree node id of the thumbnail that is being selected
            let treeNodeId = target[0].dataset.logicaltreenodeid;

            // decide on type of selection (single, range, multi) depending on keyboard input
            if (event.metaKey || event.ctrlKey) {
                this.handleMultiSelect(event, target, treeNodeId);
            } else if (event.shiftKey) {
                this.handleRangeSelect(event, target, treeNodeId);
            } else {
                this.handleSingleSelect(event, target, treeNodeId);
            }

            this.handleSelectionUpdates(this.currentSelection);
        },

        /**
         * Updates other components of the meta data editor after changes to the current gallery selection.
         * 
         * @param treeNodeIds the list of treeNodeIds of selected thumbnails
         */
        handleSelectionUpdates(treeNodeIds) {
            // update selection in other components of the meta data editor
            metadataEditor.pagination.markManyAsSelected(treeNodeIds);
            metadataEditor.logicalTree.markManyAsSelected(treeNodeIds);
            metadataEditor.gallery.stripes.markManyAsSelected(treeNodeIds);
            if (metadataEditor.physicalTree.isAvailable()) {
                metadataEditor.physicalTree.markManyAsSelected(treeNodeIds);
            }
        },

        /**
         * Adds or removes single thumbnails to the current selection using the multi seletion style (ctrl key).
         *
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object 
         * @param treeNodeId the logical tree node id of the clicked thumbnail
         */
        handleMultiSelect(event, target, treeNodeId) {
            if (this.isThumbnailSelected(treeNodeId)) {
                if (this.currentSelection.length > 1) {
                    // there are more elements still selected, remove it from list
                    let idx = this.currentSelection.indexOf(treeNodeId);
                    if (idx > -1) {
                        this.currentSelection.splice(idx, 1);
                    } else {
                        // deselecting thumbnail that is not listed in currentSelection array
                        // should not happen
                    }
                    // make last added thumbnail as last selected
                    this.markManyAsSelected(this.currentSelection, null);
                } else {
                    // keep current element selected
                    this.markManyAsSelected(this.currentSelection, treeNodeId);
                }  
            } else {
                // just add the thumbnail to the current selection
                this.currentSelection.push(treeNodeId);
                this.markManyAsSelected(this.currentSelection, treeNodeId);
            }
            metadataEditor.gallery.sendSelectionToBackend(target[0].dataset.order, target[0].dataset.stripe, "multi", event);
        },

        /**
         * Adds thumbnails to the set of selected thumbnails using the range selection style (shift key).
         * 
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object 
         * @param treeNodeId the logical tree node id of the clicked thumbnail
         */
        handleRangeSelect(event, target, treeNodeId) {
            if (this.currentSelection.length === 0) {
                // there is no prior selected item that can be used to determine the start point
                // of this range selection
                return;
            }
            let that = this;
            let fromTreeNodeId = this.currentSelection[0];
            let fromThumbnail = this.findThumbnailByTreeNodeId(fromTreeNodeId);
            let toTreeNodeId = treeNodeId;
            let toThumbnail = this.findThumbnailByTreeNodeId(toTreeNodeId);
                        
            // iterate over all thumbnails in the order they appear in the dom
            let inBetweenSelectedThumbnails = false;
            let forwardSelection = true;
            let treeNodeIds = [];
            $("#imagePreviewForm .thumbnail").each(function () {
                let currentThumbnail = $(this);
                let currentTreeNodeId = that.findTreeNodeIdByThumbnail(currentThumbnail);
                if (inBetweenSelectedThumbnails) {
                    if (forwardSelection) {
                        treeNodeIds.push(currentTreeNodeId);
                    } else {
                        treeNodeIds.unshift(currentTreeNodeId);
                    }
                }
                if (!inBetweenSelectedThumbnails && (currentThumbnail.is(fromThumbnail) || currentThumbnail.is(toThumbnail))) {
                    if (currentThumbnail.is(fromThumbnail)) {
                        forwardSelection = true;
                        treeNodeIds.push(currentTreeNodeId);
                    } else {
                        forwardSelection = false;
                        treeNodeIds.unshift(currentTreeNodeId);
                    }
                    inBetweenSelectedThumbnails = true;
                    return true;
                }
                if (inBetweenSelectedThumbnails && (currentThumbnail.is(fromThumbnail) || currentThumbnail.is(toThumbnail))) {
                    inBetweenSelectedThumbnails = false;
                    // stop iterating through thumbnails
                    return false;
                }
            });

            // update selection state
            this.lastSelectedItem = toTreeNodeId;
            this.markManyAsSelected(treeNodeIds, toTreeNodeId);
            this.updateDiscontinuousSelectionState();
            metadataEditor.gallery.sendSelectionToBackend(target[0].dataset.order, target[0].dataset.stripe, "range", event);
        },

        /**
         * Overwrites current selection to this single thumbnail only.
         * 
         * @param event the mouse event
         * @param target the thumbnail-container dom element of the clicked thumbnail as jquery object 
         * @param treeNodeId the logical tree node id of the clicked thumbnail
         */
        handleSingleSelect(event, target, treeNodeId) {
            this.markManyAsSelected([treeNodeId], treeNodeId);
            metadataEditor.gallery.sendSelectionToBackend(target[0].dataset.order, target[0].dataset.stripe, "default", event);
        },

        /**
         * Check if thumbnail is currently selected
         * @param treeNodeId the logical tree node id referencing the thumbnail
         * @returns true or false
         */
        isThumbnailSelected(treeNodeId) {
            return this.findThumbnailByTreeNodeId(treeNodeId).hasClass("selected");
        },

        /**
         * Finds a thumbnail dom element based on a given logical tree node id.
         * @param treeNodeId the tree node id as string
         * @returns the thumbnail dom element as jquery object
         */
        findThumbnailByTreeNodeId(treeNodeId) {
            return $("#imagePreviewForm .thumbnail-container[data-logicaltreenodeid=\"" + treeNodeId + "\"]").prev();
        },

        /**
         * Finds a logical tree node id given a jquery object referencing a thumbnail.
         * 
         * @param thumbnail the thumbnail
         * @returns the corresponding logical tree node id
         */
        findTreeNodeIdByThumbnail(thumbnail) {
            return thumbnail.next()[0].dataset.logicaltreenodeid;
        },

        /**
         * Finds the order-value for a thumbnail given a logical tree node id identifying the corresponding thumbnail.
         * @param treeNodeId the tree node id as string
         * @returns the corresponding order value attached to the thumbnail-container dom element
         */
        findOrderByTreeNodeId(treeNodeId) {
            let div = $("#imagePreviewForm .thumbnail-container[data-logicaltreenodeid=\"" + treeNodeId + "\"]");
            if (div.length > 0) {
                return div[0].dataset.order;
            }
            return null;
        },

        /**
         * Finds a logical tree node id for a given order-value identifying the corresponding thumbnail.
         * @param order the order-value
         * @returns the corresponding logical tree node id attached to the thumbnail-container dom element
         */
        findTreeNodeIdByOrder(order) {
            let div = $("#imagePreviewForm .thumbnail-container[data-order=\"" + order + "\"]");
            if (div.length > 0) {
                return div[0].dataset.logicaltreenodeid;
            }
            return null;
        },

        /**
         * Find all logical tree node ids for a list of order-values.
         * @param orderList the list order values
         * @returns the correspoding tree node ids for each thumbnail identified by each order value
         */
        findTreeNodeIdsByOrderList(orderList) {
            let treeNodeIds = [];
            for (let i = 0; i < orderList.length; i++) {
                treeNodeIds.push(this.findTreeNodeIdByOrder(orderList[i]));
            }
            return treeNodeIds;
        },

        /**
         * Returns logical tree node ids for all thumbnails that are currently selected.
         * @returns the list of tree node ids
         */
        findSelectedTreeNodeIds() {
            let treeNodeIds = [];
            let that = this;
            $("#imagePreviewForm .thumbnail.selected").each(function () {
                treeNodeIds.push(that.findTreeNodeIdByThumbnail($(this)));
            });
            return treeNodeIds;
        },

        /**
         * Returns the logical tree node id for the thumbnail that is currently marked as last-selected.
         */
        findLastSelectedTreeNodeId() {
            let thumbnail = $("#imagePreviewForm .thumbnail.last-selection");
            if (thumbnail.lenght > 0) {
                return this.findTreeNodeIdByThumbnail(thumbnail);
            }
            return null;
        },

        /**
         * Marks a list of thumbnails as the currently selected thumbnails by applying the corresponding CSS styles.
         * 
         * @param treeNodeIds the logical tree node ids identifying the newly selected thumbnails
         * @param lastSelected the logical tree node id identifying the thumbnail that has been selected last by the user
         */
        markManyAsSelected(treeNodeIds, lastSelected) {
            this.resetSelectionStyle();
            this.currentSelection = treeNodeIds;
            if (lastSelected === null && treeNodeIds.length > 0) {
                // use last known selected element as last selected
                this.lastSelectedItem = treeNodeIds[treeNodeIds.length - 1];
            } else {
                // use provided tree node is as last selected
                this.lastSelectedItem = lastSelected;
            }
            
            // keep previous selection but remove last-selection class
            $("#imagePreviewForm .thumbnail.last-selection").removeClass("last-selection");
            
            // find gallery thumbnail corresponding to each treeNodeId and add selected status
            for(let i = 0; i < this.currentSelection.length; i++) {
                let thumbnail = this.findThumbnailByTreeNodeId(this.currentSelection[i]);
                thumbnail.addClass("selected");
            }

            if (this.lastSelectedItem !== null) {
                let thumbnail = this.findThumbnailByTreeNodeId(this.lastSelectedItem);
                thumbnail.addClass("last-selection");
            }

            this.updateDiscontinuousSelectionState();
        },

        /**
         * Preload the images in tooltip.
         *
         * To improve performance, the images within the tooltips are loaded only when the tooltip is displayed.
         * For the preview image, it makes no difference, as the image is already loaded. However, if the image
         * from the MediaView is to be displayed in the tooltip, it must be preloaded. Otherwise, the tooltip appears
         * at a shifted position because, at the time of display, it does not yet know the size of its content.
         */
        preloadTooltipImage(tooltip) {
            let tooltipElement = document.getElementById(tooltip.id);
            if (!tooltipElement.hasAttribute("data-preloaded")) {
                let tempImage = new Image();
                tempImage.src = tooltipElement.getElementsByTagName('img')[0].src;
                tempImage.onload = function () {
                    tooltipElement.setAttribute("data-preloaded", "true");
                    tooltipElement.getElementsByTagName('img')[0].height = tempImage.height;
                    PF(tooltip.cfg.widgetVar).show();
                }
                return false;
            }
            return true;
        },

        /**
         * Checks whether selection is a continuous selection and applies corresponding CSS style class.
         */
        updateDiscontinuousSelectionState() {
            // reset discontinuous state
            $("#imagePreviewForm .thumbnail.discontinuous").removeClass("discontinuous");

            // check whether all selected thumbnails are from the same stripe 
            let stripeTreeNodeIdSet = new Set();
            let treeNodeLastNumbers = [];
            $("#imagePreviewForm .thumbnail.selected").each(function () {
                let treeNodeId = $(this).next()[0].dataset.logicaltreenodeid;
                let stripeTreeNodeId = treeNodeId.slice(0, treeNodeId.lastIndexOf("_"));
                let treeNodeLastNumber = treeNodeId.slice(treeNodeId.lastIndexOf("_") + 1);
                stripeTreeNodeIdSet.add(stripeTreeNodeId);
                treeNodeLastNumbers.push(treeNodeLastNumber);
            });
            let minNumber = Math.min.apply(null, treeNodeLastNumbers);
            let maxNumber = Math.max.apply(null, treeNodeLastNumbers);
            
            // update discontinuous style class
            $("#imagePreviewForm .thumbnail.discontinuous").removeClass("discontinuous");
            if (stripeTreeNodeIdSet.size > 1 || (maxNumber - minNumber !== treeNodeLastNumbers.length - 1)) {
                // selection is not continuous
                $("#imagePreviewForm .thumbnail.selected").addClass("discontinuous");
            }
        },

        /**
         * Resets the selection CSS style of all thumbails.
         */
        resetSelectionStyle() {
            // remove current selection styling from thumbnails
            $("#imagePreviewForm .thumbnail.discontinuous").removeClass("discontinuous");
            $("#imagePreviewForm .thumbnail.selected").removeClass("selected");
            $("#imagePreviewForm .thumbnail.last-selection").removeClass("last-selection");
        }
    },

    mediaView: {
        updateEventName : "kitodo-metadataditor-mediaview-update",
        update(){
            document.dispatchEvent(new Event(this.updateEventName));
        }
    },

    /** 
     * Event handlers methods related to gallery stripes
     */
    stripes: {

        /**
         * Handler for mouse down event if it specially occurred above a gallery stripe header.
         * 
         * @param event the mouse event
         */
        handleMouseDown(event) {
            if (!$(event.target).hasClass("selected")) {
                // retrieve logical tree node id for stripe that is being clicked on
                let stripeTreeNodeId = $(event.target)[0].dataset.logicaltreenodeid;
                // mark corresponding node in logical tree as selected
                metadataEditor.logicalTree.markManyAsSelected([stripeTreeNodeId]);
                // reset selection of other panels
                metadataEditor.gallery.pages.resetSelectionStyle();
                metadataEditor.pagination.resetSelectionStyle();
                // mark gallery stripe as selected
                this.markOneSelected(stripeTreeNodeId);
                if (metadataEditor.physicalTree.isAvailable()) {
                    // mark first thumbnail as selected 
                    let treeNodeId = this.findFirstThumbnailLogicalTreeNodeId(stripeTreeNodeId);
                    metadataEditor.physicalTree.markManyAsSelected([treeNodeId]);
                    metadataEditor.gallery.pages.markManyAsSelected([treeNodeId], treeNodeId);
                    metadataEditor.pagination.markManyAsSelected([treeNodeId]);
                }
                // send new selection to backend
                metadataEditor.gallery.sendSelectionToBackend(null, event.target.dataset.stripe, "default", event);
            }
        },

        /**
         * Finds the first thumbnail in the gallery stripe matching the provided logical tree node id.
         * 
         * @param stripeTreeNodeId the tree node id of the stripe
         * @returns the thumbnail-container dom element of the first thumbnail in the stripe as jquery object
         */
        findFirstThumbnailLogicalTreeNodeId(stripeTreeNodeId) {
            // let stripe = $("#imagePreviewForm .stripe[data-logicaltreenodeid=\"" + stripeTreeNodeId + "\"]");
            let firstTreeNodeId = null;
            $("#imagePreviewForm .thumbnail-container").each(function() {
                let treeNodeId = this.dataset.logicaltreenodeid;
                let currentStripeTreeNodeId = treeNodeId.slice(0, treeNodeId.lastIndexOf("_"));
                if (currentStripeTreeNodeId === stripeTreeNodeId) {
                    firstTreeNodeId = treeNodeId;
                    return false;
                }
                return true;
            });
            return firstTreeNodeId;
        },

        /**
         * Marks exactly one stripe as selected by applying the corresponding CSS styling.
         * 
         * @param stripeTreeNodeId the logical tree node id of the stripe that is supposed to be selected
         */
        markOneSelected(stripeTreeNodeId) {
            this.resetSelectionStyle();
            let stripe = $("#imagePreviewForm .stripe[data-logicaltreenodeid=\"" + stripeTreeNodeId + "\"]");
            stripe.addClass("selected");
        },

        /**
         * Marks multiple stripes as selected by applying the corresponding CSS styling.
         * 
         * @param stripeTreeNodeIds the list of logical tree node id of all stripes that are supposed to be selected
         */
        markManyAsSelected(stripeTreeNodeIds) {
            this.resetSelectionStyle();
            for(let i = 0; i < stripeTreeNodeIds.length; i++) {
                let stripe = $("#imagePreviewForm .stripe[data-logicaltreenodeid=\"" + stripeTreeNodeIds[i] + "\"]");
                stripe.addClass("selected");
            }
        },

        /**
         * Resets the CSS selection style of all stripes of the gallery.
         */
        resetSelectionStyle() {
            $("#imagePreviewForm .stripe.selected").removeClass("selected");
        },
    },

    /** 
     * Handlers and methods managing the drag-&-drop behavior.
    */
    dragdrop: {
        /**
         * Whether drag-&-drop is currently ongoing.
         */
        dragging: false,

        /**
         * Adds a dom element to the currently dragged thumbnail visualizing the number of selected thumbnails.
         * 
         * @param event the drag start event
         */
        addDragAmountIcon(event) {
            var dragAmount = document.querySelectorAll(".thumbnail.selected").length;
            if (dragAmount > 1) {
                var element = document.createElement("div");
                element.id = "dragAmount";
                element.innerText = dragAmount;
                event.target.appendChild(element);
            }
        },

        /** 
         * Removes the dom element visualizing the number of currently selected thumbnails from the dragged thumbnail.
         */
        removeDragAmountIcon() {
            var element = document.getElementById("dragAmount");
            if (element !== null) {
                element.parentNode.removeChild(element);
            }
        }
    },

    /**
     * Forwards selection events to the backend by calling the corresponding Primefaces remoteCommand.
     * 
     * @param pageIndex the order-value of the clicked thumbnail (if it was clicked)
     * @param stripeIndex the stripe-data-value of the stripe that is clicked (if it was clicked)
     * @param selectionType the selecting type (range, multi, default)
     * @param triggerEvent the corresponding mouse event (if there was a mouse involved)
     */
    sendSelectionToBackend(pageIndex, stripeIndex, selectionType, triggerEvent = null) {
        // call the remoteCommand in gallery.xhtml
        if (triggerEvent == null) {
            sendGallerySelect([
                {name: "page", value: pageIndex},
                {name: "stripe", value: stripeIndex},
                {name: "selectionType", value: selectionType}
            ]);
        } else {
            sendGallerySelect([
                {name: "page", value: pageIndex},
                {name: "stripe", value: stripeIndex},
                {name: "selectionType", value: selectionType},
                {name: "pageX", value: triggerEvent.pageX},
                {name: "pageY", value: triggerEvent.pageY},
                {name: "triggerContextMenu", value: triggerEvent.button === 2}
            ]);
        }
    },
};

/**
 * Event handlers and methods related to the logical structure tree.
 */
metadataEditor.logicalTree = {

    /**
     * Remembers the logical tree node id for the node that was last selected by the user.
     */
    lastSelectedTreeNodeId: null,

    /**
     * Handler that is called by Primefaces when a tree node is clicked.
     * 
     * @param node the tree node that is clicked as jquery object
     * @param event the mouse event
     */
    onNodeClick(node, event) {
        // determine tree node id of the clicked node
        let currentTreeNodeId = this._getTreeNodeIdFromNode(node);

        if (!(event.metaKey || event.ctrlKey)) {
            // remove all previous selections when clicking without ctrl key or shift key
            this.resetSelectionStyle();
        }

        // mark current node as selected
        let mode = "add";
        if (!event.shiftKey) {
            if (node.hasClass("ui-treenode-selected") && this._findSelectedNodes().length > 1) {
                // remove selection if users clicks on node again while holding ctrl key and there are other selected nodes
                this._removeNodeFromSelection(node);
                mode = "remove";
            } else {
                // add node to selection
                this._addNodeToSelection(node);
            }
            // remember node for shift selection
            this.lastSelectedTreeNodeId = currentTreeNodeId;
        } else {
            // range select between lastSelectedTreeNodeId and currentTreeNodeId
            let fromNode = this._getNodeFromTreeNodeId(this.lastSelectedTreeNodeId);
            let toNode = this._getNodeFromTreeNodeId(currentTreeNodeId);
            this._addNodeToSelection(toNode);
            if (fromNode.parent().is(toNode.parent())) {
                // valid shift selection between siblings
                let firstNode = toNode.is(fromNode.nextAll()) ? fromNode : toNode;
                let secondNode = firstNode.is(fromNode) ? toNode : fromNode;
                if (!firstNode.is(secondNode)) {
                    let that = this;
                    firstNode.nextUntil(secondNode).each(function () {
                        that._addNodeToSelection($(this));
                    });
                    this._addNodeToSelection(firstNode);
                    this._addNodeToSelection(secondNode);
                }
            } else {
                // treat shift selection as single selection
                // remember node for next shift selection
                lastSelectedTreeNodeId = currentTreeNodeId;
            }
        }

        // find all selected nodes
        let that = this;
        let treeNodeIds = this._findSelectedNodes().toArray().map(function(node) {
            return that._getTreeNodeIdFromNode($(node));
        });
        
        metadataEditor.gallery.stripes.markManyAsSelected(treeNodeIds);
        metadataEditor.gallery.pages.markManyAsSelected(treeNodeIds, mode === "add" ? currentTreeNodeId: null);
        metadataEditor.pagination.markManyAsSelected(treeNodeIds);
        
        if (this === metadataEditor.logicalTree) {
            // update physical tree if this method was invoked from a logical tree event
            if (metadataEditor.physicalTree.isAvailable()) {
                metadataEditor.physicalTree.markManyAsSelected(treeNodeIds);
            }
        }
        if (this === metadataEditor.physicalTree) {
            // update logical tree selection if this method was invoked from a physical tree event
            metadataEditor.logicalTree.markManyAsSelected(treeNodeIds);
        }
    },

    /**
     * Resets the CSS selection style of the Primefaces tree component.
     */
    resetSelectionStyle() {
        // make all tree nodes not selected
        let nodes = $("#logicalTree .ui-treenode.ui-treenode-selected");
        nodes.removeClass("ui-treenode-selected").addClass("ui-treenode-unselected");
        nodes.attr("aria-selected", "false");
        $("#logicalTree .ui-treenode-label.ui-state-highlight").removeClass("ui-state-highlight");
        $("#logicalTree .ui-treenode-label.ui-treenode-outline").removeClass("ui-treenode-outline");
    },

    /**
     * Marks a set of node as selected by applying the CSS styles to the specified tree node.
     * 
     * @param treeNodeId the logical tree node ids as a list of strings, identifying each tree node
     */
    markManyAsSelected(treeNodeIds) {
        this.resetSelectionStyle();
        PF("logicalTree").selections = [];
        for(let i = 0; i < treeNodeIds.length; i++) {
            let node = this._getNodeFromTreeNodeId(treeNodeIds[i]);
            if (node) {
                this._addNodeToSelection(node);
                PF("logicalTree").addToSelection(treeNodeIds[i]);
            }
        }        
    },

    /**
     * Return the tree node id from a jquery node object.
     * @param {*} node the jquery node object
     * @returns the tree node id
     */
    _getTreeNodeIdFromNode(node) {
        return node.attr("id").split(":")[1];
    },

    /**
     * Return the jquery node for a tree node id.
     * 
     * @param treeNodeId the tree node id
     * @returns the jquery object of the node in the logical tree
     */
    _getNodeFromTreeNodeId(treeNodeId) {
        return $("#logicalTree\\:" + treeNodeId);
    },

    /**
     * Returns true if the checkbox "show media" is checked, meaning the logical tree
     * will contain individual nodes for each media.
     * 
     * @returns true if checkbox "show media" is checked
     */
    _isHideMediaChecked() {
        return $("#logicalStructureMenuForm\\:hideMediaCheckbox input").attr("aria-checked") === "true";
    },

    /**
     * Returns query object of all selected nodes in the logical structure tree.
     * 
     * @returns the selected nodes
     */
    _findSelectedNodes() {
        return $("#logicalTree .ui-treenode.ui-treenode-selected");
    },

    /**
     * Adds one node to the selection.
     * 
     * @param node the jquery node object that is added to the selection
     */
    _addNodeToSelection(node) {
        this._styleNodeAsSelected(node);
    },

    /**
     * Styles a node as selected by applying the CSS styles to the specified tree node.
     * 
     * @param node the jquery object of the tree node
     */
    _styleNodeAsSelected(node) {
        let label = node.find("> .ui-treenode-content > .ui-treenode-label");
        node.attr("aria-selected", "true");
        node.removeClass("ui-treenode-unselected").addClass("ui-treenode-selected");
        label.addClass("ui-state-highlight ui-treenode-outline");
    },

    /**
     * Remove one node from the selection.
     * 
     * @param node the jquery node object that is removed from the selection
     */
    _removeNodeFromSelection(node) {
        this._styleNodeAsUnselected(node);  
    },

    /**
     * Style one node as unselected by removing the CSS styles of the specified node.
     * 
     * @param node the jquery object of the tree node
     */
    _styleNodeAsUnselected(node) {
        let label = node.find("> .ui-treenode-content > .ui-treenode-label");
        node.attr("aria-selected", "false");
        node.removeClass("ui-treenode-selected").addClass("ui-treenode-unselected");
        label.removeClass("ui-state-highlight ui-treenode-outline");
    }
};

/**
 * Event handlers and methods related to the physical structure tree.
 */
metadataEditor.physicalTree = {

    /**
     * Checks whether a physical structure tree is actually present in the dom tree, meaning whether
     * the view mode "separate structure" is enabled.
     * 
     * @returns true if there is a physical structure tree being visualized
     */
    isAvailable() {
        return $("#physicalTree").length > 0;
    },

    /**
     * Handler that is called by Primefaces when a tree node is clicked.
     * 
     * @param node the clicked tree node as jquery object
     * @param event the mouse event
     */
    onNodeClick(node, event) {
        metadataEditor.logicalTree.onNodeClick.apply(this, [node, event]);
    },

    /**
     * Resets the CSS selection style of the physical structure tree, meaning all nodes are 
     * removed from the selection.
     */
    resetSelectionStyle() {
        // make all tree nodes not selected
        let nodes = $("#physicalTree .ui-treenode.ui-treenode-selected");
        nodes.removeClass("ui-treenode-selected").addClass("ui-treenode-unselected");
        nodes.attr("aria-selected", "false");
        $("#physicalTree .ui-treenode-label.ui-state-highlight").removeClass("ui-state-highlight");
        $("#physicalTree .ui-treenode-label.ui-treenode-outline").removeClass("ui-treenode-outline");
    },

    /**
     * Mark a set of nodes as selected by appyling the corresponding CSS styles.
     * 
     * @param treeNodeIds the logical tree node ids as list of strings
     */
    markManyAsSelected(treeNodeIds) {
        this.resetSelectionStyle();
        PF("physicalTree").selections = [];
        for(let i = 0; i < treeNodeIds.length; i++) {
            let node = this._getNodeFromTreeNodeId(treeNodeIds[i]);
            if (node) {
                this._addNodeToSelection(node);
                let rowkey = this._findRowKeyByTreeNodeId(treeNodeIds[i]);
                PF("physicalTree").addToSelection(rowkey);
            }
        }
    },

    /**
     * Return the tree node id from a jquery node object.
     * @param node the jquery node object
     * @returns the tree node id
     */
    _getTreeNodeIdFromNode(node) {
        let order = node.find("> .ui-treenode-content label[data-order]")[0].dataset.order;
        return metadataEditor.gallery.pages.findTreeNodeIdByOrder(order);
    },

    /**
     * Return the jquery node for a tree node id.
     * 
     * @param treeNodeId the tree node id
     * @returns the jquery object of the node in the logical tree
     */
    _getNodeFromTreeNodeId(treeNodeId) {
        let order = metadataEditor.gallery.pages.findOrderByTreeNodeId(treeNodeId);
        if (order !== null) {
            let span = $("#physicalTree label[data-order=\"" + order + "\"]");
            let node = span.closest(".ui-treenode");
            return node;
        }
        return null;
    },

    /**
     * Returns query object of all selected nodes in the physical structure tree.
     * 
     * @returns the selected nodes
     */
    _findSelectedNodes() {
        return $("#physicalTree .ui-treenode.ui-treenode-selected");
    },

    /**
     * Add a single node to the selection.
     * 
     * @param {*} node the jquery node that is added to the selection
     */
    _addNodeToSelection(node) {
        metadataEditor.logicalTree._styleNodeAsSelected(node);
    },

    /**
     * Remove a single node from the selection.
     * 
     * @param node the jquery node that is removed from the selection
     */
    _removeNodeFromSelection(node) {
        metadataEditor.logicalTree._styleNodeAsUnselected(node);
    },

    /**
     * Return rowkey attribute of node for tree node id.
     * 
     * @param treeNodeId the tree node if of the node
     * @returns the rowkey attribute in the physical tree for that node
     */
    _findRowKeyByTreeNodeId(treeNodeId) {
        let order = metadataEditor.gallery.pages.findOrderByTreeNodeId(treeNodeId);
        let span = $("#physicalTree label[data-order=\"" + order + "\"]");
        let node = span.closest(".ui-treenode");
        if (node.length > 0) {
            return node[0].dataset.rowkey;
        }
        return null;
    }
};


/** 
 * Event handlers and methods related to the pagination panel.
 */
metadataEditor.pagination = {

    /**
     * Handler that is called when the selection state of the page list changes, e.g., when the user selects 
     * or deselects a page by clicking a checkbox.
     * 
     * @param event the mouse event
     */
    onChange(event) {
        // check which pages are selected
        let selectedOrder = [];
        for(let i = 0; i < event.target.length; i++) {
            if ($(event.target[i]).prop("selected")) {
                selectedOrder.push(event.target[i].index + 1);
            }
        }
        // find corresponding logical tree node ids for all selected pages
        let treeNodeIds = metadataEditor.gallery.pages.findTreeNodeIdsByOrderList(selectedOrder);

        // apply selection to other components of meta data editor
        metadataEditor.gallery.pages.markManyAsSelected(treeNodeIds, null);
        metadataEditor.logicalTree.markManyAsSelected(treeNodeIds);
        if (metadataEditor.physicalTree.isAvailable()) {
            metadataEditor.physicalTree.markManyAsSelected(treeNodeIds);
        }
    },

    /**
     * Reset CSS selection style of pagination list.
     */
    resetSelectionStyle() {
        $("#paginationForm\\:paginationSelection .ui-state-highlight").removeClass("ui-state-highlight");
        $("#paginationForm\\:paginationSelection .ui-icon-check").removeClass("ui-icon-check").addClass("ui-icon-blank");
        $("#paginationForm\\:paginationSelection .ui-state-active").removeClass("ui-state-active");
        $("#paginationForm\\:paginationSelection select option").removeAttr("selected");
        $("#paginationForm\\:paginationSelection select option").prop("selected", false);
    },

    /**
     * Mark a list of pages a selected by applying corresponding CSS styles to each list item.
     * 
     * @param {*} treeNodeIds the list of logical tree node ids that is supposed to be selected
     */
    markManyAsSelected(treeNodeIds) {
        this.resetSelectionStyle();
        for (let i = 0; i < treeNodeIds.length; i++) {
            // find treeNode in gallery view
            let thumbnailContainer = metadataEditor.gallery.pages.findThumbnailByTreeNodeId(treeNodeIds[i]).next();
            if (thumbnailContainer.length > 0) {
                let order = thumbnailContainer[0].dataset.order;
                
                // make checkbox checked
                let selectManyMenu = $("#paginationForm\\:paginationSelection");
                let selectListBox = selectManyMenu.find(".ui-selectlistbox-list");
                let selectItem = selectListBox.children().eq(order - 1);
                selectItem.addClass("ui-state-highlight");
                selectItem.find(".ui-chkbox-box").addClass("ui-state-active");
                selectItem.find(".ui-chkbox-icon").removeClass("ui-icon-blank").addClass("ui-icon-check");
                
                // mark invisible select option as selected
                let options = $("#paginationForm\\:paginationSelection select option");
                options.eq(order - 1).attr("selected", "selected");
                options.eq(order - 1).prop("selected", true);
            }
        }        
    }
};

metadataEditor.detailView = {

    /**
     * Select the previous or following media when clicking on the navigation buttons of the detail view.
     * @param {int} delta position delta with respect to currently selected media
     */
    navigate(delta) {
        // find media currently shown in detail view
        let currentTreeNodeId = $("#imagePreviewForm\\:mediaDetail").data("logicaltreenodeid");
        if (!currentTreeNodeId) {
            return;
        }
        // find current media in list of thumbnails
        let thumbnails = $("#imagePreviewForm .thumbnail-container");
        let currentThumbnail = $("#imagePreviewForm .thumbnail-container[data-logicaltreenodeid='" + currentTreeNodeId + "']");
        let index = thumbnails.index(currentThumbnail);
        if (index < 0) {
            return;
        }
        // add delta to find previous or next thumbnail
        index = Math.min(thumbnails.length - 1, Math.max(0, index + delta));
        let nextThumbnail = thumbnails.eq(index);
        let nextTreeNodeId = nextThumbnail.data("logicaltreenodeid");
        // update selection and trigger reload of detail view
        metadataEditor.gallery.pages.handleSingleSelect(null, nextThumbnail, nextTreeNodeId);
        metadataEditor.gallery.pages.handleSelectionUpdates([nextTreeNodeId]);
        scrollToPreviewThumbnail(nextThumbnail, $("#thumbnailStripeScrollableContent"));
    }    
};

metadataEditor.contextMenu = {
    listen() {
        document.oncontextmenu = function(event) {
            return event.target.tagName === "INPUT" || event.target.tagName === "TEXTAREA";
        };
        $(document).on("mousedown.thumbnail", ".thumbnail-parent", function(event) {
            if (event.originalEvent.button === 2 && $(event.target).closest(".thumbnail-parent").find(".selected").length === 1) {
                PF("mediaContextMenu").show(event);
            }
        });
        $(document).on("mousedown.stripe", ".stripe", function(event) {
            if (event.originalEvent.button === 2 && $(event.target).closest(".stripe").find(".selected").length === 1) {
                PF("stripeContextMenu").show(event);
            }
        });
    }
};

metadataEditor.shortcuts = {
    getGalleryViewMode() {
        return $("#imagePreviewForm\\:galleryViewMode ").text().toUpperCase();
    },
    changeView(galleryViewMode) {
        if (this.getGalleryViewMode() !== galleryViewMode) {
            setGalleryViewMode([{name: "galleryViewMode", value: galleryViewMode}]);
        }
    },
    jumpToGalleryImage(thumbnails, selectedThumbnail, delta, vertical) {
        let currentIndex;
        let selectableThumbnails;
        if (vertical) {
            let posX = selectedThumbnail.offset().left;
            selectableThumbnails = $([]);
            thumbnails.each(function () {
                if (posX === $(this).offset().left) {
                    selectableThumbnails = selectableThumbnails.add($(this));
                }
            });
            currentIndex = selectableThumbnails.get().indexOf(selectedThumbnail[0]);
        } else {
            selectableThumbnails = thumbnails;
            currentIndex = thumbnails.index(selectedThumbnail);
        }
        let newIndex = currentIndex + delta;
        if (currentIndex >= 0 && newIndex >= 0 && newIndex < selectableThumbnails.length) {
            let newThumbnailContainer = selectableThumbnails[newIndex];
            let treeNodeId = newThumbnailContainer.dataset.logicaltreenodeid;
            metadataEditor.gallery.pages.handleSingleSelect(null, $(newThumbnailContainer), treeNodeId);
            metadataEditor.gallery.pages.handleSelectionUpdates([treeNodeId]);
            let galleryViewMode = this.getGalleryViewMode();
            if (galleryViewMode === "LIST") {
                scrollToStructureThumbnail(selectableThumbnails.eq(newIndex), $("#imagePreviewForm\\:structuredPagesField"));
            } else if (galleryViewMode === "PREVIEW") {
                scrollToPreviewThumbnail(selectableThumbnails.eq(newIndex), $("#thumbnailStripeScrollableContent"));
            }
            return true;
        }
        return false;
    },
    jumpToSelectedImage(delta, vertical) {
        let gallery = $("#galleryWrapperPanel");
        let lastSelection = gallery.find(".thumbnail.last-selection + .thumbnail-container");
        if (lastSelection.length === 1) {
            let thumbnails = gallery.find(".thumbnail + .thumbnail-container");
            if (delta > 0) {
                for (; delta > 0; delta--) {
                    if (vertical && this.jumpToGalleryImage(thumbnails, lastSelection, delta, true)) {
                        break;
                    } else if (!vertical && this.jumpToGalleryImage(thumbnails, lastSelection, delta, false)) {
                        break;
                    }
                }
            } else if (delta < 0) {
                for (; delta < 0; delta++) {
                    if (vertical && this.jumpToGalleryImage(thumbnails, lastSelection, delta, true)) {
                        break;
                    } else if (!vertical && this.jumpToGalleryImage(thumbnails, lastSelection, delta, false)) {
                        break;
                    }
                }
            }
        }
    },
    jumpToTreeNode(treeNodes, selectedTreeNode, delta) {
        let currentIndex = treeNodes.index(selectedTreeNode);
        let newIndex = currentIndex + delta;
        if (currentIndex >= 0 && newIndex >= 0 && newIndex < treeNodes.length) {
            treeNodes.eq(newIndex).children(".ui-treenode-content").children(".ui-treenode-label").click();
            return true;
        }
        return false;
    },
    jumpToSelectedTreeNode(tree, delta, vertical) {
        let lastSelection = tree.find("li.ui-treenode[aria-selected='true']");
        if (!vertical && lastSelection.length === 1) {
            lastSelection.children(".ui-treenode-children").is(":visible");
            if (delta <= -1 && lastSelection.children(".ui-treenode-children").is(":visible")) {
                lastSelection.children(".ui-treenode-content").children(".ui-tree-toggler").click();
            } else if (delta >= 1 && lastSelection.children(".ui-treenode-children").is(":hidden")) {
                lastSelection.children(".ui-treenode-content").children(".ui-tree-toggler").click();
            }
        } else if (vertical && lastSelection.length === 1) {
            let treeNodes = tree.find("li.ui-treenode:visible");
            if (delta > 0) {
                for (; delta > 0; delta--) {
                    if (this.jumpToTreeNode(treeNodes, lastSelection, delta)) {
                        break;
                    }
                }
            } else if (delta < 0) {
                for (; delta < 0; delta++) {
                    if (this.jumpToTreeNode(treeNodes, lastSelection, delta)) {
                        break;
                    }
                }
            }
        }
    },
    navigateByShortcut(delta, vertical) {
        let focusedArea = $(".focused");
        if (focusedArea.length !== 1 || focusedArea[0] === null) {
            return;
        }
        switch (focusedArea[0].id) {
            case "logicalTree":
            case "physicalTree":
                metadataEditor.shortcuts.jumpToSelectedTreeNode(focusedArea.eq(0), delta, vertical);
                break;
            case "imagePreviewForm":
            default:
                metadataEditor.shortcuts.jumpToSelectedImage(delta, vertical); // gallery
        }
    },
    handleShortcut(shortcut) {
        switch (shortcut) {
            case "help":
                if (!(document.activeElement.tagName === "INPUT" || document.activeElement.tagName === "TEXTAREA")) {
                    PF("helpDialog").show();
                }
                break;
            case "structuredView":
                metadataEditor.shortcuts.changeView("LIST");
                break;
            case "detailView":
                metadataEditor.shortcuts.changeView("PREVIEW");
                break;
            case "nextItem":
                metadataEditor.shortcuts.navigateByShortcut(1, false);
                break;
            case "previousItem":
                metadataEditor.shortcuts.navigateByShortcut(-1, false);
                break;
            case "nextItemMulti":
                metadataEditor.shortcuts.navigateByShortcut(20, false);
                break;
            case "previousItemMulti":
                metadataEditor.shortcuts.navigateByShortcut(-20, false);
                break;
            case "downItem":
                metadataEditor.shortcuts.navigateByShortcut(1, true);
                break;
            case "upItem":
                metadataEditor.shortcuts.navigateByShortcut(-1, true);
                break;
            case "downItemMulti":
                metadataEditor.shortcuts.navigateByShortcut(20, true);
                break;
            case "upItemMulti":
                metadataEditor.shortcuts.navigateByShortcut(-20, true);
                break;
            default:
                // This default case is only reached when shortcuts exist which are not implemented.
        }
    },
    evaluateKeys(event) {
        Object.keys(metadataEditor.shortcuts.KEYS).forEach((key) => {
            let keyCombination = metadataEditor.shortcuts.KEYS[key];
            if (event.ctrlKey === keyCombination.includes("Control")
                && event.shiftKey === keyCombination.includes("Shift")
                && event.metaKey === keyCombination.includes("Meta")
                && event.altKey === keyCombination.includes("Alt")
                && keyCombination.includes(event.code)
            ) {
                metadataEditor.shortcuts.handleShortcut(key);
                event.preventDefault();
            }
        });
    },
    listen (shortcuts) {
        metadataEditor.shortcuts.KEYS = shortcuts;
        $(document).on("keydown.shortcuts", function (event) {
            metadataEditor.shortcuts.evaluateKeys(event.originalEvent);
        });
        $(document).on("click.shortcuts", ".focusable", function (event) {
            $(".focused").removeClass("focused");
            event.currentTarget.classList.add("focused");
        });
    },
    ignore() {
        $(document).off("keydown.shortcuts");
    },
    updateViews() {
        switch (this.getGalleryViewMode()) {
            case "LIST":
                destruct();
                scrollToSelectedThumbnail();
                break;
            case "PREVIEW":
                initialize();
                scrollToSelectedThumbnail();
                metadataEditor.detailMap.update();
                break;
        }
    }
};

function expandMetadata(panelClass) {
    $("." + panelClass + "[aria-expanded='false']").click();
}

function activateButtons() {
    PF('saveExit').enable();
    PF('save').enable();
    PF('validate').enable();
    PF('close').enable();
    if ($('#buttonForm\\:renameMedia').length > 0) {
        PF('renameMedia').enable();
    }
}

function deactivateButtons() {
    PF('saveExit').disable();
    PF('save').disable();
    PF('validate').disable();
    PF('close').disable();
    if ($('#buttonForm\\:renameMedia').length > 0) {
        PF('renameMedia').disable();
    }
}

$(function () {
    metadataEditor.gallery.pages.init();
});
