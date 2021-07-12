import { Directive, HostBinding, HostListener, Input, OnInit } from '@angular/core';
import { take, tap } from 'rxjs/operators';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

/**
 * Creates the build pan URL for the given template URL.
 *
 * @param template The URL to the CI with placeholders for the plan ID and project key
 * @param projectKey The project key of the programming exercise
 * @param buildPlanId The ID of the build plan for which to construct the URL
 */
export const createBuildPlanUrl = (template: string, projectKey: string, buildPlanId: string) => {
    if (template && projectKey && buildPlanId) {
        return template.replace('{buildPlanId}', buildPlanId).replace('{projectKey}', projectKey);
    }
};

@Directive({ selector: 'a[jhiBuildPlanLink]' })
export class BuildPlanLinkDirective implements OnInit {
    @HostBinding('attr.href')
    readonly href = '';

    private participationBuildPlanId: string;
    private exerciseProjectKey: string;
    private templateLink: string;
    private linkToBuildPlan?: string;

    constructor(private profileService: ProfileService) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        this.profileService
            .getProfileInfo()
            .pipe(
                take(1),
                tap((info: ProfileInfo) => {
                    this.templateLink = info.buildPlanURLTemplate;
                    this.linkToBuildPlan = createBuildPlanUrl(info.buildPlanURLTemplate, this.exerciseProjectKey, this.participationBuildPlanId);
                }),
            )
            .subscribe();
    }

    @Input()
    set projectKey(key: string) {
        this.exerciseProjectKey = key;
        this.linkToBuildPlan = createBuildPlanUrl(this.templateLink, this.exerciseProjectKey, this.participationBuildPlanId);
    }

    @Input()
    set buildPlanId(planId: string) {
        this.participationBuildPlanId = planId;
        this.linkToBuildPlan = createBuildPlanUrl(this.templateLink, this.exerciseProjectKey, this.participationBuildPlanId);
    }

    /**
     * Opens build plan link window.
     */
    @HostListener('click', ['$event'])
    openBuildPlanLink(event: any) {
        event.preventDefault();
        if (this.linkToBuildPlan) {
            window.open(this.linkToBuildPlan);
        }
    }
}
