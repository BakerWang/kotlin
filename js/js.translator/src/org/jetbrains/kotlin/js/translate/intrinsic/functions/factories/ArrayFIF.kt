/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.js.translate.intrinsic.functions.factories

import com.intellij.openapi.util.text.StringUtil.decapitalize
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.PrimitiveType.*
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.patterns.NamePredicate
import org.jetbrains.kotlin.js.patterns.PatternBuilder.pattern
import org.jetbrains.kotlin.js.translate.context.Namer
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.intrinsic.functions.basic.BuiltInPropertyIntrinsic
import org.jetbrains.kotlin.js.translate.intrinsic.functions.basic.FunctionIntrinsicWithReceiverComputed
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.name.Name

object ArrayFIF : CompositeFIF() {
    @JvmField
    val GET_INTRINSIC = intrinsify { receiver, arguments, _ ->
        assert(arguments.size == 1) { "Array get expression must have one argument." }
        val (indexExpression) = arguments
        JsArrayAccess(receiver!!, indexExpression)
    }

    @JvmField
    val SET_INTRINSIC = intrinsify { receiver, arguments, _ ->
        assert(arguments.size == 2) { "Array set expression must have two arguments." }
        val (indexExpression, value) = arguments
        val arrayAccess = JsArrayAccess(receiver!!, indexExpression)
        JsAstUtils.assignment(arrayAccess, value)
    }

    @JvmField
    val LENGTH_PROPERTY_INTRINSIC = BuiltInPropertyIntrinsic("length")

    fun castToTypedArray(p: JsProgram, type: PrimitiveType?, arg: JsArrayLiteral): JsExpression {
        return when (type) {
            BOOLEAN -> markType(p.getStringLiteral("BooleanArray"), arg)
            CHAR -> markType(p.getStringLiteral("CharArray"), arg)
            BYTE -> JsNew(JsNameRef("Int8Array"), listOf(arg))
            SHORT -> JsNew(JsNameRef("Int16Array"), listOf(arg))
            INT -> JsNew(JsNameRef("Int32Array"), listOf(arg))
            FLOAT -> JsNew(JsNameRef("Float32Array"), listOf(arg))
            DOUBLE -> JsNew(JsNameRef("Float64Array"), listOf(arg))
            LONG -> markType(p.getStringLiteral("LongArray"), arg)
            else -> arg
        }
    }

    init {
        val arrayTypeNames = mutableListOf(KotlinBuiltIns.FQ_NAMES.array.shortName())
        PrimitiveType.values().mapTo(arrayTypeNames) { it.arrayTypeName }

        val arrays = NamePredicate(arrayTypeNames)

        add(pattern(arrays, "get"), GET_INTRINSIC)
        add(pattern(arrays, "set"), SET_INTRINSIC)
        add(pattern(arrays, "<get-size>"), LENGTH_PROPERTY_INTRINSIC)
        add(pattern(arrays, "iterator"), KotlinFunctionIntrinsic("arrayIterator"))

        add(BOOLEAN.arrayPattern(), arrayWithTypePropertyIntrinsic("BooleanArray"))
        add(CHAR.arrayPattern(), arrayWithTypePropertyIntrinsic("CharArray"))
        add(BYTE.arrayPattern(), typedArrayIntrinsic("Int8"))
        add(SHORT.arrayPattern(), typedArrayIntrinsic("Int16"))
        add(INT.arrayPattern(), typedArrayIntrinsic("Int32"))
        add(FLOAT.arrayPattern(), typedArrayIntrinsic("Float32"))
        add(LONG.arrayPattern(), arrayWithTypePropertyIntrinsic("LongArray"))
        add(DOUBLE.arrayPattern(), typedArrayIntrinsic("Float64"))

        add(BOOLEAN.arrayFPattern(), arrayFWithTypePropertyIntrinsic("BooleanArray"))
        add(CHAR.arrayFPattern(), arrayFWithTypePropertyIntrinsic("CharArray"))
        add(BYTE.arrayFPattern(), typedArrayFIntrinsic("Int8"))
        add(SHORT.arrayFPattern(), typedArrayFIntrinsic("Int16"))
        add(INT.arrayFPattern(), typedArrayFIntrinsic("Int32"))
        add(FLOAT.arrayFPattern(), typedArrayFIntrinsic("Float32"))
        add(LONG.arrayFPattern(), arrayFWithTypePropertyIntrinsic("LongArray"))
        add(DOUBLE.arrayFPattern(), typedArrayFIntrinsic("Float64"))

        add(pattern(arrays, "<init>(Int,Function1)"), KotlinFunctionIntrinsic("newArrayF"))

        add(pattern(Namer.KOTLIN_LOWER_NAME, "arrayOfNulls"), KotlinFunctionIntrinsic("newArray", JsLiteral.NULL))

        val arrayFactoryMethodNames = arrayTypeNames.map { Name.identifier(it.toArrayOf()) }
        val arrayFactoryMethods = pattern(Namer.KOTLIN_LOWER_NAME, NamePredicate(arrayFactoryMethodNames))
        add(arrayFactoryMethods, intrinsify { _, arguments, _ -> arguments[0] })
    }

    private fun PrimitiveType.arrayPattern() = pattern(NamePredicate(arrayTypeName), "<init>(Int)")
    private fun PrimitiveType.arrayFPattern() = pattern(NamePredicate(arrayTypeName), "<init>(Int,Function1)")

    private fun Name.toArrayOf() = decapitalize(this.asString() + "Of")

    private fun typedArrayIntrinsic(typeName: String) = intrinsify { _, arguments, _ ->
        JsNew(JsNameRef(typeName + "Array"), arguments)
    }

    private fun markType(typeName: JsStringLiteral, e: JsExpression): JsExpression {
        return JsAstUtils.invokeKotlinFunction("withType", typeName, e);
    }

    private fun arrayWithTypePropertyIntrinsic(typeName: String) = intrinsify { _, arguments, context ->
        assert(arguments.size == 1) { "Array <init>(Int) expression must have one argument." }
        val (size) = arguments
        markType(context.program().getStringLiteral(typeName), JsAstUtils.invokeKotlinFunction("newArray", size))
    }

    private fun arrayFWithTypePropertyIntrinsic(typeName: String) = intrinsify { _, arguments, context ->
        assert(arguments.size == 2) { "Array <init>(Int, Function1) expression must have two arguments." }
        val (size, fn) = arguments
        markType(context.program().getStringLiteral(typeName), JsAstUtils.invokeKotlinFunction("newArrayF", size, fn))
    }

    private fun typedArrayFIntrinsic(typeName: String) = intrinsify { _, arguments, _ ->
        assert(arguments.size == 2) { "Array <init>(Int, Function1) expression must have two arguments." }
        val (size, fn) = arguments
        JsNew(JsNameRef(typeName + "Array"), listOf(JsAstUtils.invokeKotlinFunction("newArrayF", size, fn)))
    }

    private fun intrinsify(f: (receiver: JsExpression?, arguments: List<JsExpression>, context: TranslationContext) -> JsExpression)
        = object : FunctionIntrinsicWithReceiverComputed() {
            override fun apply(receiver: JsExpression?, arguments: List<JsExpression>, context: TranslationContext): JsExpression {
                return f(receiver, arguments, context)
            }
        }
}