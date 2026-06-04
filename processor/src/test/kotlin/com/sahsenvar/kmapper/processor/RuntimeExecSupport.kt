@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import java.lang.reflect.InvocationTargetException
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

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
fun JvmCompilationResult.invokeMapper(fileKtClass: String, fnName: String, receiver: Any?): Any? {
    val m = classLoader.loadClass(fileKtClass).declaredMethods.first { it.name == fnName }
    return try {
        m.invoke(null, receiver)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

/**
 * Instantiates a class from the compilation classloader by matching constructor arity.
 *
 * Selects the first declared constructor whose parameter count matches [args].size,
 * so callers can create data class instances without knowing the exact parameter types
 * across classloaders.
 */
fun JvmCompilationResult.newInstance(className: String, vararg args: Any?): Any {
    val ctor = classLoader.loadClass(className).declaredConstructors
        .first { it.parameterCount == args.size }
    return ctor.newInstance(*args)
}

/**
 * Reads a property value from any object via its getter method.
 *
 * Capitalises the first letter of [name] and prepends "get" to form the getter name.
 * Works across classloaders since it uses the object's own class.
 */
fun Any.prop(name: String): Any? =
    this::class.java.getMethod("get" + name.replaceFirstChar { it.uppercase() }).invoke(this)
