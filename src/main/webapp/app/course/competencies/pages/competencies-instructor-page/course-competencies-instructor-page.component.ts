import { Component, computed, effect, inject, signal } from '@angular/core';
import { CourseCompetenciesManagementTableComponent } from 'app/course/competencies/components/course-competencies-management-table/course-competencies-management-table.component';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CompetencyRelationDTO, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { onError } from 'app/shared/util/global.utils';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseCompetenciesRelationGraphComponent } from 'app/course/competencies/components/course-competencies-relation-graph/course-competencies-relation-graph.component';
import { faEdit, faFileImport } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseCompetenciesRelationModalComponent } from 'app/course/competencies/components/course-competencies-relation-modal/course-competencies-relation-modal.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-course-competencies-instructor-page',
    standalone: true,
    imports: [CourseCompetenciesManagementTableComponent, CourseCompetenciesRelationGraphComponent, FontAwesomeModule, ArtemisSharedComponentModule],
    templateUrl: './course-competencies-instructor-page.component.html',
})
export class CourseCompetenciesInstructorPageComponent {
    protected readonly faFileImport = faFileImport;
    protected readonly faEdit = faEdit;

    protected readonly documentationType: DocumentationType = 'Competencies';

    protected readonly CourseCompetencyType = CourseCompetencyType;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly modalService = inject(NgbModal);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });

    readonly courseCompetencies = signal<CourseCompetency[]>([]);
    readonly competencies = computed(() => this.courseCompetencies().filter((competency) => competency.type === CourseCompetencyType.COMPETENCY));
    readonly prerequisites = computed(() => this.courseCompetencies().filter((competency) => competency.type === CourseCompetencyType.PREREQUISITE));
    readonly relations = signal<CompetencyRelationDTO[]>([]);

    readonly standardizedCompetenciesEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies), { requireSync: true });

    readonly isLoading = signal<boolean>(false);

    constructor() {
        effect(() => this.loadCourseCompetencies(this.courseId()), { allowSignalWrites: true });
    }

    private async loadCourseCompetencies(courseId: number) {
        try {
            this.isLoading.set(true);
            const courseCompetencies = await this.courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
            this.courseCompetencies.set(courseCompetencies);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected openCourseCompetenciesRelationModal(): void {
        const modalRef = this.modalService.open(CourseCompetenciesRelationModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'course-competencies-relation-graph-modal',
        });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.courseCompetencies = this.courseCompetencies;
    }

    protected onCourseCompetencyDeletion(courseCompetencyId: number): void {
        this.courseCompetencies.update((courseCompetencies) => courseCompetencies.filter((competency) => competency.id !== courseCompetencyId));
    }

    protected onCourseCompetenciesImport(importedCourseCompetencies: CourseCompetency[]) {
        this.courseCompetencies.update((courseCompetencies) => [...courseCompetencies, ...importedCourseCompetencies]);
    }
}
