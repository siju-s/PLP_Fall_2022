package edu.ufl.cise.plpfa22.ast;

import edu.ufl.cise.plpfa22.PLPException;
import edu.ufl.cise.plpfa22.TypeCheckException;

import static edu.ufl.cise.plpfa22.IToken.Kind;
import static edu.ufl.cise.plpfa22.LogHelper.printOutput;
import static edu.ufl.cise.plpfa22.ast.Types.Type;


public class TypeChecker implements ASTVisitor {
    private boolean isTreeTraversedOnce = false;
    private boolean isAnyChangesMade = false;
    private int loopCount = 0;


    @Override
    public Object visitBlock(Block block, Object arg) throws PLPException {
        for (ConstDec dec : block.constDecs) {
            dec.visit(this, arg);
        }
        for (VarDec dec : block.varDecs) {
            dec.visit(this, arg);
        }
        for (ProcDec dec : block.procedureDecs) {
            dec.visit(this, arg);
        }
        Statement statement = block.statement;
        statement.visit(this, arg);
        return null;
    }

    @Override
    public Object visitProgram(Program program, Object arg) throws PLPException {
        // Should run at least twice
        do {
            program.block.visit(this, arg);
            isTreeTraversedOnce = true;
            loopCount++;
        } while (isAnyChangesMade || loopCount == 1);
        return null;
    }

    @Override
    public Object visitStatementAssign(StatementAssign statementAssign, Object arg) throws PLPException {
        Types.Type expressionType = (Type) statementAssign.expression.visit(this, arg);
        Declaration identDec = statementAssign.ident.getDec();
        Types.Type identType = identDec.getType();

        if (identType != null && expressionType != null && identType != expressionType) {
            throw new TypeCheckException("Type mismatch: Expected:" + identType + " but found " + expressionType);
        }

        if (identDec instanceof ConstDec) {
            throw new TypeCheckException("Cannot assign again to CONST");
        }

        if (identType == Type.PROCEDURE || expressionType == Type.PROCEDURE) {
            throw new TypeCheckException("Cannot assign to a procedure");
        }

        // Type can be inferred from the RHS or LHS. If RHS type unknown, infer from LHS (if known).
        // If LHS type unknown, infer from RHS (if known).
        if (expressionType != null && identType == null) {
            identDec.setType(expressionType);
            isAnyChangesMade = true;
        } else if (identType != null && expressionType == null) {
            statementAssign.expression.setType(identType);
            isAnyChangesMade = true;
        } else {
            isAnyChangesMade = false;
        }
        printOutput("Typechecker visitStatementAssign identType:" + identDec.getType() + " expression type:" + statementAssign.expression.getType());
        return statementAssign.expression.getType();
    }

    @Override
    public Object visitVarDec(VarDec varDec, Object arg) throws PLPException {
        return null;
    }

    @Override
    public Object visitStatementCall(StatementCall statementCall, Object arg) throws PLPException {
        Types.Type type = statementCall.ident.getDec().getType();
        if (isTreeTraversedOnce && type != Types.Type.PROCEDURE) {
            throw new TypeCheckException("Expected PROCEDURE type but found " + type);
        }
        checkNullType(type);
        return null;
    }

    private void checkNullType(Types.Type type) throws TypeCheckException {
        if (isTreeTraversedOnce && type == null) {
            throw new TypeCheckException("Type is null");
        }
    }

    @Override
    public Object visitStatementInput(StatementInput statementInput, Object arg) throws PLPException {
        Types.Type type = statementInput.ident.dec.getType();
        printOutput("TypeChecker - visitStatementInput: Type:" + type);
        if (statementInput.ident.getDec() instanceof ConstDec) {
            throw new TypeCheckException("StatementInput type cannot contain CONST");
        }
        if (isTreeTraversedOnce && !isStatementInputType(type)) {
            throw new TypeCheckException("StatementInput type should be either Number, Boolean or String");
        }
        checkNullType(type);
        return null;
    }

    private boolean isStatementOutputType(Types.Type type) {
        return type == Types.Type.NUMBER || type == Types.Type.STRING || type == Types.Type.BOOLEAN;
    }

    private boolean isStatementInputType(Types.Type type) {
        return type == Types.Type.NUMBER || type == Types.Type.STRING || type == Types.Type.BOOLEAN;
    }

