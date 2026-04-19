// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.yourplugin.dynamodb.language.psi.impl.*;

public interface DynamoQueryTypes {

  IElementType ADD_CLAUSE = new DynamoQueryElementType("ADD_CLAUSE");
  IElementType AND_EXPR = new DynamoQueryElementType("AND_EXPR");
  IElementType ATTRIBUTE_TYPE_EXPR = new DynamoQueryElementType("ATTRIBUTE_TYPE_EXPR");
  IElementType ATTR_PATH = new DynamoQueryElementType("ATTR_PATH");
  IElementType ATTR_PATH_SEGMENT = new DynamoQueryElementType("ATTR_PATH_SEGMENT");
  IElementType BEGINS_WITH_EXPR = new DynamoQueryElementType("BEGINS_WITH_EXPR");
  IElementType BETWEEN_EXPR = new DynamoQueryElementType("BETWEEN_EXPR");
  IElementType COMPARISON_EXPR = new DynamoQueryElementType("COMPARISON_EXPR");
  IElementType COMPARISON_OP = new DynamoQueryElementType("COMPARISON_OP");
  IElementType CONDITION_EXPR = new DynamoQueryElementType("CONDITION_EXPR");
  IElementType CONTAINS_EXPR = new DynamoQueryElementType("CONTAINS_EXPR");
  IElementType DELETE_CLAUSE = new DynamoQueryElementType("DELETE_CLAUSE");
  IElementType DRY_RUN_DIRECTIVE = new DynamoQueryElementType("DRY_RUN_DIRECTIVE");
  IElementType EXISTS_EXPR = new DynamoQueryElementType("EXISTS_EXPR");
  IElementType FUNCTION_CONDITION = new DynamoQueryElementType("FUNCTION_CONDITION");
  IElementType IF_NOT_EXISTS_EXPR = new DynamoQueryElementType("IF_NOT_EXISTS_EXPR");
  IElementType INDEX_CLAUSE = new DynamoQueryElementType("INDEX_CLAUSE");
  IElementType IN_EXPR = new DynamoQueryElementType("IN_EXPR");
  IElementType LIST_APPEND_EXPR = new DynamoQueryElementType("LIST_APPEND_EXPR");
  IElementType LITERAL_VALUE = new DynamoQueryElementType("LITERAL_VALUE");
  IElementType MATH_EXPR = new DynamoQueryElementType("MATH_EXPR");
  IElementType NAME_REF = new DynamoQueryElementType("NAME_REF");
  IElementType NOT_EXPR = new DynamoQueryElementType("NOT_EXPR");
  IElementType OR_EXPR = new DynamoQueryElementType("OR_EXPR");
  IElementType PARAM_REF = new DynamoQueryElementType("PARAM_REF");
  IElementType PRIMARY_CONDITION = new DynamoQueryElementType("PRIMARY_CONDITION");
  IElementType PROJECTION_LIST = new DynamoQueryElementType("PROJECTION_LIST");
  IElementType REMOVE_CLAUSE = new DynamoQueryElementType("REMOVE_CLAUSE");
  IElementType SELECT_STATEMENT = new DynamoQueryElementType("SELECT_STATEMENT");
  IElementType SET_ASSIGNMENT = new DynamoQueryElementType("SET_ASSIGNMENT");
  IElementType SET_CLAUSE = new DynamoQueryElementType("SET_CLAUSE");
  IElementType SET_EXPR = new DynamoQueryElementType("SET_EXPR");
  IElementType SIZE_COMPARISON = new DynamoQueryElementType("SIZE_COMPARISON");
  IElementType SORT_ORDER = new DynamoQueryElementType("SORT_ORDER");
  IElementType STATEMENT = new DynamoQueryElementType("STATEMENT");
  IElementType TABLE_REF = new DynamoQueryElementType("TABLE_REF");
  IElementType UPDATE_CLAUSE = new DynamoQueryElementType("UPDATE_CLAUSE");
  IElementType UPDATE_STATEMENT = new DynamoQueryElementType("UPDATE_STATEMENT");
  IElementType VALUE = new DynamoQueryElementType("VALUE");
  IElementType VALUE_LIST = new DynamoQueryElementType("VALUE_LIST");

