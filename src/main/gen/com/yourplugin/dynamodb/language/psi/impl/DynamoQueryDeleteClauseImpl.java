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

public class DynamoQueryDeleteClauseImpl extends ASTWrapperPsiElement implements DynamoQueryDeleteClause {

  public DynamoQueryDeleteClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DynamoQueryVisitor visitor) {
    visitor.visitDeleteClause(this);
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
  public DynamoQueryValue getValue() {
    return findNotNullChildByClass(DynamoQueryValue.class);
  }

}
