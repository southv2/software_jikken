package lang.c.parse;

import java.io.PrintStream;

import lang.*;
import lang.c.*;

public class Expression extends CParseRule {
	// expression ::= term { expressionAdd }
	private CParseRule expression;

	public Expression(CParseContext pcx) {
	}
	public static boolean isFirst(CToken tk) {
		return Term.isFirst(tk);
	}
	public void parse(CParseContext pcx) throws FatalErrorException {
		// ここにやってくるときは、必ずisFirst()が満たされている
		CParseRule term = null, list = null;
		term = new Term(pcx);
		term.parse(pcx);
		CTokenizer ct = pcx.getTokenizer();
		CToken tk = ct.getCurrentToken(pcx);
		while (ExpressionAdd.isFirst(tk)) {
			list = new ExpressionAdd(pcx, term);
			list.parse(pcx);
			term = list;
			tk = ct.getCurrentToken(pcx);
		}
		expression = term;
	}

	public void semanticCheck(CParseContext pcx) throws FatalErrorException {
		if (expression != null) {
			expression.semanticCheck(pcx);
			this.setCType(expression.getCType());		// expression の型をそのままコピー
			this.setConstant(expression.isConstant());
		}
	}

	public void codeGen(CParseContext pcx) throws FatalErrorException {
		PrintStream o = pcx.getIOContext().getOutStream();
		o.println(";;; expression starts");
		if (expression != null) expression.codeGen(pcx);
		o.println(";;; expression completes");
	}
}

class ExpressionAdd extends CParseRule {
	// expressionAdd ::= '+' term
	private CToken plus;
	private CParseRule left, right;

	public ExpressionAdd(CParseContext pcx, CParseRule left) {
		this.left = left;
	}
	public static boolean isFirst(CToken tk) {
		return tk.getType() == CToken.TK_PLUS;
	}
	public void parse(CParseContext pcx) throws FatalErrorException {
		// ここにやってくるときは、必ずisFirst()が満たされている
		CTokenizer ct = pcx.getTokenizer();
		plus = ct.getCurrentToken(pcx);
		// +の次の字句を読む
		CToken tk = ct.getNextToken(pcx);
		if (Term.isFirst(tk)) {
			right = new Term(pcx);
			right.parse(pcx);
		} else {
			pcx.fatalError(tk.toExplainString() + "+の後ろはtermです");
		}
	}
	public void semanticCheck(CParseContext pcx) throws FatalErrorException {
		// 足し算の型計算規則
		final int s[][] = {
		//		T_err			T_int
			{	CType.T_err,	CType.T_err },	// T_err
			{	CType.T_err,	CType.T_int },	// T_int
		};
		int lt = 0, rt = 0;
		boolean lc = false, rc = false;
		if (left != null) {
			left.semanticCheck(pcx);
			lt = left.getCType().getType();		// +の左辺の型
			lc = left.isConstant();
		} else {
			pcx.fatalError(plus.toExplainString() + "左辺がありません");
		}
		if (right != null) {
			right.semanticCheck(pcx);
			rt = right.getCType().getType();	// +の右辺の型
			rc = right.isConstant();
		} else {
			pcx.fatalError(plus.toExplainString() + "右辺がありません");
		}
		int nt = s[lt][rt];						// 規則による型計算
		if (nt == CType.T_err) {
			pcx.fatalError(plus.toExplainString() + "左辺の型[" + left.getCType().toString() + "]と右辺の型[" + right.getCType().toString() + "]は足せません");
		}
		this.setCType(CType.getCType(nt));
		this.setConstant(lc && rc);				// +の左右両方が定数のときだけ定数
	}
	public void codeGen(CParseContext pcx) throws FatalErrorException {
		PrintStream o = pcx.getIOContext().getOutStream();
		if (left != null && right != null) {
			left.codeGen(pcx);
			right.codeGen(pcx);
			o.println("\tMOV\t-(R6), R0\t; ExpressionAdd: ２数を取り出して、足し、積む<" + plus.toExplainString() + ">");
			o.println("\tADD\t-(R6), R0\t; ExpressionAdd:");
			o.println("\tMOV\tR0, (R6)+\t; ExpressionAdd:");
		}
	}
}
