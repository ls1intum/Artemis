import { Component, OnInit, inject, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CodeButtonPopoverContentComponent, NonEmptyArray } from './code-button-popover-content.component';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';

interface CodeButtonData {
    authenticationMechanisms: NonEmptyArray<RepositoryAuthenticationMethod>;
}

type CodeButtonDataState = { status: 'loading' } | { status: 'loaded'; data: CodeButtonData } | { status: 'error' };

@Component({
    selector: 'jhi-code-button-new',
    templateUrl: './code-button-new.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonNewComponent implements OnInit {
    private readonly profileService = inject(ProfileService);

    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;
    protected readonly dataState = signal<CodeButtonDataState>({ status: 'loading' });

    ngOnInit(): void {
        const authenticationMechanismStrings = this.profileService.getProfileInfo().repositoryAuthenticationMechanisms ?? [];
        const authenticationMechanisms = authenticationMechanismStrings.filter((method): method is RepositoryAuthenticationMethod =>
            Object.values(RepositoryAuthenticationMethod).includes(method as RepositoryAuthenticationMethod),
        );
        if (authenticationMechanisms.length > 0) {
            const authenticationMechanismTuple: NonEmptyArray<RepositoryAuthenticationMethod> = [authenticationMechanisms[0], ...authenticationMechanisms.slice(1)];
            this.dataState.set({
                status: 'loaded',
                data: {
                    authenticationMechanisms: authenticationMechanismTuple,
                },
            });
        } else {
            this.dataState.set({ status: 'error' });
        }
    }
}
