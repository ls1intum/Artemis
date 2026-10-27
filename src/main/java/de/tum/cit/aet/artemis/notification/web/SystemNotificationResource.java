package de.tum.cit.aet.artemis.notification.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.web.util.PaginationUtil;
import de.tum.cit.aet.artemis.core.web.util.ResponseUtil;
import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationDTO;
import de.tum.cit.aet.artemis.notification.repository.SystemNotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST controller for managing SystemNotification.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.SYSTEM_NOTIFICATIONS)
@RestController
@RequestMapping("api/notification/")
public class SystemNotificationResource {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationResource.class);

    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationResource(SystemNotificationRepository systemNotificationRepository) {
        this.systemNotificationRepository = systemNotificationRepository;
    }

    /**
     * GET /system-notifications : get all system notifications for administration purposes.
     *
     * @param pageable instance of the pageable interface to enable paging
     * @return the list of system notifications
     */
    @Operation(operationId = "getAllSystemNotifications")
    @ApiResponse(responseCode = "200", description = "System notifications for the requested page", headers = {
            @Header(name = "Link", description = "Pagination links", schema = @Schema(type = "string")),
            @Header(name = "X-Total-Count", description = "Total number of system notifications", schema = @Schema(type = "integer", format = "int64")) })
    @GetMapping("system-notifications")
    @EnforceAtLeastTutor
    public ResponseEntity<List<SystemNotificationDTO>> getAllSystemNotifications(@ParameterObject Pageable pageable) {
        log.debug("REST request to get all SystemNotifications");
        final Page<SystemNotification> page = systemNotificationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent().stream().map(SystemNotificationDTO::from).toList(), headers, HttpStatus.OK);
    }

    /**
     * GET /system-notifications/:notificationId : get the "id" system notification.
     *
     * @param notificationId the id of the system notification to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @Operation(operationId = "getSystemNotification")
    @ApiResponse(responseCode = "200", description = "The requested system notification")
    @ApiResponse(responseCode = "404", description = "System notification not found", content = @Content)
    @GetMapping("system-notifications/{notificationId}")
    @EnforceAtLeastEditor
    public ResponseEntity<SystemNotificationDTO> getSystemNotification(@PathVariable Long notificationId) {
        log.debug("REST request to get SystemNotification : {}", notificationId);
        Optional<SystemNotificationDTO> systemNotification = systemNotificationRepository.findById(notificationId).map(SystemNotificationDTO::from);
        return ResponseUtil.wrapOrNotFound(systemNotification);
    }
}
