import { Directive, HostBinding, HostListener, Input, OnInit } from '@angular/core';
import { take, tap } from 'rxjs/operators';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/layouts/profiles/profile-info.model';

/**
 * Creates the build pan URL for the given template URL.
 *
 * @param template The URL to the CI with placeholders for the plan ID and project key
 * @param projectKey The project key of the programming exercise
 * @param buildPlanId The ID of the build plan for which to construct the URL
 */
export const createBuildPlanUrl = (template: string, projectKey: string, buildPlanId: string): string | null => {
    if (template && projectKey && buildPlanId) {
        return template.replace('{buildPlanId}', buildPlanId).replace('{projectKey}', projectKey);
    }

    return null;
};

@Directive({ selector: 'a[jhiBuildPlanLink]' })
export class BuildPlanLinkDirective implements OnInit {
    @HostBinding('attr.href')
    readonly href = '';

    private participationBuildPlanId: string;
    private exerciseProjectKey: string;
    private templateLink: string;
    private linkToBuildPlan: string | null;

    constructor(private profileService: ProfileService) {}

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

    @HostListener('click', ['$event'])
    openBuildPlanLink($event: any) {
        $event.preventDefault();
        if (this.linkToBuildPlan) {
            window.open(this.linkToBuildPlan);
        }
    }
}
