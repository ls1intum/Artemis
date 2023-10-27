/*
 * Copyright (C) 2012, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Parses Git client User-Agent strings.
 */
public class ClientVersionUtil {

    /**
     * An invalid version of Git
     *
     * @return maximum version array, indicating an invalid version of Git.
     */
    public static int[] invalidVersion() {
        return new int[] { Integer.MAX_VALUE };
    }

    /**
     * Parse a Git client User-Agent header value.
     *
     * @param version
     *                    git client version string, of the form "git/1.7.9".
     * @return components of the version string. {@link #invalidVersion()} if
     *         the version string cannot be parsed.
     */
    public static int[] parseVersion(String version) {
        if (version != null && version.startsWith("git/"))
            return splitVersion(version.substring("git/".length()));
        return invalidVersion();
    }

    private static int[] splitVersion(String versionString) {
        char[] str = versionString.toCharArray();
        int[] ver = new int[4];
        int end = 0;
        int acc = 0;
        for (int i = 0; i < str.length; i++) {
            char c = str[i];
            if ('0' <= c && c <= '9') {
                acc *= 10;
                acc += c - '0';
            }
            else if (c == '.') {
                if (end == ver.length)
                    ver = grow(ver);
                ver[end++] = acc;
                acc = 0;
            }
            else if (c == 'g' && 0 < i && str[i - 1] == '.' && 0 < end) {
                // Non-tagged builds may contain a mangled git describe output.
                // "1.7.6.1.45.gbe0cc". The 45 isn't a valid component. Drop it.
                ver[end - 1] = 0;
                acc = 0;
                break;
            }
            else if (c == '-' && (i + 2) < str.length && str[i + 1] == 'r' && str[i + 2] == 'c') {
                // Release candidates aren't the same as a final release.
                if (acc > 0)
                    acc--;
                break;
            }
            else
                break;
        }
        if (acc != 0) {
            if (end == ver.length)
                ver = grow(ver);
            ver[end++] = acc;
        }
        else {
            while (0 < end && ver[end - 1] == 0)
                end--;
        }
        if (end < ver.length) {
            int[] n = new int[end];
            System.arraycopy(ver, 0, n, 0, end);
            ver = n;
        }
        return ver;
    }

    private static int[] grow(int[] tmp) {
        int[] n = new int[tmp.length + 1];
        System.arraycopy(tmp, 0, n, 0, tmp.length);
        return n;
    }

    /**
     * Compare two version strings for natural ordering.
     *
     * @param a
     *              first parsed version string.
     * @param b
     *              second parsed version string.
     * @return &lt; 0 if a is before b; 0 if a equals b; &gt;0 if a is after b.
     */
    public static int compare(int[] a, int[] b) {
        for (int i = 0; i < a.length && i < b.length; i++) {
            int cmp = a[i] - b[i];
            if (cmp != 0)
                return cmp;
        }
        return a.length - b.length;
    }

    /**
     * Convert a parsed version back to a string.
     *
     * @param ver
     *                the parsed version array.
     * @return a string, e.g. "1.6.6.0".
     */
    public static String toString(int[] ver) {
        StringBuilder b = new StringBuilder();
        for (int v : ver) {
            if (b.length() > 0)
                b.append('.');
            b.append(v);
        }
        return b.toString();
    }

    /**
     * Check if a Git client has the known push status bug.
     * <p>
     * These buggy clients do not display the status report from a failed push
     * over HTTP.
     *
     * @param version
     *                    parsed version of the Git client software.
     * @return true if the bug is present.
     * @deprecated no widely used Git versions need this any more
     */
    @Deprecated
    public static boolean hasPushStatusBug(int[] version) {
        return false;
    }

    /**
     * Check if a Git client has the known chunked request body encoding bug.
     * <p>
     * Git 1.7.5 contains a unique bug where chunked requests are malformed.
     * This applies to both fetch and push.
     *
     * @param version
     *                    parsed version of the Git client software.
     * @param request
     *                    incoming HTTP request.
     * @return true if the client has the chunked encoding bug.
     * @deprecated no widely used Git versions need this any more
     */
    @Deprecated
    public static boolean hasChunkedEncodingRequestBug(int[] version, HttpServletRequest request) {
        return false;
    }

    private ClientVersionUtil() {
    }
}