    @Override
    public Object visitStatementOutput(StatementOutput statementOutput, Object arg) throws PLPException {
        statementOutput.expression.visit(this, arg);
        if (statementOutput.expression instanceof ExpressionIdent) {
            Declaration declaration = ((ExpressionIdent) statementOutput.expression).getDec();

            Types.Type type = declaration.getType();
            printOutput("Typechecker - visitStatementOutput type:" + type);
            if (isTreeTraversedOnce && !isStatementOutputType(type)) {
                throw new TypeCheckException("StatementOutput type should be Number, String or Boolean");
            }
            checkNullType(type);
        }
        return statementOutput.expression.getType();
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
        Type type = (Type) statementIf.expression.visit(this, arg);
        printOutput("visitStatementIf -- type:" + type);
        checkGuardCondition(type);
        statementIf.statement.visit(this, arg);
        return null;
    }

    @Override
    public Object visitStatementWhile(StatementWhile statementWhile, Object arg) throws PLPException {
        Type type = (Type) statementWhile.expression.visit(this, arg);
        printOutput("visitStatementWhile -- type:" + type);
        checkGuardCondition(type);
        statementWhile.statement.visit(this, arg);
        return null;
    }

    private void checkGuardCondition(Type type) throws TypeCheckException {
        if (type != null && !type.equals(Type.BOOLEAN)) {
            throw new TypeCheckException("Guard condition should be BOOLEAN but found " + type);
        }
    }

    @Override
    public Object visitExpressionBinary(ExpressionBinary expressionBinary, Object arg) throws PLPException {
        Expression leftExpr = expressionBinary.e0;
        Expression rightExpr = expressionBinary.e1;

        // Set type for anything but ExpressionBinary
        if (!(leftExpr instanceof ExpressionBinary)) {
            leftExpr.visit(this, arg);
        }
        if (!(rightExpr instanceof ExpressionBinary)) {
            rightExpr.visit(this, arg);
        }
        Types.Type exprType = expressionBinary.getType();
        Kind kind = expressionBinary.op.getKind();

        Type type1 = leftExpr.getType();
        Type type2 = rightExpr.getType();

        // FIXME @Sh -> Break this
        if (isAnyEqualOperator(kind)) {
            if (type1 != null && type2 == null) {
                rightExpr.setType(type1);
            }
            else if (type2 != null && type1 == null) {
               leftExpr.setType(type2);
            }
        }

        type1 = (Types.Type) leftExpr.visit(this, arg);
        type2 = (Types.Type) rightExpr.visit(this, arg);

        if (isArithmeticOperator(kind) && exprType != null) {
            if (leftExpr.getType() == null) setExpressionType(exprType, leftExpr, expressionBinary);
            if (rightExpr.getType() == null) setExpressionType(exprType, rightExpr, expressionBinary);
        }

        printOutput("visitExpressionBinary kind:"+kind + " e0:"+expressionBinary.e0);

        if (type1 != null && type2 != null && !type1.equals(type2)) {
            throw new TypeCheckException("Types should be same");
        }

        isAnyChangesMade = type1 == null || type2 == null;

        if (type1 == null && type2 != null) {
            leftExpr.setType(type2);
            if (leftExpr instanceof ExpressionIdent) {
                ((ExpressionIdent) leftExpr).getDec().setType(type2);
            }
        } else if (type1 != null && type2 == null) {
            rightExpr.setType(type1);
            if (rightExpr instanceof ExpressionIdent) {
                ((ExpressionIdent) rightExpr).getDec().setType(type1);
            }
        }

        type1 = leftExpr.getType();
        type2 = rightExpr.getType();

        printOutput("visitExpressionBinary type1:"+type1 + " type2:"+type2);

        checkBinaryExpError(expressionBinary, kind, type1, type2);
        printOutput("TypeChecker- visitExpressionBinary type1:" + type1 + " Type2:" + type2);
        return expressionBinary.getType();
    }

    private boolean isArithmeticOperator(Kind kind) {
        return kind == Kind.TIMES || kind == Kind.PLUS || kind == Kind.MINUS || kind == Kind.DIV || kind == Kind.MOD;
    }

