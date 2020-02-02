import { Directive, HostListener, Input, OnInit } from '@angular/core';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { take, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';
import { createBuildPlanUrl } from 'app/entities/programming-exercise/utils/build-plan-link.directive';

@Directive({ selector: 'button[jhiBuildPlanButton], jhi-button[jhiBuildPlanButton]' })
export class BuildPlanButtonDirective implements OnInit {
    private participationBuildPlanId: string;
    private exerciseProjectKey: string;
    private linkToBuildPlan: string | null;
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
        if (this.linkToBuildPlan) {
            window.open(this.linkToBuildPlan);
        }
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
}
