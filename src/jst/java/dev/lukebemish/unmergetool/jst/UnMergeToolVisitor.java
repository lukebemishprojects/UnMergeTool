package dev.lukebemish.unmergetool.jst;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJvmModifiersOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRecursiveElementVisitor;
import dev.lukebemish.unmergetool.common.Distribution;
import dev.lukebemish.unmergetool.common.EnumAnnotation;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class UnMergeToolVisitor extends PsiRecursiveElementVisitor {
    private final Distribution distribution;
    private final Set<String> classFilesToRemove;
    private final Replacements replacements;

    public UnMergeToolVisitor(Distribution distribution, Set<String> classFilesToRemove, Replacements replacements) {
        this.distribution = distribution;
        this.classFilesToRemove = classFilesToRemove;
        this.replacements = replacements;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        boolean shouldRemove = false;
        PsiJvmModifiersOwner owner;
        switch (element) {
            case PsiClass psiClass -> {
                var fileName = psiClass.getQualifiedName();
                if (fileName != null) {
                    if (classFilesToRemove.contains(fileName.replace('.', '/')+".class")) {
                        shouldRemove = true;
                    }
                }
                owner = psiClass;
            }
            case PsiMethod psiMethod -> {
                owner = psiMethod;
            }
            case PsiField psiField -> {
                owner = psiField;
            }
            case PsiPackage psiPackage -> {
                owner = psiPackage;
            }
            default -> {
                super.visitElement(element);
                return;
            }
        }
        if (!shouldRemove) {
            for (var annotationType : EnumAnnotation.values()) {
                PsiAnnotation annotation = owner.getAnnotation(annotationType.annotationType);
                if (annotation != null) {
                    var value = annotation.findAttributeValue("value");
                    if (value != null) {
                        var values = value.getText().split("\\.");
                        var enumValue = values[values.length - 1];
                        boolean isClient = annotationType.clientValue.equals(enumValue);
                        boolean isServer = annotationType.serverValue.equals(enumValue);
                        if (!distribution.allowClient && isClient || !distribution.allowServer && isServer) {
                            shouldRemove = true;
                            break;
                        }
                    }
                }
            }
        }
        if (shouldRemove) {
            replacements.replace(element, "");
        } else {
            super.visitElement(element);
        }
    }
}
