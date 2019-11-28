/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsConstExpr
import org.rust.lang.core.psi.RsLifetime
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.resolve.ref.RsMethodCallReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference

val RsMethodCall.lifetimeArguments: List<RsLifetime> get() = typeArgumentList?.lifetimeList.orEmpty()

val RsMethodCall.typeArguments: List<RsTypeReference> get() = typeArgumentList?.typeReferenceList.orEmpty()

val RsMethodCall.constArguments: List<RsConstExpr> get() = typeArgumentList?.constExprList.orEmpty()

val RsMethodCall.textRangeWithoutValueArguments: TextRange
    get() = TextRange(startOffset, typeArgumentList?.endOffset ?: identifier.endOffset)

abstract class RsMethodCallImplMixin(node: ASTNode) : RsElementImpl(node), RsMethodCall {
    override val referenceNameElement: PsiElement get() = identifier

    override fun getReference(): RsReference = RsMethodCallReferenceImpl(this)
}
