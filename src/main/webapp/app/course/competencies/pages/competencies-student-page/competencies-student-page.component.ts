import { Component, inject, signal } from '@angular/core';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { map, switchMap } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-competencies-student-page',
    standalone: true,
    imports: [ArtemisSidebarModule, ArtemisSharedCommonModule, RouterOutlet],
    templateUrl: './competencies-student-page.component.html',
    styleUrl: 'competencies-student-page.component.scss',
})
export class CompetenciesStudentPageComponent {
    private readonly competenciesKey = 'competencies';

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);
    private readonly competencyApiService = inject(CompetencyApiService);
    private readonly courseOverviewService = inject(CourseOverviewService);

    private readonly courseId$ = this.activatedRoute.parent!.parent!.params.pipe(map((params) => Number(params.courseId)));

    readonly isCollapsed = signal<boolean>(this.courseOverviewService.getSidebarCollapseStateFromStorage(this.competenciesKey));
    readonly isLoading = signal<boolean>(false);
    readonly competencies = toSignal(this.courseId$.pipe(switchMap((courseId) => this.getAllCompetencies(courseId))), { initialValue: [] });

    readonly competencySelected = signal<boolean>(true);

    async getAllCompetencies(courseId: number) {
        try {
            this.isLoading.set(true);
            return await this.competencyApiService.getAllByCourseId(courseId);
        } catch (error) {
            this.alertService.error(error);
            return [];
        } finally {
            this.isLoading.set(false);
        }
    }

    toggleSidebar(): void {
        this.isCollapsed.update((isCollapsed) => !isCollapsed);
        this.courseOverviewService.setSidebarCollapseState(this.competenciesKey, this.isCollapsed());
    }
}
