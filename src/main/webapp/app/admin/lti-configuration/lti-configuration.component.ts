import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { faExclamationTriangle, faSort, faWrench } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lit-configuration.service';

@Component({
    selector: 'jhi-lti-configuration',
    templateUrl: './lti-configuration.component.html',
})
export class LtiConfigurationComponent implements OnInit {
    course: Course;
    platforms: LtiPlatformConfiguration[];

    activeTab = 1;

    predicate = 'type';
    reverse = false;

    // Icons
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faWrench = faWrench;

    constructor(
        private route: ActivatedRoute,
        private ltiConfigurationService: LtiConfigurationService,
    ) {}

    /**
     * Gets the configuration for the course encoded in the route and fetches the exercises
     */
    ngOnInit() {
        this.ltiConfigurationService.findAll().subscribe((configuredLtiPlatforms) => {
            if (configuredLtiPlatforms) {
                this.platforms = configuredLtiPlatforms;
            }
        });
    }

    /**
     * Gets the dynamic registration url
     */
    getDynamicRegistrationUrl(): string {
        return `${location.origin}/lti/dynamic-registration`; // Needs to match url in lti.route
    }

    /**
     * Gets the deep linking url
     */
    getDeepLinkingUrl(): string {
        return `${location.origin}/api/public/lti13/deep-linking`; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the tool url
     */
    getToolUrl(): string {
        return `${location.origin}/courses`; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the keyset url
     */
    getKeysetUrl(): string {
        return `${location.origin}/.well-known/jwks.json`; // Needs to match url in CustomLti13Configurer
    }

    /**
     * Gets the initiate login url
     */
    getInitiateLoginUrl(): string {
        return `${location.origin}/api/public/lti13/initiate-login`; // Needs to match uri in CustomLti13Configurer
    }

    /**
     * Gets the redirect uri
     */
    getRedirectUri(): string {
        return `${location.origin}/api/public/lti13/auth-callback`; // Needs to match uri in CustomLti13Configurer
    }
}
