package com.zemlianikin.flexiblesearch.plugins.converter.calc

import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Balancer
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.BracketBalancer
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Evaluator
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Lexer
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.LexiconLexer
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Parser
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.RecursiveDescentParser
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Stop
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.Token
import com.zemlianikin.flexiblesearch.plugins.converter.calc.engine.TypedEvaluator
import com.zemlianikin.flexiblesearch.plugins.converter.core.RateTable

/**
 * Конвейер: Lexer → Balancer → Parser → Evaluator. Stop любого этапа
 * становится результатом.
 */
class PipelineCalculator(
    private val lexer: Lexer,
    private val balancer: Balancer,
    private val parser: Parser,
    private val evaluator: Evaluator,
) : Calculator {
    override fun calculate(text: String, rates: RateTable): Calculation =
        try {
            val balanced = balancer.balance(lexer.lex(text))
            val value = evaluator.eval(parser.parse(balanced.tokens), rates)
            val currencies =
                balanced.tokens
                    .filterIsInstance<Token.Currency>()
                    .map { it.code }
                    .toSet()
            Calculation.Ok(value, balanced.virtualParens, currencies)
        } catch (stop: Stop) {
            stop.outcome
        }
}

/** Калькулятор со стандартными этапами. */
fun defaultCalculator(): Calculator =
    PipelineCalculator(
        LexiconLexer(),
        BracketBalancer(),
        RecursiveDescentParser(),
        TypedEvaluator(),
    )
