package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


public class Lox {

    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    // We store the interpreter as an object field
    // so that later we can store (Lox) global variables in it
    private static final Interpreter interpreter = new Interpreter();
    public static void main(String[] args) throws IOException {
        System.out.println("In main");
        if (args.length > 1){
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            System.out.println("Running file");
            runFile(args[0]);
        } else{
            System.out.println("Running prompt");
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();
        // Don't continue to subsequent phases if there is a parsing error
        if (hadError) return;
        interpreter.interpret(statements);
        // System.out.println(new AstPrinter().print(expression));

    }

    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error (Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + 
            "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    static void report(int line, String where, String message) {
        System.err.printf("[line %s] Error %s: %s\n", line, where, message);
        hadError = true;
    }
}
