import { Directive, HostListener, Input, OnInit } from '@angular/core';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { take, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';
import { createBuildPlanUrl } from 'app/entities/programming-exercise/utils/build-plan-link.directive';

@Directive({ selector: 'button[jhiBuildPlanButton], jhi-button[jhiBuildPlanButton]' })
export class BuildPlanButtonDirective implements OnInit {
    @Input() projectKey: string;
    @Input() buildPlanId: string;

    private linkToBuildPlan: string;

    constructor(private profileService: ProfileService) {}

    ngOnInit(): void {
        this.profileService
            .getProfileInfo()
            .pipe(
                take(1),
                tap((info: ProfileInfo) => {
                    this.linkToBuildPlan = createBuildPlanUrl(info.buildPlanURLTemplate, this.projectKey, this.buildPlanId);
                }),
            )
            .subscribe();
    }

    @HostListener('click')
    onClick() {
        window.open(this.linkToBuildPlan);
    }
}
