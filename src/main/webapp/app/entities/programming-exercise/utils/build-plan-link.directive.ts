import { Directive, ElementRef, HostBinding, Input, OnInit, Renderer2 } from '@angular/core';
import { take, tap } from 'rxjs/operators';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/layouts';

export const createBuildPlanUrl = (template: string, projectKey: string, buildPlanId: string) => {
    return template.replace('{buildPlanId}', buildPlanId).replace('{projectKey}', projectKey);
};

@Directive({ selector: 'a[jhiBuildPlanLink]' })
export class BuildPlanLinkDirective implements OnInit {
    @Input() projectKey: string;
    @Input() buildPlanId: string;

    @HostBinding('attr.href')
    linkTemplate: string;
    @HostBinding('attr.target')
    readonly target = '_blank';
    @HostBinding('attr.rel')
    readonly rel = 'noopener noreferrer';

    constructor(private profileService: ProfileService, private el: ElementRef, private renderer: Renderer2) {}

    ngOnInit(): void {
        this.profileService
            .getProfileInfo()
            .pipe(
                take(1),
                tap((info: ProfileInfo) => {
                    this.linkTemplate = createBuildPlanUrl(info.buildPlanURLTemplate, this.projectKey, this.buildPlanId);
                }),
            )
            .subscribe();
    }
}
