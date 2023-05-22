package de.tum.in.www1.artemis.web.rest.ogparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.linkpreview.LinkPreviewService;

/**
 * REST controller for Link Preview.
 */
@RestController
@RequestMapping("/api")
public class LinkPreviewResource {

    private final Logger log = LoggerFactory.getLogger(LinkPreviewResource.class);

    private final LinkPreviewService linkPreviewService;

    public LinkPreviewResource(LinkPreviewService linkPreviewService) {
        this.linkPreviewService = linkPreviewService;
    }

    @GetMapping("/link-preview")
    @PreAuthorize("hasRole('User')")
    public String getLinkPreview(@RequestBody String url) {
        log.debug("REST request to get link preview for url: {}", url);
        return linkPreviewService.getLinkPreview(url);
    }
}
