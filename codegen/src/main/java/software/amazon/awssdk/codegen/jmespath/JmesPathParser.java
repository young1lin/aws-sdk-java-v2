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

package software.amazon.awssdk.codegen.jmespath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import software.amazon.awssdk.utils.Validate;

public class JmesPathParser {
    public static void parse(String jmesPathString, JmesPathVisitor visitor) {

    }

    private static final class ParsingVisitor {
        private final String input;

        private ParsingVisitor(String input) {
            this.input = input;
        }

        private Expression parse() {
            ParseResult<Expression> expression = parseExpression(0, input.length());
            if (expression.hasError()) {
                ParseError error = expression.getError();
                throw new IllegalArgumentException("Unable to parse input at character " + error.position + ": " +
                                                   error.errorMessage);
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
            if (startPosition < 0 || endPosition > input.length() + 1) {
                return ParseResult.error("Illegal parse range: [" + startPosition + ", " + endPosition + "]", startPosition);
            }

            return new CompositeParser<>("expression",
                                         new ConvertingParser<>(this::parseSubExpression, Expression::subExpression),
                                         new ConvertingParser<>(this::parseIndexExpression, Expression::indexExpression),
                                         new ConvertingParser<>(this::parseComparatorExpression, Expression::comparatorExpression),
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
         *                                      multi-select-list /
         *                                      multi-select-hash /
         *                                      function-expression /
         *                                      "*" )
         */
        private ParseResult<SubExpression> parseSubExpression(int startPosition, int endPosition) {
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
                                          new ConvertingParser<>(this::parseFunctionExpression, SubExpressionRight::functionExpression),
                                          new ConvertingParser<>(this::parseStarExpression, SubExpressionRight::starExpression))
                    .parse(dotPosition + 1, endPosition);
                if (rightSide.hasError()) {
                    continue;
                }

                return ParseResult.success(new SubExpression(leftSide.getResult(), rightSide.getResult()));
            }

            return ParseResult.error("Invalid sub-expression", startPosition);
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

        /**
         * not-expression    = "!" expression
         */
        private ParseResult<NotExpression> parseNotExpression(int startPosition, int endPosition) {
            if (input.charAt(startPosition) != '!') {
                return ParseResult.error("Expected '!'", startPosition);
            }

            return parseExpression(startPosition + 1, endPosition).mapResult(NotExpression::new);
        }

        /**
         * paren-expression  = "(" expression ")"
         */
        private ParseResult<ParenExpression> parseParenExpression(int startPosition, int endPosition) {
            if (input.charAt(startPosition) != '(') {
                return ParseResult.error("Expected '('", startPosition);
            }

            if (input.charAt(endPosition - 1) != ')') {
                return ParseResult.error("Expected ')'", endPosition - 1);
            }

            return parseExpression(startPosition + 1, endPosition).mapResult(ParenExpression::new);
        }

        /**
         * index-expression  = expression bracket-specifier / bracket-specifier
         */
        private ParseResult<IndexExpression> parseIndexExpression(int startPosition, int endPosition) {
            return new CompositeParser<>("bracket-specifier",
                                         new ConvertingParser<>(this::parseIndexExpressionWithLhsExpression, Function.identity()),
                                         new ConvertingParser<>(this::parseBracketSpecifier, b -> IndexExpression.indexExpression(null, b)))
                .parse(startPosition, endPosition);
        }

        private ParseResult<IndexExpression> parseIndexExpressionWithLhsExpression(int startPosition, int endPosition) {
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

            return ParseResult.error("Invalid index-expression with lhs-expression", startPosition);
        }

        /**
         * bracket-specifier = "[" (number / "*" / slice-expression) "]" / "[]"
         * bracket-specifier =/ "[?" expression "]"
         */
        private ParseResult<BracketSpecifier> parseBracketSpecifier(int startPosition, int endPosition) {
            if (input.charAt(startPosition) != '[') {
                return ParseResult.error("Expecting '['", startPosition);
            }

            if (input.charAt(endPosition - 1) != '[') {
                return ParseResult.error("Expecting ']'", endPosition - 1);
            }

            if (charsInRange(startPosition, endPosition) == 2) {
                return ParseResult.success(BracketSpecifier.bracketSpecifierWithoutContents());
            }

            // TODO: continue review here
            if (input.charAt(startPosition + 1) == '?') {
                return parseExpression(startPosition + 1, endPosition - 1)
                    .mapResult(e -> BracketSpecifier.bracketSpecifierWithQuestionMark(new BracketSpecifierWithQuestionMark(e)));
            }

            return new CompositeParser<>("bracket-specifier-content",
                                         new ConvertingParser<>(this::parseNumber, n ->
                                             BracketSpecifier.bracketSpecifierWithContents(BracketSpecifierWithContents.number(n))),
                                         new ConvertingParser<>(this::parseStarExpression, s ->
                                             BracketSpecifier.bracketSpecifierWithContents(BracketSpecifierWithContents.starExpression(s))),
                                         new ConvertingParser<>(this::parseSliceExpression, s ->
                                             BracketSpecifier.bracketSpecifierWithContents(BracketSpecifierWithContents.sliceExpression(s))))
                .parse(startPosition + 1, endPosition - 1);
        }

        private ParseResult<ComparatorExpression> parseComparatorExpression(int startPosition, int endPosition) {
            for (Comparator comparator : Comparator.values()) {
                List<Integer> comparatorPositions = findCharacters(startPosition, endPosition, comparator.tokenSymbol());

                for (Integer comparatorPosition : comparatorPositions) {
                    ParseResult<Expression> lhsExpression = parseExpression(startPosition, comparatorPosition);
                    if (lhsExpression.hasError()) {
                        continue;
                    }

                    ParseResult<Expression> rhsExpression = parseExpression(comparatorPosition + comparator.tokenSymbol().length(),
                                                                            endPosition);
                    if (rhsExpression.hasError()) {
                        continue;
                    }

                    return ParseResult.success(new ComparatorExpression(lhsExpression.getResult(), comparator, rhsExpression.getResult()));
                }
            }


            return ParseResult.error("Invalid comparator expression", startPosition);
        }

        private ParseResult<SliceExpression> parseSliceExpression(int startPosition, int endPosition) {
            int tokenCount = charsInRange(startPosition, endPosition);
            if (tokenCount < 1 || tokenCount > 5) {
                return ParseResult.error("Expected slice expression", startPosition);
            }

            int parsePosition = startPosition;
            ParseResult<Integer> firstNumber = parseNumber(startPosition, startPosition + 1);
            if (!firstNumber.hasError()) {
                ++parsePosition;
            }

            if (parseExpectedToken(parsePosition, parsePosition + 1, ':').hasError()) {
                return ParseResult.error("Expected ':'", parsePosition);
            }

            ++parsePosition;
            ParseResult<Integer> secondNumber = parseNumber(parsePosition, parsePosition + 1);
            if (!secondNumber.hasError()) {
                ++parsePosition;
            }

            if (parsePosition == endPosition) {
                return ParseResult.success(new SliceExpression(firstNumber.getResultOrNull(),
                                                               secondNumber.getResultOrNull(),
                                                               null));
            }

            if (parseExpectedToken(parsePosition, parsePosition + 1, ':').hasError()) {
                return ParseResult.error("Expected ':'", parsePosition);
            }

            ++parsePosition;
            ParseResult<Integer> thirdNumber = parseNumber(parsePosition, parsePosition + 1);

            if (!thirdNumber.hasError()) {{
                ++parsePosition;
            }}

            if (parsePosition != endPosition) {
                return ParseResult.error("Unexpected character (expected end of slice statement)", parsePosition);
            }

            return ParseResult.success(new SliceExpression(firstNumber.getResultOrNull(),
                                                           secondNumber.getResultOrNull(),
                                                           thirdNumber.getResultOrNull()));
        }

        private ParseResult<StarExpression> parseStarExpression(int startPosition, int endPosition) {
            return parseExpectedToken(startPosition, endPosition, '*').mapResult(v -> new StarExpression());
        }

        private ParseResult<Void> parseExpectedToken(int startPosition, int endPosition, char expectedToken) {
            if (!(input.charAt(startPosition) == expectedToken)) {
                return ParseResult.error("Expected '" + expectedToken + "'", startPosition);
            }

            if (charsInRange(startPosition, endPosition) != 1) {
                return ParseResult.error("Unexpected character", startPosition + 1);
            }

            return ParseResult.success(null);
        }

        private ParseResult<MultiSelectList> parseMultiSelectList(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '[', ']', this::parseExpression).mapResult(MultiSelectList::new);
        }

