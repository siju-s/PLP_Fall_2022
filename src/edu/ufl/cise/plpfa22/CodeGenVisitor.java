package edu.ufl.cise.plpfa22;

import edu.ufl.cise.plpfa22.ast.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import edu.ufl.cise.plpfa22.IToken.Kind;
import edu.ufl.cise.plpfa22.ast.Types.Type;

public class CodeGenVisitor implements ASTVisitor, Opcodes {

	public static final String JAVA_LANG_STRING = "java/lang/String";
	public static final String JAVA_LANG_STRING_DESC = "(Ljava/lang/String;)Z";
	final String packageName;
	final String className;
	final String sourceFileName;
	final String fullyQualifiedClassName; 
	final String classDesc;
	
	ClassWriter classWriter;

	private static final String BOOLEAN_NOT_CLASS = "edu/ufl/cise/plpfa22/BooleanNotOp";

	
	public CodeGenVisitor(String className, String packageName, String sourceFileName) {
		super();
		this.packageName = packageName;
		this.className = className;
		this.sourceFileName = sourceFileName;
		this.fullyQualifiedClassName = packageName + "/" + className;
		this.classDesc="L"+this.fullyQualifiedClassName+';';
	}

	@Override
	public Object visitBlock(Block block, Object arg) throws PLPException {
		MethodVisitor methodVisitor = (MethodVisitor)arg;
		methodVisitor.visitCode();
		for (ConstDec constDec : block.constDecs) {
			constDec.visit(this, null);
		}
		for (VarDec varDec : block.varDecs) {
			varDec.visit(this, methodVisitor);
		}
		for (ProcDec procDec: block.procedureDecs) {
			procDec.visit(this, null);
		}
		//add instructions from statement to method
		block.statement.visit(this, arg);
		methodVisitor.visitInsn(RETURN);
		methodVisitor.visitMaxs(0,0);
		methodVisitor.visitEnd();
		return null;

	}

	@Override
	public Object visitProgram(Program program, Object arg) throws PLPException {
		//create a classWriter and visit it
		classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		//Hint:  if you get failures in the visitMaxs, try creating a ClassWriter with 0
		// instead of ClassWriter.COMPUTE_FRAMES.  The result will not be a valid classfile,
		// but you will be able to print it so you can see the instructions.  After fixing,
		// restore ClassWriter.COMPUTE_FRAMES
		classWriter.visit(V18, ACC_PUBLIC | ACC_SUPER, fullyQualifiedClassName, null, "java/lang/Object", null);

		//get a method visitor for the main method.		
		MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
		//visit the block, passing it the methodVisitor
		program.block.visit(this, methodVisitor);
		//finish up the class
        classWriter.visitEnd();
        //return the bytes making up the classfile
		return classWriter.toByteArray();
	}

