package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage.arrays;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.EAUltimateProjectSettings;
import com.kalessil.phpStorm.phpInspectionsEA.fixers.UseSuggestedReplacementFixer;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.GenericPhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.settings.StrictnessCategory;
import com.kalessil.phpStorm.phpInspectionsEA.utils.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class InArrayMissUseInspector extends PhpInspection {
    private static final String patternComparison = "'%s' should be used instead.";
    private static final String patternKeyExists  = "'%s' should be used instead. It is safe to refactor for type-safe code when the indexes are integers/strings only.";

    @NotNull
    @Override
    public String getShortName() {
        return "InArrayMissUseInspection";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "'in_array(...)' misused";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new GenericPhpElementVisitor() {
            @Override
            public void visitPhpFunctionCall(@NotNull FunctionReference reference) {
                if (this.shouldSkipAnalysis(reference, StrictnessCategory.STRICTNESS_CATEGORY_PERFORMANCE)) { return; }

                final String functionName = reference.getName();
                if (functionName == null || !functionName.equals("in_array")) {
                    return;
                }
                final PsiElement[] arguments = reference.getParameters();
                if (arguments.length != 2 && arguments.length != 3) {
                    return;
                }

                final Set<PsiElement> possibleValues = PossibleValuesDiscoveryUtil.discover(arguments[1]);
                final PsiElement discoveredValue     = possibleValues.size() == 1 ? possibleValues.iterator().next() : null;
                possibleValues.clear();

                /* pattern: array_key_exists equivalence */
                if (OpenapiTypesUtil.isFunctionReference(discoveredValue)) {
                    final FunctionReference nestedCall = (FunctionReference) discoveredValue;
                    final String nestedFunctionName    = nestedCall.getName();
                    if (nestedFunctionName != null && nestedFunctionName.equals("array_keys")) {
                        /* ensure the nested call is a complete expression */
                        final PsiElement[] nestedCallParams = nestedCall.getParameters();
                        if (nestedCallParams.length == 1) {
                            final String replacement = "array_key_exists(%k%, %a%)"
                                    .replace("%a%", nestedCallParams[0].getText())
                                    .replace("%k%", arguments[0].getText());
                            final boolean provideFix = discoveredValue == arguments[1];
                            holder.registerProblem(
                                    reference,
                                    MessagesPresentationUtil.prefixWithEa(String.format(patternKeyExists, replacement)),
                                    provideFix ? new UseArrayKeyExistsFix(replacement) : null
                            );
                        }
                    }
                }
                /* pattern: comparison equivalence */
                else if (discoveredValue instanceof ArrayCreationExpression) {
                    int itemsCount      = 0;
                    PsiElement lastItem = null;
                    for (final PsiElement oneItem : discoveredValue.getChildren()) {
                        if (oneItem instanceof PhpPsiElement) {
                            ++itemsCount;
                            lastItem = oneItem;
                        }
                    }

                    lastItem = lastItem instanceof ArrayHashElement ? ((ArrayHashElement) lastItem).getValue() : lastItem;
                    if (itemsCount <= 1 && null != lastItem) {
                        final PsiElement parent = reference.getParent();

                        /* find out what what intended to happen */
                        boolean checkExists = true;
                        PsiElement target   = reference;
                        if (parent instanceof UnaryExpression) {
                            final UnaryExpression unary = (UnaryExpression) parent;
                            if (OpenapiTypesUtil.is(unary.getOperation(), PhpTokenTypes.opNOT)) {
                                checkExists = false;
                                target      = parent;
                            }
                        }
                        if (parent instanceof BinaryExpression) {
                            /* extract in_arrays' expression parts */
                            final BinaryExpression expression = (BinaryExpression) parent;
                            final PsiElement secondOperand    = OpenapiElementsUtil.getSecondOperand(expression, reference);
                            if (PhpLanguageUtil.isBoolean(secondOperand)) {
                                final IElementType operation = expression.getOperationType();
                                if (PhpTokenTypes.opEQUAL == operation || PhpTokenTypes.opIDENTICAL == operation) {
                                    target      = parent;
                                    checkExists = PhpLanguageUtil.isTrue(secondOperand);
                                } else if (PhpTokenTypes.opNOT_EQUAL == operation || PhpTokenTypes.opNOT_IDENTICAL == operation) {
                                    target      = parent;
                                    checkExists = !PhpLanguageUtil.isTrue(secondOperand);
                                } else {
                                    target = reference;
                                }
                            }
                        }

                        final boolean isStrict   = arguments.length == 3 && PhpLanguageUtil.isTrue(arguments[2]);
                        final String  comparison = (checkExists ? "==" : "!=") + (isStrict ? "=" : "");
                        final String replacement = holder.getProject().getComponent(EAUltimateProjectSettings.class).isPreferringYodaComparisonStyle()
                                                   ? String.format("%s %s %s", lastItem.getText(), comparison, arguments[0].getText())
                                                   : String.format("%s %s %s", arguments[0].getText(), comparison, lastItem.getText());
                        final boolean provideFix = discoveredValue == arguments[1];
                        holder.registerProblem(
                                target,
                                MessagesPresentationUtil.prefixWithEa(String.format(patternComparison, replacement)),
                                provideFix ? new UseComparisonFix(replacement) : null
                        );
                    }
                }
            }
        };
    }

    private static final class UseArrayKeyExistsFix extends UseSuggestedReplacementFixer {
        private static final String title = "Use array_key_exists() instead";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        UseArrayKeyExistsFix(@NotNull String expression) {
            super(expression);
        }
    }

    private static final class UseComparisonFix extends UseSuggestedReplacementFixer {
        private static final String title = "Compare elements instead";

        @NotNull
        @Override
        public String getName() {
            return MessagesPresentationUtil.prefixWithEa(title);
        }

        UseComparisonFix(@NotNull String expression) {
            super(expression);
        }
    }
}
