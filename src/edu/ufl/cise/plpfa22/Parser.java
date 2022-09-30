package edu.ufl.cise.plpfa22;

import edu.ufl.cise.plpfa22.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Parser implements IParser {

    private IToken firstToken;

    private IToken token;

    private final ILexer lexer;
    private boolean isBeginParentBlock;

    public Parser(ILexer lexer) {
        this.lexer = lexer;
        try {
            firstToken = lexer.next();
            token = firstToken;
        } catch (LexicalException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ASTNode parse() throws PLPException {
        return handleProgram(token);
    }

    private Program handleProgram(IToken startToken) throws LexicalException, SyntaxException {
        Block block = handleBlock(startToken);
        match(IToken.Kind.DOT);
        consume();
        match(IToken.Kind.EOF);
        consume();
        return new Program(startToken, block);
    }

    private Block handleBlock(IToken startToken) throws LexicalException, SyntaxException {
        List<ConstDec> constDecs = new ArrayList<>();
        List<VarDec> varDecs = new ArrayList<>();
        List<ProcDec> procedureDecs = new ArrayList<>();
        Statement statement = new StatementEmpty(startToken);
      while (token.getKind() != IToken.Kind.DOT) {
          switch (token.getKind()) {
              case KW_CONST -> {
                  consume();
                  IToken ident;
                  do {
                      if (token.getKind() == IToken.Kind.IDENT) {
                          ident = token;
                          consume();
                          match(IToken.Kind.EQ);
                          consume();
                          switch (token.getKind()) {
                              case NUM_LIT -> constDecs.add(new ConstDec(firstToken, ident, token.getIntValue()));
                              case STRING_LIT -> constDecs.add(new ConstDec(firstToken, ident, token.getStringValue()));
                              case BOOLEAN_LIT -> constDecs.add(new ConstDec(firstToken, ident, token.getBooleanValue()));
                              default -> throw new SyntaxException();
                          }
                          consume();
                          if (token.getKind() == IToken.Kind.COMMA) consume();
                      } else {
                          throw new SyntaxException();
                      }
                  } while (token.getKind() != IToken.Kind.SEMI);
                  consume();
              }
              case KW_VAR -> {
                  consume();
                  while (this.token.getKind() != IToken.Kind.SEMI) {
                      if (this.token.getKind() == IToken.Kind.IDENT) {
                          varDecs.add(new VarDec(firstToken, this.token));
                      }
                      consume();
                  }
                  consume();
              }
              case KW_PROCEDURE -> {
                  consume();
                  match(IToken.Kind.IDENT);
                  if (token.getKind() != IToken.Kind.DOT && token.getKind() != IToken.Kind.EOF) {
                      IToken ident = token;
                      consume();
                      match(IToken.Kind.SEMI);
                      consume();
                      Block block = handleBlock(token);
                      procedureDecs.add(new ProcDec(firstToken, ident, block));
                  }
              }
              default -> statement = handleStatement(startToken);
          }
          if (statement instanceof StatementBlock || statement instanceof StatementCall) {
              break;
          }
      }
        return new Block(firstToken, constDecs, varDecs, procedureDecs, statement);
    }

    private Statement handleStatement(IToken startToken) throws LexicalException, SyntaxException {
        Statement statement;
        switch (token.getKind()) {
            case BANG -> {
                consume();
                Expression expression = handleExpression(startToken);
                statement =  new StatementOutput(startToken, expression);
            }
            case KW_IF -> {
                consume();
                Expression expression = handleExpression(startToken);
                //consume();
                match(IToken.Kind.KW_THEN);
                consume();
                Statement ifStatement = handleStatement(startToken);
                statement = new StatementIf(startToken, expression, ifStatement);
            }
            case KW_WHILE -> {
                consume();
                Expression expression = handleExpression(startToken);
                match(IToken.Kind.KW_DO);
                consume();
                Statement whileStatement = handleStatement(startToken);
                statement = new StatementWhile(startToken, expression, whileStatement);
            }
            case KW_BEGIN -> {
                List<Statement> statements =  new ArrayList<>();
                consume();
                isBeginParentBlock = true;
                while (token.getKind() != IToken.Kind.KW_END) {
                    Statement statement1 = handleStatement(token);
                    if(token.getKind() == IToken.Kind.SEMI) consume();
                    statements.add(statement1);
                }
                match(IToken.Kind.KW_END);
                consume();
                statement =  new StatementBlock(firstToken, statements);
                if (token.getKind() == IToken.Kind.SEMI) {
                    consume();
                }
                isBeginParentBlock = false;
            }
            case QUESTION -> {
                consume();
                match(IToken.Kind.IDENT);
                statement =  new StatementInput(firstToken, new Ident(token));
                consume();
            }
            case KW_CALL -> {
                consume();
                match(IToken.Kind.IDENT);
                statement = new StatementCall(firstToken, new Ident(token));
                consume();
                // CALL can end with a semi too but not necessary
                if (token.getKind() == IToken.Kind.SEMI) {
                    consume();
                }
            }
            case IDENT -> {
                IToken ident = token;
                consume();
                match(IToken.Kind.ASSIGN);
                consume();
                statement = new StatementAssign(startToken, new Ident(ident), handleExpression(startToken));
            }
            case STRING_LIT, BOOLEAN_LIT, NUM_LIT -> {
                statement =  new StatementOutput(startToken, getExpression(startToken));
            }
            default -> statement =  new StatementEmpty(startToken);
        }
        return statement;
    }


    private static void throwSyntaxException(String msg, IToken itoken) throws SyntaxException {
        throw new SyntaxException(msg + " Found token:" + itoken.getKind(),
                itoken.getSourceLocation().line(), itoken.getSourceLocation().line());
    }

    private Expression handlePrimaryExpression(IToken itoken) throws LexicalException, SyntaxException {
        Expression expression;
        System.out.println("itoken = " + token.getKind());
        switch (token.getKind()) {
            case IDENT -> expression = new ExpressionIdent(token);
            case NUM_LIT -> expression = new ExpressionNumLit(token);
            case STRING_LIT -> expression = new ExpressionStringLit(token);
            case BOOLEAN_LIT -> expression = new ExpressionBooleanLit(token);
            case LPAREN -> {
                consume(); // LPAREN
                expression = handleExpression(itoken);
                match(IToken.Kind.RPAREN);
            }
            default -> throw new SyntaxException();
        }
        consume();
        return expression;
    }

    private Expression handleMultiplicativeExpression(IToken itoken) throws LexicalException, SyntaxException {
        Expression operand1 = handlePrimaryExpression(token);
        Expression operand2;
        IToken operator;
        while (token.getKind() == IToken.Kind.TIMES || token.getKind() == IToken.Kind.DIV
                || token.getKind() == IToken.Kind.MOD) {
            operator = token;
            switch (token.getKind()) {
                case TIMES, DIV, MOD -> consume();
            }
            operand2 = handlePrimaryExpression(token);
            operand1 = new ExpressionBinary(firstToken, operand1, operator, operand2);
        }
        return operand1;
    }


    private Expression handleAdditiveExpression(IToken itoken) throws LexicalException, SyntaxException {
        Expression operand1 = handleMultiplicativeExpression(token);
        Expression operand2;
        IToken operator;
        while (token.getKind() == IToken.Kind.PLUS || token.getKind() == IToken.Kind.MINUS) {
            operator = token;
            switch (token.getKind()) {
                case PLUS, MINUS -> consume();
            }
            operand2 = handleMultiplicativeExpression(token);
            operand1 = new ExpressionBinary(firstToken, operand1, operator, operand2);
        }
        return operand1;
    }

    private Expression handleExpression(IToken startToken) throws LexicalException, SyntaxException {
        Expression operand1 = handleAdditiveExpression(token);
        Expression operand2;
        IToken operator;
        IToken.Kind kind = token.getKind();
        System.out.println("token kind= " + kind + " value: " + String.valueOf(token.getText()) + "" +
                " startToken kind:"+startToken.getKind() + " startTokenValue:"+ Arrays.toString(startToken.getText()));
        if (startToken.getKind() != IToken.Kind.KW_IF && startToken.getKind() != IToken.Kind.KW_WHILE) {
            if (isInvalidExprCondition(kind))
                throwSyntaxException("Invalid expression", token);
        }
        while (isExpressionOperand(token.getKind())) {
            operator = token;
            if (isExpressionOperand(token.getKind())) consume();
            operand2 = handleAdditiveExpression(token);
            operand1 = new ExpressionBinary(firstToken, operand1, operator, operand2);
        }
        if (startToken.getKind() == IToken.Kind.BANG && !isBeginParentBlock) {
            if (token.getKind() == IToken.Kind.SEMI) {
                throwSyntaxException("Found incorrect semi with bang", token);
            }
        }
        if (token.getKind() == IToken.Kind.SEMI) {
            consume();
        }
        return operand1;
    }

    private boolean isInvalidExprCondition(IToken.Kind kind) {
        return !isValidOperator(kind) && kind != IToken.Kind.DOT && kind != IToken.Kind.RPAREN && kind != IToken.Kind.KW_END;
    }

    private boolean isValidOperator(IToken.Kind kind) {
        return isExpressionOperand(kind) || kind == IToken.Kind.TIMES || kind == IToken.Kind.MOD || kind == IToken.Kind.DIV ||
                kind == IToken.Kind.PLUS || kind == IToken.Kind.MINUS || kind == IToken.Kind.SEMI;
    }

    private boolean isExpressionOperand(IToken.Kind kind) {
        switch (kind) {
            case LT, GT, EQ, NEQ, LE, GE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void consume() throws LexicalException {
        token = lexer.next();
    }

    private void match(IToken.Kind kind) throws SyntaxException {
        if (token.getKind() != kind) {
            throwSyntaxException("Expected "+kind, token);
        }
    }

    private Expression getExpression(IToken token) throws SyntaxException {
        switch (token.getKind()) {
            case NUM_LIT -> {
                return new ExpressionNumLit(token);
            }
            case BOOLEAN_LIT -> {
                return new ExpressionBooleanLit(token);
            }
            case STRING_LIT -> {
                return new ExpressionStringLit(token);
            }
            case IDENT -> {
                return new ExpressionIdent(token);
            }
            default -> {
                throw new SyntaxException();
//                throwSyntaxException("Token should be (NUM_LIT, BOOLEAN_LIT, STRING_LIT or IDENT", token);
            }
        }
    }
}
