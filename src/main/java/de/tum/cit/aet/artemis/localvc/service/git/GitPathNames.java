package de.tum.cit.aet.artemis.localvc.service.git;

import java.util.Locale;

/**
 * Recognizes the names that a file system treats as the metadata directory of a git repository, so that a tree of a repository cannot carry one.
 * <p>
 * Besides {@code .git}, the case variants, the Unicode characters that HFS+ ignores, the trailing dots and spaces that NTFS ignores, the NTFS stream syntax and the 8.3 short name
 * {@code git~1} resolve to the metadata directory on some file systems. A tree that holds such an entry becomes configuration and hooks of a repository once it is extracted.
 */
public final class GitPathNames {

    private GitPathNames() {
        // utility class
    }

    /**
     * Whether a single path segment names the metadata directory of a git repository on some file system.
     *
     * @param segment one name of a path, without separators
     * @return true if the segment has to be refused
     */
    public static boolean isGitMetadataName(String segment) {
        StringBuilder normalized = new StringBuilder(segment.length());
        segment.codePoints().filter(codePoint -> !isIgnoredByHfs(codePoint)).forEach(normalized::appendCodePoint);
        // NTFS stream syntax such as ".git::$INDEX_ALLOCATION" selects the directory itself
        int stream = normalized.indexOf(":");
        if (stream >= 0) {
            normalized.setLength(stream);
        }
        // NTFS ignores trailing dots and spaces
        int end = normalized.length();
        while (end > 0 && (normalized.charAt(end - 1) == '.' || normalized.charAt(end - 1) == ' ')) {
            end--;
        }
        String name = normalized.substring(0, end).toLowerCase(Locale.ROOT);
        return name.equals(".git") || name.matches("git~\\d");
    }

    /**
     * Whether any segment of a path names the metadata directory of a git repository.
     *
     * @param path a path with forward or backward slashes as separators
     * @return true if the path has to be refused
     */
    public static boolean containsGitMetadataName(String path) {
        for (String segment : path.split("[/\\\\]")) {
            if (isGitMetadataName(segment)) {
                return true;
            }
        }
        return false;
    }

    /** The code points that HFS+ drops when it compares names: zero width and directional marks. */
    private static boolean isIgnoredByHfs(int codePoint) {
        return codePoint >= 0x200C && codePoint <= 0x200F || codePoint >= 0x202A && codePoint <= 0x202E || codePoint >= 0x206A && codePoint <= 0x206F || codePoint == 0xFEFF;
    }
}
