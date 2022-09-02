import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Course, CourseGroup } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-registered-students',
    templateUrl: './registered-students.component.html',
})
export class RegisteredStudentsComponent implements OnInit {
    isLoading = false;
    tutorialGroup: TutorialGroup;
    registeredStudents: User[] = [];
    course: Course;
    courseGroup = CourseGroup.STUDENTS;
    isAdmin = false;
    filteredUsersSize = 0;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
        private accountService: AccountService,
        private courseService: CourseManagementService,
    ) {}

    ngOnInit(): void {
        this.loadAll();
    }

    handleUsersSizeChange = (filteredUsersSize: number) => (this.filteredUsersSize = filteredUsersSize);

    addToGroup = (login: string) => this.tutorialGroupService.registerStudent(this.tutorialGroup.id!, login);

    removeFromGroup = (login: string) => this.tutorialGroupService.deregisterStudent(this.tutorialGroup.id!, login);

    userSearch = (loginOrName: string) => this.courseService.searchStudents(this.course.id!, loginOrName);

    get exportFilename(): string {
        if (this.course && this.tutorialGroup) {
            return this.course.title + ' ' + this.tutorialGroup.title;
        } else {
            return 'RegisteredStudents';
        }
    }

    loadAll = () => {
        this.isLoading = true;
        this.isAdmin = this.accountService.isAdmin();
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.data])
            .pipe(
                take(1),
                switchMap(([params, data]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.course = data.course;
                    return this.tutorialGroupService.getOne(tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
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
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    };
}
