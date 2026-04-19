// This is a generated file. Not intended for manual editing.
package com.yourplugin.dynamodb.language.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DynamoQueryStatement extends PsiElement {

  @Nullable
  DynamoQueryDryRunDirective getDryRunDirective();

  @Nullable
  DynamoQuerySelectStatement getSelectStatement();

  @Nullable
  DynamoQueryUpdateStatement getUpdateStatement();

}
