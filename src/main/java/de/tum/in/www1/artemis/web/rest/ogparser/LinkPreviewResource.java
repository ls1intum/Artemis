package de.tum.in.www1.artemis.web.rest.ogparser;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.linkpreview.LinkPreviewService;
import de.tum.in.www1.artemis.web.rest.dto.LinkPreviewDTO;

/**
 * REST controller for Link Preview.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LinkPreviewResource {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewResource.class);

    private final LinkPreviewService linkPreviewService;

    public LinkPreviewResource(LinkPreviewService linkPreviewService) {
        this.linkPreviewService = linkPreviewService;
    }

    /**
     * POST /link-preview : link preview for given url.
     *
     * @param url the url to parse
     * @return the LinkPreviewDTO containing the meta information
     */
    @PostMapping("link-preview")
    @EnforceAtLeastStudent
    @Cacheable(value = "linkPreview", key = "#url", unless = "#result == null")
    public LinkPreviewDTO getLinkPreview(@RequestBody String url) {
        log.debug("REST request to get link preview for url: {}", url);
        return linkPreviewService.getLinkPreview(url);
    }
}