        private ParseResult<MultiSelectHash> parseMultiSelectHash(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '{', '}', this::parseKeyValueExpression).mapResult(MultiSelectHash::new);
        }

        private <T> ParseResult<List<T>> parseMultiSelect(int startPosition, int endPosition,
                                                          char startDelimiter, char endDelimiter,
                                                          Parser<T> entryParser) {
            if (!(input.charAt(startPosition) == startDelimiter)) {
                return ParseResult.error("Expected '" + startDelimiter + "'", startPosition);
            }

            if (!(input.charAt(endPosition - 1) == endDelimiter)) {
                return ParseResult.error("Expected '" + startDelimiter + "'", endPosition - 1);
            }

            List<T> results = new ArrayList<>();

            List<Integer> commaPositions = findCharacters(startPosition + 1, endPosition - 1, ",");

            if (commaPositions.isEmpty()) {
                return entryParser.parse(startPosition + 1, endPosition - 1).mapResult(Collections::singletonList);
            }

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
                return ParseResult.error("Invalid value", startPosition + 1);
            }

            if (results.size() > 1) {
                return ParseResult.error("Ambiguous separation at ", startPosition);
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

            ParseResult<T> entry = entryParser.parse(startPositionAfterComma, endPosition);
            if (entry.hasError()) {
                return ParseResult.error("Ambiguous separation at ", startPosition);
            }
            results.add(entry.getResult());

            return ParseResult.success(results);
        }

