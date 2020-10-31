package nederlib.bka

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.CommandNode
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Allowing suspend operations by delaying invocations
 * Use suspend bridged versions of extensions to enable suspend-ability
 * */
class SuspendBridge<S>(val source: S) { 
    @PublishedApi internal val suspends = mutableListOf<suspend () -> Unit>()
}

val <S> S.suspendBridgedSource get() = SuspendBridge(this)

internal val <S: Any> S.realSourceType get(): KClass<*> = if (this is SuspendBridge<*>) { source!!::class } else this::class
internal val <S: Any> S.realSource get(): Any = if (this is SuspendBridge<*>) { source!! } else this

internal operator fun <S> CommandContext<SuspendBridge<S>>.plusAssign(suspend: suspend () -> Unit) {
    source.suspends += suspend
} 

fun <S> CommandBuilder<SuspendBridge<S>>.runSuspend(command: suspend S.(CommandArguments) -> Unit) 
        = executes { it += { it.source.source.command(it.arguments) } }

fun <S, T: Any> CommandBuilder<SuspendBridge<S>>
        .runSuspend(args: KClass<T>, command: suspend S.(T) -> Unit)
        = executes { it += { it.source.source.apply { command(it.arguments.construct(args)) } } }

/**
 * You should either restore a local reference or suppress suspension error to use [runSuspend] 
 *   in a builder with a coroutine context outside the builder
 * ```
 * // without coroutine context here outside
 * val localReference = ::mySuspendFunction // OK: directly refer to suspend in non-suspend context
 * dispatcher.register(...) {
 *     ...
 *         runSuspend(::mySuspendFunction) // OK: directly refer to suspend in non-suspend context with no outer suspend context
 *     ...
 * }
 * // within coroutine context here outside
 * val localReference = ::mySuspendFunction // OK: directly refer to suspend in suspend context
 * dispatcher.register(...) {
 *     ...
 *         runSuspend(localReference) // OK: indirectly use local reference
 *         runSuspend(::mySuspendFunction) // Error: directly refer to suspend in non-suspend context with an outer suspend context
 *         runSuspendLazy { ::mySuspendFunction } // Error
 *         runSuspendLazy(suspend { ::mySuspendFunction }) // Error
 *         runSuspendLazy { localReference } // OK
 *         runSuspendLazy(suspend { localReference }) // OK
 *         @Suppress(runSuspendSuppressor)
 *         runSuspend(::mySuspendFunction) // OK: suppress without runtime error
 *     ...
 * }
 * ```
 * */
fun <S, R: Any> CommandBuilder<SuspendBridge<S>>.runSuspend(function: KFunction<R>) 
        = executes { it += { it.arguments.suspendCall(function) } }

const val runSuspendSuppressor = "NON_LOCAL_SUSPENSION_POINT"

@Deprecated(message = "only available for KDoc", 
    replaceWith = ReplaceWith("runSuspend", imports = ["nederlib.bka"]), 
    level = DeprecationLevel.HIDDEN)
internal fun <S, R: Any> CommandBuilder<SuspendBridge<S>>.runSuspendLazy(function: suspend () -> KFunction<R>)
        = executes { it += { it.arguments.suspendCall(function()) } }

fun <S> CommandBuilder<SuspendBridge<S>>.redirectSuspend(
    node: CommandNode<SuspendBridge<S>>, modifier: S.(CommandArguments) -> S) {
    underlying.redirect(node) { it.source.source.modifier(it.arguments).suspendBridgedSource }
}

fun <S> CommandBuilder<SuspendBridge<S>>.forkSuspend(
    node: CommandNode<SuspendBridge<S>>, modifier: S.(CommandArguments) -> Collection<S>) {
    underlying.fork(node) { it.source.source.modifier(it.arguments).map { it.suspendBridgedSource } }
}

fun <S> CommandBuilder<SuspendBridge<S>>.meetSuspend(predicate: S.() -> Boolean) = meet { source.predicate() }

typealias SuspendCommandDispatcher<S> = CommandDispatcher<SuspendBridge<S>>

suspend inline fun <S> CommandDispatcher<SuspendBridge<S>>.executeSuspend(input: String, source: S): Int {
    val suspendBridge = SuspendBridge(source)
    val ret = execute(input, suspendBridge)
    suspendBridge.suspends.forEach { it() }
    return ret
}