  IElementType ADD = new DynamoQueryTokenType("ADD");
  IElementType AND = new DynamoQueryTokenType("AND");
  IElementType ASC = new DynamoQueryTokenType("ASC");
  IElementType ATTRIBUTE_TYPE = new DynamoQueryTokenType("attribute_type");
  IElementType BEGINS_WITH = new DynamoQueryTokenType("begins_with");
  IElementType BETWEEN = new DynamoQueryTokenType("BETWEEN");
  IElementType BLOCK_COMMENT = new DynamoQueryTokenType("BLOCK_COMMENT");
  IElementType BOOLEAN = new DynamoQueryTokenType("BOOLEAN");
  IElementType COLON = new DynamoQueryTokenType(":");
  IElementType COMMA = new DynamoQueryTokenType(",");
  IElementType CONTAINS = new DynamoQueryTokenType("contains");
  IElementType DELETE = new DynamoQueryTokenType("DELETE");
  IElementType DESC = new DynamoQueryTokenType("DESC");
  IElementType DOT = new DynamoQueryTokenType(".");
  IElementType DRY_RUN = new DynamoQueryTokenType("--dry-run");
  IElementType EQ = new DynamoQueryTokenType("=");
  IElementType EXISTS = new DynamoQueryTokenType("exists");
  IElementType FROM = new DynamoQueryTokenType("FROM");
  IElementType GT = new DynamoQueryTokenType(">");
  IElementType GTE = new DynamoQueryTokenType(">=");
  IElementType HASH = new DynamoQueryTokenType("#");
  IElementType IDENTIFIER = new DynamoQueryTokenType("IDENTIFIER");
  IElementType IF_NOT_EXISTS = new DynamoQueryTokenType("if_not_exists");
  IElementType IN = new DynamoQueryTokenType("IN");
  IElementType INDEX = new DynamoQueryTokenType("INDEX");
  IElementType LIMIT = new DynamoQueryTokenType("LIMIT");
  IElementType LINE_COMMENT = new DynamoQueryTokenType("LINE_COMMENT");
  IElementType LIST_APPEND = new DynamoQueryTokenType("list_append");
  IElementType LPAREN = new DynamoQueryTokenType("(");
  IElementType LT = new DynamoQueryTokenType("<");
  IElementType LTE = new DynamoQueryTokenType("<=");
  IElementType MINUS = new DynamoQueryTokenType("-");
  IElementType NEQ = new DynamoQueryTokenType("<>");
  IElementType NOT = new DynamoQueryTokenType("NOT");
  IElementType NULL = new DynamoQueryTokenType("NULL");
  IElementType NUMBER = new DynamoQueryTokenType("NUMBER");
  IElementType OR = new DynamoQueryTokenType("OR");
  IElementType PLUS = new DynamoQueryTokenType("+");
  IElementType REMOVE = new DynamoQueryTokenType("REMOVE");
  IElementType RPAREN = new DynamoQueryTokenType(")");
  IElementType SELECT = new DynamoQueryTokenType("SELECT");
  IElementType SET = new DynamoQueryTokenType("SET");
  IElementType SIZE = new DynamoQueryTokenType("size");
  IElementType STAR = new DynamoQueryTokenType("*");
  IElementType STRING = new DynamoQueryTokenType("STRING");
  IElementType TYPE = new DynamoQueryTokenType("type");
  IElementType UPDATE = new DynamoQueryTokenType("UPDATE");
  IElementType USE = new DynamoQueryTokenType("USE");
  IElementType WHERE = new DynamoQueryTokenType("WHERE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADD_CLAUSE) {
        return new DynamoQueryAddClauseImpl(node);
      }
      else if (type == AND_EXPR) {
        return new DynamoQueryAndExprImpl(node);
      }
      else if (type == ATTRIBUTE_TYPE_EXPR) {
        return new DynamoQueryAttributeTypeExprImpl(node);
      }
      else if (type == ATTR_PATH) {
        return new DynamoQueryAttrPathImpl(node);
      }
      else if (type == ATTR_PATH_SEGMENT) {
        return new DynamoQueryAttrPathSegmentImpl(node);
      }
      else if (type == BEGINS_WITH_EXPR) {
        return new DynamoQueryBeginsWithExprImpl(node);
      }
      else if (type == BETWEEN_EXPR) {
        return new DynamoQueryBetweenExprImpl(node);
      }
      else if (type == COMPARISON_EXPR) {
        return new DynamoQueryComparisonExprImpl(node);
      }
      else if (type == COMPARISON_OP) {
        return new DynamoQueryComparisonOpImpl(node);
      }
      else if (type == CONDITION_EXPR) {
        return new DynamoQueryConditionExprImpl(node);
      }
      else if (type == CONTAINS_EXPR) {
        return new DynamoQueryContainsExprImpl(node);
      }
      else if (type == DELETE_CLAUSE) {
        return new DynamoQueryDeleteClauseImpl(node);
      }
      else if (type == DRY_RUN_DIRECTIVE) {
        return new DynamoQueryDryRunDirectiveImpl(node);
      }
      else if (type == EXISTS_EXPR) {
        return new DynamoQueryExistsExprImpl(node);
      }
      else if (type == FUNCTION_CONDITION) {
        return new DynamoQueryFunctionConditionImpl(node);
      }
      else if (type == IF_NOT_EXISTS_EXPR) {
        return new DynamoQueryIfNotExistsExprImpl(node);
      }
      else if (type == INDEX_CLAUSE) {
        return new DynamoQueryIndexClauseImpl(node);
      }
      else if (type == IN_EXPR) {
        return new DynamoQueryInExprImpl(node);
      }
      else if (type == LIST_APPEND_EXPR) {
        return new DynamoQueryListAppendExprImpl(node);
      }
      else if (type == LITERAL_VALUE) {
        return new DynamoQueryLiteralValueImpl(node);
      }
      else if (type == MATH_EXPR) {
        return new DynamoQueryMathExprImpl(node);
      }
      else if (type == NAME_REF) {
        return new DynamoQueryNameRefImpl(node);
      }
      else if (type == NOT_EXPR) {
        return new DynamoQueryNotExprImpl(node);
      }
      else if (type == OR_EXPR) {
        return new DynamoQueryOrExprImpl(node);
      }
      else if (type == PARAM_REF) {
        return new DynamoQueryParamRefImpl(node);
      }
      else if (type == PRIMARY_CONDITION) {
        return new DynamoQueryPrimaryConditionImpl(node);
      }
      else if (type == PROJECTION_LIST) {
        return new DynamoQueryProjectionListImpl(node);
      }
      else if (type == REMOVE_CLAUSE) {
        return new DynamoQueryRemoveClauseImpl(node);
      }
      else if (type == SELECT_STATEMENT) {
        return new DynamoQuerySelectStatementImpl(node);
      }
      else if (type == SET_ASSIGNMENT) {
        return new DynamoQuerySetAssignmentImpl(node);
      }
      else if (type == SET_CLAUSE) {
        return new DynamoQuerySetClauseImpl(node);
      }
      else if (type == SET_EXPR) {
        return new DynamoQuerySetExprImpl(node);
      }
      else if (type == SIZE_COMPARISON) {
        return new DynamoQuerySizeComparisonImpl(node);
      }
      else if (type == SORT_ORDER) {
        return new DynamoQuerySortOrderImpl(node);
      }
      else if (type == STATEMENT) {
        return new DynamoQueryStatementImpl(node);
      }
      else if (type == TABLE_REF) {
        return new DynamoQueryTableRefImpl(node);
      }
      else if (type == UPDATE_CLAUSE) {
        return new DynamoQueryUpdateClauseImpl(node);
      }
      else if (type == UPDATE_STATEMENT) {
        return new DynamoQueryUpdateStatementImpl(node);
      }
      else if (type == VALUE) {
        return new DynamoQueryValueImpl(node);
      }
      else if (type == VALUE_LIST) {
        return new DynamoQueryValueListImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
