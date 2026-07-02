package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * Builds the {@code copyOut} report tars the verifier reads from the sandbox's verifier-owned reports directory, so the deterministic
 * {@link AuthoritativeVerificationServiceTest} can drive the NEW copyOut+production-parser flow without Docker. The verifier copies the reports dir out and routes each collected
 * file by the {@code <seq>__<canonical>} name the pristine {@code verify.sh} assigns: JUnit reports carry the {@link SandboxBuildCommandService#COLLECTED_JUNIT_TOKEN} canonical
 * token, SCA reports keep their per-tool canonical name. These fixtures pack real JUnit/SCA XML under exactly those names, prefixed with the assignment directory the way Docker
 * prefixes copied-out entries, so the validated {@link CollectedReports} reader strips it correctly.
 */
final class ReportTarFixtures {

    private static final int MODE_FILE = 0644;

    private ReportTarFixtures() {
    }

    /** Composes a JUnit report XML with one {@code <testcase>} per name in {@code allNames}, marking the names in {@code failedNames} with a {@code <failure>}. */
    static String junitXml(List<String> allNames, List<String> failedNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"GeneratedSuite\" tests=\"").append(allNames.size()).append("\" failures=\"").append(failedNames.size())
                .append("\" errors=\"0\" skipped=\"0\">\n");
        for (String name : allNames) {
            sb.append("  <testcase name=\"").append(xmlAttr(name)).append("\" classname=\"GeneratedSuite\"");
            if (failedNames.contains(name)) {
                sb.append("><failure message=\"expected behaviour not met\"/></testcase>\n");
            }
            else {
                sb.append("/>\n");
            }
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }

    /**
     * A reports tar carrying one JUnit report (under the JUnit canonical token) for the given names, prefixed with {@code assignment} as Docker prefixes a copied-out directory.
     */
    static TarArchiveInputStream junitReports(String assignment, List<String> allNames, List<String> failedNames) {
        return tar(assignment, Map.of("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                junitXml(allNames, failedNames).getBytes(StandardCharsets.UTF_8)));
    }

    /** A reports tar carrying a JUnit report plus the given SCA reports (keyed by their canonical per-tool file name, e.g. {@code spotbugsXml.xml}). */
    static TarArchiveInputStream junitAndScaReports(String assignment, List<String> allNames, List<String> failedNames, Map<String, String> scaReportsByCanonicalName) {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        entries.put("0001" + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN,
                junitXml(allNames, failedNames).getBytes(StandardCharsets.UTF_8));
        int seq = 2;
        for (Map.Entry<String, String> sca : scaReportsByCanonicalName.entrySet()) {
            entries.put(String.format("%04d", seq++) + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + sca.getKey(), sca.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return tar(assignment, entries);
    }

    /** Packs the given {@code flatName -> bytes} entries into a tar, each prefixed with {@code prefix/} (the assignment dir Docker prepends to a copied-out directory). */
    static TarArchiveInputStream tar(String prefix, Map<String, byte[]> flatNameToBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> entry : flatNameToBytes.entrySet()) {
                writeFile(tar, prefix + "/" + entry.getKey(), entry.getValue());
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    private static void writeFile(TarArchiveOutputStream tar, String name, byte[] content) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        entry.setMode(MODE_FILE);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    /** Escapes the five XML attribute-significant characters so a test name with quotes/ampersands stays well-formed. */
    private static String xmlAttr(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
