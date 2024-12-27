package dev.lukebemish.unmergetool.common;

import org.jspecify.annotations.Nullable;

public enum EnumAnnotation implements AnnotationType {
    NEO_API("net.neoforged.api.distmarker.OnlyIn", "value", "CLIENT", "DEDICATED_SERVER", "_interface", "net.neoforged.api.distmarker.OnlyIns"),
    FORGE_API("net.minecraftforge.api.distmarker.OnlyIn", "value", "CLIENT", "DEDICATED_SERVER", "_interface", "net.minecraftforge.api.distmarker.OnlyIns"),
    FORGE_NMF("net.minecraftforge.fml.relauncher.SideOnly", "value", "CLIENT", "SERVER"),
    FORGE_CPW("cpw.mods.fml.relauncher.SideOnly", "value", "CLIENT", "SERVER"),
    FABRIC("net.fabricmc.api.Environment", "value", "CLIENT", "SERVER");
    
    public final String annotationType;
    public final String annotationName;
    public final String clientValue;
    public final String serverValue;
    
    // TODO: how do we need to use this?
    public final @Nullable String interfaces;
    public final @Nullable String repeatable;

    EnumAnnotation(String annotationType, String annotationName, String clientValue, String serverValue, @Nullable String interfaces, @Nullable String repeatable) {
        this.annotationType = annotationType;
        this.annotationName = annotationName;
        this.clientValue = clientValue;
        this.serverValue = serverValue;
        this.interfaces = interfaces;
        this.repeatable = repeatable;
    }

    EnumAnnotation(String annotationType, String annotationName, String clientValue, String serverValue) {
        this(annotationType, annotationName, clientValue, serverValue, null, null);
    }
}
