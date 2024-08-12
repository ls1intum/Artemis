import { Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faEdit, faFileImport, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { outputToObservable, toSignal } from '@angular/core/rxjs-interop';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { ImportCourseCompetenciesDirective } from 'app/course/competencies/components/import-course-competencies-directive/import-course-competencies.directive';
import { CourseCompetencyType, getIcon } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-course-competencies-management-table',
    standalone: true,
    imports: [ArtemisSharedCommonModule, RouterLink, ArtemisMarkdownModule, ArtemisSharedModule],
    templateUrl: './course-competencies-management-table.component.html',
    styleUrl: './course-competencies-management-table.component.scss',
})
export class CourseCompetenciesManagementTableComponent extends ImportCourseCompetenciesDirective {
    protected readonly getIcon = getIcon;

    protected readonly faRobot = faRobot;
    protected readonly faTrash = faTrash;
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;

    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);

    readonly competencyType = input.required<CourseCompetencyType>();
    readonly standardizedCompetenciesEnabled = input.required<boolean>();

    readonly dialogErrorSource = output<string>();
    readonly dialogError = outputToObservable(this.dialogErrorSource);

    private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
    private readonly irisCombinedCourseSettings = signal<IrisCourseSettings | undefined>(undefined);
    readonly irisCompetencyGenerationEnabled = computed(() => {
        return (this.profileInfo()?.activeProfiles.includes(PROFILE_IRIS) ?? false) && (this.irisCombinedCourseSettings()?.irisCompetencyGenerationSettings?.enabled ?? false);
    });

    protected async deleteCourseCompetency(courseCompetencyId: number): Promise<void> {
        try {
            await this.courseCompetencyApiService.deleteCourseCompetency(this.courseId(), courseCompetencyId);
            this.dialogErrorSource.emit('');
            this.courseCompetencies.update((courseCompetencies) => courseCompetencies.filter((courseCompetency) => courseCompetency.id !== courseCompetencyId));
            this.relations.update((relations) => {
                return relations.filter((relation) => relation.headCompetencyId !== courseCompetencyId && relation.tailCompetencyId !== courseCompetencyId);
            });
        } catch (error) {
            this.dialogErrorSource.emit(error.message);
        }
    }
}
