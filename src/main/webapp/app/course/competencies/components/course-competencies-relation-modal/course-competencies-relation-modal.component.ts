import { Component, effect, inject, input, signal } from '@angular/core';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { CompetencyRelationDTO, CourseCompetency } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseCompetenciesRelationGraphComponent } from 'app/course/competencies/components/course-competencies-relation-graph/course-competencies-relation-graph.component';

@Component({
    selector: 'jhi-course-competencies-relation-modal',
    standalone: true,
    imports: [ArtemisSharedCommonModule, CompetencyGraphComponent, CourseCompetenciesRelationGraphComponent],
    templateUrl: './course-competencies-relation-modal.component.html',
    styleUrl: './course-competencies-relation-modal.component.scss',
})
export class CourseCompetenciesRelationModalComponent {
    protected readonly closeIcon = faXmark;

    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly activeModal = inject(NgbActiveModal);

    readonly courseId = input.required<number>();
    readonly courseCompetencies = input.required<CourseCompetency[]>();

    readonly isLoading = signal<boolean>(false);
    readonly relations = signal<CompetencyRelationDTO[]>([]);

    constructor() {
        effect(() => this.loadRelations(this.courseId()), { allowSignalWrites: true });
    }

    private async loadRelations(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const relations = await this.courseCompetencyApiService.getCourseCompetencyRelationsByCourseId(courseId);
            this.relations.set(relations);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected closeModal(): void {
        this.activeModal.close();
    }
}
