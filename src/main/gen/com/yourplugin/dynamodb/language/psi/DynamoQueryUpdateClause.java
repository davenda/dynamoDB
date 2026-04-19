// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryUpdateClause extends PsiElement {

  @Nullable
  DynamoQueryAddClause getAddClause();

  @Nullable
  DynamoQueryDeleteClause getDeleteClause();

  @Nullable
  DynamoQueryRemoveClause getRemoveClause();

  @Nullable
  DynamoQuerySetClause getSetClause();

}
