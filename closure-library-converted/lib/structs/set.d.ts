export { structs_Set as Set };
/**
 * @fileoverview Datastructure: Set.
 *
 *
 * This class implements a set data structure. Adding and removing is O(1). It
 * supports both object and primitive values. Be careful because you can add
 * both 1 and new Number(1), because these are not the same. You can even add
 * multiple new Number(1) because these are not equal.
 */
/**
 * A set that can contain both primitives and objects.  Adding and removing
 * elements is O(1).  Primitives are treated as identical if they have the same
 * type and convert to the same string.  Objects are treated as identical only
 * if they are references to the same object.  WARNING: A structs_Set can
 * contain both 1 and (new Number(1)), because they are not the same.  WARNING:
 * Adding (new Number(1)) twice will yield two distinct elements, because they
 * are two different objects.  WARNING: Any object that is added to a
 * structs_Set will be modified!  Because google.getUid() is used to
 * identify objects, every object in the set will be mutated.
 * @implements {Collection<T>}
 * @final
 * @template T
 * @deprecated This type is misleading: use ES6 Set instead.
 */
declare class structs_Set<T> implements Collection<T> {
    /**
     * Obtains a unique key for an element of the set.  Primitives will yield the
     * same key if they have the same type and convert to the same string.  Object
     * references will yield the same key only if they refer to the same object.
     * @param {*} val Object or primitive value to get a key for.
     * @return {string} A unique key for this value/object.
     * @private
     */
    private static getKey_;
    /**
     * A set that can contain both primitives and objects.  Adding and removing
     * elements is O(1).  Primitives are treated as identical if they have the same
     * type and convert to the same string.  Objects are treated as identical only
     * if they are references to the same object.  WARNING: A structs_Set can
     * contain both 1 and (new Number(1)), because they are not the same.  WARNING:
     * Adding (new Number(1)) twice will yield two distinct elements, because they
     * are two different objects.  WARNING: Any object that is added to a
     * structs_Set will be modified!  Because google.getUid() is used to
     * identify objects, every object in the set will be mutated.
     * @param {Array<T>|Object<?,T>=} opt_values Initial values to start with.
     * @template T
     * @deprecated This type is misleading: use ES6 Set instead.
     */
    constructor(opt_values?: any);
    map_: StructsMap<any, any>;
    /**
     * @return {number} The number of elements in the set.
     * @override
     */
    getCount(): number;
    /**
     * Add a primitive or an object to the set.
     * @param {T} element The primitive or object to add.
     * @override
     */
    add(element: T): void;
    /**
     * Adds all the values in the given collection to this set.
     * @param {Array<T>|Collection<T>|Object<?,T>} col A collection
     *     containing the elements to add.
     */
    addAll(col: any): void;
    /**
     * Removes all values in the given collection from this set.
     * @param {Array<T>|Collection<T>|Object<?,T>} col A collection
     *     containing the elements to remove.
     */
    removeAll(col: any): void;
    /**
     * Removes the given element from this set.
     * @param {T} element The primitive or object to remove.
     * @return {boolean} Whether the element was found and removed.
     * @override
     * @suppress {checkTypes}
     */
    remove(element: T): boolean;
    /**
     * Removes all elements from this set.
     */
    clear(): void;
    /**
     * Tests whether this set is empty.
     * @return {boolean} True if there are no elements in this set.
     */
    isEmpty(): boolean;
    /**
     * Tests whether this set contains the given element.
     * @param {T} element The primitive or object to test for.
     * @return {boolean} True if this set contains the given element.
     * @override
     */
    contains(element: T): boolean;
    /**
     * Tests whether this set contains all the values in a given collection.
     * Repeated elements in the collection are ignored, e.g.  (new
     * structs_Set([1, 2])).containsAll([1, 1]) is True.
     * @param {Collection<T>|Object} col A collection-like object.
     * @return {boolean} True if the set contains all elements.
     */
    containsAll(col: any): boolean;
    /**
     * Finds all values that are present in both this set and the given collection.
     * @param {Array<S>|Object<?,S>} col A collection.
     * @return {!structs_Set<T|S>} A new set containing all the values
     *     (primitives or objects) present in both this set and the given
     *     collection.
     * @template S
     */
    intersection<S>(col: any): structs_Set<T | S>;
    /**
     * Finds all values that are present in this set and not in the given
     * collection.
     * @param {Array<T>|Collection<T>|Object<?,T>} col A collection.
     * @return {!structs_Set} A new set containing all the values
     *     (primitives or objects) present in this set but not in the given
     *     collection.
     */
    difference(col: any): structs_Set;
    /**
     * Returns an array containing all the elements in this set.
     * @return {!Array<T>} An array containing all the elements in this set.
     */
    getValues(): T[];
    /**
     * Creates a shallow clone of this set.
     * @return {!structs_Set<T>} A new set containing all the same elements as
     *     this set.
     */
    clone(): structs_Set<T>;
    /**
     * Tests whether the given collection consists of the same elements as this set,
     * regardless of order, without repetition.  Primitives are treated as equal if
     * they have the same type and convert to the same string; objects are treated
     * as equal if they are references to the same object.  This operation is O(n).
     * @param {Collection<T>|Object} col A collection.
     * @return {boolean} True if the given collection consists of the same elements
     *     as this set, regardless of order, without repetition.
     */
    equals(col: any): boolean;
    /**
     * Tests whether the given collection contains all the elements in this set.
     * Primitives are treated as equal if they have the same type and convert to the
     * same string; objects are treated as equal if they are references to the same
     * object.  This operation is O(n).
     * @param {Collection<T>|Object} col A collection.
     * @return {boolean} True if this set is a subset of the given collection.
     */
    isSubsetOf(col: any): boolean;
    /**
     * Returns an iterator that iterates over the elements in this set.
     * @param {boolean=} opt_keys This argument is ignored.
     * @return {!Iterator} An iterator over the elements in this set.
     */
    __iterator__(opt_keys?: boolean | undefined): Iterator;
}
declare namespace structs_Set {
    export const getUid_: typeof google.getUid;
}
import { Collection } from "./collection.js";
import { Map as StructsMap } from "./map.js";
import { Iterator } from "../iter/iter.js";
import * as google from "../google.js";
