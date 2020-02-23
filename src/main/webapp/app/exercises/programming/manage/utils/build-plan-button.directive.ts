import { Directive, HostBinding, HostListener, Input, OnInit } from '@angular/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { take, tap } from 'rxjs/operators';
import { createBuildPlanUrl } from 'app/exercises/programming/manage/utils/build-plan-link.directive';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

@Directive({ selector: 'button[jhiBuildPlanButton], jhi-button[jhiBuildPlanButton]' })
export class BuildPlanButtonDirective implements OnInit {
    @HostBinding('style.visibility')
    visibility = 'hidden';

    private participationBuildPlanId: string;
    private exerciseProjectKey: string;
    private buildPlanLink: string | null;
    private templateLink: string;

    constructor(private profileService: ProfileService) {}

    ngOnInit(): void {
        this.profileService
            .getProfileInfo()
            .pipe(
                take(1),
                tap((info: ProfileInfo) => {
                    this.templateLink = info.buildPlanURLTemplate;
                    this.linkToBuildPlan = createBuildPlanUrl(this.templateLink, this.exerciseProjectKey, this.participationBuildPlanId);
                }),
            )
            .subscribe();
    }

    @HostListener('click')
    onClick() {
        window.open(this.buildPlanLink!);
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

    set linkToBuildPlan(link: string | null) {
        this.buildPlanLink = link;
        this.visibility = link ? 'visible' : 'hidden';
    }
}
