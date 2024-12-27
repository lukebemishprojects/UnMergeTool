package dev.lukebemish.unmergetool.jst;

import com.google.auto.service.AutoService;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

@AutoService(SourceTransformerPlugin.class)
public class UnMergeToolPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "unmergetool";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new UnMergeToolTransformer();
    }
}
