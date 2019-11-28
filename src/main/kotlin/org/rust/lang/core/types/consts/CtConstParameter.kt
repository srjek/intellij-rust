/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.consts

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.types.HAS_CT_PARAMETER_MASK
import org.rust.lang.core.types.TypeFlags

class CtConstParameter(parameter: RsConstParameter) : Const() {
    val parameter: RsConstParameter = CompletionUtil.getOriginalOrSelf(parameter)

    override val flags: TypeFlags = HAS_CT_PARAMETER_MASK

    override fun equals(other: Any?): Boolean = other is CtConstParameter && other.parameter == parameter

    override fun hashCode(): Int = parameter.hashCode()

    override fun toString(): String = parameter.name ?: "<unknown>"
}
