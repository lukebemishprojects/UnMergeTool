package dev.lukebemish.unmergetool.common;

import java.util.List;

public enum Distribution {
    CLIENT(true, false, List.of("Fabric-Loom-Server-Only-Entries")),
    SERVER(false, true, List.of("Fabric-Loom-Client-Only-Entries")),
    COMMON(false, false, List.of("Fabric-Loom-Client-Only-Entries", "Fabric-Loom-Server-Only-Entries"));

    public final boolean allowClient;
    public final boolean allowServer;
    public final List<String> manifestExcludedClasses;

    Distribution(boolean allowClient, boolean allowServer, List<String> manifestExcludedClasses) {
        this.allowClient = allowClient;
        this.allowServer = allowServer;
        this.manifestExcludedClasses = manifestExcludedClasses;
    }
}
