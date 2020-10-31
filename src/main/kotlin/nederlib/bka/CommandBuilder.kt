package nederlib.bka

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.CommandNode
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

@CommandBuilderDsl
class CommandBuilder<S>(@PublishedApi internal val underlying: ArgumentBuilder<S, *>) {
    
    /**
     * Delegate underlying executes with defaulted single success
     * */
    internal fun executes(command: (CommandContext<S>) -> Unit) {
        underlying.executes { 
            command(it)
            1
        }
    }
    
    fun run(command: S.(CommandArguments) -> Unit) = executes { it.source.command(it.arguments) }
    fun <T: Any> run(args: KClass<T>, command: S.(T) -> Unit) = executes { it.source.command(it.arguments.construct(args)) }
    fun <R: Any> run(function: KFunction<R>) = executes { it.arguments.call(function) }
    
    inline fun then(other: CommandBuilder<S>, builder: CBer<S> = {}): CommandNode<S> {
        val built = other.apply(builder).underlying.build()
        underlying.then(built)
        return built
    }
    
    fun redirect(node: CommandNode<S>) {
        underlying.redirect(node)
    }

    fun redirect(node: CommandNode<S>, modifier: S.(CommandArguments) -> S) { 
        underlying.redirect(node) { it.source.modifier(it.arguments) }
    }
    
    fun fork(node: CommandNode<S>, modifier: S.(CommandArguments) -> Collection<S>) {
        underlying.fork(node) { it.source.modifier(it.arguments) }
    }
    
    fun meet(predicate: S.() -> Boolean) {
        underlying.requires(predicate)
    }
    
    constructor(name: String): this(LiteralArgumentBuilder.literal(name))
    constructor(name: String, argumentType: ArgumentType<*>): this(RequiredArgumentBuilder.argument(name, argumentType))
    
    companion object Freestanding {
        /**
         * @see nederlib.bka.literal
         * */
        inline fun <S> literal(name: String, builder: CBer<S>) = CommandBuilder<S>(name).apply(builder)

        inline fun <S> bool(name: String, builder: CBer<S>) = CommandBuilder<S>(name, BoolArgumentType.bool()).apply(builder)


        inline fun <S> int(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, builder: CBer<S>) =
            CommandBuilder<S>(name, IntegerArgumentType.integer(min, max)).apply(builder)

        inline fun <S> int(name: String, range: IntRange, builder: CBer<S>) = int(name, range.first, range.last, builder)
        inline fun <S> long(name: String, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, builder: CBer<S>) =
            CommandBuilder<S>(name, LongArgumentType.longArg(min, max)).apply(builder)

        inline fun <S> long(name: String, range: LongRange, builder: CBer<S>) = long(name, range.first, range.last, builder)

        inline fun <S> float(name: String, min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE, builder: CBer<S>) =
            CommandBuilder<S>(name, FloatArgumentType.floatArg(min, max)).apply(builder)

        inline fun <S> double(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, builder: CBer<S>) =
            CommandBuilder<S>(name, DoubleArgumentType.doubleArg(min, max)).apply(builder)

        inline fun <S> word(name: String, builder: CBer<S>) = CommandBuilder<S>(name, StringArgumentType.word()).apply(builder)
        inline fun <S> string(name: String, builder: CBer<S>) = CommandBuilder<S>(name, StringArgumentType.string()).apply(builder)
        inline fun <S> greedyString(name: String, builder: CBer<S>) = CommandBuilder<S>(name, StringArgumentType.greedyString()).apply(builder)
        
    }

    inline fun literal(name: String, builder: CBer<S>)                                                                   = then(Freestanding.literal(name, builder))
    inline fun bool(name: String, builder: CBer<S>)                                                                      = then(Freestanding.bool(name, builder))
    inline fun int(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, builder: CBer<S>)                   = then(Freestanding.int(name, min, max, builder))
    inline fun int(name: String, range: IntRange, builder: CBer<S>)                                                      = then(Freestanding.int(name, range, builder))
    inline fun long(name: String, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, builder: CBer<S>)              = then(Freestanding.long(name, min, max, builder))
    inline fun long(name: String, range: LongRange, builder: CBer<S>)                                                    = then(Freestanding.long(name, range, builder))
    inline fun float(name: String, min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE, builder: CBer<S>)        = then(Freestanding.float(name, min, max, builder))
    inline fun double(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, builder: CBer<S>)   = then(Freestanding.double(name, min, max, builder))
    inline fun word(name: String, builder: CBer<S>)                                                                      = then(Freestanding.word(name, builder))
    inline fun string(name: String, builder: CBer<S>)                                                                    = then(Freestanding.string(name, builder))
    inline fun greedyString(name: String, builder: CBer<S>)                                                              = then(Freestanding.greedyString(name, builder))
}


@CommandBuilderDsl
fun <S> CommandDispatcher<S>.literal(name: String, builder: CBer<S>): CommandNode<S> {
    return register(CommandBuilder.literal(name, builder).underlying as LiteralArgumentBuilder<S>)
}

@DslMarker
annotation class CommandBuilderDsl

internal typealias CBer<S> = CommandBuilder<S>.() -> Unit