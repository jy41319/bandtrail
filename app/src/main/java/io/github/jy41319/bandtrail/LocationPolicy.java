package io.github.jy41319.bandtrail;

public final class LocationPolicy {
    public static final long MAX_AGE_MS = 90_000;
    private LocationPolicy() {}
    public static boolean usable(long seenElapsed, long fixElapsed, float accuracy, double lat, double lon) {
        return fixElapsed >= 0 && seenElapsed >= fixElapsed && seenElapsed - fixElapsed <= MAX_AGE_MS
            && Float.isFinite(accuracy) && accuracy > 0 && accuracy <= 1000
            && Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }
    public static boolean staleObservation(long now, long observed) {
        return observed < 0 || observed > now || now - observed > 15_000;
    }
}
