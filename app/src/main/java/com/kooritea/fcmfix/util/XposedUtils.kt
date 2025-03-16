package com.kooritea.fcmfix.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.math.min

object XposedUtils {
    fun findAndHookConstructorAnyParam(
        className: String?,
        classLoader: ClassLoader?,
        callbacks: XC_MethodHook?,
        vararg parameterTypes: Class<*>?
    ): XC_MethodHook.Unhook {
        val clazz = XposedHelpers.findClass(className, classLoader)
        return findAndHookConstructorAnyParam(clazz, callbacks, *parameterTypes)
    }

    fun findAndHookConstructorAnyParam(
        clazz: Class<*>, callbacks: XC_MethodHook?, vararg parameterTypes: Class<*>?
    ): XC_MethodHook.Unhook {
        var bestMatch: Constructor<*>? = null
        var matchCount = 0
        for (constructor in clazz.declaredConstructors) {
            val constructorParamTypes = constructor.parameterTypes
            var _matchCount = 0
            for (i in 0..<min(
                constructorParamTypes.size.toDouble(),
                parameterTypes.size.toDouble()
            ).toInt()) {
                if (parameterTypes[i] == constructorParamTypes[i]) {
                    _matchCount++
                }
            }
            if (_matchCount >= matchCount) {
                matchCount = _matchCount
                bestMatch = constructor
            }
        }
        if (bestMatch == null) {
            throw NoSuchMethodError(clazz.name)
        }
        return XposedBridge.hookMethod(
            XposedHelpers.findConstructorExact(
                clazz,
                *bestMatch.parameterTypes
            ), callbacks
        )
    }

    fun findAndHookMethodMostParam(
        clazz: Class<*>,
        methodName: String,
        callbacks: XC_MethodHook?
    ): XC_MethodHook.Unhook {
        var bestMatch: Method? = null
        for (method in clazz.declaredMethods) {
            if (methodName == method.name) {
                if (bestMatch == null || method.parameterTypes.size > bestMatch.parameterTypes.size) {
                    bestMatch = method
                }
            }
        }
        if (bestMatch == null) {
            throw NoSuchMethodError(clazz.name + '#' + methodName)
        }
        return XposedBridge.hookMethod(
            XposedHelpers.findMethodExact(
                clazz,
                methodName,
                *bestMatch.parameterTypes
            ), callbacks
        )
    }

    fun findAndHookMethodAnyParam(
        clazz: Class<*>,
        methodName: String,
        callbacks: XC_MethodHook?,
        vararg parameterTypes: Any?
    ): XC_MethodHook.Unhook {
        var bestMatch: Method? = null
        var matchCount = 0
        for (method in clazz.declaredMethods) {
            if (methodName == method.name) {
                val methodParamTypes = method.parameterTypes
                var _matchCount = 0
                for (i in 0..<min(
                    methodParamTypes.size.toDouble(),
                    parameterTypes.size.toDouble()
                ).toInt()) {
                    if (parameterTypes[i] === methodParamTypes[i]) {
                        _matchCount++
                    }
                }
                if (_matchCount >= matchCount) {
                    matchCount = _matchCount
                    bestMatch = method
                }
            }
        }
        if (bestMatch == null) {
            throw NoSuchMethodError(clazz.name + '#' + methodName)
        }
        return XposedBridge.hookMethod(
            XposedHelpers.findMethodExact(
                clazz,
                methodName,
                *bestMatch.parameterTypes
            ), callbacks
        )
    }

    fun tryFindAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        parameterCount: Int,
        callbacks: XC_MethodHook?
    ): XC_MethodHook.Unhook? {
        return try {
            findAndHookMethod(clazz, methodName, parameterCount, callbacks)
        } catch (e: NoSuchMethodError) {
            null
        }
    }

    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        parameterCount: Int,
        callbacks: XC_MethodHook?
    ): XC_MethodHook.Unhook {
        var method: Method? = null
        for (m in clazz.declaredMethods) {
            if (m.name == methodName && m.parameterTypes.size == parameterCount) {
                method = m
            }
        }
        if (method == null) {
            throw NoSuchMethodError(clazz.name + '#' + methodName)
        }
        return XposedBridge.hookMethod(
            XposedHelpers.findMethodExact(
                clazz,
                methodName,
                *method.parameterTypes
            ), callbacks
        )
    }

    fun findMethod(clazz: Class<*>, methodName: String, parameterCount: Int): Method? {
        var method: Method? = null
        for (m in clazz.declaredMethods) {
            if (m.name == methodName && m.parameterTypes.size == parameterCount) {
                method = m
            }
        }
        return method
    }

    fun findAndHookMethodAnyParam(
        className: String?,
        classLoader: ClassLoader?,
        methodName: String,
        callbacks: XC_MethodHook?,
        vararg parameterTypes: Any?
    ): XC_MethodHook.Unhook {
        val clazz = XposedHelpers.findClass(className, classLoader)
        return findAndHookMethodAnyParam(clazz, methodName, callbacks, *parameterTypes)
    }

    fun getObjectFieldByPath(obj: Any, pathFieldName: String, clazz: Class<*>): Any {
        val result = getObjectFieldByPath(obj, pathFieldName)
        if (result.javaClass != clazz) {
            throw NoSuchFieldError(obj.javaClass.name + "#" + pathFieldName + ";Found " + result.javaClass.name + " but not equal " + clazz.name + ".")
        }
        return result
    }

    @JvmStatic
    fun getObjectFieldByPath(obj: Any, pathFieldName: String): Any {
        val paths =
            pathFieldName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var tmp = obj
        try {
            for (fieldName in paths) {
                tmp = XposedHelpers.getObjectField(tmp, fieldName)
            }
        } catch (e: Exception) {
            throw NoSuchFieldError(obj.javaClass.name + "#" + pathFieldName)
        }
        return tmp
    }
}
