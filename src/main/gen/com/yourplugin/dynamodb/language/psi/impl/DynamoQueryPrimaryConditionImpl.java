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

public class DynamoQueryPrimaryConditionImpl extends ASTWrapperPsiElement implements DynamoQueryPrimaryCondition {

  public DynamoQueryPrimaryConditionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DynamoQueryVisitor visitor) {
    visitor.visitPrimaryCondition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DynamoQueryVisitor) accept((DynamoQueryVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DynamoQueryBetweenExpr getBetweenExpr() {
    return findChildByClass(DynamoQueryBetweenExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryComparisonExpr getComparisonExpr() {
    return findChildByClass(DynamoQueryComparisonExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryConditionExpr getConditionExpr() {
    return findChildByClass(DynamoQueryConditionExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryFunctionCondition getFunctionCondition() {
    return findChildByClass(DynamoQueryFunctionCondition.class);
  }

  @Override
  @Nullable
  public DynamoQueryInExpr getInExpr() {
    return findChildByClass(DynamoQueryInExpr.class);
  }

}
