import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { faRobot } from '@fortawesome/free-solid-svg-icons';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { Competency, CompetencyRelationDTO } from 'app/entities/competency.model';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { CourseCompetenciesTableDirective } from 'app/course/competencies/components/course-competencies-table/course-competencies-table.directive';

@Component({
    selector: 'jhi-competencies-table',
    standalone: true,
    imports: [ArtemisSharedCommonModule, RouterLink, ArtemisMarkdownModule, ArtemisSharedModule],
    templateUrl: './competencies-table.component.html',
    styleUrls: ['../course-competencies-table/course-competencies-table.component.scss', './competencies-table.component.scss'],
})
export class CompetenciesTableComponent extends CourseCompetenciesTableDirective<Competency> {
    protected readonly faRobot = faRobot;

    private readonly competencyApiService = inject(CompetencyApiService);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);

    private readonly profileInfo = toSignal(this.profileService.getProfileInfo());
    private readonly irisCombinedCourseSettings = signal<IrisCourseSettings | undefined>(undefined);
    readonly irisCompetencyGenerationEnabled = computed(() => {
        return (this.profileInfo()?.activeProfiles.includes(PROFILE_IRIS) ?? false) && (this.irisCombinedCourseSettings()?.irisCompetencyGenerationSettings?.enabled ?? false);
    });

    readonly standardizedCompetenciesEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies));

    protected async deleteCompetency(competencyId: number): Promise<void> {
        await this.deleteCourseCompetency(competencyId, this.competencyApiService.deleteCompetency.bind(this.competencyApiService));
    }

    protected async openImportAllModal(): Promise<void> {
        const modal = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        //unary operator is necessary as otherwise courseId is seen as a string and will not match.
        modal.componentInstance.disabledIds = [+this.courseId];
        const result = await modal.result;
        await this.importAllCompetencies(result as ImportAllFromCourseResult);
    }

    private async importAllCompetencies(result: ImportAllFromCourseResult): Promise<void> {
        const courseTitle = result.courseForImportDTO.title ?? '';
        try {
            const importedCompetenciesWithTailRelation = await this.competencyApiService.importAllCompetencies(
                this.courseId(),
                result.courseForImportDTO.id!,
                result.importRelations,
            );
            const importedCompetencies = importedCompetenciesWithTailRelation.map((dto) => dto.competency).filter((element): element is Competency => !!element);
            if (importedCompetencies.length > 0) {
                this.alertService.success('artemisApp.competency.importAll.success', {
                    noOfCompetencies: importedCompetenciesWithTailRelation.length,
                    courseTitle: courseTitle,
                });
                this.courseCompetencies.update((existingCourseCompetencies) => existingCourseCompetencies.concat(importedCompetencies));
                const relations = importedCompetenciesWithTailRelation.flatMap((dto) => dto.tailRelations).filter((element): element is CompetencyRelationDTO => !!element);
                this.relations.update((existingRelations) => existingRelations.concat(relations));
            } else {
                this.alertService.warning('artemisApp.competency.importAll.warning', { courseTitle: courseTitle });
            }
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