        private ParseResult<KeyValueExpression> parseKeyValueExpression(int startPosition, int endPosition) {
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

                return ParseResult.success(new KeyValueExpression(identifier.getResult(), expression.getResult()) {
                });
            }

            return ParseResult.error("Invalid pipe/or/and-expression", startPosition);
        }

        private ParseResult<FunctionExpression> parseFunctionExpression(int startPosition, int endPosition) {
            ParseResult<String> functionName = parseUnquotedString(startPosition, startPosition + 1);
            if (functionName.hasError()) {
                return ParseResult.error("Expected valid function name (" + functionName.getError().errorMessage + ")",
                                         startPosition);
            }

            return new CompositeParser<>("no-args", this::parseNoArgs, this::parseNonZeroArgs)
                .parse(startPosition + 1, endPosition)
                .mapResult(args -> new FunctionExpression(functionName.getResult(), args));
        }

        private ParseResult<List<FunctionArg>> parseNoArgs(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) != 2) {
                return ParseResult.error("Invalid no-arg call", startPosition);
            }

            if (input.charAt(startPosition) == '(' && input.charAt(endPosition - 1) == ')') {
                return ParseResult.success(Collections.emptyList());
            }

            return ParseResult.error("Invalid no-arg call", startPosition);
        }

        private ParseResult<List<FunctionArg>> parseNonZeroArgs(int startPosition, int endPosition) {
            return parseMultiSelect(startPosition, endPosition, '(', ')', this::parseFunctionArg);
        }

        private ParseResult<FunctionArg> parseFunctionArg(int startPosition, int endPosition) {
            return new CompositeParser<>("function-arg",
                                         new ConvertingParser<>(this::parseExpression, FunctionArg::expression),
                                         new ConvertingParser<>(this::parseExpressionType, FunctionArg::expressionType))
                .parse(startPosition, endPosition);
        }

        private ParseResult<ExpressionType> parseExpressionType(int startPosition, int endPosition) {
            if (!(input.charAt(startPosition) == '&')) {
                return ParseResult.error("Expected '&'", startPosition);
            }

            return parseExpression(startPosition + 1, endPosition).mapResult(ExpressionType::new);
        }

        private <T> ParseResult<T> parseBinaryExpression(int startPosition, int endPosition, String delimiter,
                                                         BiFunction<Expression, Expression, T> constructor) {
            List<Integer> delimiterPositions = findCharacters(startPosition + 1, endPosition - 1, delimiter);
            for (Integer delimiterPosition : delimiterPositions) {
                ParseResult<Expression> leftSide = parseExpression(startPosition, delimiterPosition);
                if (leftSide.hasError()) {
                    continue;
                }

                ParseResult<Expression> rightSide = parseExpression(delimiterPosition + 1, endPosition);
                if (rightSide.hasError()) {
                    continue;
                }

                return ParseResult.success(constructor.apply(leftSide.getResult(), rightSide.getResult()));
            }

            return ParseResult.error("Invalid binary-expression", startPosition);
        }

        private ParseResult<Integer> parseNumber(int startPosition, int endPosition) {
            if (input.charAt(startPosition) == '-') {
                return parseNonNegativeNumber(startPosition + 1, endPosition).mapResult(i -> -i);
            }

            return parseNonNegativeNumber(startPosition, endPosition);
        }

        private ParseResult<Integer> parseNonNegativeNumber(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) != 1) {
                return ParseResult.error("Expected number", startPosition);
            }

            try {
                return ParseResult.success(Integer.parseInt(input.substring(startPosition, endPosition)));
            } catch (NumberFormatException e) {
                return ParseResult.error("Expected number", startPosition);
            }
        }

        private ParseResult<String> parseRawString(int startPosition, int endPosition) {
            if (input.charAt(startPosition) != '\'') {
                return ParseResult.error("Expected \"'\"", startPosition);
            }

            if (input.charAt(endPosition) != '\'') {
                return ParseResult.error("Expected \"'\"", endPosition);
            }

            if (charsInRange(startPosition, endPosition) == 0) {
                return ParseResult.success("");
            }

            return parseRawStringChars(startPosition + 1, endPosition - 1);
        }

        private ParseResult<Literal> parseLiteral(int startPosition, int endPosition) {
            if (input.charAt(startPosition) != '`') {
                return ParseResult.error("Expected '`'", startPosition);
            }

            if (input.charAt(endPosition - 1) != '`') {
                return ParseResult.error("Expected '`'", endPosition - 1);
            }

            StringBuilder jsonString = new StringBuilder();
            for (int i = startPosition + 1; i < endPosition - 1; i++) {
                char character = input.charAt(i);
                if (character == '`') {
                    int lastChar = i - 1;
                    if (lastChar <= 0) {
                        return ParseResult.error("Unexpected '`' in literal", startPosition);
                    }

                    int escapeCount = 0;
                    for (int j = i - 1; j >= startPosition; j--) {
                        if (input.charAt(j) == '\\') {
                            ++escapeCount;
                        } else {
                            break;
                        }
                    }

                    if (escapeCount % 2 == 0) {
                        return ParseResult.error("Unescaped '`' in literal", startPosition);
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
                return ParseResult.error("Invalid JSON encountered in literal: " + e.getMessage(), startPosition);
            }
        }

        private ParseResult<String> parseQuotedString(int startPosition, int endPosition) {
            if (!(input.charAt(startPosition) == '\'')) {
                return ParseResult.error("Expected \"'\"", startPosition);
            }

            if (!(input.charAt(endPosition - 1) == '\'')) {
                return ParseResult.error("Expected \"'\"", endPosition - 1);
            }

            int stringStart = startPosition + 1;
            int stringEnd = endPosition - 1;

            int stringTokenCount = charsInRange(stringStart, stringEnd);
            if (stringTokenCount < 1) {
                return ParseResult.error("Invalid quoted-string", startPosition);
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

        private ParseResult<String> parseUnescapedChar(int startPosition, int endPosition) {
            for (int i = startPosition; i < endPosition; i++) {
                if (!isLegalUnescapedChar(input.charAt(i))) {
                    return ParseResult.error("Invalid character in sequence", startPosition);
                }
            }

            return ParseResult.success(input.substring(startPosition, endPosition));
        }

        private ParseResult<String> parseEscapedChar(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) >= 2) {
                return ParseResult.error("Invalid escape-char sequence", startPosition);
            }

            if (input.charAt(startPosition) != '\\') {
                return ParseResult.error("Expected '\\'", startPosition);
            }

            char escapedChar = input.charAt(startPosition + 1);
            switch (escapedChar) {
                case '"': return ParseResult.success("\\\"");
                case '\\': return ParseResult.success("\\\\");
                case '/': return ParseResult.success("/");
                case 'b': return ParseResult.success("\\b");
                case 'f': return ParseResult.success("\\f");
                case 'n': return ParseResult.success("\\n");
                case 'r': return ParseResult.success("\\r");
                case 't': return ParseResult.success("\\t");
            }

            if (escapedChar != 'u') {
                return ParseResult.error("Invalid escape sequence", startPosition);
            }

            int unicodeEndIndex = startPosition + 1 + 4;
            if (unicodeEndIndex > endPosition) {
                return ParseResult.error("Invalid unicode sequence", startPosition);
            }

            String unicodePattern = input.substring(startPosition + 1, unicodeEndIndex);
            try {
                Long.parseLong(unicodePattern, 16);
            } catch (NumberFormatException e) {
                return ParseResult.error("Invalid unicode hex sequence", startPosition);
            }

            return ParseResult.success(input.substring(startPosition, endPosition));
        }

        private ParseResult<String> parseRawStringChars(int startPosition, int endPosition) {
            return new CompositeParser<>("raw-string-char",
                                         this::parseLegalRawStringChars,
                                         this::parsePreservedEscape,
                                         this::parseRawStringEscape)
                .parse(startPosition, endPosition);
        }

        private ParseResult<String> parseLegalRawStringChars(int startPosition, int endPosition) {
            for (int i = startPosition; i < endPosition; i++) {
                if (!isLegalRawStringChar(input.charAt(i))) {
                    return ParseResult.error("Invalid character in sequence", startPosition);
                }
            }

            return ParseResult.success(input.substring(startPosition, endPosition));
        }

        private ParseResult<String> parsePreservedEscape(int startPosition, int endPosition) {
            if (!(input.charAt(startPosition) == '\\')) {
                return ParseResult.error("Expected \\", startPosition);
            }

            return parseLegalRawStringChars(startPosition + 1, endPosition);
        }

        private ParseResult<String> parseRawStringEscape(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) != 2) {
                return ParseResult.error("Invalid raw string escape", startPosition);
            }

            if (!(input.charAt(startPosition) == '\\')) {
                return ParseResult.error("Expected '\\'", startPosition);
            }

            if (!(input.charAt(startPosition + 1) == '\'') && !(input.charAt(startPosition + 1) == '\\')) {
                return ParseResult.error("Expected \"'\"", startPosition);
            }

            return ParseResult.success(String.valueOf(input.charAt(startPosition + 1)));
        }

        private boolean isLegalUnescapedChar(char c) {
            return (c >= 0x20 && c <= 0x21) ||
                   (c >= 0x23 && c <= 0x5B) ||
                   (c >= 0x5D);
        }

        private boolean isLegalRawStringChar(char c) {
            return (c >= 0x20 && c <= 0x26) ||
                   (c >= 0x28 && c <= 0x5B) ||
                   (c >= 0x5B);
        }

        private ParseResult<String> parseIdentifier(int startPosition, int endPosition) {
            return new CompositeParser<>("identifier",
                                         this::parseUnquotedString,
                                         this::parseQuotedString)
                .parse(startPosition, endPosition);
        }

        private ParseResult<String> parseUnquotedString(int startPosition, int endPosition) {
            int stringTokenCount = charsInRange(startPosition, endPosition);
            if (stringTokenCount <= 1) {
                return ParseResult.error("Invalid unquoted-string", startPosition);
            }

            char firstToken = input.charAt(startPosition);
            if (!Character.isLetter(firstToken) && firstToken != '_') {
                return ParseResult.error("Unescaped strings must start with [A-Za-z_]", startPosition);
            }

            for (int i = startPosition; i < endPosition; i++) {
                char c = input.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return ParseResult.error("Invalid character in unescaped-string", i);
                }
            }

            return ParseResult.success(input.substring(startPosition, endPosition));
        }

        private ParseResult<CurrentNode> parseCurrentNode(int startPosition, int endPosition) {
            if (charsInRange(startPosition, endPosition) != 1) {
                return ParseResult.error("Expected @", startPosition);
            }
            return ParseResult.success(new CurrentNode());
        }

        private int charsInRange(int startPosition, int endPosition) {
            return endPosition - startPosition - 1;
        }

        private List<Integer> findCharacters(int startPosition, int endPosition, String symbol) {
            List<Integer> results = new ArrayList<>();

            int start = startPosition;
            while (true) {
                int match = input.indexOf(symbol, start);
                if (match < 0 || match >= endPosition) {
                    break;
                }
                results.add(match);
                start = match + 1;
            }

            return results;
        }
    }

    private static final class CompositeParser<T> implements Parser<T> {
        private final String expectedType;
        private final List<Parser<T>> parsers;

        @SafeVarargs
        private CompositeParser(String expectedType, Parser<T>... parsers) {
            this.expectedType = expectedType;
            this.parsers = Arrays.asList(parsers);
        }

        @Override
        public ParseResult<T> parse(int startPosition, int endPosition) {
            for (Parser<T> parseCall : parsers) {
                ParseResult<T> parseResult = parseCall.parse(startPosition, endPosition);

                if (parseResult.hasResult()) {
                    return parseResult;
                }
            }

            return ParseResult.error("Unable to parse " + expectedType, startPosition);
        }
    }

    private static final class ConvertingParser<T, U> implements Parser<U> {
        private final Parser<T> parser;
        private final Function<T, U> finalizer;

        private ConvertingParser(Parser<T> parser, Function<T, U> finalizer) {
            this.parser = parser;
            this.finalizer = finalizer;
        }

        @Override
        public ParseResult<U> parse(int startPosition, int endPosition) {
            ParseResult<T> result = parser.parse(startPosition, endPosition);

            if (result.hasError()) {
                return ParseResult.error(result.getError());
            } else {
                return ParseResult.success(finalizer.apply(result.getResult()));
            }
        }
    }


    @FunctionalInterface
    private interface Parser<T> {
        ParseResult<T> parse(int startPosition, int endPosition);
    }

    private static final class ParseResult<T> {
        private final T result;
        private final ParseError error;

        private ParseResult(T result, ParseError error) {
            this.result = result;
            this.error = error;
        }

        public static <T> ParseResult<T> success(T result) {
            return new ParseResult<>(result, null);
        }

        public static <T> ParseResult<T> error(String errorMessage, int position) {
            Validate.notNull(errorMessage, "errorMessage");
            return error(new ParseError(errorMessage, position));
        }

        public static <T> ParseResult<T> error(ParseError error) {
            Validate.notNull(error, "error");
            return new ParseResult<>(null, error);
        }

        public <U> ParseResult<U> mapResult(Function<T, U> mapper) {
            if (hasError()) {
                return ParseResult.error(error);
            } else {
                return ParseResult.success(mapper.apply(result));
            }
        }

        public boolean hasResult() {
            return result != null;
        }

        public boolean hasError() {
            return error != null;
        }

        private T getResult() {
            Validate.validState(hasResult(), "Result not available");
            return result;
        }

        private T getResultOrNull() {
            if (!hasResult()) {
                return null;
            }

            return result;
        }

        private ParseError getError() {
            Validate.validState(hasError(), "Error not available");
            return error;
        }
    }

    private static final class ParseError {
        private final String errorMessage;
        private final int position;

        private ParseError(String errorMessage, int position) {
            this.errorMessage = errorMessage;
            this.position = position;
        }
    }
}