import { Directive, inject, input, model } from '@angular/core';
import { Competency, CompetencyRelationDTO, CourseCompetency } from 'app/entities/competency.model';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

@Directive({
    standalone: true,
})
export abstract class ImportCourseCompetenciesDirective {
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);
    protected readonly courseCompetencyApiService = inject(CourseCompetencyApiService);

    readonly courseId = input.required<number>();
    readonly courseCompetencies = model.required<CourseCompetency[]>();
    readonly relations = model.required<CompetencyRelationDTO[]>();

    protected async openImportAllModal(courseCompetencyType: string): Promise<void> {
        const modal = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        //unary operator is necessary as otherwise courseId is seen as a string and will not match.
        modal.componentInstance.disabledIds = [+this.courseId];
        modal.componentInstance.competencyType = courseCompetencyType;
        const result = await modal.result;
        await this.importAllCompetencies(result as ImportAllFromCourseResult);
    }

    private async importAllCompetencies(result: ImportAllFromCourseResult): Promise<void> {
        const courseTitle = result.courseForImportDTO.title ?? '';
        try {
            const importedCompetenciesWithTailRelation = await this.courseCompetencyApiService.importAll(this.courseId(), result.courseForImportDTO.id!, result.importRelations);
            const importedCompetencies = importedCompetenciesWithTailRelation.map((dto) => dto.competency).filter((element): element is Competency => !!element);
            if (importedCompetencies.length > 0) {
                this.alertService.success('artemisApp.competency.importAll.success', {
                    noOfCompetencies: importedCompetenciesWithTailRelation.length,
                    courseTitle: courseTitle,
                });
                this.courseCompetencies.update((existingCourseCompetencies) => existingCourseCompetencies.concat(importedCompetencies));
                const importedRelations = importedCompetenciesWithTailRelation.flatMap((dto) => dto.tailRelations).filter((element): element is CompetencyRelationDTO => !!element);
                this.relations.update((existingRelations) => existingRelations.concat(importedRelations));
            } else {
                this.alertService.warning('artemisApp.competency.importAll.warning', { courseTitle: courseTitle });
            }
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
