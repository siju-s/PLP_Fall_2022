package edu.ufl.cise.plpfa22.ast;

import edu.ufl.cise.plpfa22.*;

import java.util.Arrays;

public class AstVisitorImpl implements ASTVisitor {
    private final SymbolTable symbolTable = new SymbolTable();

    @Override
    public Object visitBlock(Block block, Object arg) throws PLPException {
        for (ConstDec dec : block.constDecs) {
            dec.visit(this, arg);
        }
        for (VarDec dec : block.varDecs) {
            dec.visit(this, arg);
        }
        for (ProcDec dec : block.procedureDecs) {
            showOutput("Dec:" + dec + " scope:" + symbolTable.getCurrentScope());
            dec.setNest(symbolTable.getCurrentScope());
            boolean result = symbolTable.insert(String.valueOf(dec.ident.getText()), dec);
            if (!result) {
                throw new ScopeException();
            }
        }
        for (ProcDec dec : block.procedureDecs) {
            dec.visit(this, arg);
        }
        Statement statement = block.statement;
        statement.visit(this, arg);
        return null;
    }

    private void showOutput(Object text) {
        LogHelper.printOutput(text);
    }

    @Override
    public Object visitProgram(Program program, Object arg) throws PLPException {
        program.block.visit(this, arg);
        return null;
    }

    @Override
    public Object visitStatementAssign(StatementAssign statementAssign, Object arg) throws PLPException {
        showOutput("statementAssign = " + Arrays.toString(statementAssign.ident.getText()));
        Declaration leftIdentDeclaration = symbolTable.lookup(String.valueOf(statementAssign.ident.getText()));
        if (leftIdentDeclaration == null) {
            throw new ScopeException();
        }
        statementAssign.ident.visit(this, arg);
        statementAssign.expression.visit(this, arg);


        showOutput("visitStatementAssign type:" + statementAssign.expression.type);
        return null;
    }

    @Override
    public Object visitVarDec(VarDec varDec, Object arg) throws PLPException {
        showOutput("visitVarDec varDEc:" + Arrays.toString(varDec.ident.getText()));
        varDec.setNest(symbolTable.getCurrentScope());
        boolean result = symbolTable.insert(String.valueOf(varDec.ident.getText()), varDec);
        if (!result) {
            throw new ScopeException();
        }
        return null;
    }

    @Override
    public Object visitStatementCall(StatementCall statementCall, Object arg) throws PLPException {
        setupIdent(arg, statementCall.ident);
        return null;
    }

    private void setupIdent(Object arg, Ident ident) throws PLPException {
        ident.visit(this, arg);
        Declaration declaration = symbolTable.lookup(String.valueOf(ident.firstToken.getText()));
        if (declaration == null) {
            throw new ScopeException();
        }
    }

    @Override
    public Object visitStatementInput(StatementInput statementInput, Object arg) throws PLPException {
        setupIdent(arg, statementInput.ident);
        return null;
    }

    @Override
    public Object visitStatementOutput(StatementOutput statementOutput, Object arg) throws PLPException {
        statementOutput.expression.visit(this, arg);
        showOutput(statementOutput.expression);
        if (statementOutput.expression instanceof ExpressionIdent) {
            Declaration declaration = symbolTable.lookup(String.valueOf(statementOutput.expression.firstToken.getText()));
            if (declaration == null) {
                throw new ScopeException();
            }
        }
        return null;
    }

    @Override
    public Object visitStatementBlock(StatementBlock statementBlock, Object arg) throws PLPException {
        for (Statement statement : statementBlock.statements) {
            statement.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitStatementIf(StatementIf statementIf, Object arg) throws PLPException {
        statementIf.expression.visit(this, arg);
        statementIf.statement.visit(this, arg);
        return null;
    }

    @Override
    public Object visitStatementWhile(StatementWhile statementWhile, Object arg) throws PLPException {
        statementWhile.expression.visit(this, arg);
        statementWhile.statement.visit(this, arg);
        return null;
    }

    @Override
    public Object visitExpressionBinary(ExpressionBinary expressionBinary, Object arg) throws PLPException {
        expressionBinary.e0.visit(this, arg);
        expressionBinary.e1.visit(this, arg);
        return null;
    }

    @Override
    public Object visitExpressionIdent(ExpressionIdent expressionIdent, Object arg) throws PLPException {
        Declaration declaration = symbolTable.lookup(String.valueOf(expressionIdent.firstToken.getText()));
        if (declaration == null) {
            throw new ScopeException();
        }
        expressionIdent.setDec(declaration);
        expressionIdent.setNest(symbolTable.getCurrentScope());
        return expressionIdent.getDec().getType();
    }

    @Override
    public Object visitExpressionNumLit(ExpressionNumLit expressionNumLit, Object arg) throws PLPException {
        return expressionNumLit.getType();
    }

    @Override
    public Object visitExpressionStringLit(ExpressionStringLit expressionStringLit, Object arg) throws PLPException {
        return expressionStringLit.getType();
    }

    @Override
    public Object visitExpressionBooleanLit(ExpressionBooleanLit expressionBooleanLit, Object arg) throws PLPException {
        return expressionBooleanLit.getType();
    }

    @Override
    public Object visitProcedure(ProcDec procDec, Object arg) throws PLPException {
        symbolTable.enterScope();
        procDec.block.visit(this, arg);
        symbolTable.clearProcVariables();
        symbolTable.leaveScope();
        return procDec.getType();
    }

    @Override
    public Object visitConstDec(ConstDec constDec, Object arg) throws PLPException {
        constDec.setNest(symbolTable.getCurrentScope());
        boolean result = symbolTable.insert(String.valueOf(constDec.ident.getText()), constDec);
        if (!result) {
            throw new ScopeException();
        }
        return constDec.getType();
    }

    @Override
    public Object visitStatementEmpty(StatementEmpty statementEmpty, Object arg) throws PLPException {
        return null;
    }

    @Override
    public Object visitIdent(Ident ident, Object arg) throws PLPException {
        Declaration declaration = symbolTable.lookup(String.valueOf(ident.firstToken.getText()));
        ident.setDec(declaration);
        ident.setNest(symbolTable.getCurrentScope());
        return null;
    }
}
