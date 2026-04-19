// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.yourplugin.dynamodb.language.psi.DynamoQueryTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.yourplugin.dynamodb.language.psi.*;

public class DynamoQuerySelectStatementImpl extends ASTWrapperPsiElement implements DynamoQuerySelectStatement {

  public DynamoQuerySelectStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DynamoQueryVisitor visitor) {
    visitor.visitSelectStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DynamoQueryVisitor) accept((DynamoQueryVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DynamoQueryConditionExpr getConditionExpr() {
    return findChildByClass(DynamoQueryConditionExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryIndexClause getIndexClause() {
    return findChildByClass(DynamoQueryIndexClause.class);
  }

  @Override
  @NotNull
  public DynamoQueryProjectionList getProjectionList() {
    return findNotNullChildByClass(DynamoQueryProjectionList.class);
  }

  @Override
  @Nullable
  public DynamoQuerySortOrder getSortOrder() {
    return findChildByClass(DynamoQuerySortOrder.class);
  }

  @Override
  @NotNull
  public DynamoQueryTableRef getTableRef() {
    return findNotNullChildByClass(DynamoQueryTableRef.class);
  }

}
