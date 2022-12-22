package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.util.Tuple;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitsDTO;

@Service
public class LectureUnitProcessingService {

    private final Logger log = LoggerFactory.getLogger(FileService.class);

    public List<LectureUnitsDTO> splitUnits(List<LectureUnitSplitDTO> lectureUnitSplitDTOs, MultipartFile file) throws IOException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<LectureUnitsDTO> units = new ArrayList<>();
        Splitter pdfSplitter = new Splitter();
        PDDocument document = PDDocument.load(file.getBytes());
        List<PDDocument> documentUnits;

        for (LectureUnitSplitDTO lectureUnit : lectureUnitSplitDTOs) {
            AttachmentUnit attachmentUnit = new AttachmentUnit();
            Attachment attachment = new Attachment();
            LectureUnitsDTO lectureUnitsDTO = new LectureUnitsDTO();
            PDDocumentInformation pdDocumentInformation = new PDDocumentInformation();

            pdfSplitter.setStartPage(Integer.parseInt(lectureUnit.startPage));
            pdfSplitter.setEndPage(Integer.parseInt(lectureUnit.endPage));
            pdfSplitter.setSplitAtPage(Integer.parseInt(lectureUnit.endPage));

            documentUnits = pdfSplitter.split(document);
            pdDocumentInformation.setTitle(lectureUnit.getUnitName());
            documentUnits.get(0).setDocumentInformation(pdDocumentInformation);
            documentUnits.get(0).save(outputStream);

            // setup attachmentUnit and attachment
            attachmentUnit.setDescription("");
            attachment.setName(lectureUnit.getUnitName());
            attachment.setAttachmentType(AttachmentType.FILE);
            attachment.setVersion(1);
            attachment.setReleaseDate(lectureUnit.getReleaseDate());
            attachment.setUploadDate(ZonedDateTime.now());

            lectureUnitsDTO.setAttachmentUnit(attachmentUnit);
            lectureUnitsDTO.setAttachment(attachment);

            // prepare file to be set from byte[] to MultipartFile by using CommonsMultipartFile
            String tempDirectory = System.getProperty("java.io.tmpdir");

            File tempFile = new File(tempDirectory, lectureUnit.getUnitName() + ".pdf");
            Files.write(tempFile.toPath(), outputStream.toByteArray());

            File outputFile = new File(tempFile.toString());
            FileItem fileItem = new DiskFileItem("mainUnitFile", Files.probeContentType(outputFile.toPath()), false, outputFile.getName(), (int) outputFile.length(),
                    outputFile.getParentFile());

            InputStream input = new FileInputStream(outputFile);
            OutputStream os = fileItem.getOutputStream();
            IOUtils.copy(input, os);
            MultipartFile multipartFile = new CommonsMultipartFile(fileItem);
            lectureUnitsDTO.setFile(multipartFile);

            units.add(lectureUnitsDTO);
            tempFile.deleteOnExit();
            documentUnits.get(0).close();
        }

        document.close();
        return units;
    }

    public Optional<List<LectureUnitSplitDTO>> getSplitUnitData(MultipartFile file) throws IOException {

        List<LectureUnitSplitDTO> units = new ArrayList<>();

        PDDocument document = PDDocument.load(file.getBytes());
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> unitsDocumentMap = separateIntoUnits(document);

        unitsDocumentMap.forEach((k, v) -> {
            LectureUnitSplitDTO newLectureUnit = new LectureUnitSplitDTO();
            newLectureUnit.setUnitName(v.x());
            newLectureUnit.setStartPage(v.y().x().toString());
            newLectureUnit.setEndPage(v.y().y().toString());
            units.add(newLectureUnit);
        });

        document.close();
        return Optional.of(units);
    }

    private Map<Integer, Tuple<String, Tuple<Integer, Integer>>> separateIntoUnits(PDDocument document) throws IOException {
        Map<Integer, Tuple<String, Tuple<Integer, Integer>>> outlineMap = new HashMap<>();

        Splitter pdfSplitter = new Splitter();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        List<PDDocument> Pages = pdfSplitter.split(document);

        Iterator<PDDocument> iterator = Pages.listIterator();

        int outlineCount = 0;
        boolean breakFlag = false;
        int index = 1;
        while (iterator.hasNext()) {
            PDDocument pd = iterator.next();
            String slideText = pdfStripper.getText(pd);

            if (slideText.contains("Outline")) {
                outlineCount++;

                String[] lines = slideText.split("\r\n|\r|\n");

                outlineMap.put(outlineCount, new Tuple<>(lines[outlineCount + 1], new Tuple<>((outlineCount == 1) ? 1 : index, document.getNumberOfPages())));
                if (outlineCount > 1) {
                    int previousOutlineCount = outlineCount - 1;
                    int previousStart = outlineMap.get(previousOutlineCount).y().x();
                    outlineMap.remove(previousOutlineCount);
                    outlineMap.put(previousOutlineCount, new Tuple<>(lines[previousOutlineCount + 1], new Tuple<>(previousStart, index - 2)));
                }
            }
            index++;
        }
        return outlineMap;
    }
}
