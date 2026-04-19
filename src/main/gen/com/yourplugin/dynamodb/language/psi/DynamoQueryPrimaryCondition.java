// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryPrimaryCondition extends PsiElement {

  @Nullable
  DynamoQueryBetweenExpr getBetweenExpr();

  @Nullable
  DynamoQueryComparisonExpr getComparisonExpr();

  @Nullable
  DynamoQueryConditionExpr getConditionExpr();

  @Nullable
  DynamoQueryFunctionCondition getFunctionCondition();

  @Nullable
  DynamoQueryInExpr getInExpr();

}
