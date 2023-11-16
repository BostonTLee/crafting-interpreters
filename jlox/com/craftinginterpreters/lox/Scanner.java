package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<Token>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, TokenType> keywords = Map.ofEntries(
        Map.entry("and", AND),
        Map.entry("class", CLASS),
        Map.entry("else", ELSE),
        Map.entry("false", FALSE),
        Map.entry("for", FOR),
        Map.entry("fun", FUN),
        Map.entry("if", IF),
        Map.entry("nil", NIL),
        Map.entry("or", OR),
        Map.entry("print", PRINT),
        Map.entry("return", RETURN),
        Map.entry("super", SUPER),
        Map.entry("this", THIS),
        Map.entry("true", TRUE),
        Map.entry("var", VAR),
        Map.entry("while", WHILE)
    );


    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            case '/':
                if (match('/')) {
                    // The slash is the first character of a comment
                    // Comment consumes entire line
                    // Notice we don't add any tokens (duh)
                    while (peek() != '\n' && !isAtEnd())
                        advance();
                } else {
                    // The slash is a division sign
                    addToken(SLASH);
                }

            case ' ':
                break;
            case '\r':
                break;
            case '\t':
                break;
            case '\n':
                line++;
                break;

            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    number();

                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    // Sets hadError so code only gets scanned, not executed
                    Lox.error(line, String.format("Unexpected character: %s", c));
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        // If we don't match a reserved keyword
        // the identifier is user-defined
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    private void number() {
        while (isDigit(peek()))
            advance();

        // Need two characters of lookahead
        // We don't want to consume the '.' until we are sure the character after
        // it is a digit
        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek()))
                advance();
        }
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            // Allows multiline strings
            if (peek() == '\n')
                line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // Consume the closing '"'
        advance();

        // Trim the quotes from the value
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private char advance() {
        return source.charAt(current++);
    }

    private boolean match(char expected) {
        if (isAtEnd())
            return false;
        if (source.charAt(current) != expected)
            return false;

        current++;
        return true;
    }

    private char peek() {
        // Like advance, but doesn't consume the character
        // We have one character of "lookahead"
        // The amount of lookahead needed is determined by the lexical grammar rules
        if (isAtEnd())
            return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        // Now there are two characters of lookahead
        // Specifically required for parsing decimal numbers
        if (current + 1 >= source.length())
            return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return ((c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_');
    }

    private boolean isAlphaNumeric(char c) {
        return (isAlpha(c) || isDigit(c));
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
