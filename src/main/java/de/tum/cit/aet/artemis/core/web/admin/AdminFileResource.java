package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.FileUtil;

/**
 * REST controller for files that administrators upload outside of a course.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage(UserFeature.MARKDOWN_UPLOADS)
@RestController
@RequestMapping("api/core/admin/")
public class AdminFileResource {

    private static final Logger log = LoggerFactory.getLogger(AdminFileResource.class);

    /**
     * POST /admin/markdown-file-upload : Upload a new file for markdown that does not belong to a course, such as for the legal documents.
     * The editors of course content upload through {@code POST /markdown-file-upload}, which checks the role in the course.
     *
     * @param file         The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     */
    @PostMapping("markdown-file-upload")
    public ResponseEntity<String> saveMarkdownFile(@RequestParam(value = "file") MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFileName)
            throws URISyntaxException {
        log.debug("REST request to upload file for markdown outside of a course: {}", file.getOriginalFilename());
        String publicPath = FileUtil.handleSaveFile(file, keepFileName, true).toString();
        // fail-safe to raise awareness if the public path is not correct (should not happen)
        if (publicPath.startsWith(ARTEMIS_FILE_PATH_PREFIX)) {
            throw new IllegalArgumentException("The public path should not contain the Artemis file path prefix");
        }
        String responsePath = ARTEMIS_FILE_PATH_PREFIX + publicPath;

        // return path for getting the file
        String responseBody = "{\"path\":\"" + responsePath + "\"}";

        return ResponseEntity.created(new URI(responsePath)).body(responseBody);
    }
}
