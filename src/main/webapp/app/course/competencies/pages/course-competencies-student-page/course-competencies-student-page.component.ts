import { Component, computed, effect, inject, signal } from '@angular/core';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { AlertService } from 'app/core/util/alert.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseCompetency, CourseCompetencyType, getIcon } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

@Component({
    selector: 'jhi-course-competencies-student-page',
    standalone: true,
    imports: [ArtemisSidebarModule, ArtemisSharedCommonModule, RouterOutlet],
    templateUrl: './course-competencies-student-page.component.html',
})
export class CourseCompetenciesStudentPageComponent {
    private readonly courseCompetenciesKey = 'course-competencies';

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);

    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly courseOverviewService = inject(CourseOverviewService);

    readonly collapseState = signal<CollapseState>({
        competencies: true,
        prerequisites: false,
    }).asReadonly();

    private readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });

    readonly isCollapsed = signal<boolean>(this.courseOverviewService.getSidebarCollapseStateFromStorage(this.courseCompetenciesKey));
    readonly isLoading = signal<boolean>(false);

    private readonly courseCompetencies = signal<CourseCompetency[]>([]);
    private readonly competencies = computed(() => {
        return this.courseCompetencies().filter((courseCompetency) => courseCompetency.type === CourseCompetencyType.COMPETENCY);
    });
    private readonly prerequisites = computed(() => {
        return this.courseCompetencies().filter((courseCompetency) => courseCompetency.type === CourseCompetencyType.PREREQUISITE);
    });

    constructor() {
        // Fetch data when the course id is available
        effect(() => this.loadData(this.courseId()), { allowSignalWrites: true });
        // Navigate to the first course competency when the competencies are loaded
        effect(() => this.navigateToFirstCompetency(this.competencies()));
    }

    private readonly competencySidebarCards = computed(() => {
        return this.competencies().map((competency) => {
            return this.mapCourseCompetencyToSidebarCardElement(competency);
        });
    });

    private readonly prerequisitesSidebarCards = computed(() => {
        return this.prerequisites().map((prerequisite) => {
            return this.mapCourseCompetencyToSidebarCardElement(prerequisite);
        });
    });

    readonly sidebarData = computed(() => {
        return <SidebarData>{
            storageId: 'course-competency',
            groupByCategory: true,
            ungroupedData: [...this.competencySidebarCards(), ...this.prerequisitesSidebarCards()],
            groupedData: <AccordionGroups>{
                competencies: {
                    entityData: this.competencySidebarCards(),
                },
                prerequisites: {
                    entityData: this.prerequisitesSidebarCards(),
                },
            },
        };
    });

    private navigateToFirstCompetency(competencies: CourseCompetency[]) {
        if (competencies.length > 0) {
            this.router.navigate([this.competencies().first()!.id], { relativeTo: this.activatedRoute });
        }
    }

    async loadData(courseId: number): Promise<void> {
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

    private mapCourseCompetencyToSidebarCardElement(courseCompetency: CourseCompetency) {
        return <SidebarCardElement>{
            id: courseCompetency.id,
            title: courseCompetency.title,
            size: 'M',
            icon: getIcon(courseCompetency.taxonomy),
        };
    }

    toggleSidebar(): void {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.courseOverviewService.setSidebarCollapseState(this.courseCompetenciesKey, this.isCollapsed());
    }
}
