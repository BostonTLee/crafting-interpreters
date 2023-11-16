package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import static com.craftinginterpreters.lox.TokenType.*;

import java.util.logging.Logger;
import java.util.logging.Level;


public class Parser {

    Logger logger = Logger.getLogger(Parser.class.getName());

    private static class ParseError extends RuntimeException{}

    final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        logger.setLevel(Level.OFF);
        // ConsoleHandler handler = new ConsoleHandler();
        // handler.setFormatter(new SimpleFormatter() {
        //   private static final String format = "[%2$-7s] %3$s %n";
        // });
        // logger.addHandler(handler);
        // System.out.println(logger.getLevel());
        // this.logger = Logger.getLogger(Parser.class.getName());

        List<Stmt> statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        logger.info(String.format("Consuming type: %s", type));
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            // Discards tokens until we consume a semicolon (end of a statement)
            // Could go awry if we match a semicolon in a for loop
            if (previous().type == SEMICOLON) return;
            // If our next token is one of the below, we are (likely) starting
            // a *new* statement
            switch (peek().type) {
                case CLASS, FOR, FUN, IF, PRINT, RETURN, VAR, WHILE: return;
            }
            advance();
        }
    }

    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        TokenType nextType = peek().type;
        logger.info("Checking next type...");
        logger.info(String.format("Next type: %s", nextType));
        return (nextType == type);
    }

    private Token advance() {
        logger.info("Advancing token");
        if (!isAtEnd())
            current++;
        return previous();
    }

    private boolean isAtEnd() {
        boolean isAtEnd = (peek().type == EOF);
        logger.info(String.format("Is at end: %s", isAtEnd));
        return isAtEnd;
    }

    private Token peek() {
        // Return the current token we have yet to consume
        Token nextToken = tokens.get(current);
        logger.info(String.format("Next token: %s", nextToken));
        return nextToken;
    }

    private Token previous() {
        // Returns the most recently-consumed token
        Token previousToken = tokens.get(current - 1);
        logger.info(String.format("Previous token: %s", previousToken));
        return previousToken;
    }

    private Stmt declaration() {
        try{
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'");

        Stmt initializer;
        if(match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            // Wrapping in an expression statement
            // so that the initializer is always a Stmt
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        if (increment != null) {
            // The desugared body is the old body with the increment
            // clause (e.g. i++) run at the end of every iteration
            body = new Stmt.Block(Arrays.asList(
                body,
                new Stmt.Expression(increment)
            ));
        }

        // If there is no condition, treat the loop as infinite
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // If there is an initializer, put it before the desugared while loop
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        // Solution to "dangling else" problem:
        // An else statement belongs to the nearest 'if'
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after print.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }


    private Expr expression() {
        // Rule: expression -> equality
        logger.info("Evaluating expression");
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            // We recurse instead of looping because assignment
            // is right-associative
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                // This cast works because every valid assignment target
                // is also valid as a normal expression.
                // However, not all expressions are valid assignment targets... 
                // As of Ch 8, the only valid assignment target is a variable expression
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            // We report an error, but don't throw it here,
            // because the parser isn't in an invalid state
            error(equals, "Invalid assignment target");
        }

        return expr;
    }
    
    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr , operator, right);
        }

        return expr;
    }

    private Expr equality() {
        logger.info("Evaluating equality");
        // Rule: equality -> comparison ( ("==" | "!=") comparison )*

        // If the loop below never fires
        // ie, we never see an equality operator,
        // we "fall through" to just matching comparison.
        // This means we match any expression of higher precedence

        Expr expr = comparison();
        // This loop is the ( ("==" | "!=") comparison )*
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        logger.info("Evaluating comparison");
        // Rule: comparison -> term ( (">" | "<" | ">=" | "<=") term )*
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        logger.info("Evaluating term");
        // Rule: term -> factor ( ("+" | "-") factor)*
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        logger.info("Evaluating factor");
        // Rule: factor -> unary ( ("*" | "/") unary)*
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary(){
        logger.info("Evaluating unary");
        // Rule: unary -> ("!" | "-") unary | primary
        if (match(MINUS, BANG)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        
        // If we don't find a unary operator, just match primary
        return primary();
    }

    private Expr primary() {
        logger.info("Evaluating primary");
        // Rule: primary -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression
        // ")"
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(LEFT_PAREN)) {
            Expr expression = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            logger.info("Found grouping");
            return new Expr.Grouping(expression);
        }
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
        throw error(peek(), "Expect expression.");
    }

}
