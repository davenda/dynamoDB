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

public class DynamoQueryBetweenExprImpl extends ASTWrapperPsiElement implements DynamoQueryBetweenExpr {

  public DynamoQueryBetweenExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DynamoQueryVisitor visitor) {
    visitor.visitBetweenExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DynamoQueryVisitor) accept((DynamoQueryVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DynamoQueryAttrPath getAttrPath() {
    return findNotNullChildByClass(DynamoQueryAttrPath.class);
  }

  @Override
  @NotNull
  public List<DynamoQueryValue> getValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DynamoQueryValue.class);
  }

}
