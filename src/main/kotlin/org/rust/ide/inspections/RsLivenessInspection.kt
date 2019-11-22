/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.rust.ide.colors.RsColor
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.types.DeclarationKind
import org.rust.lang.core.types.DeclarationKind.Parameter
import org.rust.lang.core.types.DeclarationKind.Variable
import org.rust.lang.core.types.liveness

class RsLivenessInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(func: RsFunction) {
                // Disable inside doc tests
                if (func.isDoctestInjection) return

                // #[allow(unused)]
                if (func.queryAttributes.hasAttributeWithArg("allow", "unused")) return

                // Don't analyze functions with unresolved macro calls
                if (func.descendantsOfType<RsMacroCall>().any { it.resolveToMacro() == null }) return

                // TODO: fix backwards propagation when multiple exits presented
                if (func.descendantsOfType<RsLoopExpr>().any()) return

                val liveness = func.liveness ?: return

                for (deadDeclaration in liveness.deadDeclarations) {
                    val name = deadDeclaration.binding.name ?: continue
                    if (name.startsWith("_")) continue
                    registerUnusedProblem(holder, deadDeclaration.binding, name, deadDeclaration.kind)
                }
            }
        }

    private fun registerUnusedProblem(
        holder: RsProblemsHolder,
        binding: RsPatBinding,
        name: String,
        kind: DeclarationKind
    ) {
        if (!binding.isPhysical) return

        // TODO: remove this check when multi-resolve for `RsOrPats` is implemented
        if (binding.ancestorStrict<RsOrPats>() != null) return

        val message = when (kind) {
            Parameter -> "Parameter `$name` is never used"
            Variable -> "Variable `$name` is never used"
        }
        val descriptor = holder.manager.createProblemDescriptor(
            binding,
            message,
            RenameFix(binding, "_$name"),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            holder.isOnTheFly
        )
        descriptor.setTextAttributes(RsColor.DEAD_CODE.textAttributesKey)
        holder.registerProblem(descriptor)
    }
}
