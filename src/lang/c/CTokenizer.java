package lang.c;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import lang.Tokenizer;

public class CTokenizer extends Tokenizer<CToken, CParseContext> {
	private int			lineNo, colNo;
	private char		backCh;
	private boolean		backChExist = false;

	public CTokenizer(CTokenRule rule) {
		this.setRule(rule);
		lineNo = 1; colNo = 1;
	}

	private CTokenRule						rule;
	public void setRule(CTokenRule rule)	{ this.rule = rule; }
	public CTokenRule getRule()				{ return rule; }

	private InputStream in;
	private PrintStream err;

	private char readChar() {
		char ch;
		if (backChExist) {
			ch = backCh;
			backChExist = false;
		} else {
			try {
				ch = (char) in.read();
			} catch (IOException e) {
				e.printStackTrace(err);
				ch = (char) -1;
			}
		}
		++colNo;
		if (ch == '\n')  { colNo = 1; ++lineNo; }
//		System.out.print("'"+ch+"'("+(int)ch+")");
		return ch;
	}
	private void backChar(char c) {
		backCh = c;
		backChExist = true;
		--colNo;
		if (c == '\n') { --lineNo; }
	}

	// 現在読み込まれているトークンを返す
	private CToken currentTk = null;
	public CToken getCurrentToken(CParseContext pctx) {
		return currentTk;
	}
	// 新しく次のトークンを読み込んで返す
	public CToken getNextToken(CParseContext pctx) {
		in = pctx.getIOContext().getInStream();
		err = pctx.getIOContext().getErrStream();
		currentTk = readToken();
//		System.out.println("Token='" + currentTk.toString());
		return currentTk;
	}
	private CToken readToken() {
		CToken tk = null;
		char ch;
		int  startCol = colNo;
		StringBuffer text = new StringBuffer();

		int state = 0;
		boolean accept = false;
		while (!accept) {
			switch (state) {
			case 0:					// 初期状態
				ch = readChar();
				System.err.println(ch+"case0\n");
				if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
				} else if (ch == (char) -1) {	// EOF
					startCol = colNo - 1;
					state = 1;
				} else if (ch >= '0' && ch <= '9') {
					startCol = colNo - 1;
					text.append(ch);
					state = 3;
				} else if (ch == '+') {
					startCol = colNo - 1;
					text.append(ch);
					state = 5;
				} else if (ch == '-') {
					startCol = colNo - 1;
					text.append(ch);
					state = 6;
				} else if (ch == '/') {
					startCol = colNo - 1;
					text.append(ch);
					state = 7;
				} else {			// ヘンな文字を読んだ
					System.err.println("ILL\n");
					startCol = colNo - 1;
					text.append(ch);
					state = 2;
				}
				break;
			case 1:					// EOFを読んだ
				System.err.println("case1\n");
				tk = new CToken(CToken.TK_EOF, lineNo, startCol, "end_of_file");
				accept = true;
				break;
			case 2:					// ヘンな文字を読んだ
				System.err.println("case2\n");
				tk = new CToken(CToken.TK_ILL, lineNo, startCol, text.toString());
				accept = true;
				break;
			case 3:					// 数（10進数）の開始
				ch = readChar();
				System.err.println(""+ch+"case3\n");
				if (ch >= '0' && ch <= '9') {
					text.append(ch);
				} else {
					backChar(ch);
					state = 4;
				}
				break;
			case 4:					// 数の終わり
				System.err.println("case4\n");
				tk = new CToken(CToken.TK_NUM, lineNo, startCol, text.toString());
				accept = true;
				break;
			case 5:					// +を読んだ
				System.err.println("cas53\n");
				tk = new CToken(CToken.TK_PLUS, lineNo, startCol, "+");
				accept = true;
				break;
			case 6:					// -を読んだ
				System.err.println("case6\n");
				tk = new CToken(CToken.TK_MINUS, lineNo, startCol, "-");
				accept = true;
				break;
			case 7:					// /を読んだ コメントアウト処理の開始
				ch = readChar();
				System.err.println(""+ch+"case7\n");
				if (ch == '/'){		// //なので、行コメントアウトに分岐
					state = 8;
				} else if (ch == '*'){	// /*なので、文コメントアウトに分岐
					state = 10;
				} else {			// ヘンな文字を読んだ
					startCol = colNo - 1;
					text.append(ch);
					state = 2;
				}
				break;
			case 8:// 行コメントアウト処理
				ch = readChar();
				System.err.println(""+ch+"case8\n");
				if (ch == (char) - 1 || ch == '\r' || ch == '\n'){	// 行かファイルの終わりなのでコメント終了の処理へ
					startCol = colNo - 1;
					state = 9;
				} else {
					text.append(ch);	//コメント処理継続
				}
				break;
			case 9:					// 行コメントアウト終了処理
				System.err.println("case9\n");
				tk = new CToken(CToken.TK_COUT, lineNo, startCol, text.toString());
				accept = true;
				break;
			case 10:					// 文コメントアウト処理
				ch = readChar();
				System.err.println(""+ch+"case10\n");
				if (ch == (char) - 1){	// EOFは、エラーを吐いて終了するため、そのための分岐 state2へ
					startCol = colNo - 1;
					text.append(ch);
					state = 2;
				} else if (ch == '*'){	// /*ときて*を認識 文コメントアウト終了の可能性があるため、次の文字が/かどうかを判断する次状態へ
					startCol = colNo - 1;
					state = 11;
				} else {				// コメント処理継続
					text.append(ch);
				}
				break;
			case 11:
				ch = readChar();		// 文コメントアウト処理 2状態目 */と来るかどうかの判断
				System.err.println(""+ch+"case11\n");
				if (ch == (char) - 1){	// EOFは、エラーを吐いて終了するため、そのための分岐 state2へ
					startCol = colNo - 1;
					text.append(ch);
					state = 2;
				} else if (ch == '*'){	// /* *ときて*と認識 はじめの*をコメントとして考えると、まだこの状態を継続すべき
					text.append(ch);
				} else if (ch == '/'){	// /*ときて*/と認識 文コメントアウト終了の処理へ
					startCol = colNo - 1;
					state = 12;
				} else {				// /* *ときて関係ない文字を認識 はじめの*をコメントとして処理し、state10へ遷移
					text.append('*');
					text.append(ch);
					state = 10;
				}
				break;
			case 12:					// 文コメントアウト終了処理
				System.err.println("case12\n");
				tk = new CToken(CToken.TK_COUT, lineNo, startCol, text.toString());
				accept = true;
				break;
			}
		}
		return tk;
	}

	public void skipTo(CParseContext pctx, int t) {
		int i = getCurrentToken(pctx).getType();
		while (i != t && i != CToken.TK_EOF) {
			i = getNextToken(pctx).getType();
		}
		pctx.warning(getCurrentToken(pctx).toExplainString() + "まで読み飛ばしました");
	}
	public void skipTo(CParseContext pctx, int t1, int t2) {
		int i = getCurrentToken(pctx).getType();
		while (i != t1 && i != t2 && i != CToken.TK_EOF) {
			i = getNextToken(pctx).getType();
		}
		pctx.warning(getCurrentToken(pctx).toExplainString() + "まで読み飛ばしました");
	}
	public void skipTo(CParseContext pctx, int t1, int t2, int t3, int t4, int t5, int t6) {
		int i = getCurrentToken(pctx).getType();
		while (i != t1 && i != t2 && i != t3 && i != t4 && i != t5 && i != t6 && i != CToken.TK_EOF) {
			i = getNextToken(pctx).getType();
		}
		pctx.warning(getCurrentToken(pctx).toExplainString() + "まで読み飛ばしました");
	}
}
