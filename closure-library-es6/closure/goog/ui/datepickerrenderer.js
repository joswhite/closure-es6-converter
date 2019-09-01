// Copyright 2013 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview The renderer interface for {@link goog.ui.DatePicker}.
 *
 * @see ../demos/datepicker.html
 */

goog.provide('goog.ui.DatePickerRenderer');

/**
 * The renderer for {@link goog.ui.DatePicker}. Renders the date picker's
 * navigation header and footer.
 * @interface
 */
goog.ui.DatePickerRenderer = class {


	/**
	 * @abstract
	 * Render the navigation row.
	 *
	 * @param {!Element} row The parent element to render the component into.
	 * @param {boolean} simpleNavigation Whether the picker should render a simple
	 *     navigation menu that only contains controls for navigating to the next
	 *     and previous month. The default navigation menu contains controls for
	 *     navigating to the next/previous month, next/previous year, and menus for
	 *     jumping to specific months and years.
	 * @param {boolean} showWeekNum Whether week numbers should be shown.
	 * @param {string} fullDateFormat The full date format.
	 *     {@see goog.i18n.DateTimeSymbols}.
	 */
	renderNavigationRow(row, simpleNavigation, showWeekNum, fullDateFormat) {}

	/**
	 * @abstract
	 * Render the footer row.
	 *
	 * @param {!Element} row The parent element to render the component into.
	 * @param {boolean} showWeekNum Whether week numbers should be shown.
	 */
	renderFooterRow(row, showWeekNum) {}
};

