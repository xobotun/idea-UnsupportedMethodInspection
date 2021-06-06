package com.xobotun.idea.uminspection;

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;

public class UnsupportedMethodInspection extends AbstractBaseUastLocalInspectionTool {
    public UnsupportedMethodInspection() {
        super(UElement.class);
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkMethod(@NotNull final UMethod method, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        System.out.println("Checking " + method);

        return super.checkMethod(method, manager, isOnTheFly);
    }
}
