package com.mcmanager.provider;

public enum UpdateStatus {
    /** The installed version is the latest compatible one. */
    UP_TO_DATE,
    /** A newer compatible version is available. */
    UPDATE_AVAILABLE,
    /** No version exists at all for the given game version and loader. */
    NO_COMPATIBLE,
    /** The file wasn't recognized or couldn't be checked. */
    UNKNOWN
}