	@Override
	public Object visitStatementAssign(StatementAssign statementAssign, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitVarDec(VarDec varDec, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitStatementCall(StatementCall statementCall, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitStatementInput(StatementInput statementInput, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitStatementOutput(StatementOutput statementOutput, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor)arg;
		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		statementOutput.expression.visit(this, arg);
		Type etype = statementOutput.expression.getType();
		String JVMType = (etype.equals(Type.NUMBER) ? "I" : (etype.equals(Type.BOOLEAN) ? "Z" : "Ljava/lang/String;"));
		String printlnSig = "(" + JVMType +")V";
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", printlnSig, false);
		return null;
	}

	@Override
	public Object visitStatementBlock(StatementBlock statementBlock, Object arg) throws PLPException {
		for(Statement statement: statementBlock.statements) {
			statement.visit(this, arg);
		}
		return null;
	}

	@Override
	public Object visitStatementIf(StatementIf statementIf, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor) arg;
		statementIf.expression.visit(this, arg);
		Label exprLabel = new Label();
		mv.visitJumpInsn(IFEQ, exprLabel);
		statementIf.statement.visit(this, arg);
		mv.visitLabel(exprLabel);
		return null;
	}

	@Override
	public Object visitStatementWhile(StatementWhile statementWhile, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor) arg;
		Label label1 = new Label();
		mv.visitJumpInsn(GOTO, label1);
		Label label2 = new Label();
		mv.visitLabel(label2);
		statementWhile.statement.visit(this, arg);
		mv.visitLabel(label1);
		statementWhile.expression.visit(this, arg);
		mv.visitJumpInsn(IFNE, label2);
		return null;
	}

	@Override
	public Object visitExpressionBinary(ExpressionBinary expressionBinary, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor) arg;
		Type argType = expressionBinary.e0.getType();
		Kind op = expressionBinary.op.getKind();
		switch (argType) {
		case NUMBER -> {
			expressionBinary.e0.visit(this, arg);
			expressionBinary.e1.visit(this, arg);
			switch (op) {
			case PLUS -> mv.visitInsn(IADD);
			case MINUS -> mv.visitInsn(ISUB);
			case TIMES -> mv.visitInsn(IMUL);
			case DIV -> mv.visitInsn(IDIV);
			case MOD -> mv.visitInsn(IREM);
			case EQ -> {
				visitExpBinaryOp(mv, IF_ICMPNE);
			}
			case NEQ -> {
				visitExpBinaryOp(mv, IF_ICMPEQ);
			}
			case LT -> {
				visitExpBinaryOp(mv, IF_ICMPGE);
			}
			case LE -> {
				visitExpBinaryOp(mv, IF_ICMPGT);
			}
			case GT -> {
				visitExpBinaryOp(mv, IF_ICMPLE);
			}
			case GE -> {
				visitExpBinaryOp(mv, IF_ICMPLT);
			}
			default -> {
				throw new IllegalStateException("code gen bug in visitExpressionBinary NUMBER");
			}
			}
		}
		case BOOLEAN -> {
			expressionBinary.e0.visit(this, arg);
			expressionBinary.e1.visit(this, arg);
			switch (op) {
				case PLUS -> mv.visitInsn(IOR);
				case TIMES -> mv.visitInsn(IAND);
				case EQ -> {
					visitExpBinaryOp(mv, IF_ICMPNE);
				}
				case NEQ -> {
					visitExpBinaryOp(mv, IF_ICMPEQ);
				}
				case LT -> {
					visitExpBinaryOp(mv, IF_ICMPGE);
				}
				case LE -> {
					visitExpBinaryOp(mv, IF_ICMPGT);
				}
				case GT -> {
					visitExpBinaryOp(mv, IF_ICMPLE);
				}
				case GE -> {
					visitExpBinaryOp(mv, IF_ICMPLT);
				}
				default -> {
					throw new IllegalStateException("code gen bug in visitExpressionBinary BOOLEAN");
				}
			}
		}
		case STRING -> {
			Label start = new Label();
			Label end = new Label();
			expressionBinary.e0.visit(this, arg);
			expressionBinary.e1.visit(this, arg);

			switch (op) {
				case PLUS -> {
					mv.visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_STRING, "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
				}
				case EQ -> {
					mv.visitJumpInsn(IF_ACMPEQ, start);
					mv.visitLdcInsn(false);
				}
				case NEQ -> {
					mv.visitJumpInsn(IF_ACMPNE, start);
					mv.visitLdcInsn(false);
				}
				case LT -> {
					mv.visitInsn(Opcodes.SWAP);
					mv.visitInsn(DUP2);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
                    mv.visitVarInsn(ISTORE, 1);

					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
					mv.visitMethodInsn(INVOKESTATIC, BOOLEAN_NOT_CLASS, "not", "(Z)Z",false);

                    mv.visitVarInsn(ILOAD, 1);
					mv.visitInsn(IAND);

				}
				case LE -> {
					mv.visitInsn(Opcodes.SWAP);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
				}
				case GT -> {
					mv.visitInsn(DUP2);
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
					mv.visitVarInsn(ISTORE, 1);

					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
					mv.visitMethodInsn(INVOKESTATIC, BOOLEAN_NOT_CLASS, "not", "(Z)Z",false);

					mv.visitVarInsn(ILOAD, 1);
					mv.visitInsn(IAND);
				}
				case GE -> {
					mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
				}
				default -> {
					throw new IllegalStateException("code gen bug in visitExpressionBinary BOOLEAN");
				}
			}
			mv.visitJumpInsn(Opcodes.GOTO, end); //GOTO L0
			mv.visitLabel(start);

			mv.visitLdcInsn(true);//LDC 1
			mv.visitLabel(end);
		}
		default -> {
			throw new IllegalStateException("code gen bug in visitExpressionBinary");
		}
		}
		return null;
	}

	private void visitExpBinaryOp(MethodVisitor mv, int opcode) {
			Label labelNumEqFalseBr = new Label();
			mv.visitJumpInsn(opcode, labelNumEqFalseBr);
			mv.visitInsn(ICONST_1);
			Label labelPostNumEq = new Label();
			mv.visitJumpInsn(GOTO, labelPostNumEq);
			mv.visitLabel(labelNumEqFalseBr);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(labelPostNumEq);
	}

	@Override
	public Object visitExpressionIdent(ExpressionIdent expressionIdent, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitExpressionNumLit(ExpressionNumLit expressionNumLit, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor)arg;
		mv.visitLdcInsn(expressionNumLit.getFirstToken().getIntValue());
		return null;
	}

	@Override
	public Object visitExpressionStringLit(ExpressionStringLit expressionStringLit, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor)arg;
		mv.visitLdcInsn(expressionStringLit.getFirstToken().getStringValue());
		return null;
	}

	@Override
	public Object visitExpressionBooleanLit(ExpressionBooleanLit expressionBooleanLit, Object arg) throws PLPException {
		MethodVisitor mv = (MethodVisitor)arg;
		mv.visitLdcInsn(expressionBooleanLit.getFirstToken().getBooleanValue());
		return null;
	}

	@Override
	public Object visitProcedure(ProcDec procDec, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitConstDec(ConstDec constDec, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object visitStatementEmpty(StatementEmpty statementEmpty, Object arg) throws PLPException {
		return null;
	}

	@Override
	public Object visitIdent(Ident ident, Object arg) throws PLPException {
		throw new UnsupportedOperationException();
	}

}
