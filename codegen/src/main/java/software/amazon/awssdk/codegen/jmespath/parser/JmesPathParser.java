/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen.jmespath.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.awssdk.codegen.internal.Jackson;
import software.amazon.awssdk.codegen.jmespath.component.AndExpression;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifier;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithContents;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithQuestionMark;
import software.amazon.awssdk.codegen.jmespath.component.Comparator;
import software.amazon.awssdk.codegen.jmespath.component.ComparatorExpression;
import software.amazon.awssdk.codegen.jmespath.component.CurrentNode;
import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.component.ExpressionType;
import software.amazon.awssdk.codegen.jmespath.component.FunctionArg;
import software.amazon.awssdk.codegen.jmespath.component.FunctionExpression;
import software.amazon.awssdk.codegen.jmespath.component.IndexExpression;
import software.amazon.awssdk.codegen.jmespath.component.KeyValueExpression;
import software.amazon.awssdk.codegen.jmespath.component.Literal;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectHash;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectList;
import software.amazon.awssdk.codegen.jmespath.component.NotExpression;
import software.amazon.awssdk.codegen.jmespath.component.OrExpression;
import software.amazon.awssdk.codegen.jmespath.component.ParenExpression;
import software.amazon.awssdk.codegen.jmespath.component.PipeExpression;
import software.amazon.awssdk.codegen.jmespath.component.SliceExpression;
import software.amazon.awssdk.codegen.jmespath.component.StarExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpressionRight;
import software.amazon.awssdk.codegen.jmespath.parser.util.CompositeParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.ConvertingParser;

public class JmesPathParser {
    private JmesPathParser() {
    }

    public static Expression parse(String jmesPathString) {
        return new ParsingVisitor(jmesPathString).parse();
    }

    private static final class ParsingVisitor {
        private final ParserContext context;

        private ParsingVisitor(String input) {
            this.context = new ParserContext(input);
        }

        private Expression parse() {
            ParseResult<Expression> expression = parseExpression(0, context.input().length());
            if (expression.hasError()) {
                ParseError error = expression.getError();
                throw new IllegalArgumentException("Failed to parse expression:\n" + error.errorMessage());
            }

            return expression.getResult();
        }

        /**
         * expression        = sub-expression / index-expression  / comparator-expression
         * expression        =/ or-expression / identifier
         * expression        =/ and-expression / not-expression / paren-expression
         * expression        =/ "*" / multi-select-list / multi-select-hash / literal
         * expression        =/ function-expression / pipe-expression / raw-string
         * expression        =/ current-node
         */
        private ParseResult<Expression> parseExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (startPosition < 0 || endPosition > context.input().length() + 1) {
                return ParseResult.error("expression", "Illegal parse range: [" + startPosition + ", " + endPosition + "]",
                                         startPosition);
            }

            return new CompositeParser<>("expression",
                                         new ConvertingParser<>(this::parseSubExpression, Expression::subExpression),
                                         new ConvertingParser<>(this::parseIndexExpression, Expression::indexExpression),
                                         new ConvertingParser<>(this::parseComparatorExpression,
                                                                Expression::comparatorExpression),
                                         new ConvertingParser<>(this::parseOrExpression, Expression::orExpression),
                                         new ConvertingParser<>(this::parseIdentifier, Expression::identifier),
                                         new ConvertingParser<>(this::parseAndExpression, Expression::andExpression),
                                         new ConvertingParser<>(this::parseNotExpression, Expression::notExpression),
                                         new ConvertingParser<>(this::parseParenExpression, Expression::parenExpression),
                                         new ConvertingParser<>(this::parseStarExpression, Expression::starExpression),
                                         new ConvertingParser<>(this::parseMultiSelectList, Expression::multiSelectList),
                                         new ConvertingParser<>(this::parseMultiSelectHash, Expression::multiSelectHash),
                                         new ConvertingParser<>(this::parseLiteral, Expression::literal),
                                         new ConvertingParser<>(this::parseFunctionExpression, Expression::functionExpression),
                                         new ConvertingParser<>(this::parsePipeExpression, Expression::pipeExpression),
                                         new ConvertingParser<>(this::parseRawString, Expression::rawString),
                                         new ConvertingParser<>(this::parseCurrentNode, Expression::currentNode))
                .parse(startPosition, endPosition);
        }

