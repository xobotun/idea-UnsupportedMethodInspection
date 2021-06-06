package com.xobotun.idea.uminspection;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.SPIReferencesSearcher;
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.java.JavaConstructorUCallExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.List;

import static com.xobotun.idea.LogUtils.showInfo;
import static com.xobotun.idea.ProjectUtils.currentProject;

public class UnsupportedMethodInspection extends AbstractBaseUastLocalInspectionTool {
    public UnsupportedMethodInspection() {
        super(UMethod.class, UField.class);
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkMethod(@NotNull final UMethod method, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        // Check normal methods
        return doCheck(method, manager, isOnTheFly);
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkField(@NotNull final UField field, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        // Checks field initializers
        return doCheck(field, manager, isOnTheFly);
    }

    protected ProblemDescriptor[] doCheck(UElement methodOrField, InspectionManager manager, boolean isOnTheFly) {
        CallSiteVisitor visitor = new CallSiteVisitor(manager, isOnTheFly);

        methodOrField.accept(visitor);

        return visitor.problemsFound.isEmpty() ? null : visitor.problemsFound.toArray(new ProblemDescriptor[0]);
    }
}

class CallSiteVisitor extends AbstractUastVisitor {
    final InspectionManager manager;
    final boolean isOnTheFly;
    List<ProblemDescriptor> problemsFound = new ArrayList<>();

    public CallSiteVisitor(final InspectionManager manager, final boolean isOnTheFly) {
        this.manager = manager;
        this.isOnTheFly = isOnTheFly;
    }

    @Override
    public boolean visitCallExpression(@NotNull final UCallExpression node) {
        PsiMethod method = node.resolve();
        if (method != null) {
            if (method.hasModifier(JvmModifier.ABSTRACT)) {
                // TODO: figure out real node.getReceiver() type
            } else {
                handleKnownMethod(node, method);
            }
        } else if (node instanceof JavaConstructorUCallExpression) {
            JavaConstructorUCallExpression constructor = (JavaConstructorUCallExpression) node;
        } else {
            showInfo(String.format("Failed to inspect if method call %s can lead to an exception throw", node.getClass()));
        }

        return true;
    }

    protected void handleKnownMethod(UCallExpression node, PsiMethod thatWasCalled) {
        PsiCodeBlock code = thatWasCalled.getBody();
        if (code == null) {
            handleCompiledSource(node, thatWasCalled);
        } else {
            handleKnownSource(node, code);
        }
    }

    protected void handleKnownSource(UCallExpression node, PsiCodeBlock code) {
        PsiStatement[] statements = code.getStatements();
        if (statements.length > 0 && statements[0] instanceof PsiThrowStatement) {
            // Only the first statement should be 'throw'. Otherwise there might be more logic before it. And it is a too complicated of a case.
            PsiType exceptionType = tryExtractExceptionType((PsiThrowStatement)statements[0]);
            raiseWarning(node, exceptionType);
        }
    }

    protected void handleCompiledSource(UCallExpression node, PsiMethod thatWasCalled) {
        node.getSourcePsi().getNavigationElement();
    }

    protected void raiseWarning(UCallExpression callSite, @Nullable PsiType exceptionType) {
        problemsFound.add( manager.createProblemDescriptor(callSite.getSourcePsi(),
               String.format("Calling '%s' will throw %s", callSite.getMethodName(), exceptionType != null ? exceptionType.getPresentableText() : "an exception"),
               new LocalQuickFix[0], ProblemHighlightType.WARNING, isOnTheFly, false));
    }

    protected static PsiType tryExtractExceptionType(PsiThrowStatement statement) {
        for (PsiElement child : statement.getChildren()) {
            if (child instanceof PsiNewExpressionImpl) {
                // Will fail in cases like `new ExceptionManager().createException()`
                return ((PsiNewExpressionImpl) child).getType();
            } else if (child instanceof PsiMethodCallExpression) {
                // Will fail in cases like `currentExceptionManager().createException()`
                return ((PsiMethodCallExpression) child).getType();
            }
        }
        return null;
    }
}
