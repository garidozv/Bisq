package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

// This code will be copied into the generate lexer class

%{

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

// JFLex options

%cup		// Switches to CUP compatibility mode to interface with a CUP generated parser
%line		// Switches line counting ON (variable `yyline` holds the number of the current line)
%column		// Switches column counting ON (variable `yycolumn` holds the number of the current column)

// State definitions

%xstate COMMENT

// The code that will be executed each time the end of file is reached
// The code should return the value that indicates the end of file to the parser

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

// Remove any whitespace

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\r\n" 	{ }
"\f" 	{ }

// Keywords

"program"   	{ return new_symbol(sym.PROGRAM, 	yytext()); }
"break"   		{ return new_symbol(sym.BREAK, 		yytext()); }
"class"   		{ return new_symbol(sym.CLASS, 		yytext()); }
"else"   		{ return new_symbol(sym.ELSE, 		yytext()); }
"const"   		{ return new_symbol(sym.CONST, 		yytext()); }
"if"   			{ return new_symbol(sym.IF, 		yytext()); }
"new"   		{ return new_symbol(sym.NEW, 		yytext()); }
"print" 		{ return new_symbol(sym.PRINT, 		yytext()); }
"read" 			{ return new_symbol(sym.READ, 		yytext()); }
"return"   		{ return new_symbol(sym.RETURN, 	yytext()); }
"void" 			{ return new_symbol(sym.VOID, 		yytext()); }
"extends"   	{ return new_symbol(sym.EXTENDS, 	yytext()); }
"continue"   	{ return new_symbol(sym.CONTINUE, 	yytext()); }
"union"   		{ return new_symbol(sym.UNION, 		yytext()); }
"do"   			{ return new_symbol(sym.DO, 		yytext()); }
"while"   		{ return new_symbol(sym.WHILE, 		yytext()); }
"map"   		{ return new_symbol(sym.MAP, 		yytext()); }
"interface"   	{ return new_symbol(sym.INTERFACE, 	yytext()); }


// Operators

"+" 		{ return new_symbol(sym.ADD, 	yytext()); }
"-" 		{ return new_symbol(sym.SUB, 	yytext()); }
"*" 		{ return new_symbol(sym.MUL, 	yytext()); }
"/" 		{ return new_symbol(sym.DIV, 	yytext()); }
"%" 		{ return new_symbol(sym.MOD, 	yytext()); }
"==" 		{ return new_symbol(sym.EQ, 	yytext()); }
"!=" 		{ return new_symbol(sym.NE, 	yytext()); }
">" 		{ return new_symbol(sym.GT, 	yytext()); }
">=" 		{ return new_symbol(sym.GE, 	yytext()); }
"<" 		{ return new_symbol(sym.LT, 	yytext()); }
"<=" 		{ return new_symbol(sym.LE, 	yytext()); }
"&&" 		{ return new_symbol(sym.AND, 	yytext()); }
"||" 		{ return new_symbol(sym.OR, 	yytext()); }
"=" 		{ return new_symbol(sym.EQUAL, 	yytext()); }
"++" 		{ return new_symbol(sym.INC, 	yytext()); }
"--" 		{ return new_symbol(sym.DEC, 	yytext()); }
";" 		{ return new_symbol(sym.SEMI, 	yytext()); }
":" 		{ return new_symbol(sym.COL, 	yytext()); }
"," 		{ return new_symbol(sym.COMMA, 	yytext()); }
"." 		{ return new_symbol(sym.DOT, 	yytext()); }
"(" 		{ return new_symbol(sym.LPAREN, yytext()); }
")" 		{ return new_symbol(sym.RPAREN, yytext()); }
"[" 		{ return new_symbol(sym.LBRACK, yytext()); }
"]" 		{ return new_symbol(sym.RBRACK, yytext()); }
"{" 		{ return new_symbol(sym.LBRACE, yytext()); }
"}"			{ return new_symbol(sym.RBRACE, yytext()); }


// Single-line comments

"//" 				{ yybegin(COMMENT); }
<COMMENT> . 		{ yybegin(COMMENT); }
<COMMENT> "\r\n" 	{ yybegin(YYINITIAL); }


// Tokens

"'"."'" 						{ return new_symbol(sym.CHAR, Character.valueOf(yytext().charAt(1))); }
("bool"|"false") 				{ return new_symbol(sym.BOOL, Boolean.valueOf(yytext())); }
[:digit:]+  							{ return new_symbol(sym.NUMBER, Integer.valueOf(yytext())); }
[:letter:]([:letter:]|[:digit:]|_)* 	{ return new_symbol(sym.IDENT, yytext()); }


// Error

. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }
