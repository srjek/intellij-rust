/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.types.inference
import org.rust.lang.utils.addToHolder

abstract class RsDiagnosticBasedInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitFunction(o: RsFunction) = collectDiagnostics(holder, o)
        override fun visitConstant(o: RsConstant) = collectDiagnostics(holder, o)
        override fun visitArrayType(o: RsArrayType) = collectDiagnostics(holder, o)
        override fun visitVariantDiscriminant(o: RsVariantDiscriminant) = collectDiagnostics(holder, o)
        override fun visitConstExpr(o: RsConstExpr) = collectDiagnostics(holder, o)
    }

    private fun collectDiagnostics(holder: RsProblemsHolder, element: RsInferenceContextOwner) {
        for (it in element.inference.diagnostics) {
            if (it.inspectionClass == javaClass) it.addToHolder(holder)
        }
    }
}

class RsExperimentalChecksInspection : RsDiagnosticBasedInspection()

class RsTypeCheckInspection : RsDiagnosticBasedInspection()
