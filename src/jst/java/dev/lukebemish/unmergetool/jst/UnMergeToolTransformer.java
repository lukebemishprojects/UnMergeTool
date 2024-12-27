package dev.lukebemish.unmergetool.jst;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJvmModifiersOwner;
import dev.lukebemish.unmergetool.common.Distribution;
import dev.lukebemish.unmergetool.common.EnumAnnotation;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnMergeToolTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--distribution", description = "The distribution to keep elements from", required = true)
    public Distribution distribution;

    @CommandLine.Option(names = "--target-classes", description = "List of classes that should be targeted", required = true)
    public Path targetClasses;

    private final Set<String> classFilesToRemove = new HashSet<>();

    private final Set<String> classFilesToTarget = new HashSet<>();

    @Override
    public void beforeRun(TransformContext context) {
        SourceTransformer.super.beforeRun(context);
        // TODO: Read `Fabric-Loom-Client-Only-Entries` and company from MANIFEST.MF

        try {
            var lines = Files.readAllLines(targetClasses);
            classFilesToTarget.addAll(lines);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        if (psiFile instanceof PsiJavaFile psiJavaFile) {
            for (var psiClass : psiJavaFile.getClasses()) {
                var fqn = psiClass.getQualifiedName();
                var inManifestRemoval = fqn != null && classFilesToRemove.contains(fqn.replace('.', '/')+".class");
                if (inManifestRemoval || shouldRemoveElement(psiClass)) {
                    replacements.replace(psiClass, "");
                } else {
                    var name = binaryName(psiClass);
                    if (classFilesToTarget.contains(name)) {
                        handleClass(psiClass, replacements);
                    }
                }
            }
        }
    }

    void handleClass(PsiClass psiClass, Replacements replacements) {
        for (var psiMethod : psiClass.getMethods()) {
            if (shouldRemoveElement(psiMethod)) {
                replacements.replace(psiMethod, "");
            }
        }
        for (var psiField : psiClass.getFields()) {
            if (shouldRemoveElement(psiField)) {
                replacements.replace(psiField, "");
            }
        }
        for (var psiInnerClass : psiClass.getInnerClasses()) {
            if (shouldRemoveElement(psiInnerClass)) {
                replacements.replace(psiInnerClass, "");
            } else {
                var name = binaryName(psiInnerClass);
                if (classFilesToTarget.contains(name)) {
                    handleClass(psiInnerClass, replacements);
                }
            }
        }
    }

    private static final Map<String, EnumAnnotation> ANNOTATION_TYPE_FQN_MAP = Arrays.stream(EnumAnnotation.values())
        .collect(Collectors.toMap(e -> e.annotationType, Function.identity()));

    private static String binaryName(PsiClass psiClass) {
        StringBuilder builder = new StringBuilder();
        PsiHelper.getBinaryClassName(psiClass, builder);
        return builder.toString();
    }

    boolean shouldRemoveElement(PsiJvmModifiersOwner owner) {
        for (var annotation : owner.getAnnotations()) {
            var annotationType = ANNOTATION_TYPE_FQN_MAP.get(annotation.getQualifiedName());
            if (annotationType != null) {
                var value = annotation.findAttributeValue("value");
                if (value != null) {
                    var values = value.getText().split("\\.");
                    var enumValue = values[values.length - 1];
                    boolean isClient = annotationType.clientValue.equals(enumValue);
                    boolean isServer = annotationType.serverValue.equals(enumValue);
                    if (!distribution.allowClient && isClient || !distribution.allowServer && isServer) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
