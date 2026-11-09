package com.lime.odyssey.lib.api.config;

import java.util.List;

/** Optional cross-field validation for a config class. Return an empty list when valid. */
public interface Validatable {
    List<String> validate();
}
