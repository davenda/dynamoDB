// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryBetweenExpr extends PsiElement {

  @NotNull
  DynamoQueryAttrPath getAttrPath();

  @NotNull
  List<DynamoQueryValue> getValueList();

}
