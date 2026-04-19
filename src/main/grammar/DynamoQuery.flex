package com.yourplugin.dynamodb.language.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.yourplugin.dynamodb.language.psi.DynamoQueryTypes;
import com.intellij.psi.TokenType;

%%

%class DynamoQueryLexer
%public
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

/* ── Whitespace ── */
WHITE_SPACE = [ \t\n\r]+

/* ── Comments ── */
LINE_COMMENT  = "--" [^\r\n]*
BLOCK_COMMENT = "/*" ~"*/"

/* ── Keywords ── */
KW_SELECT    = [Ss][Ee][Ll][Ee][Cc][Tt]
KW_FROM      = [Ff][Rr][Oo][Mm]
KW_WHERE     = [Ww][Hh][Ee][Rr][Ee]
KW_AND       = [Aa][Nn][Dd]
KW_OR        = [Oo][Rr]
KW_NOT       = [Nn][Oo][Tt]
KW_BETWEEN   = [Bb][Ee][Tt][Ww][Ee][Ee][Nn]
KW_IN        = [Ii][Nn]
KW_BEGINS_WITH = [Bb][Ee][Gg][Ii][Nn][Ss][_][Ww][Ii][Tt][Hh]
KW_CONTAINS  = [Cc][Oo][Nn][Tt][Aa][Ii][Nn][Ss]
KW_EXISTS    = [Ee][Xx][Ii][Ss][Tt][Ss]
KW_INDEX     = [Ii][Nn][Dd][Ee][Xx]
KW_LIMIT     = [Ll][Ii][Mm][Ii][Tt]
KW_DESC      = [Dd][Ee][Ss][Cc]
KW_ASC       = [Aa][Ss][Cc]
KW_UPDATE    = [Uu][Pp][Dd][Aa][Tt][Ee]
KW_SET       = [Ss][Ee][Tt]
KW_REMOVE    = [Rr][Ee][Mm][Oo][Vv][Ee]
KW_ADD       = [Aa][Dd][Dd]
KW_DELETE    = [Dd][Ee][Ll][Ee][Tt][Ee]
KW_IF_NOT_EXISTS = "if_not_exists"
KW_LIST_APPEND   = "list_append"
KW_SIZE      = [Ss][Ii][Zz][Ee]
KW_TYPE      = [Tt][Yy][Pp][Ee]
KW_ATTRIBUTE_TYPE = "attribute_type"
KW_DRY_RUN   = "--dry-run"
KW_USE       = [Uu][Ss][Ee]

/* ── Identifiers & Literals ── */
IDENTIFIER   = [a-zA-Z_][a-zA-Z0-9_\-\.]*
NUMBER       = -?[0-9]+(\.[0-9]+)?
STRING       = \"([^\\\"]|\\.)*\" | \'([^\\\']|\\.)*\'
BOOLEAN      = "true" | "false" | "TRUE" | "FALSE"
NULL         = "null" | "NULL"

/* ── Operators ── */
EQ  = "="
NEQ = "<>" | "!="
LT  = "<"
LTE = "<="
GT  = ">"
GTE = ">="
STAR  = "*"
PLUS  = "+"
MINUS = "-"
DOT   = "."
COMMA = ","
LPAREN = "("
RPAREN = ")"
COLON  = ":"     /* ExpressionAttributeValues prefix */
HASH   = "#"     /* ExpressionAttributeNames prefix */

%%

<YYINITIAL> {
    {WHITE_SPACE}       { return TokenType.WHITE_SPACE; }
    {LINE_COMMENT}      { return DynamoQueryTypes.LINE_COMMENT; }
    {BLOCK_COMMENT}     { return DynamoQueryTypes.BLOCK_COMMENT; }

    {KW_DRY_RUN}        { return DynamoQueryTypes.DRY_RUN; }
    {KW_SELECT}         { return DynamoQueryTypes.SELECT; }
    {KW_FROM}           { return DynamoQueryTypes.FROM; }
    {KW_WHERE}          { return DynamoQueryTypes.WHERE; }
    {KW_AND}            { return DynamoQueryTypes.AND; }
    {KW_OR}             { return DynamoQueryTypes.OR; }
    {KW_NOT}            { return DynamoQueryTypes.NOT; }
    {KW_BETWEEN}        { return DynamoQueryTypes.BETWEEN; }
    {KW_IN}             { return DynamoQueryTypes.IN; }
    {KW_BEGINS_WITH}    { return DynamoQueryTypes.BEGINS_WITH; }
    {KW_CONTAINS}       { return DynamoQueryTypes.CONTAINS; }
    {KW_EXISTS}         { return DynamoQueryTypes.EXISTS; }
    {KW_INDEX}          { return DynamoQueryTypes.INDEX; }
    {KW_LIMIT}          { return DynamoQueryTypes.LIMIT; }
    {KW_DESC}           { return DynamoQueryTypes.DESC; }
    {KW_ASC}            { return DynamoQueryTypes.ASC; }
    {KW_UPDATE}         { return DynamoQueryTypes.UPDATE; }
    {KW_SET}            { return DynamoQueryTypes.SET; }
    {KW_REMOVE}         { return DynamoQueryTypes.REMOVE; }
    {KW_ADD}            { return DynamoQueryTypes.ADD; }
    {KW_DELETE}         { return DynamoQueryTypes.DELETE; }
    {KW_IF_NOT_EXISTS}  { return DynamoQueryTypes.IF_NOT_EXISTS; }
    {KW_LIST_APPEND}    { return DynamoQueryTypes.LIST_APPEND; }
    {KW_SIZE}           { return DynamoQueryTypes.SIZE; }
    {KW_TYPE}           { return DynamoQueryTypes.TYPE; }
    {KW_ATTRIBUTE_TYPE} { return DynamoQueryTypes.ATTRIBUTE_TYPE; }
    {KW_USE}            { return DynamoQueryTypes.USE; }

    {BOOLEAN}           { return DynamoQueryTypes.BOOLEAN; }
    {NULL}              { return DynamoQueryTypes.NULL; }
    {NUMBER}            { return DynamoQueryTypes.NUMBER; }
    {STRING}            { return DynamoQueryTypes.STRING; }
    {IDENTIFIER}        { return DynamoQueryTypes.IDENTIFIER; }

    {EQ}    { return DynamoQueryTypes.EQ; }
    {NEQ}   { return DynamoQueryTypes.NEQ; }
    {LTE}   { return DynamoQueryTypes.LTE; }
    {LT}    { return DynamoQueryTypes.LT; }
    {GTE}   { return DynamoQueryTypes.GTE; }
    {GT}    { return DynamoQueryTypes.GT; }
    {STAR}  { return DynamoQueryTypes.STAR; }
    {PLUS}  { return DynamoQueryTypes.PLUS; }
    {MINUS} { return DynamoQueryTypes.MINUS; }
    {DOT}   { return DynamoQueryTypes.DOT; }
    {COMMA} { return DynamoQueryTypes.COMMA; }
    {LPAREN} { return DynamoQueryTypes.LPAREN; }
    {RPAREN} { return DynamoQueryTypes.RPAREN; }
    {COLON} { return DynamoQueryTypes.COLON; }
    {HASH}  { return DynamoQueryTypes.HASH; }
}

[^] { return TokenType.BAD_CHARACTER; }
