package org.telegram.messenger;

import android.util.Base64;

/** Validates login QR contents before any authorization request is sent. */
public final class QrLoginToken {
    private static final String PREFIX = "tg://login?token=";

    private QrLoginToken() {}

    public static byte[] decode(String link) {
        if (link == null || !link.startsWith(PREFIX)) return null;
        String encoded = link.substring(PREFIX.length());
        // A QR is untrusted input. Reject empty, oversized or non-Base64 payloads,
        // including URI parameters/fragments and padding in the middle.
        if (encoded.isEmpty() || encoded.length() > 8192
                || !encoded.matches("[A-Za-z0-9_+/-]+={0,2}")) return null;
        int padding = encoded.indexOf('=');
        int length = padding < 0 ? encoded.length() : padding;
        if (length % 4 == 1 || (padding >= 0 && encoded.length() % 4 != 0)) return null;
        try {
            byte[] token = Base64.decode(encoded.replace('+', '-').replace('/', '_'), Base64.URL_SAFE);
            return token.length == 0 ? null : token;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
