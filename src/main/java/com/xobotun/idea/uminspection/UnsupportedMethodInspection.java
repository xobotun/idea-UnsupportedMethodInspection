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
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.java.JavaConstructorUCallExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.*;

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
    protected final InspectionManager manager;
    protected final boolean isOnTheFly;
    List<ProblemDescriptor> problemsFound = new ArrayList<>();
    protected Map<String, PsiType> declarations = new HashMap<>();

    public CallSiteVisitor(final InspectionManager manager, final boolean isOnTheFly) {
        this.manager = manager;
        this.isOnTheFly = isOnTheFly;
    }

    // Variables are fields, local variables, parameters, etc.
    @Override
    public boolean visitVariable(@NotNull final UVariable node) {
        UExpression initializer = node.getUastInitializer();
        if (initializer != null) {
            declarations.put(node.getName(), initializer.getExpressionType());
        }
        return false; // This is not our main purpose, just a side effect. Return false to contiune logic of traversing the code block
    }

    @Override
    public boolean visitCallExpression(@NotNull final UCallExpression node) {
        PsiMethod method = node.resolve();
        if (method != null) {
            if (method.hasModifier(JvmModifier.ABSTRACT)) {
                // TODO: what about non-overriden methods that were overriden somewhere up in the hierarchy?
                handleKnownAbstractMethod(node, method);
            } else {
                handleKnownMethod(node, node.getReceiverType(), method);
            }
        } else if (node instanceof JavaConstructorUCallExpression || node.getClass().getSimpleName().contains("onstructor")) {
            // Just ignore constructors for now. They don't throw exceptions, normally. Or at least those that throw them are usually private.
        } else {
            showInfo(String.format("Failed to inspect if method call %s can lead to an exception throw", node.getClass()));
        }

        return true;
    }

    protected void handleKnownAbstractMethod(UCallExpression node, PsiMethod thatWasCalled) {
        UExpression receiver = node.getReceiver();

        if (receiver instanceof UReferenceExpression) {
            String receiverName = ((UReferenceExpression)receiver).getResolvedName();
            PsiType receiverActualType = declarations.get(receiverName);
            if (receiverActualType instanceof PsiClassType && !receiverActualType.equals(receiver.getExpressionType())) {
                PsiClass actualClass = ((PsiClassType) receiverActualType).resolve();
                if (actualClass != null) {
                    PsiMethod[] allMethods = actualClass.getAllMethods();
                    Optional<Pair<PsiMethod, PsiMethod>> actualMethod = Arrays.stream(allMethods)
                                                                              .flatMap(m -> Arrays.stream(m.findSuperMethods(false)).map(sm -> new Pair<>(m, sm)))
                                                                              .filter(pair -> pair.getSecond().equals(thatWasCalled))
                                                                              .findFirst();
                    if (actualMethod.isPresent()) {
                        handleKnownMethod(node, receiverActualType, actualMethod.get().getFirst());
                        return;
                    }
                }
            }
        }

        showInfo(String.format("Failed to inspect if method call %s.%s can lead to an exception throw", node, thatWasCalled));
    }

    protected void handleKnownMethod(UCallExpression callSite, PsiType receiverType, PsiMethod thatWasCalled) {
        PsiCodeBlock code = thatWasCalled.getBody();
        if (code == null) {
            handleCompiledSource(callSite, thatWasCalled);
        } else {
            handleKnownSource(callSite, receiverType, code);
        }
    }

    protected void handleKnownSource(UCallExpression node, PsiType receiverType, PsiCodeBlock code) {
        PsiStatement[] statements = code.getStatements();
        if (statements.length > 0 && statements[0] instanceof PsiThrowStatement) {
            // Only the first statement should be 'throw'. Otherwise there might be more logic before it. And it is a too complicated of a case.
            PsiType exceptionType = tryExtractExceptionType((PsiThrowStatement)statements[0]);
            raiseWarning(node, receiverType, exceptionType);
        }
    }

    protected void handleCompiledSource(UCallExpression node, PsiMethod thatWasCalled) {
        System.out.printf("Cannot get sources for %s%n", node);
        node.getSourcePsi().getNavigationElement();
    }

    protected void raiseWarning(UCallExpression callSite, PsiType receiverType, @Nullable PsiType exceptionType) {
        problemsFound.add( manager.createProblemDescriptor(callSite.getSourcePsi(),
               String.format("Calling '%s.%s' will throw %s", receiverType.getPresentableText(), callSite.getMethodName(), exceptionType != null ? exceptionType.getPresentableText() : "an exception"),
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
