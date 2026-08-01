import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { faInfoCircle, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceCourseConsent, ScienceSettingsService, isScienceCourseConsent } from 'app/account/user/settings/science-settings/science-settings.service';
import { Setting, UserSettingsStructure } from 'app/account/user/settings/user-settings.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiToggleSwitchComponent } from 'app/shared-ui/tum-ui/toggle-switch/tum-ui-toggle-switch.component';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss', 'science-settings.component.scss'],
    imports: [FormsModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiButtonDirective, TumUiMessageComponent, TumUiToggleSwitchComponent],
})
export class ScienceSettingsComponent implements OnInit {
    private readonly destroyRef = inject(DestroyRef);
    private readonly scienceSettingsService = inject(ScienceSettingsService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    readonly consents = signal<ScienceCourseConsent[]>([]);
    readonly userSettings = signal<UserSettingsStructure<Setting> | undefined>(undefined);
    readonly settings = signal<Setting[]>([]);
    readonly loading = signal(false);

    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        this.loadConsents();
    }

    loadConsents(): void {
        this.loading.set(true);
        this.scienceSettingsService
            .getScienceSettingsUpdates()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((consents) => {
                this.consents.set(consents.filter(isScienceCourseConsent));
            });
        this.scienceSettingsService
            .refreshScienceSettings()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => this.loading.set(false),
                error: () => {
                    this.loading.set(false);
                    this.alertService.error('error.unexpectedError');
                },
            });
    }

    toggleConsent(consent: ScienceCourseConsent): void {
        this.scienceSettingsService.saveConsentForCourse(consent.courseId, !consent.active).subscribe({
            next: () => this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'),
            error: () => this.alertService.error('error.unexpectedError'),
        });
    }

    toggleSetting(event: MouseEvent): void {
        const settingId = (event.currentTarget as HTMLElement | undefined)?.id;
        const setting = this.settings().find((candidate) => candidate.settingId === settingId);
        if (setting) {
            setting.active = !setting.active;
        }
    }

    deleteData(consent: ScienceCourseConsent): void {
        const confirmed = window.confirm(
            this.translateService.instant('artemisApp.userSettings.scienceSettingsPage.deleteDataQuestion', {
                courseTitle: consent.courseTitle ?? this.translateService.instant('artemisApp.userSettings.scienceSettingsPage.thisCourse'),
            }),
        );
        if (!confirmed) {
            return;
        }
        this.scienceSettingsService.deleteScienceDataForCourse(consent.courseId).subscribe({
            next: () => this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'),
            error: () => this.alertService.error('error.unexpectedError'),
        });
    }
}
