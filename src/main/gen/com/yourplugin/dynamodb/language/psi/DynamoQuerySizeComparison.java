// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQuerySizeComparison extends PsiElement {

  @NotNull
  DynamoQueryAttrPath getAttrPath();

  @NotNull
  DynamoQueryComparisonOp getComparisonOp();

  @NotNull
  DynamoQueryValue getValue();

}
