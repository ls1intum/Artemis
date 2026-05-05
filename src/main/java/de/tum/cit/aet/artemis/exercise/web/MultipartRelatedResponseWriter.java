package de.tum.cit.aet.artemis.exercise.web;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import de.tum.cit.aet.artemis.exercise.service.ProblemStatementRenderingService.AttachedImage;

class MultipartRelatedResponseWriter {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    static void write(OutputStream os, String boundary, byte[] jsonRootPart, List<AttachedImage> images) throws IOException {
        var out = new BufferedOutputStream(os);

        byte[] boundaryLine = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] endBoundary = ("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII);

        // Root part (JSON)
        out.write(boundaryLine);
        out.write(CRLF);
        out.write("Content-Type: application/json; charset=UTF-8\r\n".getBytes(StandardCharsets.US_ASCII));
        out.write("Content-ID: <root@artemis>\r\n".getBytes(StandardCharsets.US_ASCII));
        out.write(CRLF);
        out.write(jsonRootPart);
        out.write(CRLF);

        // Image parts
        for (AttachedImage image : images) {
            out.write(boundaryLine);
            out.write(CRLF);
            out.write(("Content-Type: " + image.contentType() + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(("Content-ID: <" + image.contentId() + ">\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(("Content-Disposition: inline; filename=\"" + image.filename() + "\"\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write(image.data());
            out.write(CRLF);
        }

        out.write(endBoundary);
        out.write(CRLF);
        out.flush();
    }
}
