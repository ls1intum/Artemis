import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faEdit, faFileImport, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { outputToObservable, toSignal } from '@angular/core/rxjs-interop';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { CourseCompetency, CourseCompetencyType, getIcon } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { lastValueFrom } from 'rxjs';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-course-competencies-management-table',
    standalone: true,
    imports: [RouterLink, ArtemisMarkdownModule, ArtemisSharedModule, ArtemisSharedComponentModule],
    templateUrl: './course-competencies-management-table.component.html',
    styleUrl: './course-competencies-management-table.component.scss',
})
export class CourseCompetenciesManagementTableComponent {
    protected readonly getIcon = getIcon;

    protected readonly faRobot = faRobot;
    protected readonly faTrash = faTrash;
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;

    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);
    protected readonly courseCompetencyApiService = inject(CourseCompetencyApiService);

    readonly courseId = input.required<number>();
    readonly courseCompetencies = input.required<CourseCompetency[]>();
    readonly courseCompetencyType = input.required<CourseCompetencyType>();
    readonly standardizedCompetenciesEnabled = input.required<boolean>();

    readonly onCourseCompetencyDeletion = output<number>();
    readonly onCourseCompetenciesImport = output<CourseCompetency[]>();

    readonly dialogErrorSource = output<string>();
    readonly dialogError = outputToObservable(this.dialogErrorSource);

    private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
    private readonly irisCombinedSettings = signal<IrisCourseSettings | undefined>(undefined);
    private readonly irisEnabled = computed(() => this.profileInfo()?.activeProfiles.includes(PROFILE_IRIS) ?? false);
    readonly irisCompetencyGenerationEnabled = computed(() => {
        return this.irisEnabled() && (this.irisCombinedSettings()?.irisCompetencyGenerationSettings?.enabled ?? false);
    });

    constructor() {
        effect(() => this.loadIrisSettings(this.courseId()), { allowSignalWrites: true });
    }

    private async loadIrisSettings(courseId: number): Promise<void> {
        if (this.irisEnabled()) {
            try {
                const irisCombinedSettings = await lastValueFrom(this.irisSettingsService.getCombinedCourseSettings(courseId));
                this.irisCombinedSettings.set(irisCombinedSettings);
            } catch (error) {
                onError(this.alertService, error);
            }
        }
    }

    protected async deleteCourseCompetency(courseCompetencyId: number): Promise<void> {
        try {
            await this.courseCompetencyApiService.deleteCourseCompetency(this.courseId(), courseCompetencyId);
            this.dialogErrorSource.emit('');
            this.onCourseCompetencyDeletion.emit(courseCompetencyId);
        } catch (error) {
            this.dialogErrorSource.emit(error.message);
        }
    }

    protected async openImportAllModal(courseCompetencyType: string): Promise<void> {
        const modal = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        //unary operator is necessary as otherwise courseId is seen as a string and will not match.
        modal.componentInstance.disabledIds = [+this.courseId()];
        modal.componentInstance.competencyType = courseCompetencyType;
        const result = await modal.result;
        await this.importAllCourseCompetencies(result as ImportAllFromCourseResult);
    }

    private async importAllCourseCompetencies(result: ImportAllFromCourseResult): Promise<void> {
        const courseTitle = result.courseForImportDTO.title ?? '';
        try {
            const importedCompetenciesWithTailRelation = await this.courseCompetencyApiService.importAll(this.courseId(), result.courseForImportDTO.id!, result.importRelations);
            const importedCompetencies = importedCompetenciesWithTailRelation.map((dto) => dto.competency).filter((element): element is CourseCompetency => !!element);
            if (importedCompetencies.length > 0) {
                this.alertService.success(`artemisApp.${this.courseCompetencyType()}.importAll.success`, {
                    noOfCourseCompetencies: importedCompetencies.length,
                    courseTitle: courseTitle,
                });
                this.onCourseCompetenciesImport.emit(importedCompetencies);
            } else {
                this.alertService.warning(`artemisApp.${this.courseCompetencyType()}.importAll.warning`, { courseTitle: courseTitle });
            }
        } catch (error) {
            this.alertService.error(error);
        }
    }
}
