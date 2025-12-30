import { Component, HttpResourceRef, Signal, computed, effect, inject, signal } from '@angular/core';
import { RawTutorialGroupDetailGroupDTO, TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { EMPTY, catchError, filter, map, switchMap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { CourseTutorialGroupDetailComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-course-tutorial-group-detail-container',
    templateUrl: './course-tutorial-group-detail-container.component.html',
    imports: [LoadingIndicatorContainerComponent, CourseTutorialGroupDetailComponent],
})
export class CourseTutorialGroupDetailContainerComponent {
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private courseManagementService = inject(CourseManagementService);

    private courseId = this.getCourseIdSignal();
    private tutorialGroupId = this.getTutorialGroupIdSignal();
    private tutorialGroupResource = signal<HttpResourceRef<RawTutorialGroupDetailGroupDTO | undefined> | undefined>(undefined);
    private courseResponse = this.getCourseResponseSignal();
    private courseLoadFailed = signal(false);

    isLoading = computed(() => (this.tutorialGroupResource()?.isLoading() ?? false) || (!this.courseLoadFailed() && this.courseResponse() === undefined));
    tutorialGroup = computed(() => {
        const raw = this.tutorialGroupResource()?.value();
        if (!raw) {
            return undefined;
        }
        return new TutorialGroupDetailGroupDTO(raw);
    });
    course = computed(() => this.courseResponse()?.body ?? undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (!courseId || !tutorialGroupId) {
                return;
            }
            this.courseLoadFailed.set(false);
            this.tutorialGroupResource.set(this.tutorialGroupService.getTutorialGroupDetailGroupDTOResource(courseId, tutorialGroupId));
        });

        effect(() => {
            const resource = this.tutorialGroupResource();
            if (!resource) {
                return;
            }
            const error = resource.error();
            if (error) {
                onError(this.alertService, error as HttpErrorResponse);
            }
        });
    }

    private getCourseIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.route.parent?.parent?.params.pipe(
                map((params) => {
                    const courseId = params.courseId ? parseInt(params.courseId, 10) : undefined;
                    return Number.isNaN(courseId) ? undefined : courseId;
                }),
                filter((courseId): courseId is number => courseId !== undefined),
            ) ?? EMPTY,
            { initialValue: undefined },
        );
    }

    private getTutorialGroupIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.route.params.pipe(
                map((params) => {
                    const tutorialGroupId = params.tutorialGroupId ? parseInt(params.tutorialGroupId, 10) : undefined;
                    return Number.isNaN(tutorialGroupId) ? undefined : tutorialGroupId;
                }),
            ),
            { initialValue: undefined },
        );
    }

    private getCourseResponseSignal() {
        return toSignal(
            this.route.parent?.parent?.params.pipe(
                map((params) => {
                    const courseId = params.courseId ? parseInt(params.courseId, 10) : undefined;
                    return Number.isNaN(courseId) ? undefined : courseId;
                }),
                filter((courseId): courseId is number => courseId !== undefined),
                switchMap((courseId) =>
                    this.courseManagementService.find(courseId).pipe(
                        catchError((error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                            this.courseLoadFailed.set(true);
                            return EMPTY;
                        }),
                    ),
                ),
            ) ?? EMPTY,
            { initialValue: undefined },
        );
    }
}
