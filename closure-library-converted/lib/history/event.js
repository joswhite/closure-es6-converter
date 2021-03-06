import {Event as EventsEvent} from './../events/event.js';
import {EventType} from './eventtype.js';
// Copyright 2010 The Closure Library Authors. All Rights Reserved.
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
 * @fileoverview The event object dispatched when the history changes.
 */

/**
 * Event object dispatched after the history state has changed.
 *     action, such as forward or back, clicking on a link, editing the URL, or
 *     calling {@code window.history.(go|back|forward)}.
 *     False if the token has been changed by a `setToken` or
 *     `replaceToken` call.
 * @extends {EventsEvent}
 * @final
 */
class history_Event extends EventsEvent {

  /**
   * Event object dispatched after the history state has changed.
   * @param {string} token The string identifying the new history state.
   * @param {boolean} isNavigation True if the event was triggered by a browser
   *     action, such as forward or back, clicking on a link, editing the URL, or
   *     calling {@code window.history.(go|back|forward)}.
   *     False if the token has been changed by a `setToken` or
   *     `replaceToken` call.
   */
  constructor(token, isNavigation) {
    super(EventType.NAVIGATE);
  
    /**
     * The current history state.
     * @type {string}
     */
    this.token = token;
  
    /**
     * Whether the event was triggered by browser navigation.
     * @type {boolean}
     */
    this.isNavigation = isNavigation;
  }
}

export {history_Event as Event};