        /**
         * sub-expression    = expression "." ( identifier /
         * multi-select-list /
         * multi-select-hash /
         * function-expression /
         * "*" )
         */
        private ParseResult<SubExpression> parseSubExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            List<Integer> dotPositions = findCharacters(startPosition + 1, endPosition - 1, ".");
            for (Integer dotPosition : dotPositions) {
                ParseResult<Expression> leftSide = parseExpression(startPosition, dotPosition);
                if (leftSide.hasError()) {
                    continue;
                }

                ParseResult<SubExpressionRight> rightSide =
                    new CompositeParser<>("sub-expression-right",
                                          new ConvertingParser<>(this::parseIdentifier, SubExpressionRight::identifier),
                                          new ConvertingParser<>(this::parseMultiSelectList, SubExpressionRight::multiSelectList),
                                          new ConvertingParser<>(this::parseMultiSelectHash, SubExpressionRight::multiSelectHash),
                                          new ConvertingParser<>(this::parseFunctionExpression,
                                                                 SubExpressionRight::functionExpression),
                                          new ConvertingParser<>(this::parseStarExpression, SubExpressionRight::starExpression))
                        .parse(dotPosition + 1, endPosition);
                if (rightSide.hasError()) {
                    continue;
                }

                return ParseResult.success(new SubExpression(leftSide.getResult(), rightSide.getResult()));
            }

