// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryUpdateStatement extends PsiElement {

  @Nullable
  DynamoQueryConditionExpr getConditionExpr();

  @NotNull
  DynamoQueryTableRef getTableRef();

  @NotNull
  List<DynamoQueryUpdateClause> getUpdateClauseList();

}
