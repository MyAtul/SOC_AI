package ai_soc_backend.incident.service;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical form for configured host identifiers; deliberately performs no DNS lookup. */
public final class HostIdentity {
    private HostIdentity() { }

    public static String canonicalize(String raw) {
        if (raw == null) return null;
        String host = Normalizer.normalize(
                Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT),
                Normalizer.Form.NFC);
        if (host.isBlank() || host.equals("localhost") || host.length() > 255) return null;
        if (!host.codePoints().allMatch(cp -> Character.isLetterOrDigit(cp) || cp == '.' || cp == '_' || cp == '-' || cp == ':')) {
            return null;
        }
        if (!Character.isLetterOrDigit(host.codePointAt(0)) || !Character.isLetterOrDigit(host.codePointBefore(host.length()))) {
            return null;
        }
        return host;
    }
}
