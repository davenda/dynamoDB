// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.yourplugin.dynamodb.language.psi.DynamoQueryTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DynamoQueryParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return root(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // ADD    attrPath value
  public static boolean addClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "addClause")) return false;
    if (!nextTokenIs(builder_, ADD)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ADD);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, ADD_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // notExpr  (AND notExpr)*
  public static boolean andExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, AND_EXPR, "<and expr>");
    result_ = notExpr(builder_, level_ + 1);
    result_ = result_ && andExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AND notExpr)*
  private static boolean andExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!andExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "andExpr_1", pos_)) break;
    }
    return true;
  }

  // AND notExpr
  private static boolean andExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "andExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND);
    result_ = result_ && notExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // attrPathSegment (DOT attrPathSegment)*
  public static boolean attrPath(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrPath")) return false;
    if (!nextTokenIs(builder_, "<attr path>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTR_PATH, "<attr path>");
    result_ = attrPathSegment(builder_, level_ + 1);
    result_ = result_ && attrPath_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (DOT attrPathSegment)*
  private static boolean attrPath_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrPath_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!attrPath_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "attrPath_1", pos_)) break;
    }
    return true;
  }

  // DOT attrPathSegment
  private static boolean attrPath_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrPath_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && attrPathSegment(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // nameRef | nameRef LPAREN NUMBER RPAREN
  public static boolean attrPathSegment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrPathSegment")) return false;
    if (!nextTokenIs(builder_, "<attr path segment>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ATTR_PATH_SEGMENT, "<attr path segment>");
    result_ = nameRef(builder_, level_ + 1);
    if (!result_) result_ = attrPathSegment_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // nameRef LPAREN NUMBER RPAREN
  private static boolean attrPathSegment_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attrPathSegment_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = nameRef(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, LPAREN, NUMBER, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ATTRIBUTE_TYPE LPAREN attrPath COMMA value   RPAREN
  public static boolean attributeTypeExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attributeTypeExpr")) return false;
    if (!nextTokenIs(builder_, ATTRIBUTE_TYPE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ATTRIBUTE_TYPE, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, ATTRIBUTE_TYPE_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // BEGINS_WITH    LPAREN attrPath COMMA value   RPAREN
  public static boolean beginsWithExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "beginsWithExpr")) return false;
    if (!nextTokenIs(builder_, BEGINS_WITH)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, BEGINS_WITH, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, BEGINS_WITH_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // attrPath BETWEEN value AND value
  public static boolean betweenExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "betweenExpr")) return false;
    if (!nextTokenIs(builder_, "<between expr>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BETWEEN_EXPR, "<between expr>");
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, BETWEEN);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, AND);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // attrPath comparisonOp value
  public static boolean comparisonExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparisonExpr")) return false;
    if (!nextTokenIs(builder_, "<comparison expr>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPARISON_EXPR, "<comparison expr>");
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && comparisonOp(builder_, level_ + 1);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // EQ | NEQ | LT | LTE | GT | GTE
  public static boolean comparisonOp(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparisonOp")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, COMPARISON_OP, "<comparison op>");
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, GTE);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // orExpr
  public static boolean conditionExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "conditionExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONDITION_EXPR, "<condition expr>");
    result_ = orExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // CONTAINS       LPAREN attrPath COMMA value   RPAREN
  public static boolean containsExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "containsExpr")) return false;
    if (!nextTokenIs(builder_, CONTAINS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CONTAINS, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, CONTAINS_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // DELETE attrPath value
  public static boolean deleteClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "deleteClause")) return false;
    if (!nextTokenIs(builder_, DELETE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DELETE);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, DELETE_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // DRY_RUN
  public static boolean dryRunDirective(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "dryRunDirective")) return false;
    if (!nextTokenIs(builder_, DRY_RUN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DRY_RUN);
    exit_section_(builder_, marker_, DRY_RUN_DIRECTIVE, result_);
    return result_;
  }

  /* ********************************************************** */
  // EXISTS         LPAREN attrPath               RPAREN
  public static boolean existsExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "existsExpr")) return false;
    if (!nextTokenIs(builder_, EXISTS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, EXISTS, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, EXISTS_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // beginsWithExpr
  //   | containsExpr
  //   | existsExpr
  //   | attributeTypeExpr
  //   | sizeComparison
  public static boolean functionCondition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "functionCondition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUNCTION_CONDITION, "<function condition>");
    result_ = beginsWithExpr(builder_, level_ + 1);
    if (!result_) result_ = containsExpr(builder_, level_ + 1);
    if (!result_) result_ = existsExpr(builder_, level_ + 1);
    if (!result_) result_ = attributeTypeExpr(builder_, level_ + 1);
    if (!result_) result_ = sizeComparison(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // IF_NOT_EXISTS LPAREN attrPath COMMA value RPAREN
  public static boolean ifNotExistsExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ifNotExistsExpr")) return false;
    if (!nextTokenIs(builder_, IF_NOT_EXISTS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IF_NOT_EXISTS, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, IF_NOT_EXISTS_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // attrPath IN LPAREN valueList RPAREN
  public static boolean inExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "inExpr")) return false;
    if (!nextTokenIs(builder_, "<in expr>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IN_EXPR, "<in expr>");
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IN, LPAREN);
    result_ = result_ && valueList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN IDENTIFIER RPAREN
  public static boolean indexClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "indexClause")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LPAREN, IDENTIFIER, RPAREN);
    exit_section_(builder_, marker_, INDEX_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // LIST_APPEND   LPAREN attrPath COMMA value RPAREN
  public static boolean listAppendExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "listAppendExpr")) return false;
    if (!nextTokenIs(builder_, LIST_APPEND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LIST_APPEND, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, LIST_APPEND_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // STRING | NUMBER | BOOLEAN | NULL
  public static boolean literalValue(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literalValue")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LITERAL_VALUE, "<literal value>");
    result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, BOOLEAN);
    if (!result_) result_ = consumeToken(builder_, NULL);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // attrPath PLUS value | attrPath MINUS value
  public static boolean mathExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mathExpr")) return false;
    if (!nextTokenIs(builder_, "<math expr>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MATH_EXPR, "<math expr>");
    result_ = mathExpr_0(builder_, level_ + 1);
    if (!result_) result_ = mathExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // attrPath PLUS value
  private static boolean mathExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mathExpr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PLUS);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // attrPath MINUS value
  private static boolean mathExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mathExpr_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MINUS);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // HASH? IDENTIFIER
  public static boolean nameRef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameRef")) return false;
    if (!nextTokenIs(builder_, "<name ref>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NAME_REF, "<name ref>");
    result_ = nameRef_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // HASH?
  private static boolean nameRef_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "nameRef_0")) return false;
    consumeToken(builder_, HASH);
    return true;
  }

  /* ********************************************************** */
  // NOT? primaryCondition
  public static boolean notExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "notExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NOT_EXPR, "<not expr>");
    result_ = notExpr_0(builder_, level_ + 1);
    result_ = result_ && primaryCondition(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // NOT?
  private static boolean notExpr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "notExpr_0")) return false;
    consumeToken(builder_, NOT);
    return true;
  }

  /* ********************************************************** */
  // andExpr  (OR  andExpr)*
  public static boolean orExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "orExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OR_EXPR, "<or expr>");
    result_ = andExpr(builder_, level_ + 1);
    result_ = result_ && orExpr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (OR  andExpr)*
  private static boolean orExpr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "orExpr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!orExpr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "orExpr_1", pos_)) break;
    }
    return true;
  }

  // OR  andExpr
  private static boolean orExpr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "orExpr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR);
    result_ = result_ && andExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // COLON IDENTIFIER
  public static boolean paramRef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paramRef")) return false;
    if (!nextTokenIs(builder_, COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COLON, IDENTIFIER);
    exit_section_(builder_, marker_, PARAM_REF, result_);
    return result_;
  }

  /* ********************************************************** */
  // comparisonExpr
  //   | betweenExpr
  //   | inExpr
  //   | functionCondition
  //   | LPAREN conditionExpr RPAREN
  public static boolean primaryCondition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primaryCondition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PRIMARY_CONDITION, "<primary condition>");
    result_ = comparisonExpr(builder_, level_ + 1);
    if (!result_) result_ = betweenExpr(builder_, level_ + 1);
    if (!result_) result_ = inExpr(builder_, level_ + 1);
    if (!result_) result_ = functionCondition(builder_, level_ + 1);
    if (!result_) result_ = primaryCondition_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // LPAREN conditionExpr RPAREN
  private static boolean primaryCondition_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primaryCondition_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && conditionExpr(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER (COMMA IDENTIFIER)* | STAR
  public static boolean projectionList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "projectionList")) return false;
    if (!nextTokenIs(builder_, "<projection list>", IDENTIFIER, STAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PROJECTION_LIST, "<projection list>");
    result_ = projectionList_0(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, STAR);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // IDENTIFIER (COMMA IDENTIFIER)*
  private static boolean projectionList_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "projectionList_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    result_ = result_ && projectionList_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA IDENTIFIER)*
  private static boolean projectionList_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "projectionList_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!projectionList_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "projectionList_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA IDENTIFIER
  private static boolean projectionList_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "projectionList_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // REMOVE attrPath (COMMA attrPath)*
  public static boolean removeClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "removeClause")) return false;
    if (!nextTokenIs(builder_, REMOVE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, REMOVE);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && removeClause_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, REMOVE_CLAUSE, result_);
    return result_;
  }

  // (COMMA attrPath)*
  private static boolean removeClause_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "removeClause_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!removeClause_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "removeClause_2", pos_)) break;
    }
    return true;
  }

  // COMMA attrPath
  private static boolean removeClause_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "removeClause_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && attrPath(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // statement*
  static boolean root(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "root")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!statement(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "root", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // SELECT projectionList
  //     FROM tableRef
  //     (USE INDEX indexClause)?
  //     (WHERE conditionExpr)?
  //     (LIMIT NUMBER)?
  //     sortOrder?
  public static boolean selectStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement")) return false;
    if (!nextTokenIs(builder_, SELECT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SELECT);
    result_ = result_ && projectionList(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, FROM);
    result_ = result_ && tableRef(builder_, level_ + 1);
    result_ = result_ && selectStatement_4(builder_, level_ + 1);
    result_ = result_ && selectStatement_5(builder_, level_ + 1);
    result_ = result_ && selectStatement_6(builder_, level_ + 1);
    result_ = result_ && selectStatement_7(builder_, level_ + 1);
    exit_section_(builder_, marker_, SELECT_STATEMENT, result_);
    return result_;
  }

  // (USE INDEX indexClause)?
  private static boolean selectStatement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_4")) return false;
    selectStatement_4_0(builder_, level_ + 1);
    return true;
  }

  // USE INDEX indexClause
  private static boolean selectStatement_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, USE, INDEX);
    result_ = result_ && indexClause(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (WHERE conditionExpr)?
  private static boolean selectStatement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_5")) return false;
    selectStatement_5_0(builder_, level_ + 1);
    return true;
  }

  // WHERE conditionExpr
  private static boolean selectStatement_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHERE);
    result_ = result_ && conditionExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (LIMIT NUMBER)?
  private static boolean selectStatement_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_6")) return false;
    selectStatement_6_0(builder_, level_ + 1);
    return true;
  }

  // LIMIT NUMBER
  private static boolean selectStatement_6_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_6_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LIMIT, NUMBER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // sortOrder?
  private static boolean selectStatement_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "selectStatement_7")) return false;
    sortOrder(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // attrPath EQ setExpr
  public static boolean setAssignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setAssignment")) return false;
    if (!nextTokenIs(builder_, "<set assignment>", HASH, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SET_ASSIGNMENT, "<set assignment>");
    result_ = attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, EQ);
    result_ = result_ && setExpr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SET    setAssignment (COMMA setAssignment)*
  public static boolean setClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setClause")) return false;
    if (!nextTokenIs(builder_, SET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SET);
    result_ = result_ && setAssignment(builder_, level_ + 1);
    result_ = result_ && setClause_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, SET_CLAUSE, result_);
    return result_;
  }

  // (COMMA setAssignment)*
  private static boolean setClause_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setClause_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!setClause_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "setClause_2", pos_)) break;
    }
    return true;
  }

  // COMMA setAssignment
  private static boolean setClause_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setClause_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && setAssignment(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ifNotExistsExpr
  //   | listAppendExpr
  //   | mathExpr
  //   | value
  public static boolean setExpr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "setExpr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SET_EXPR, "<set expr>");
    result_ = ifNotExistsExpr(builder_, level_ + 1);
    if (!result_) result_ = listAppendExpr(builder_, level_ + 1);
    if (!result_) result_ = mathExpr(builder_, level_ + 1);
    if (!result_) result_ = value(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SIZE           LPAREN attrPath RPAREN comparisonOp value
  public static boolean sizeComparison(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sizeComparison")) return false;
    if (!nextTokenIs(builder_, SIZE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, SIZE, LPAREN);
    result_ = result_ && attrPath(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && comparisonOp(builder_, level_ + 1);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, SIZE_COMPARISON, result_);
    return result_;
  }

  /* ********************************************************** */
  // ASC | DESC
  public static boolean sortOrder(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sortOrder")) return false;
    if (!nextTokenIs(builder_, "<sort order>", ASC, DESC)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SORT_ORDER, "<sort order>");
    result_ = consumeToken(builder_, ASC);
    if (!result_) result_ = consumeToken(builder_, DESC);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // dryRunDirective? (selectStatement | updateStatement)
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT, "<statement>");
    result_ = statement_0(builder_, level_ + 1);
    result_ = result_ && statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // dryRunDirective?
  private static boolean statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_0")) return false;
    dryRunDirective(builder_, level_ + 1);
    return true;
  }

  // selectStatement | updateStatement
  private static boolean statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_1")) return false;
    boolean result_;
    result_ = selectStatement(builder_, level_ + 1);
    if (!result_) result_ = updateStatement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean tableRef(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tableRef")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, TABLE_REF, result_);
    return result_;
  }

  /* ********************************************************** */
  // setClause
  //   | removeClause
  //   | addClause
  //   | deleteClause
  public static boolean updateClause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "updateClause")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UPDATE_CLAUSE, "<update clause>");
    result_ = setClause(builder_, level_ + 1);
    if (!result_) result_ = removeClause(builder_, level_ + 1);
    if (!result_) result_ = addClause(builder_, level_ + 1);
    if (!result_) result_ = deleteClause(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // UPDATE tableRef
  //     (WHERE conditionExpr)?
  //     updateClause+
  public static boolean updateStatement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "updateStatement")) return false;
    if (!nextTokenIs(builder_, UPDATE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, UPDATE);
    result_ = result_ && tableRef(builder_, level_ + 1);
    result_ = result_ && updateStatement_2(builder_, level_ + 1);
    result_ = result_ && updateStatement_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, UPDATE_STATEMENT, result_);
    return result_;
  }

  // (WHERE conditionExpr)?
  private static boolean updateStatement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "updateStatement_2")) return false;
    updateStatement_2_0(builder_, level_ + 1);
    return true;
  }

  // WHERE conditionExpr
  private static boolean updateStatement_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "updateStatement_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHERE);
    result_ = result_ && conditionExpr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // updateClause+
  private static boolean updateStatement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "updateStatement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = updateClause(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!updateClause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "updateStatement_3", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // paramRef | literalValue
  public static boolean value(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "value")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VALUE, "<value>");
    result_ = paramRef(builder_, level_ + 1);
    if (!result_) result_ = literalValue(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // value (COMMA value)*
  public static boolean valueList(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueList")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VALUE_LIST, "<value list>");
    result_ = value(builder_, level_ + 1);
    result_ = result_ && valueList_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (COMMA value)*
  private static boolean valueList_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueList_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!valueList_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "valueList_1", pos_)) break;
    }
    return true;
  }

  // COMMA value
  private static boolean valueList_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "valueList_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

}
