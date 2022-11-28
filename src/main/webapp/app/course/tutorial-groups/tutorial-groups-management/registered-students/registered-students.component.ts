import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, tap } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Course, CourseGroup } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-registered-students',
    templateUrl: './registered-students.component.html',
})
export class RegisteredStudentsComponent {
    @Input()
    course: Course;

    @Input()
    tutorialGroupId: number;

    tutorialGroup: TutorialGroup;
    isLoading = false;
    registeredStudents: User[] = [];
    courseGroup = CourseGroup.STUDENTS;
    isAdmin = false;
    filteredUsersSize = 0;
    numberOfRegistrations = 0;

    registrationsChanged = false;

    isInitialized = false;

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

    constructor(
        private activeModal: NgbActiveModal,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
        private accountService: AccountService,
        private courseService: CourseManagementService,
    ) {}

    initialize() {
        if (!this.tutorialGroupId || !this.course) {
            console.error('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
            this.loadAll();
        }
    }

    handleUsersSizeChange = (filteredUsersSize: number) => (this.filteredUsersSize = filteredUsersSize);

    addToGroup = (login: string) =>
        this.tutorialGroupService.registerStudent(this.course.id!, this.tutorialGroup.id!, login).pipe(
            tap({
                next: () => {
                    this.registrationsChanged = true;
                    this.numberOfRegistrations++;
                },
            }),
        );

    removeFromGroup = (login: string) =>
        this.tutorialGroupService.deregisterStudent(this.course.id!, this.tutorialGroup.id!, login).pipe(
            tap({
                next: () => {
                    this.registrationsChanged = true;
                    this.numberOfRegistrations--;
                },
            }),
        );

    userSearch = (loginOrName: string) => this.courseService.searchStudents(this.course.id!, loginOrName);

    get exportFilename(): string {
        if (this.course && this.tutorialGroup) {
            return this.course.title + ' ' + this.tutorialGroup.title;
        } else {
            return 'RegisteredStudents';
        }
    }

    loadAll = () => {
        this.tutorialGroupService
            .getOneOfCourse(this.course.id!, this.tutorialGroupId)
            .pipe(finalize(() => (this.isLoading = false)))
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
            });
    };

    clear() {
        if (this.registrationsChanged) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss();
        }
    }
}
