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

public class DynamoQuerySetExprImpl extends ASTWrapperPsiElement implements DynamoQuerySetExpr {

  public DynamoQuerySetExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DynamoQueryVisitor visitor) {
    visitor.visitSetExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DynamoQueryVisitor) accept((DynamoQueryVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public DynamoQueryIfNotExistsExpr getIfNotExistsExpr() {
    return findChildByClass(DynamoQueryIfNotExistsExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryListAppendExpr getListAppendExpr() {
    return findChildByClass(DynamoQueryListAppendExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryMathExpr getMathExpr() {
    return findChildByClass(DynamoQueryMathExpr.class);
  }

  @Override
  @Nullable
  public DynamoQueryValue getValue() {
    return findChildByClass(DynamoQueryValue.class);
  }

}
