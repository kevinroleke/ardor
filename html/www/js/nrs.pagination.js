/******************************************************************************
 * Copyright © 2016-2021 Jelurida IP B.V.                                     *
 *                                                                            *
 * See the LICENSE.txt file at the top-level directory of this distribution   *
 * for licensing information.                                                 *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,*
 * no part of this software, including this file, may be copied, modified,    *
 * propagated, or distributed except according to the terms contained in the  *
 * LICENSE.txt file.                                                          *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

NRS.onSiteBuildDone().then(() => {
    NRS = (function(NRS, $, undefined) {

        const MAX_JAVA_INT = Math.pow(2, 31);

        //The default implementation uses the legacy static fields in NRS
        function Pagination() {
            this.firstIndex = 0;
            this.currentResultSize = 0;

            this.reset = function() {
                this.firstIndex = 0;
                this.currentResultSize = 0;
            };

            this.getContainer = function() {
                return $("#" + NRS.currentPage + "_page .data-pagination");
            };

            this.getItemsPerPage = function() {
                return NRS.itemsPerPage;
            };

            ///Converts indexes (used to call the API) to item number (used to display the page content).
            /// Inverse function of itemNumberToIndex.
            this.indexToItemNumber = function(index) {
                return index + 1;
            };

            ///Coverts items number (used to display the page content) to indexes (used to call the API).
            /// Inverse function of indexToItemNumber.
            this.itemNumberToIndex = function(itemNr) {
                return itemNr - 1;
            };

            this.getFirstIndex = function() {
                return this.firstIndex;
            };

            ///Last index should be one more than necessary. The extra item is removed
            /// in onResult
            this.getLastIndex = function() {
                return this.firstIndex + this.getItemsPerPage();
            };

            this.onResult = function(resultArray) {
                if (Array.isArray(resultArray)) {
                    this.currentResultSize = resultArray.length;
                    if (resultArray.length > this.getItemsPerPage()) {
                        resultArray.pop();
                    }
                } else {
                    this.currentResultSize = 0;
                }
            }

            this.setResultSize = function(size) {
                this.currentResultSize = size;
            };

            this.goToIndex = function(index) {
                this.firstIndex = index;

                NRS.pageLoading();

                NRS.pages[NRS.currentPage]();
            };

            this.getNextPageIndex = function() {
                return this.firstIndex + this.getItemsPerPage();
            };

            this.getPrevPageIndex = function() {
                return Math.max(0, this.firstIndex - this.getItemsPerPage());
            };

            this.hasMorePages = function() {
                return this.currentResultSize > this.getItemsPerPage();
            };

            this.gotoPlaceholder = function() {
                return $.t("go_to_item");
            }
        }

        NRS.pagination = {};
        NRS.defaultPagination = new Pagination();

        NRS.getCurrentPagination = function () {
            let result = NRS.pagination[NRS.currentPage];
            if (!result) {
                result = NRS.defaultPagination;
            }
            return result;
        };

        function buildCurrentPageWidget(pagination) {
            let currentPageText;
            let visibleItems = pagination.hasMorePages() ? pagination.getItemsPerPage() : pagination.currentResultSize;

            if (visibleItems > 0) {
                let start = pagination.getFirstIndex() + 1;
                let end = pagination.getFirstIndex() + visibleItems;
                currentPageText = start + "-" + end;
            } else {
                currentPageText = `${$.t('no_results')} <i class='fa fa-exclamation-circle'/>`
            }

            return currentPageText;
        }

        NRS.addPagination = function (pagination) {
            if (pagination == null) {
                pagination = NRS.getCurrentPagination();
            }
            let $paginationContainer = pagination.getContainer();
            if ($paginationContainer.length == 0) {
                return;
            }

            var prevHTML = '<span style="width:48px;text-align:right;">';
            var firstHTML = '<span style="min-width:48px;text-align:right;">';
            var currentHTML = '<span style="min-width:48px;text-align:left;">';
            var nextHTML = '<span style="width:48px;text-align:left;">';

            if (pagination.getFirstIndex() > 0) {
                prevHTML += "<a class='page-link' href='#' data-index='" + pagination.getPrevPageIndex() + "' title='" + $.t("previous") + "' style='font-size:20px;'>";
                prevHTML += "<i class='far fa-arrow-circle-left'></i></a>";
            } else {
                prevHTML += '&nbsp;';
            }

            if (pagination.hasMorePages()) {
                currentHTML += buildCurrentPageWidget(pagination) + "&nbsp;";
                nextHTML += "<a class='page-link' href='#' data-index='" + pagination.getNextPageIndex() + "' title='" + $.t("next") + "' style='font-size:20px;'>";
                nextHTML += "<i class='fa fa-arrow-circle-right'></i></a>";
            } else {
                if (pagination.getFirstIndex() > 0) {
                    currentHTML += buildCurrentPageWidget(pagination) + "&nbsp;";
                } else {
                    currentHTML += "&nbsp;";
                }
                nextHTML += "&nbsp;";
            }
            if (pagination.getFirstIndex() > 0) {
                firstHTML += "&nbsp;<a class='page-link' href='#' data-index='0'>" + 1 +
                        "-" + pagination.getItemsPerPage() + "</a>&nbsp;|&nbsp;";
            } else {
                firstHTML += "&nbsp;";
            }

            prevHTML += '</span>';
            firstHTML += '</span>';
            currentHTML += '</span>';
            nextHTML += '</span>';

            var output = prevHTML + firstHTML + currentHTML + nextHTML;

            if (pagination.getFirstIndex() > 0 || pagination.hasMorePages()) {
                output += `<span style="min-width:48px;text-align:left;">
                        <input type="number" class="pagination-index-input" placeholder="${pagination.gotoPlaceholder()}" value=""/>
                        <button class="pagination-index-button">${$.t("go")}</button>
                   </span>`
            }

            $paginationContainer.html(output);
            $paginationContainer.data("pagination", pagination);
        };

        $(document).on("click", ".data-pagination a.page-link", function(e) {
            e.preventDefault();
            let pagination = $(this).closest(".data-pagination").data("pagination");
            pagination.goToIndex($(this).data("index"));
        });

        $(document).on("focus", ".pagination-index-input", function(e) {
            if ($(this).val() === '') {
                let pagination = $(this).closest(".data-pagination").data("pagination");
                $(this).removeClass("invalid").val(pagination.indexToItemNumber(pagination.getFirstIndex())).select();
            }
        });

        $(document).on("keypress", ".pagination-index-input", function(e) {
            if(e.which == 13) {
                $(this).closest(".data-pagination").find(".pagination-index-button").click();
            }
        });

        $(document).on("click", ".pagination-index-button", function(e) {
            let $pagination = $(this).closest(".data-pagination");
            let pagination = $pagination.data("pagination");
            let inputVal = parseInt($pagination.find(".pagination-index-input").val());
            let itemIndex = pagination.itemNumberToIndex(Math.max(0, inputVal));
            if (isNaN(itemIndex) || itemIndex > MAX_JAVA_INT) {
                $(".pagination-index-input").addClass("invalid");
            } else {
                pagination.goToIndex(Math.max(0, itemIndex));
            }
        });

        return NRS;
    }(NRS || {}, jQuery));
});