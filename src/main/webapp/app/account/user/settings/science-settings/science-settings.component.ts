import { Component, OnInit, inject, signal } from '@angular/core';
import { faInfoCircle, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceCourseConsent, ScienceSettingsService, isScienceCourseConsent } from 'app/account/user/settings/science-settings/science-settings.service';
import { Setting, UserSettingsStructure } from 'app/account/user/settings/user-settings.model';

@Component({
    selector: 'jhi-science-settings',
    templateUrl: 'science-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [FaIconComponent],
})
export class ScienceSettingsComponent implements OnInit {
    private readonly scienceSettingsService = inject(ScienceSettingsService);
    private readonly alertService = inject(AlertService);

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
        this.scienceSettingsService.getScienceSettingsUpdates().subscribe((consents) => {
            this.consents.set(consents.filter(isScienceCourseConsent));
        });
        this.scienceSettingsService.refreshScienceSettings();
        this.loading.set(false);
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
        const confirmed = window.confirm(`Delete your science interaction data for ${consent.courseTitle ?? 'this course'}? Consent audit events will be retained.`);
        if (!confirmed) {
            return;
        }
        this.scienceSettingsService.deleteScienceDataForCourse(consent.courseId).subscribe({
            next: () => this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'),
            error: () => this.alertService.error('error.unexpectedError'),
        });
    }
}
