// Copyright 2005 The Closure Library Authors. All Rights Reserved.
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
 * @fileoverview A disposable implementation of a custom
 * listenable/event target. See also: documentation for
 * `goog.events.Listenable`.
 *
 * @author arv@google.com (Erik Arvidsson) [Original implementation]
 * @see ../demos/eventtarget.html
 * @see goog.events.Listenable
 */

goog.provide('goog.events.EventTarget');

goog.require('goog.Disposable');
goog.require('goog.asserts');
goog.require('goog.events');
goog.require('goog.events.Event');
goog.require('goog.events.Listenable');
goog.require('goog.events.ListenerMap');
goog.require('goog.object');

/**
 * @implements {goog.events.Listenable}
 */
goog.events.EventTarget = class extends goog.Disposable {
  /**
   * An implementation of `goog.events.Listenable` with full W3C
   * EventTarget-like support (capture/bubble mechanism, stopping event
   * propagation, preventing default actions).
   *
   * You may subclass this class to turn your class into a Listenable.
   *
   * Unless propagation is stopped, an event dispatched by an
   * EventTarget will bubble to the parent returned by
   * `getParentEventTarget`. To set the parent, call
   * `setParentEventTarget`. Subclasses that don't support
   * changing the parent can override the setter to throw an error.
   *
   * Example usage:
   * <pre>
   *   var source = new goog.events.EventTarget();
   *   function handleEvent(e) {
   *     alert('Type: ' + e.type + '; Target: ' + e.target);
   *   }
   *   source.listen('foo', handleEvent);
   *   // Or: goog.events.listen(source, 'foo', handleEvent);
   *   ...
   *   source.dispatchEvent('foo');  // will call handleEvent
   *   ...
   *   source.unlisten('foo', handleEvent);
   *   // Or: goog.events.unlisten(source, 'foo', handleEvent);
   * </pre>
   */
  constructor() {
    super();

    /**
     * Maps of event type to an array of listeners.
     * @private {!goog.events.ListenerMap}
     */
    this.eventTargetListeners_ = new goog.events.ListenerMap(this);

    /**
     * The object to use for event.target. Useful when mixing in an
     * EventTarget to another object.
     * @private {!Object}
     */
    this.actualEventTarget_ = this;

    /**
     * Parent event target, used during event bubbling.
     *
     * TODO(chrishenry): Change this to goog.events.Listenable. This
     * currently breaks people who expect getParentEventTarget to return
     * goog.events.EventTarget.
     *
   * @private {?goog.events.EventTarget}
     */
    this.parentEventTarget_ = null;
  }

  /**
   * Returns the parent of this event target to use for bubbling.
   *
   * @return {goog.events.EventTarget} The parent EventTarget or null if
   *     there is no parent.
   * @override
   */
  getParentEventTarget() {
    return this.parentEventTarget_;
  }

  /**
   * Sets the parent of this event target to use for capture/bubble
   * mechanism.
   * @param {goog.events.EventTarget} parent Parent listenable (null if none).
   */
  setParentEventTarget(parent) {
    this.parentEventTarget_ = parent;
  }

  /**
   * Adds an event listener to the event target. The same handler can only be
   * added once per the type. Even if you add the same handler multiple times
   * using the same type then it will only be called once when the event is
   * dispatched.
   *
   * @param {string|!goog.events.EventId} type The type of the event to listen for
   * @param {function(?):?|{handleEvent:function(?):?}|null} handler The function
   *     to handle the event. The handler can also be an object that implements
   *     the handleEvent method which takes the event object as argument.
   * @param {boolean=} opt_capture In DOM-compliant browsers, this determines
   *     whether the listener is fired during the capture or bubble phase
   *     of the event.
   * @param {Object=} opt_handlerScope Object in whose scope to call
   *     the listener.
   * @deprecated Use `#listen` instead, when possible. Otherwise, use
   *     `goog.events.listen` if you are passing Object
   *     (instead of Function) as handler.
   */
  addEventListener(type, handler, opt_capture, opt_handlerScope) {
    goog.events.listen(this, type, handler, opt_capture, opt_handlerScope);
  }

  /**
   * Removes an event listener from the event target. The handler must be the
   * same object as the one added. If the handler has not been added then
   * nothing is done.
   *
   * @param {string} type The type of the event to listen for.
   * @param {function(?):?|{handleEvent:function(?):?}|null} handler The function
   *     to handle the event. The handler can also be an object that implements
   *     the handleEvent method which takes the event object as argument.
   * @param {boolean=} opt_capture In DOM-compliant browsers, this determines
   *     whether the listener is fired during the capture or bubble phase
   *     of the event.
   * @param {Object=} opt_handlerScope Object in whose scope to call
   *     the listener.
   * @deprecated Use `#unlisten` instead, when possible. Otherwise, use
   *     `goog.events.unlisten` if you are passing Object
   *     (instead of Function) as handler.
   */
  removeEventListener(type, handler, opt_capture, opt_handlerScope) {
    goog.events.unlisten(this, type, handler, opt_capture, opt_handlerScope);
  }

  /** @override */
  dispatchEvent(e) {
    this.assertInitialized_();

    var ancestorsTree, ancestor = this.getParentEventTarget();
    if (ancestor) {
      ancestorsTree = [];
      var ancestorCount = 1;
      for (; ancestor; ancestor = ancestor.getParentEventTarget()) {
        ancestorsTree.push(ancestor);
        goog.asserts.assert((++ancestorCount < goog.events.EventTarget.MAX_ANCESTORS_), 'infinite loop');
      }
    }

    return goog.events.EventTarget.dispatchEventInternal_(this.actualEventTarget_, e, ancestorsTree);
  }

  /**
   * Removes listeners from this object.  Classes that extend EventTarget may
   * need to override this method in order to remove references to DOM Elements
   * and additional listeners.
   * @override
 * @protected
   */
  disposeInternal() {
    super.disposeInternal();

    this.removeAllListeners();
    this.parentEventTarget_ = null;
  }

  /** @override */
  listen(type, listener, opt_useCapture, opt_listenerScope) {
    this.assertInitialized_();
    return this.eventTargetListeners_.add(String(type), listener, false /* callOnce */, opt_useCapture,
        opt_listenerScope);
  }

  /** @override */
  listenOnce(type, listener, opt_useCapture, opt_listenerScope) {
    return this.eventTargetListeners_.add(String(type), listener, true /* callOnce */, opt_useCapture, opt_listenerScope);
  }

  /** @override */
  unlisten(type, listener, opt_useCapture, opt_listenerScope) {
    return this.eventTargetListeners_.remove(String(type), listener, opt_useCapture, opt_listenerScope);
  }

  /** @override */
  unlistenByKey(key) {
    return this.eventTargetListeners_.removeByKey(key);
  }

  /** @override */
  removeAllListeners(opt_type) {
    // TODO(chrishenry): Previously, removeAllListeners can be called on
    // uninitialized EventTarget, so we preserve that behavior. We
    // should remove this when usages that rely on that fact are purged.
    if (!this.eventTargetListeners_) {
      return 0;
    }
    return this.eventTargetListeners_.removeAll(opt_type);
  }

  /** @override */
  fireListeners(type, capture, eventObject) {
    // TODO(chrishenry): Original code avoids array creation when there
    // is no listener, so we do the same. If this optimization turns
    // out to be not required, we can replace this with
    // getListeners(type, capture) instead, which is simpler.
    var listenerArray = this.eventTargetListeners_.listeners[String(type)];
    if (!listenerArray) {
      return true;
    }
    listenerArray = listenerArray.concat();

    var rv = true;
    for (var i = 0; i < listenerArray.length; ++i) {
      var listener = listenerArray[i];
      // We might not have a listener if the listener was removed.
      if (listener && !listener.removed && listener.capture == capture) {
        var listenerFn = listener.listener;
        var listenerHandler = listener.handler || listener.src;

        if (listener.callOnce) {
          this.unlistenByKey(listener);
        }
        rv = listenerFn.call(listenerHandler, eventObject) !== false && rv;
      }
    }

    return rv && eventObject.returnValue_ != false;
  }

  /** @override */
  getListeners(type, capture) {
    return this.eventTargetListeners_.getListeners(String(type), capture);
  }

  /** @override */
  getListener(type, listener, capture, opt_listenerScope) {
    return this.eventTargetListeners_.getListener(String(type), listener, capture, opt_listenerScope);
  }

  /** @override */
  hasListener(opt_type, opt_capture) {
    var id = goog.isDef(opt_type) ? String(opt_type) : undefined;
    return this.eventTargetListeners_.hasListener(id, opt_capture);
  }

  /**
   * Sets the target to be used for `event.target` when firing
   * event. Mainly used for testing. For example, see
   * `goog.testing.events.mixinListenable`.
   * @param {!Object} target The target.
   */
  setTargetForTesting(target) {
    this.actualEventTarget_ = target;
  }

  /**
   * Asserts that the event target instance is initialized properly.
   * @private
   */
  assertInitialized_() {
    goog.asserts.assert(this.eventTargetListeners_,
        'Event target is not initialized. Did you call the superclass ' + '(goog.events.EventTarget) constructor?');
  }

  /**
   * Dispatches the given event on the ancestorsTree.
   *
   * @param {!Object} target The target to dispatch on.
   * @param {goog.events.Event|Object|string} e The event object.
   * @param {Array<goog.events.Listenable>=} opt_ancestorsTree The ancestors
   *     tree of the target, in reverse order from the closest ancestor
   *     to the root event target. May be null if the target has no ancestor.
   * @return {boolean} If anyone called preventDefault on the event object (or
   *     if any of the listeners returns false) this will also return false.
   * @private
   */
  static dispatchEventInternal_(target, e, opt_ancestorsTree) {
    /** @suppress {missingProperties} */
    var type = e.type || /** @type {string} */ (e);

    // If accepting a string or object, create a custom event object so that
    // preventDefault and stopPropagation work with the event.
    if (goog.isString(e)) {
      e = new goog.events.Event(e, target);
    } else if (!(e instanceof goog.events.Event)) {
      var oldEvent = e;
      e = new goog.events.Event(type, target);
      goog.object.extend(e, oldEvent);
    } else {
      e.target = e.target || target;
    }

    var rv = true, currentTarget;

    // Executes all capture listeners on the ancestors, if any.
    if (opt_ancestorsTree) {
      for (var i = opt_ancestorsTree.length - 1; !e.propagationStopped_ && i >= 0; i--) {
        currentTarget = e.currentTarget = opt_ancestorsTree[i];
        rv = currentTarget.fireListeners(type, true, e) && rv;
      }
    }

    // Executes capture and bubble listeners on the target.
    if (!e.propagationStopped_) {
      currentTarget = /** @type {?} */ (e.currentTarget = target);
      rv = currentTarget.fireListeners(type, true, e) && rv;
      if (!e.propagationStopped_) {
        rv = currentTarget.fireListeners(type, false, e) && rv;
      }
    }

    // Executes all bubble listeners on the ancestors, if any.
    if (opt_ancestorsTree) {
      for (i = 0; !e.propagationStopped_ && i < opt_ancestorsTree.length; i++) {
        currentTarget = e.currentTarget = opt_ancestorsTree[i];
        rv = currentTarget.fireListeners(type, false, e) && rv;
      }
    }

    return rv;
  }
};

goog.events.Listenable.addImplementation(goog.events.EventTarget);


/**
 * An artificial cap on the number of ancestors you can have. This is mainly
 * for loop detection.
 * @const {number}
 * @private
 */
goog.events.EventTarget.MAX_ANCESTORS_ = 1000;

