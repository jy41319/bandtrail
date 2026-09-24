package io.github.jy41319.bandtrail;

import java.util.*;

/** Discovery-only inventory; system records never count as live scan evidence. */
final class DeviceCatalog {
    static final class Entry {
        final String address;
        String alias = "", cachedName = "", advertisedName = "";
        boolean bonded, connected;
        long observed = -1;
        int rssi;
        Entry(String address) { this.address = address; }
        String name() { return !alias.isEmpty() ? alias : !cachedName.isEmpty() ? cachedName : advertisedName; }
        boolean saved() { return bonded || connected; }
        String label() { return name().isEmpty() ? "未命名设备 · " + address.substring(12) : name(); }
    }
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    Entry get(String address) { return entries.get(address); }
    Entry system(String address, String alias, String cachedName, boolean bonded, boolean connected) {
        Entry e = entry(address, true); if (e == null) return null;
        e.alias = clean(alias); e.cachedName = clean(cachedName);
        e.bonded |= bonded; e.connected |= connected;
        return e;
    }
    Entry scan(String address, String alias, String cachedName, String advertised, int rssi, long observed) {
        Entry e = entry(address, false); if (e == null) return null;
        if (!clean(alias).isEmpty()) e.alias = clean(alias);
        if (!clean(cachedName).isEmpty()) e.cachedName = clean(cachedName);
        if (!clean(advertised).isEmpty()) e.advertisedName = clean(advertised);
        if (observed >= e.observed) { e.observed = observed; e.rssi = rssi; }
        return e;
    }
    private Entry entry(String address, boolean system) {
        String normalized = normalize(address); if (normalized == null) return null;
        Entry e = entries.get(normalized);
        // Unknown broadcasts cannot crowd out a user's system records.
        if (e == null && (system || entries.size() < 100)) {
            e = new Entry(normalized); entries.put(normalized, e);
        }
        return e;
    }
    List<Entry> sorted() {
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparingInt((Entry e) -> e.connected ? 0 : e.bonded ? 1 : !e.name().isEmpty() ? 2 : 3)
            .thenComparing(Entry::label).thenComparing(e -> e.address));
        return result;
    }
    static String clean(String name) {
        if (name == null) return "";
        String value = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        return value.length() > 60 ? value.substring(0, 60) : value;
    }
    static String normalize(String value) {
        if (value == null) return null;
        String hex = value.trim().replace(":", "").replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!hex.matches("[0-9A-F]{12}") || hex.equals("000000000000") || hex.equals("FFFFFFFFFFFF")) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i += 2) { if (i > 0) result.append(':'); result.append(hex, i, i + 2); }
        return result.toString();
    }
}
