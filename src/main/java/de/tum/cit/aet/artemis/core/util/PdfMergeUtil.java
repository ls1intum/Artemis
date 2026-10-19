package de.tum.cit.aet.artemis.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility.DocumentMergeMode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PdfMergeUtil {

    private static final Logger log = LoggerFactory.getLogger(PdfMergeUtil.class);

    private PdfMergeUtil() {
    }

    static Optional<byte[]> mergePdfFiles(List<Path> paths, String mergedPdfFilename) {
        if (paths == null || paths.isEmpty()) {
            return Optional.empty();
        }

        List<Path> existingPaths = paths.stream().filter(Files::exists).toList();
        if (existingPaths.isEmpty()) {
            return Optional.of(new byte[0]);
        }

        try {
            return Optional.of(mergePdfFiles(existingPaths, mergedPdfFilename, DocumentMergeMode.PDFBOX_LEGACY_MODE));
        }
        catch (IOException legacyMergeException) {
            log.warn(
                    "Could not fully merge PDF files {}. Retrying with a page-only merge that omits PDF tags, bookmarks, and internal destinations. Re-export or repair the source PDFs to preserve these elements.",
                    existingPaths, legacyMergeException);
            return mergePdfFilesPageOnly(existingPaths, mergedPdfFilename);
        }
    }

    private static byte[] mergePdfFiles(List<Path> paths, String mergedPdfFilename, DocumentMergeMode mergeMode) throws IOException {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        pdfMerger.setDocumentMergeMode(mergeMode);
        for (Path path : paths) {
            pdfMerger.addSource(path.toFile());
        }

        PDDocumentInformation documentInformation = new PDDocumentInformation();
        documentInformation.setTitle(mergedPdfFilename);
        pdfMerger.setDestinationDocumentInformation(documentInformation);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(null);
            return outputStream.toByteArray();
        }
    }

    private static Optional<byte[]> mergePdfFilesPageOnly(List<Path> paths, String mergedPdfFilename) {
        try {
            int expectedPageCount = getPdfPageCount(paths);
            byte[] mergedPdf = mergePdfFiles(paths, mergedPdfFilename, DocumentMergeMode.OPTIMIZE_RESOURCES_MODE);
            if (!hasExpectedPageCount(mergedPdf, expectedPageCount)) {
                log.error("Page-only merge of PDF files {} did not produce the expected {} pages", paths, expectedPageCount);
                return Optional.empty();
            }

            byte[] mergedPdfWithTitle = setPdfTitleAndRemoveStructureParentReferences(mergedPdf, mergedPdfFilename);
            if (!hasExpectedPageCount(mergedPdfWithTitle, expectedPageCount)) {
                log.error("Adding the title to the page-only merge of PDF files {} changed the expected {} pages", paths, expectedPageCount);
                return Optional.empty();
            }
            return Optional.of(mergedPdfWithTitle);
        }
        catch (IOException fallbackMergeException) {
            log.error("Could not merge PDF files {} with the page-only fallback", paths, fallbackMergeException);
            return Optional.empty();
        }
    }

    private static int getPdfPageCount(List<Path> paths) throws IOException {
        int pageCount = 0;
        for (Path path : paths) {
            try (PDDocument document = Loader.loadPDF(path.toFile())) {
                pageCount += document.getNumberOfPages();
            }
        }
        return pageCount;
    }

    private static boolean hasExpectedPageCount(byte[] pdf, int expectedPageCount) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages() == expectedPageCount;
        }
    }

    private static byte[] setPdfTitleAndRemoveStructureParentReferences(byte[] pdf, String title) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(title);
            Set<COSStream> visitedXObjectStreams = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var page : document.getPages()) {
                page.getCOSObject().removeItem(COSName.STRUCT_PARENTS);
                for (var annotation : page.getAnnotations()) {
                    annotation.getCOSObject().removeItem(COSName.STRUCT_PARENT);
                    removeStructureParentReferences(annotation.getAppearance(), visitedXObjectStreams);
                }
                removeStructureParentReferences(page.getResources(), visitedXObjectStreams);
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void removeStructureParentReferences(PDAppearanceDictionary appearance, Set<COSStream> visitedXObjectStreams) throws IOException {
        if (appearance == null) {
            return;
        }

        removeStructureParentReferences(appearance.getNormalAppearance(), visitedXObjectStreams);
        removeStructureParentReferences(appearance.getRolloverAppearance(), visitedXObjectStreams);
        removeStructureParentReferences(appearance.getDownAppearance(), visitedXObjectStreams);
    }

    private static void removeStructureParentReferences(PDAppearanceEntry appearanceEntry, Set<COSStream> visitedXObjectStreams) throws IOException {
        if (appearanceEntry == null) {
            return;
        }

        if (appearanceEntry.isStream()) {
            removeStructureParentReferences(appearanceEntry.getAppearanceStream(), visitedXObjectStreams);
        }
        else if (appearanceEntry.isSubDictionary()) {
            for (var appearanceStream : appearanceEntry.getSubDictionary().values()) {
                removeStructureParentReferences(appearanceStream, visitedXObjectStreams);
            }
        }
    }

    private static void removeStructureParentReferences(PDResources resources, Set<COSStream> visitedXObjectStreams) throws IOException {
        if (resources == null) {
            return;
        }

        for (COSName xObjectName : resources.getXObjectNames()) {
            try {
                removeStructureParentReferences(resources.getXObject(xObjectName), visitedXObjectStreams);
            }
            catch (IOException unreadableXObjectException) {
                log.debug("Skipping unreadable XObject {} while removing structure parent references", xObjectName, unreadableXObjectException);
            }
        }
    }

    private static void removeStructureParentReferences(PDXObject xObject, Set<COSStream> visitedXObjectStreams) throws IOException {
        if (xObject == null || !visitedXObjectStreams.add(xObject.getCOSObject())) {
            return;
        }

        xObject.getCOSObject().removeItem(COSName.STRUCT_PARENT);
        if (xObject instanceof PDFormXObject form) {
            form.getCOSObject().removeItem(COSName.STRUCT_PARENTS);
            removeStructureParentReferences(form.getResources(), visitedXObjectStreams);
        }
    }
}