    private void checkBinaryExpError(ExpressionBinary expressionBinary, Kind kind, Type type1, Type type2) throws TypeCheckException {
        if (isTreeTraversedOnce && type1 == null && type2 == null) {
            throw new TypeCheckException("Types are not known for LHS and RHS.");
        }

        if (type1 != null && type1.equals(type2)) {
            if (isAnyEqualOperator(kind) && type1 != Type.PROCEDURE) {
                expressionBinary.setType(Type.BOOLEAN);
            }
            else if ((type1 == Type.NUMBER || type1 == Type.STRING || type1 == Type.BOOLEAN) && (kind == Kind.PLUS)) {
                expressionBinary.setType(type1);
            }
            else if ((type1 == Type.NUMBER) && (kind == Kind.MINUS || kind == Kind.MOD || kind == Kind.DIV)) {
                expressionBinary.setType(type1);
            }
            else if ((type1 == Type.NUMBER || type1 == Type.BOOLEAN) && (kind == Kind.TIMES)) {
                expressionBinary.setType(type1);
            }
            else {
                throw new TypeCheckException("Operation not allowed");
            }
        }
    }

    private void setExpressionType(Type type, Expression expression, ExpressionBinary expressionBinary) {
        if (expression instanceof ExpressionIdent) {
            ((ExpressionIdent) expression).getDec().setType(type);
            expression.setType(type);
        } else if (expression instanceof ExpressionBinary) {
            Expression e0 = ((ExpressionBinary) expression).e0;
            Expression e1 = ((ExpressionBinary) expression).e1;

            // Set expression type for child expressions
            setExpressionType(type, e0, expressionBinary);
            setExpressionType(type, e1, expressionBinary);
        }
    }

    private boolean isAnyEqualOperator(Kind kind) {
        return kind == Kind.EQ || kind == Kind.NEQ || kind == Kind.LT || kind == Kind.LE || kind == Kind.GT
                || kind == Kind.GE;
    }

    @Override
    public Object visitExpressionIdent(ExpressionIdent expressionIdent, Object arg) throws PLPException {
        Declaration declaration = expressionIdent.getDec();
        Type declarationType = declaration.getType();
        Type identType = expressionIdent.getType();
        isAnyChangesMade = declarationType == null || identType == null;

        if (identType == null) {
            expressionIdent.setType(declarationType);
        }
        else if (declarationType == null) {
            declaration.setType(identType);
        }

        return expressionIdent.getDec().getType();
    }

    @Override
    public Object visitExpressionNumLit(ExpressionNumLit expressionNumLit, Object arg) throws PLPException {
        Type type = expressionNumLit.getType();
        isAnyChangesMade = type == null;
        if (type == null) {
            expressionNumLit.setType(Type.NUMBER);
        }
        return expressionNumLit.getType();
    }

    @Override
    public Object visitExpressionStringLit(ExpressionStringLit expressionStringLit, Object arg) throws PLPException {
        Type type = expressionStringLit.getType();
        isAnyChangesMade = type == null;
        if (type == null) {
            expressionStringLit.setType(Type.STRING);
        }
        return expressionStringLit.getType();
    }

    @Override
    public Object visitExpressionBooleanLit(ExpressionBooleanLit expressionBooleanLit, Object arg) throws PLPException {
        Type type = expressionBooleanLit.getType();
        isAnyChangesMade = type == null;
        if (type == null) {
            expressionBooleanLit.setType(Types.Type.BOOLEAN);
        }
        return expressionBooleanLit.getType();
    }

    @Override
    public Object visitProcedure(ProcDec procDec, Object arg) throws PLPException {
        Type type = procDec.getType();
        isAnyChangesMade = type == null;
        if (type == null) {
            procDec.setType(Types.Type.PROCEDURE);
        }
        procDec.block.visit(this, arg);
        return procDec.getType();
    }

    @Override
    public Object visitConstDec(ConstDec constDec, Object arg) throws PLPException {
        Type type = constDec.getType();
        isAnyChangesMade = type == null;

        if (type == null) {
            if (constDec.val instanceof Integer) {
                constDec.setType(Types.Type.NUMBER);
            } else if (constDec.val instanceof String) {
                constDec.setType(Types.Type.STRING);
            } else if (constDec.val instanceof Boolean) {
                constDec.setType(Types.Type.BOOLEAN);
            }
        }
        return constDec.getType();
    }

    @Override
    public Object visitStatementEmpty(StatementEmpty statementEmpty, Object arg) throws PLPException {
        return null;
    }

    @Override
    public Object visitIdent(Ident ident, Object arg) throws PLPException {
        return null;
    }
}