            return ParseResult.error("sub-expression", "Invalid sub-expression", startPosition);
        }

        /**
         * pipe-expression   = expression "|" expression
         */
        private ParseResult<PipeExpression> parsePipeExpression(int startPosition, int endPosition) {
            return parseBinaryExpression(startPosition, endPosition, "|", PipeExpression::new);
        }

        /**
         * or-expression     = expression "||" expression
         */
        private ParseResult<OrExpression> parseOrExpression(int startPosition, int endPosition) {
            return parseBinaryExpression(startPosition, endPosition, "||", OrExpression::new);
        }

        /**
         * and-expression    = expression "&&" expression
         */
        private ParseResult<AndExpression> parseAndExpression(int startPosition, int endPosition) {
            return parseBinaryExpression(startPosition, endPosition, "&&", AndExpression::new);
        }

        private <T> ParseResult<T> parseBinaryExpression(int startPosition, int endPosition, String delimiter,
                                                         BiFunction<Expression, Expression, T> constructor) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            List<Integer> delimiterPositions = findCharacters(startPosition + 1, endPosition - 1, delimiter);
            for (Integer delimiterPosition : delimiterPositions) {
                ParseResult<Expression> leftSide = parseExpression(startPosition, delimiterPosition);
                if (leftSide.hasError()) {
                    continue;
                }

                ParseResult<Expression> rightSide = parseExpression(delimiterPosition + delimiter.length(), endPosition);
                if (rightSide.hasError()) {
                    continue;
                }

                return ParseResult.success(constructor.apply(leftSide.getResult(), rightSide.getResult()));
            }

            return ParseResult.error("binary-expression", "Invalid binary-expression", startPosition);
        }

        /**
         * not-expression    = "!" expression
         */
        private ParseResult<NotExpression> parseNotExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != '!') {
                return ParseResult.error("not-expression", "Expected '!'", startPosition);
            }

            return parseExpression(startPosition + 1, endPosition).mapResult(NotExpression::new);
        }

        /**
         * paren-expression  = "(" expression ")"
         */
        private ParseResult<ParenExpression> parseParenExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != '(') {
                return ParseResult.error("paren-expression", "Expected '('", startPosition);
            }

            if (context.input().charAt(endPosition - 1) != ')') {
                return ParseResult.error("paren-expression", "Expected ')'", endPosition - 1);
            }

            return parseExpression(startPosition + 1, endPosition - 1).mapResult(ParenExpression::new);
        }

        /**
         * index-expression  = expression bracket-specifier / bracket-specifier
         */
        private ParseResult<IndexExpression> parseIndexExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            return new CompositeParser<>("bracket-specifier",
                                         new ConvertingParser<>(this::parseIndexExpressionWithLhsExpression, Function.identity()),
                                         new ConvertingParser<>(this::parseBracketSpecifier, b ->
                                             IndexExpression.indexExpression(null, b)))
                .parse(startPosition, endPosition);
        }

        /**
         * expression bracket-specifier
         */
        private ParseResult<IndexExpression> parseIndexExpressionWithLhsExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            List<Integer> bracketPositions = findCharacters(startPosition + 1, endPosition - 1, "[");
            for (Integer bracketPosition : bracketPositions) {
                ParseResult<Expression> leftSide = parseExpression(startPosition, bracketPosition);
                if (leftSide.hasError()) {
                    continue;
                }

                ParseResult<BracketSpecifier> rightSide = parseBracketSpecifier(bracketPosition, endPosition);
                if (rightSide.hasError()) {
                    continue;
                }

                return ParseResult.success(IndexExpression.indexExpression(leftSide.getResult(), rightSide.getResult()));
            }

            return ParseResult.error("index-expression with lhs-expression", "Invalid index-expression with lhs-expression",
                                     startPosition);
        }

        /**
         * multi-select-list = "[" ( expression *( "," expression ) ) "]"
         */
        private ParseResult<MultiSelectList> parseMultiSelectList(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '[', ']', this::parseExpression)
                .mapResult(MultiSelectList::new);
        }

        /**
         * multi-select-hash = "{" ( keyval-expr *( "," keyval-expr ) ) "}"
         */
        private ParseResult<MultiSelectHash> parseMultiSelectHash(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '{', '}', this::parseKeyValueExpression)
                .mapResult(MultiSelectHash::new);
        }

        /**
         * Parses "startDelimiter" ( entryParserType *( "," entryParserType ) ) "endDelimiter"
         * <p>
         * Used by {@link #parseMultiSelectHash}, {@link #parseMultiSelectList}.
         */
        private <T> ParseResult<List<T>> parseMultiSelect(int startPosition, int endPosition,
                                                          char startDelimiter, char endDelimiter,
                                                          Parser<T> entryParser) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != startDelimiter) {
                return ParseResult.error("multi-select", "Expected '" + startDelimiter + "'", startPosition);
            }

            if (context.input().charAt(endPosition - 1) != endDelimiter) {
                return ParseResult.error("multi-select", "Expected '" + endDelimiter + "'", endPosition - 1);
            }

            List<Integer> commaPositions = findCharacters(startPosition + 1, endPosition - 1, ",");

            if (commaPositions.isEmpty()) {
                return entryParser.parse(startPosition + 1, endPosition - 1).mapResult(Collections::singletonList);
            }

            List<T> results = new ArrayList<>();

            // Find first valid entries before a comma
            int startOfSecondEntry = -1;
            for (Integer comma : commaPositions) {
                ParseResult<T> result = entryParser.parse(startPosition + 1, comma);
                if (result.hasError()) {
                    continue;
                }

                results.add(result.getResult());
                startOfSecondEntry = comma + 1;
            }

            if (results.size() == 0) {
                return ParseResult.error("multi-select", "Invalid value", startPosition + 1);
            }

            if (results.size() > 1) {
                // TODO: We might need to complicate this and support multiple results. TBD.
                return ParseResult.error("multi-select", "Ambiguous separation", startPosition);
            }

            // Find any subsequent entries
            int startPositionAfterComma = startOfSecondEntry;
            for (Integer commaPosition : commaPositions) {
                if (startPositionAfterComma > commaPosition) {
                    continue;
                }

                ParseResult<T> entry = entryParser.parse(startPositionAfterComma, commaPosition);
                if (entry.hasError()) {
                    continue;
                }

                results.add(entry.getResult());

                startPositionAfterComma = commaPosition + 1;
            }

            ParseResult<T> entry = entryParser.parse(startPositionAfterComma, endPosition - 1);
            if (entry.hasError()) {
                return ParseResult.error("multi-select", "Ambiguous separation", startPosition);
            }
            results.add(entry.getResult());

            return ParseResult.success(results);
        }

        /**
         * keyval-expr       = identifier ":" expression
         */
        private ParseResult<KeyValueExpression> parseKeyValueExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            List<Integer> delimiterPositions = findCharacters(startPosition + 1, endPosition - 1, ":");
            for (Integer delimiterPosition : delimiterPositions) {
                ParseResult<String> identifier = parseIdentifier(startPosition, delimiterPosition);
                if (identifier.hasError()) {
                    continue;
                }

                ParseResult<Expression> expression = parseExpression(delimiterPosition + 1, endPosition);
                if (expression.hasError()) {
                    continue;
                }

                return ParseResult.success(new KeyValueExpression(identifier.getResult(), expression.getResult()));
            }

            return ParseResult.error("keyval-expr", "Invalid keyval-expr", startPosition);
        }

        /**
         * bracket-specifier = "[" (number / "*" / slice-expression) "]" / "[]"
         * bracket-specifier =/ "[?" expression "]"
         */
        private ParseResult<BracketSpecifier> parseBracketSpecifier(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != '[') {
                return ParseResult.error("bracket-specifier", "Expecting '['", startPosition);
            }

            if (context.input().charAt(endPosition - 1) != ']') {
                return ParseResult.error("bracket-specifier", "Expecting ']'", endPosition - 1);
            }

            // "[]"
            if (charsInRange(startPosition, endPosition) == 2) {
                return ParseResult.success(BracketSpecifier.withoutContents());
            }

            // "[?" expression "]"
            if (context.input().charAt(startPosition + 1) == '?') {
                return parseExpression(startPosition + 2, endPosition - 1)
                    .mapResult(e -> BracketSpecifier.withQuestionMark(new BracketSpecifierWithQuestionMark(e)));
            }

            // "[" (number / "*" / slice-expression) "]"
            return new CompositeParser<>("bracket-specifier-content",
                                         new ConvertingParser<>(this::parseNumber, n ->
                                             BracketSpecifier.withContents(BracketSpecifierWithContents.number(n))),
                                         new ConvertingParser<>(this::parseStarExpression, s ->
                                             BracketSpecifier.withContents(BracketSpecifierWithContents.starExpression(s))),
                                         new ConvertingParser<>(this::parseSliceExpression, s ->
                                             BracketSpecifier.withContents(BracketSpecifierWithContents.sliceExpression(s))))
                .parse(startPosition + 1, endPosition - 1);
        }

        /**
         * comparator-expression = expression comparator expression
         */
        private ParseResult<ComparatorExpression> parseComparatorExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            for (Comparator comparator : Comparator.values()) {
                List<Integer> comparatorPositions = findCharacters(startPosition, endPosition, comparator.tokenSymbol());

                for (Integer comparatorPosition : comparatorPositions) {
                    ParseResult<Expression> lhsExpression = parseExpression(startPosition, comparatorPosition);
                    if (lhsExpression.hasError()) {
                        continue;
                    }

                    ParseResult<Expression> rhsExpression =
                        parseExpression(comparatorPosition + comparator.tokenSymbol().length(), endPosition);
                    if (rhsExpression.hasError()) {
                        continue;
                    }

                    return ParseResult.success(new ComparatorExpression(lhsExpression.getResult(),
                                                                        comparator,
                                                                        rhsExpression.getResult()));
                }
            }


            return ParseResult.error("comparator-expression", "Invalid comparator expression", startPosition);
        }

        /**
         * slice-expression  = [number] ":" [number] [ ":" [number] ]
         */
        private ParseResult<SliceExpression> parseSliceExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            // Find the first colon
            int firstColonIndex = context.input().indexOf(':', startPosition);
            if (firstColonIndex < 0 || firstColonIndex >= endPosition) {
                return ParseResult.error("slice-expression", "Expected slice expression", startPosition);
            }

            // Find the second colon (if it exists)
            int maybeSecondColonIndex = context.input().indexOf(':', firstColonIndex + 1);
            OptionalInt secondColonIndex = maybeSecondColonIndex < 0 || maybeSecondColonIndex >= endPosition
                                           ? OptionalInt.empty()
                                           : OptionalInt.of(maybeSecondColonIndex);

            // Find the first number bounds (if it exists)
            int firstNumberStart = startPosition;
            int firstNumberEnd = firstColonIndex;

            // Find the second number bounds (if it exists)
            int secondNumberStart = firstColonIndex + 1;
            int secondNumberEnd = secondColonIndex.orElse(endPosition);

            // Find the third number bounds (if it exists)
            int thirdNumberStart = secondColonIndex.orElse(endPosition) + 1;
            int thirdNumberEnd = endPosition;

            // Parse the first number (if it exists)
            Optional<Integer> firstNumber = Optional.empty();
            if (firstNumberStart < firstNumberEnd) {
                ParseResult<Integer> firstNumberParse = parseNumber(firstNumberStart, firstNumberEnd);
                if (firstNumberParse.hasError()) {
                    return ParseResult.error(firstNumberParse.getError());
                }
                firstNumber = Optional.of(firstNumberParse.getResult());
            }

            // Parse the second number (if it exists)
            Optional<Integer> secondNumber = Optional.empty();
            if (secondNumberStart < secondNumberEnd) {
                ParseResult<Integer> secondNumberParse = parseNumber(secondNumberStart, secondNumberEnd);
                if (secondNumberParse.hasError()) {
                    return ParseResult.error(secondNumberParse.getError());
                }
                secondNumber = Optional.of(secondNumberParse.getResult());
            }

            // Parse the third number (if it exists)
            Optional<Integer> thirdNumber = Optional.empty();
            if (thirdNumberStart < thirdNumberEnd) {
                ParseResult<Integer> thirdNumberParse = parseNumber(thirdNumberStart, thirdNumberEnd);
                if (thirdNumberParse.hasError()) {
                    return ParseResult.error(thirdNumberParse.getError());
                }
                thirdNumber = Optional.of(thirdNumberParse.getResult());
            }

            return ParseResult.success(new SliceExpression(firstNumber.orElse(null),
                                                           secondNumber.orElse(null),
                                                           thirdNumber.orElse(null)));
        }

        private int numDigits(int value) {
            int result = 0;
            long counter = 1;
            while (counter <= result) {
                result++;
                counter *= 10;
            }
            return result;
        }

        /**
         * function-expression = unquoted-string ( no-args / one-or-more-args )
         */
        private ParseResult<FunctionExpression> parseFunctionExpression(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            int paramIndex = context.input().indexOf('(', startPosition);
            if (paramIndex <= 0) {
                return ParseResult.error("function-expression", "Expected function", startPosition);
            }

            ParseResult<String> functionNameParse = parseUnquotedString(startPosition, paramIndex);
            if (functionNameParse.hasError()) {
                return ParseResult.error("function-expression",
                                         "Expected valid function name (" + functionNameParse.getError().errorMessage() + ")",
                                         startPosition);
            }

            return new CompositeParser<>("no-args", this::parseNoArgs, this::parseOneOrMoreArgs)
                .parse(paramIndex, endPosition)
                .mapResult(args -> new FunctionExpression(functionNameParse.getResult(), args));
        }

        /**
         * no-args             = "(" ")"
         */
        private ParseResult<List<FunctionArg>> parseNoArgs(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != '(') {
                return ParseResult.error("no-args", "Expected '('", startPosition);
            }

            int closePosition = trimLeftWhitespace(startPosition + 1, endPosition);

            if (context.input().charAt(closePosition) != ')') {
                return ParseResult.error("no-args", "Expected ')'", closePosition);
            }

            if (closePosition + 1 != endPosition) {
                return ParseResult.error("no-args", "Unexpected character", closePosition + 1);
            }

            return ParseResult.success(Collections.emptyList());
        }

        /**
         * one-or-more-args    = "(" ( function-arg *( "," function-arg ) ) ")"
         */
        private ParseResult<List<FunctionArg>> parseOneOrMoreArgs(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '(', ')', this::parseFunctionArg);
        }

        /**
         * function-arg        = expression / expression-type
         */
        private ParseResult<FunctionArg> parseFunctionArg(int startPosition, int endPosition) {
            return new CompositeParser<>("function-arg",
                                         new ConvertingParser<>(this::parseExpression, FunctionArg::expression),
                                         new ConvertingParser<>(this::parseExpressionType, FunctionArg::expressionType))
                .parse(startPosition, endPosition);
        }

        /**
         * current-node        = "@"
         */
        private ParseResult<CurrentNode> parseCurrentNode(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            return parseExpectedToken("current-node", startPosition, endPosition, '@').mapResult(x -> new CurrentNode());
        }

        /**
         * expression-type     = "&" expression
         */
        private ParseResult<ExpressionType> parseExpressionType(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) != '&') {
                return ParseResult.error("expression-type", "Expected '&'", startPosition);
            }

            return parseExpression(startPosition + 1, endPosition).mapResult(ExpressionType::new);
        }

        /**
         * raw-string        = "'" *raw-string-char "'"
         */
        private ParseResult<String> parseRawString(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (charsInRange(startPosition, endPosition) < 2) {
                return ParseResult.error("raw-string", "Invalid length", startPosition);
            }

            if (context.input().charAt(startPosition) != '\'') {
                return ParseResult.error("raw-string", "Expected \"'\"", startPosition);
            }

            if (context.input().charAt(endPosition - 1) != '\'') {
                return ParseResult.error("raw-string", "Expected \"'\"", endPosition - 1);
            }

            if (charsInRange(startPosition, endPosition) == 2) {
                return ParseResult.success("");
            }

            return parseRawStringChars(startPosition + 1, endPosition - 1);
        }

        /**
         * raw-string-char   = (%x20-26 / %x28-5B / %x5D-10FFFF) / preserved-escape /
         * raw-string-escape
         */
        private ParseResult<String> parseRawStringChars(int startPosition, int endPosition) {
            return new CompositeParser<>("raw-string-char",
                                         this::parseLegalRawStringChars,
                                         this::parsePreservedEscape,
                                         this::parseRawStringEscape)
                .parse(startPosition, endPosition);
        }

        /**
         * %x20-26 / %x28-5B / %x5D-10FFFF
         */
        private ParseResult<String> parseLegalRawStringChars(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) < 1) {
                return ParseResult.error("raw-string-chars", "Invalid bounds", startPosition);
            }

            for (int i = startPosition; i < endPosition; i++) {
                if (!isLegalRawStringChar(context.input().charAt(i))) {
                    return ParseResult.error("raw-string-chars", "Invalid character in sequence", startPosition);
                }
            }

            return ParseResult.success(context.input().substring(startPosition, endPosition));
        }

        private boolean isLegalRawStringChar(char c) {
            return (c >= 0x20 && c <= 0x26) ||
                   (c >= 0x28 && c <= 0x5B) ||
                   (c >= 0x5B);
        }

        /**
         * preserved-escape  = escape (%x20-26 / %28-5B / %x5D-10FFFF)
         */
        private ParseResult<String> parsePreservedEscape(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) < 2) {
                return ParseResult.error("preserved-escape", "Invalid bounds", startPosition);
            }

            if (context.input().charAt(startPosition) != '\\') {
                return ParseResult.error("preserved-escape", "Expected \\", startPosition);
            }

            return parseLegalRawStringChars(startPosition + 1, endPosition).mapResult(v -> "\\" + v);
        }

        /**
         * raw-string-escape = escape ("'" / escape)
         */
        private ParseResult<String> parseRawStringEscape(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) != 2) {
                return ParseResult.error("raw-string-escape", "Invalid raw string escape", startPosition);
            }

            if (context.input().charAt(startPosition) != '\\') {
                return ParseResult.error("raw-string-escape", "Expected '\\'", startPosition);
            }

            if (context.input().charAt(startPosition + 1) != '\'' && context.input().charAt(startPosition + 1) != '\\') {
                return ParseResult.error("raw-string-escape", "Expected \"'\"", startPosition);
            }

            return ParseResult.success(context.input().substring(startPosition, endPosition));
        }

        /**
         * literal           = "`" json-value "`"
         */
        private ParseResult<Literal> parseLiteral(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (charsInRange(startPosition, endPosition) < 2) {
                return ParseResult.error("literal", "Invalid bounds", startPosition);
            }

            if (context.input().charAt(startPosition) != '`') {
                return ParseResult.error("literal", "Expected '`'", startPosition);
            }

            if (context.input().charAt(endPosition - 1) != '`') {
                return ParseResult.error("literal", "Expected '`'", endPosition - 1);
            }

            StringBuilder jsonString = new StringBuilder();
            for (int i = startPosition + 1; i < endPosition - 1; i++) {
                char character = context.input().charAt(i);
                if (character == '`') {
                    int lastChar = i - 1;
                    if (lastChar <= 0) {
                        return ParseResult.error("literal", "Unexpected '`'", startPosition);
                    }

                    int escapeCount = 0;
                    for (int j = i - 1; j >= startPosition; j--) {
                        if (context.input().charAt(j) == '\\') {
                            ++escapeCount;
                        } else {
                            break;
                        }
                    }

                    if (escapeCount % 2 == 0) {
                        return ParseResult.error("literal", "Unescaped '`'", startPosition);
                    }

                    jsonString.setLength(jsonString.length() - 1); // Remove escape.
                    jsonString.append('`');
                } else {
                    jsonString.append(character);
                }
            }

            try {
                return ParseResult.success(new Literal(Jackson.readJrsValue(jsonString.toString())));
            } catch (IOException e) {
                return ParseResult.error("literal", "Invalid JSON: " + e.getMessage(), startPosition);
            }
        }

        /**
         * number            = ["-"]1*digit
         * digit             = %x30-39
         */
        private ParseResult<Integer> parseNumber(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (context.input().charAt(startPosition) == '-') {
                return parseNonNegativeNumber(startPosition + 1, endPosition).mapResult(i -> -i);
            }

            return parseNonNegativeNumber(startPosition, endPosition);
        }

        private ParseResult<Integer> parseNonNegativeNumber(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (charsInRange(startPosition, endPosition) < 1) {
                return ParseResult.error("number", "Expected number", startPosition);
            }

            try {
                return ParseResult.success(Integer.parseInt(context.input().substring(startPosition, endPosition)));
            } catch (NumberFormatException e) {
                return ParseResult.error("number", "Expected number", startPosition);
            }
        }

        /**
         * identifier        = unquoted-string / quoted-string
         */
        private ParseResult<String> parseIdentifier(int startPosition, int endPosition) {
            return new CompositeParser<>("identifier",
                                         this::parseUnquotedString,
                                         this::parseQuotedString)
                .parse(startPosition, endPosition);
        }

        /**
         * unquoted-string   = (%x41-5A / %x61-7A / %x5F) *(  ; A-Za-z_
         * %x30-39  /  ; 0-9
         * %x41-5A /  ; A-Z
         * %x5F    /  ; _
         * %x61-7A)   ; a-z
         */
        private ParseResult<String> parseUnquotedString(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (charsInRange(startPosition, endPosition) < 1) {
                return ParseResult.error("unquoted-string", "Invalid unquoted-string", startPosition);
            }

            char firstToken = context.input().charAt(startPosition);
            if (!Character.isLetter(firstToken) && firstToken != '_') {
                return ParseResult.error("unquoted-string", "Unescaped strings must start with [A-Za-z_]", startPosition);
            }

            for (int i = startPosition; i < endPosition; i++) {
                char c = context.input().charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return ParseResult.error("unquoted-string", "Invalid character in unescaped-string", i);
                }
            }

            return ParseResult.success(context.input().substring(startPosition, endPosition));
        }

        /**
         * quoted-string     = quote 1*(unescaped-char / escaped-char) quote
         */
        private ParseResult<String> parseQuotedString(int startPosition, int endPosition) {
            startPosition = trimLeftWhitespace(startPosition, endPosition);
            endPosition = trimRightWhitespace(startPosition, endPosition);

            if (!(context.input().charAt(startPosition) == '\'')) {
                return ParseResult.error("quoted-string", "Expected \"'\"", startPosition);
            }

            if (!(context.input().charAt(endPosition - 1) == '\'')) {
                return ParseResult.error("quoted-string", "Expected \"'\"", endPosition - 1);
            }

            int stringStart = startPosition + 1;
            int stringEnd = endPosition - 1;

            int stringTokenCount = charsInRange(stringStart, stringEnd);
            if (stringTokenCount < 1) {
                return ParseResult.error("quoted-string", "Invalid quoted-string", startPosition);
            }

            Parser<String> unescapedOrEscapedCharParser = new CompositeParser<>("quoted-string",
                                                                                this::parseUnescapedChar,
                                                                                this::parseEscapedChar);
            StringBuilder result = new StringBuilder();
            for (int i = stringStart; i < stringEnd; i++) {
                ParseResult<String> string = unescapedOrEscapedCharParser.parse(i, i + 1);
                if (string.hasError()) {
                    return string;
                }
                result.append(string.getResult());
            }

            return ParseResult.success(result.toString());
        }

        /**
         * unescaped-char    = %x20-21 / %x23-5B / %x5D-10FFFF
         */
        private ParseResult<String> parseUnescapedChar(int startPosition, int endPosition) {
            for (int i = startPosition; i < endPosition; i++) {
                if (!isLegalUnescapedChar(context.input().charAt(i))) {
                    return ParseResult.error("unescaped-char", "Invalid character in sequence", startPosition);
                }
            }

            return ParseResult.success(context.input().substring(startPosition, endPosition));
        }

        private boolean isLegalUnescapedChar(char c) {
            return (c >= 0x20 && c <= 0x21) ||
                   (c >= 0x23 && c <= 0x5B) ||
                   (c >= 0x5D);
        }

        /**
         * escaped-char      = escape (
         * %x22 /          ; "    quotation mark  U+0022
         * %x5C /          ; \    reverse solidus U+005C
         * %x2F /          ; /    solidus         U+002F
         * %x62 /          ; b    backspace       U+0008
         * %x66 /          ; f    form feed       U+000C
         * %x6E /          ; n    line feed       U+000A
         * %x72 /          ; r    carriage return U+000D
         * %x74 /          ; t    tab             U+0009
         * %x75 4HEXDIG )  ; uXXXX                U+XXXX
         */
        private ParseResult<String> parseEscapedChar(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) >= 2) {
                return ParseResult.error("escaped-char", "Invalid escape-char sequence", startPosition);
            }

            if (context.input().charAt(startPosition) != '\\') {
                return ParseResult.error("escaped-char", "Expected '\\'", startPosition);
            }

            char escapedChar = context.input().charAt(startPosition + 1);
            switch (escapedChar) {
                case '"':
                    return ParseResult.success("\\\"");
                case '\\':
                    return ParseResult.success("\\\\");
                case '/':
                    return ParseResult.success("/");
                case 'b':
                    return ParseResult.success("\\b");
                case 'f':
                    return ParseResult.success("\\f");
                case 'n':
                    return ParseResult.success("\\n");
                case 'r':
                    return ParseResult.success("\\r");
                case 't':
                    return ParseResult.success("\\t");
                default: // continue
            }

            if (escapedChar != 'u') {
                return ParseResult.error("escaped-char", "Invalid escape sequence", startPosition);
            }

            int unicodeEndIndex = startPosition + 1 + 4;
            if (unicodeEndIndex > endPosition) {
                return ParseResult.error("escaped-char", "Invalid unicode sequence", startPosition);
            }

            String unicodePattern = context.input().substring(startPosition + 1, unicodeEndIndex);
            try {
                Long.parseLong(unicodePattern, 16);
            } catch (NumberFormatException e) {
                return ParseResult.error("escaped-char", "Invalid unicode hex sequence", startPosition);
            }

            return ParseResult.success(context.input().substring(startPosition, endPosition));
        }

        /**
         * "*"
         */
        private ParseResult<StarExpression> parseStarExpression(int startPosition, int endPosition) {
            return parseExpectedToken("star-expression", startPosition, endPosition, '*').mapResult(v -> new StarExpression());
        }

        private int charsInRange(int startPosition, int endPosition) {
            return endPosition - startPosition;
        }

        private List<Integer> findCharacters(int startPosition, int endPosition, String symbol) {
            List<Integer> results = new ArrayList<>();

            int start = startPosition;
            while (true) {
                int match = context.input().indexOf(symbol, start);
                if (match < 0 || match >= endPosition) {
                    break;
                }
                results.add(match);
                start = match + 1;
            }

            return results;
        }

        private ParseResult<Void> parseExpectedToken(String parser, int startPosition, int endPosition, char expectedToken) {
            if (context.input().charAt(startPosition) != expectedToken) {
                return ParseResult.error(parser, "Expected '" + expectedToken + "'", startPosition);
            }

            if (charsInRange(startPosition, endPosition) != 1) {
                return ParseResult.error(parser, "Unexpected character", startPosition + 1);
            }

            return ParseResult.success(null);
        }

        private int trimLeftWhitespace(int startPosition, int endPosition) {
            while (context.input().charAt(startPosition) == ' ' && startPosition < endPosition - 1) {
                ++startPosition;
            }

            return startPosition;
        }

        private int trimRightWhitespace(int startPosition, int endPosition) {
            while (context.input().charAt(endPosition - 1) == ' ' && startPosition < endPosition - 1) {
                --endPosition;
            }

            return endPosition;
        }
    }
}