import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, inject, input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, takeUntil, tap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Course, CourseGroup } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { captureException } from '@sentry/angular';

@Component({
    selector: 'jhi-registered-students',
    templateUrl: './registered-students.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, CourseGroupComponent],
})
export class RegisteredStudentsComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private courseService = inject(CourseManagementService);
    private cdr = inject(ChangeDetectorRef);

    course = input.required<Course>();
    tutorialGroupId = input.required<number>();

    tutorialGroup: TutorialGroup;
    isLoading = false;
    registeredStudents: User[] = [];
    courseGroup = CourseGroup.STUDENTS;
    isAdmin = false;
    filteredUsersSize = 0;
    numberOfRegistrations = 0;

    registrationsChanged = false;

    isInitialized = false;
    ngUnsubscribe = new Subject<void>();

    get capacityReached(): boolean {
        if (!this.tutorialGroup) {
            return false;
        }
        if (this.tutorialGroup.capacity === undefined || this.tutorialGroup.capacity === null) {
            return false;
        } else {
            return this.numberOfRegistrations >= this.tutorialGroup.capacity;
        }
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    initialize() {
        if (!this.tutorialGroupId() || !this.course()) {
            captureException('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
            this.loadAll();
        }
    }

    handleUsersSizeChange = (filteredUsersSize: number) => (this.filteredUsersSize = filteredUsersSize);

    addToGroup = (login: string) =>
        this.tutorialGroupService.registerStudent(this.course().id!, this.tutorialGroup.id!, login).pipe(
            tap({
                next: () => {
                    this.registrationsChanged = true;
                    this.numberOfRegistrations++;
                },
            }),
        );

    removeFromGroup = (login: string) =>
        this.tutorialGroupService.deregisterStudent(this.course().id!, this.tutorialGroup.id!, login).pipe(
            tap({
                next: () => {
                    this.registrationsChanged = true;
                    this.numberOfRegistrations--;
                },
            }),
        );

    userSearch = (loginOrName: string) => this.courseService.searchStudents(this.course().id!, loginOrName);

    get exportFilename(): string {
        if (this.course() && this.tutorialGroup) {
            return this.course().title + ' ' + this.tutorialGroup.title;
        } else {
            return 'RegisteredStudents';
        }
    }

    loadAll = () => {
        this.tutorialGroupService
            .getOneOfCourse(this.course().id!, this.tutorialGroupId())
            .pipe(
                finalize(() => (this.isLoading = false)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroup = tutorialGroupResult.body;
                        // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                        if (!this.tutorialGroup.registrations) {
                            this.tutorialGroup.registrations = [];
                        }
                        this.registeredStudents = this.tutorialGroup.registrations.map((registration) => registration.student!);
                        this.numberOfRegistrations = this.registeredStudents.length;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    };

    clear() {
        if (this.registrationsChanged) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
        }
    }
}
