package nederlib.bka

import com.mojang.brigadier.context.CommandContext
import kotlin.reflect.*
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Source-independent command arguments delegation
 * */
class CommandArguments(
    /**
     * Forbidding accessibility for the type parameter S has been erased here
     * However, source is still available via [unpack] and related methods
     * */
    private val underlying: CommandContext<*>
) {

    fun <T : Any> argumentOf(name: String, type: KClass<T>): T = underlying.getArgument(name, type.java)

    /**
     * Unpack arguments to construct a data class
     * @see unpack
     * */
    fun <R: Any> construct(type: KClass<R>): R {
        require(type.isData) { "unsupported to unpack the context into the non-data class" }
        require(!type.isInner) { "inner class unsupported" }
        require(type.typeParameters.isEmpty()) { "generic type unsupported" }
        val constructor = requireNotNull(type.primaryConstructor) { "primary constructor not found" }
        check(constructor)
        return constructor.callBy(unpack(constructor))
    }

    /**
     * Unpack arguments to call to function, probably with a receiver
     * @see unpack
     * */
    fun <R: Any> call(function: KFunction<R>): R {
        check(function)
        require(!function.isSuspend) { "use suspendCall() to call suspend" }
        return function.callBy(unpack(function))
    }

    /**
     * Unpack arguments to call to suspend function, probably with a receiver
     * @see unpack
     * */
    suspend fun <R: Any> suspendCall(function: KFunction<R>): R {
        check(function)
        return function.callSuspendBy(unpack(function))
    }


    /**
     * Unpack arguments into a map according to a function that possesses a list of
     *   non-vararg, non-generic and non-nullable parameters
     * Exceptionally, if a receiver is available, [underlying].source will be used
     * 
     * Each required parameter of [function]
     *   must be found a corresponding argument in the context
     *   with an assignable type and the exactly same name
     *
     * @throws IllegalArgumentException if any requirement above were not met
     * */
    fun <R: Any> unpack(function: KFunction<R>): Map<KParameter, Any>
            = function.parameters.associateWithTo(HashMap()) { param ->
        require(!param.isVararg) { "vararg parameter unsupported" }
        require(param.type.classifier !is KTypeParameter
                && param.type.arguments.isEmpty()) { "generic parameter unsupported" }
        require(!param.type.isMarkedNullable) { "nullable parameter unsupported" }
        val paramname = param.name
        val typename = (param.type.classifier as KClass<*>).simpleName
        if (paramname == null) {
            require(param.kind != KParameter.Kind.VALUE) { "unnamed value parameter unsupported" }
            val realType = underlying.source.realSourceType
            require(param.type.jvmErasure == realType) 
            { "receiver must be typed S, expected '$typename', found '${realType.simpleName}'" }
            OptionalParameterPlaceholder
        } else try { argumentOf(paramname, param.type.jvmErasure) } catch (e: IllegalArgumentException) {
            val message = e.message ?: throw e
            when {
                " is defined as " in message -> require(false) {
                    val found = message.substringAfter(" is defined as ").substringBefore(", not ")
                    "the type of '$paramname' mismatched, expected '$typename', found '$found'"
                }
                "No such argument" in message -> {
                    require(param.isOptional) {
                        "missing required constructor parameter '$paramname: $typename'"
                    }
                    OptionalParameterPlaceholder
                }
                else -> throw e
            }
        }
    }.mapValues { (key, value) -> if (key.kind != KParameter.Kind.VALUE) underlying.source.realSource else value
    }.filterValues { it != OptionalParameterPlaceholder }

    private companion object OptionalParameterPlaceholder {
        private fun <R> check(function: KFunction<R>) {
            require(function.visibility == KVisibility.PUBLIC) { "function not accessible" }
            require(!function.isAbstract) { "function not implemented" }
            require(function.typeParameters.isEmpty()) { "generic function unsupported" }
            require(function !is KProperty<*>) { "property access unsupported" }
            require(!function.isExternal) { "external function unsupported" }
            require(function.instanceParameter == null || function.extensionReceiverParameter == null)
            { "instance and extension receiver cannot be available at the same time" }
        }
    }
}

val <S> CommandContext<S>.arguments get() = CommandArguments(this)