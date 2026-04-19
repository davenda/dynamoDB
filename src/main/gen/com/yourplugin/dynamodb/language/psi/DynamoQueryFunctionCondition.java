// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryFunctionCondition extends PsiElement {

  @Nullable
  DynamoQueryAttributeTypeExpr getAttributeTypeExpr();

  @Nullable
  DynamoQueryBeginsWithExpr getBeginsWithExpr();

  @Nullable
  DynamoQueryContainsExpr getContainsExpr();

  @Nullable
  DynamoQueryExistsExpr getExistsExpr();

  @Nullable
  DynamoQuerySizeComparison getSizeComparison();

}
