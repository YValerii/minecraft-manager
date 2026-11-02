package com.mcmanager.provider;

/** Result of an update check; update is populated for UP_TO_DATE and UPDATE_AVAILABLE. */
public record UpdateCheck(UpdateStatus status, UpdateInfo update) {

    public static UpdateCheck unknown() {
        return new UpdateCheck(UpdateStatus.UNKNOWN, null);
    }
}
