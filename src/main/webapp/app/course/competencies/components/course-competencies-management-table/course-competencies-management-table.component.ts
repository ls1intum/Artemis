import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faEdit, faFileImport, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CourseCompetency, CourseCompetencyStudentProgressDTO, CourseCompetencyType, getIcon } from 'app/entities/competency.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { outputToObservable, toSignal } from '@angular/core/rxjs-interop';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { lastValueFrom } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-course-competencies-management-table',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, FontAwesomeModule, TranslateDirective, ArtemisSharedModule, ArtemisMarkdownModule, CommonModule],
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
    private readonly competencyService = inject(CompetencyService);
    private readonly prerequisiteService = inject(PrerequisiteService);
    private service: CompetencyService | PrerequisiteService;

    readonly courseId = input.required<number>();
    readonly courseCompetencies = input.required<CourseCompetencyStudentProgressDTO[]>();
    readonly courseCompetencyType = input.required<CourseCompetencyType>();
    readonly standardizedCompetenciesEnabled = input.required<boolean>();

    readonly onCourseCompetencyDeletion = output<number>();
    readonly onCourseCompetenciesImport = output<CourseCompetencyStudentProgressDTO[]>();

    readonly dialogErrorSource = output<string>();
    readonly dialogError = outputToObservable(this.dialogErrorSource);

    private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
    private readonly irisCombinedSettings = signal<IrisCourseSettings | undefined>(undefined);
    private readonly irisEnabled = computed(() => this.profileInfo()?.activeProfiles.includes(PROFILE_IRIS) ?? false);
    readonly irisCompetencyGenerationEnabled = computed(() => {
        return this.irisEnabled() && (this.irisCombinedSettings()?.irisCompetencyGenerationSettings?.enabled ?? false);
    });

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadIrisSettings(courseId));
        });
        effect(() => {
            if (this.courseCompetencyType() === CourseCompetencyType.COMPETENCY) {
                this.service = this.competencyService;
            } else {
                this.service = this.prerequisiteService;
            }
        });
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
            await lastValueFrom(this.service.delete(courseCompetencyId, this.courseId()));
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
            const response = await lastValueFrom(this.service.importAll(this.courseId(), result.courseForImportDTO.id!, result.importRelations));
            const importedCompetenciesWithTailRelation = response.body ?? [];
            const importedCompetencies = importedCompetenciesWithTailRelation
                .map((dto) => dto.competency)
                .filter((element): element is CourseCompetency => !!element)
                .map(
                    (courseCompetency) =>
                        <CourseCompetencyStudentProgressDTO>{
                            id: courseCompetency.id,
                            title: courseCompetency.title,
                            description: courseCompetency.description,
                            numberOfMasteredStudents: 0,
                            numberOfStudents: 0,
                            optional: courseCompetency.optional,
                            softDueDate: courseCompetency.softDueDate,
                            taxonomy: courseCompetency.taxonomy,
                            type: courseCompetency.type,
                        },
                );
            if (importedCompetencies.length > 0) {
                this.alertService.success(`artemisApp.${this.courseCompetencyType()}.importAll.success`, {
                    noOfCompetencies: importedCompetencies.length,
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
