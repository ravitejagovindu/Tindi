package com.tindi.api.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The fixed set of pictures a recipe step can be shown with. Clients ship one image for each. */
public enum Illustration {
    PREP, SOAK, BLEND, MIX, SAUTE, SIMMER, BOIL, FRY, BAKE, STEAM, REST, SERVE, GENERIC;

    public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();

    /** For model output: ignores case and surrounding space, and turns anything unknown into {@link #GENERIC}. */
    public static Illustration orGeneric(String value) {
        String name = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        return NAMES.contains(name) ? valueOf(name) : GENERIC;
    }
}
