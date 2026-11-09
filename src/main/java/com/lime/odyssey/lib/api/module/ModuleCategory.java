package com.lime.odyssey.lib.api.module;

/** The four Odyssey Suite categories. */
public enum ModuleCategory {
    LIBRARY("Library"),
    MODIFICATION("Modification"),
    PATCH("Patches"),
    IMPROVEMENT("Improvements");

    private final String label;

    ModuleCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
