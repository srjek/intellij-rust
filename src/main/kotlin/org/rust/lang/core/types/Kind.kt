/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

typealias TypeFlags = Int

const val HAS_TY_INFER_MASK: TypeFlags = 1
const val HAS_TY_TYPE_PARAMETER_MASK: TypeFlags = 2
const val HAS_TY_PROJECTION_MASK: TypeFlags = 4
const val HAS_RE_EARLY_BOUND_MASK: TypeFlags = 8
const val HAS_CT_INFER_MASK: TypeFlags = 16
const val HAS_CT_PARAMETER_MASK: TypeFlags = 32

/**
 * An entity in the Rust typesystem, which can be one of several kinds (only types, lifetimes and constants for now).
 */
interface Kind {
    val flags: TypeFlags
}

fun mergeFlags(kinds: Collection<Kind>): TypeFlags = kinds.fold(0) { a, b -> a or b.flags }

fun mergeFlags(element: BoundElement<*>): TypeFlags =
    mergeFlags(element.subst.kinds) or mergeFlags(element.assoc.values)
