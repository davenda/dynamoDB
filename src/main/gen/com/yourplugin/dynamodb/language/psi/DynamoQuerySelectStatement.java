// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQuerySelectStatement extends PsiElement {

  @Nullable
  DynamoQueryConditionExpr getConditionExpr();

  @Nullable
  DynamoQueryIndexClause getIndexClause();

  @NotNull
  DynamoQueryProjectionList getProjectionList();

  @Nullable
  DynamoQuerySortOrder getSortOrder();

  @NotNull
  DynamoQueryTableRef getTableRef();

}
