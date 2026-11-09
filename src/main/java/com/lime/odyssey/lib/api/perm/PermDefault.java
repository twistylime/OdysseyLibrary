package com.lime.odyssey.lib.api.perm;

/** Who has a permission when no permission mod overrides it. */
public enum PermDefault {
    /** Every player. */
    ALL,
    /** OP level >= {@code adminOpLevel} from library.json (default 2). */
    ADMIN,
    /** OP level 4. */
    OWNER
}
