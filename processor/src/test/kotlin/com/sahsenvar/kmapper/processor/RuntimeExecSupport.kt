@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.lang.reflect.InvocationTargetException

/**
 * Invokes a KSP-generated top-level extension function via reflection.
 *
 * Generated top-level extension `fun Src.toX()` compiles to a static method on the
 * file's companion object class. Naming convention:
 *   source class `UserRemote` → file `UserRemoteMappers.kt` → JVM class `UserRemoteMappersKt`
 *   the extension `fun UserRemote.toUserDomain()` → static method `toUserDomain(UserRemote)`
 *
 * Uses `declaredMethods.first { it.name == fnName }` to avoid param-type lookup across
 * classloaders. Unwraps [InvocationTargetException] so callers receive the real thrown
 * exception (e.g. [com.sahsenvar.kmapper.MappingException.RequiredFieldMissing]).
 */
fun JvmCompilationResult.invokeMapper(
    fileKtClass: String,
    fnName: String,
    receiver: Any?,
): Any? {
    val m = classLoader.loadClass(fileKtClass).declaredMethods.first { it.name == fnName }
    return try {
        m.invoke(null, receiver)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

/**
 * Invokes a generated Result-boundary mapper (`fun Src.toXResult(): Result<X>`) reflectively
 * and re-boxes the JVM-level return into a typed [Result].
 *
 * `kotlin.Result` is a value class over `Any?`, so the compiled static method's name carries
 * a value-class mangling suffix (e.g. `toXResult-IoAF18A`) and its erased return value is the
 * UNBOXED underlying value: the success value itself, or the internal `kotlin.Result$Failure`
 * wrapper carrying the exception. This helper matches the mangled name with a prefix check and
 * detects the failure wrapper by class name (the stdlib is shared parent-first across the
 * kctfork classloader, but `Result.Failure` is internal — reflection keeps us decoupled).
 */
fun JvmCompilationResult.invokeResultMapper(
    fileKtClass: String,
    fnName: String,
    receiver: Any?,
    vararg extraArgs: Any?,
): Result<Any?> {
    val method =
        classLoader
            .loadClass(fileKtClass)
            .declaredMethods
            .first { it.name == fnName || it.name.startsWith("$fnName-") }
    val rawReturn =
        try {
            method.invoke(null, receiver, *extraArgs)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    return if (rawReturn != null && rawReturn.javaClass.name == "kotlin.Result\$Failure") {
        val exceptionField = rawReturn.javaClass.getDeclaredField("exception").apply { isAccessible = true }
        Result.failure(exceptionField.get(rawReturn) as Throwable)
    } else {
        Result.success(rawReturn)
    }
}

/**
 * Instantiates a class from the compilation classloader by matching constructor arity.
 *
 * Selects the first declared constructor whose parameter count matches [args].size,
 * so callers can create data class instances without knowing the exact parameter types
 * across classloaders.
 */
fun JvmCompilationResult.newInstance(
    className: String,
    vararg args: Any?,
): Any {
    val ctor =
        classLoader
            .loadClass(className)
            .declaredConstructors
            .first { it.parameterCount == args.size }
    return ctor.newInstance(*args)
}

/**
 * Reads a property value from any object via its getter method.
 *
 * Capitalises the first letter of [name] and prepends "get" to form the getter name.
 * Works across classloaders since it uses the object's own class.
 */
fun Any.prop(name: String): Any? = this::class.java.getMethod("get" + name.replaceFirstChar { it.uppercase() }).invoke(this)
