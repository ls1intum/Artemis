import { __decorate, __metadata } from 'tslib';
import { Component, ViewChild, ViewEncapsulation } from '@angular/core';
import { Subject, of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { UserService } from 'app/core/user/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/utils/icons.utils';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faInfoCircle, faPlus, faUpload, faUserSlash } from '@fortawesome/free-solid-svg-icons';
const cssClasses = {
    alreadyRegistered: 'already-registered',
    newlyRegistered: 'newly-registered',
};
let ExamStudentsComponent = class ExamStudentsComponent {
    constructor(router, route, alertService, eventManager, examManagementService, userService, accountService) {
        this.router = router;
        this.route = route;
        this.alertService = alertService;
        this.eventManager = eventManager;
        this.examManagementService = examManagementService;
        this.userService = userService;
        this.accountService = accountService;
        this.ButtonType = ButtonType;
        this.ButtonSize = ButtonSize;
        this.ActionType = ActionType;
        this.SERVER_API_URL = SERVER_API_URL;
        this.missingImage = '/content/images/missing_image.png';
        this.dataWithImages = [];
        this.allRegisteredUsers = [];
        this.filteredUsersSize = 0;
        this.dialogErrorSource = new Subject();
        this.dialogError$ = this.dialogErrorSource.asObservable();
        this.isLoading = false;
        this.isSearching = false;
        this.searchFailed = false;
        this.searchNoResults = false;
        this.isTransitioning = false;
        this.rowClass = undefined;
        this.isAdmin = false;
        this.faPlus = faPlus;
        this.faUserSlash = faUserSlash;
        this.faInfoCircle = faInfoCircle;
        this.faUpload = faUpload;
        this.searchAllUsers = (stream$) => {
            return stream$.pipe(
                switchMap(({ text: loginOrName }) => {
                    this.searchFailed = false;
                    this.searchNoResults = false;
                    if (loginOrName.length < 3) {
                        return of([]);
                    }
                    this.isSearching = true;
                    return this.userService
                        .search(loginOrName)
                        .pipe(map((usersResponse) => usersResponse.body))
                        .pipe(
                            tap((users) => {
                                if (users.length === 0) {
                                    this.searchNoResults = true;
                                }
                            }),
                            catchError(() => {
                                this.searchFailed = true;
                                return of([]);
                            }),
                        );
                }),
                tap(() => {
                    this.isSearching = false;
                }),
                tap((users) => {
                    setTimeout(() => {
                        for (let i = 0; i < this.dataTable.typeaheadButtons.length; i++) {
                            const isAlreadyInCourseGroup = this.allRegisteredUsers.map((user) => user.id).includes(users[i].id);
                            this.dataTable.typeaheadButtons[i].insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInCourseGroup ? 'users' : 'users-plus']);
                            if (isAlreadyInCourseGroup) {
                                this.dataTable.typeaheadButtons[i].classList.add(cssClasses.alreadyRegistered);
                            }
                        }
                    });
                }),
            );
        };
        this.onAutocompleteSelect = (user, callback) => {
            if (!this.allRegisteredUsers.map((u) => u.id).includes(user.id) && user.login) {
                this.isTransitioning = true;
                this.examManagementService.addStudentToExam(this.courseId, this.exam.id, user.login).subscribe({
                    next: (student) => {
                        this.isTransitioning = false;
                        user.visibleRegistrationNumber = student.body.registrationNumber;
                        this.allRegisteredUsers.push(user);
                        callback(user);
                        this.flashRowClass(cssClasses.newlyRegistered);
                    },
                    error: (error) => {
                        if (error.status === 403) {
                            this.onError(`artemisApp.exam.error.${error.error.errorKey}`);
                        }
                        this.isTransitioning = false;
                    },
                });
            } else {
                callback(user);
            }
        };
        this.handleUsersSizeChange = (filteredUsersSize) => {
            this.filteredUsersSize = filteredUsersSize;
        };
        this.searchResultFormatter = (user) => {
            const { name, login } = user;
            return `${name} (${login})`;
        };
        this.searchTextFromUser = (user) => {
            return user.login || '';
        };
        this.dataTableRowClass = () => {
            return this.rowClass;
        };
        this.flashRowClass = (className) => {
            this.rowClass = className;
            setTimeout(() => (this.rowClass = undefined));
        };
    }
    ngOnInit() {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.isAdmin = this.accountService.isAdmin();
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.allRegisteredUsers =
                (exam.examUsers &&
                    exam.examUsers.map((examUser) => {
                        return {
                            ...examUser.user,
                            ...examUser,
                        };
                    })) ||
                [];
            this.isTestExam = this.exam.testExam;
            this.isLoading = false;
        });
    }
    reloadExamWithRegisteredUsers() {
        this.isLoading = true;
        this.examManagementService.find(this.courseId, this.exam.id, true).subscribe((examResponse) => {
            this.exam = examResponse.body;
            this.allRegisteredUsers =
                (this.exam.examUsers &&
                    this.exam.examUsers.map((examUser) => {
                        return {
                            ...examUser.user,
                            ...examUser,
                        };
                    })) ||
                [];
            this.isLoading = false;
        });
    }
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }
    removeFromExam(user, event) {
        this.examManagementService.removeStudentFromExam(this.courseId, this.exam.id, user.login, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRegisteredUsers = this.allRegisteredUsers.filter((u) => u.login !== user.login);
                this.dialogErrorSource.next('');
            },
            error: (error) => this.dialogErrorSource.next(error.message),
        });
    }
    removeAllStudents(event) {
        this.examManagementService.removeAllStudentsFromExam(this.courseId, this.exam.id, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRegisteredUsers = [];
                this.dialogErrorSource.next('');
            },
            error: (error) => this.dialogErrorSource.next(error.message),
        });
    }
    onError(error) {
        this.alertService.error(error);
        this.isTransitioning = false;
    }
    registerAllStudentsFromCourse() {
        if (this.exam?.id) {
            this.examManagementService.addAllStudentsOfCourseToExam(this.courseId, this.exam.id).subscribe({
                next: () => {
                    this.reloadExamWithRegisteredUsers();
                },
                error: (error) => this.dialogErrorSource.next(error.message),
            });
        }
    }
};
__decorate([ViewChild(DataTableComponent), __metadata('design:type', DataTableComponent)], ExamStudentsComponent.prototype, 'dataTable', void 0);
ExamStudentsComponent = __decorate(
    [
        Component({
            selector: 'jhi-exam-students',
            templateUrl: './exam-students.component.html',
            styleUrls: ['./exam-students.component.scss'],
            encapsulation: ViewEncapsulation.None,
        }),
        __metadata('design:paramtypes', [Router, ActivatedRoute, AlertService, EventManager, ExamManagementService, UserService, AccountService]),
    ],
    ExamStudentsComponent,
);
export { ExamStudentsComponent };
//# sourceMappingURL=exam-students.component.js.map
