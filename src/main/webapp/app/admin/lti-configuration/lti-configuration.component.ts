import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { faExclamationTriangle, faPencilAlt, faSort, faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';
import { LtiConfigurationService } from 'app/admin/lti-configuration/lti-configuration.service';
import { SortService } from 'app/shared/service/sort.service';
import { Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

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
    faPencilAlt = faPencilAlt;
    faTrash = faTrash;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private router: Router,
        private ltiConfigurationService: LtiConfigurationService,
        private sortService: SortService,
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

    sortRows() {
        this.sortService.sortByProperty(this.platforms, this.predicate, this.reverse);
    }

    deleteLtiPlatform(platformId: number): void {
        this.ltiConfigurationService.deleteLtiPlatform(platformId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.router.navigate(['admin', 'lti-configuration']);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
