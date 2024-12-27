package dev.lukebemish.unmergetool.jst;

import com.intellij.psi.PsiFile;
import dev.lukebemish.unmergetool.common.Distribution;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;

public class UnMergeToolTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--distribution", description = "The distribution to keep elements from", required = true)
    public Distribution distribution;

    private final Set<String> classFilesToRemove = new HashSet<>();
    
    @Override
    public void beforeRun(TransformContext context) {
        SourceTransformer.super.beforeRun(context);
        // TODO: Read `Fabric-Loom-Client-Only-Entries` and company from MANIFEST.MF
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new UnMergeToolVisitor(distribution, classFilesToRemove, replacements).visitElement(psiFile);
    }
}
