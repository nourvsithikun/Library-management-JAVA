package com.istad.library.model;

import java.util.Locale;

/** Describes whether a registered member is allowed to borrow books. */
public enum AccountStatus {
    ACTIVE,
    INACTIVE;

    public static AccountStatus fromText(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
