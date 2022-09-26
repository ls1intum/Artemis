import { Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseGroup, Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { catchError, concat, finalize, forkJoin, map, merge, Observable, of, OperatorFunction, Subject } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { debounceTime, distinctUntilChanged, filter } from 'rxjs/operators';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { faSave } from '@fortawesome/free-solid-svg-icons';

export interface TutorialGroupFormData {
    title?: string;
    teachingAssistant?: User;
    additionalInformation?: string;
    capacity?: number;
    isOnline?: boolean;
    language?: Language;
    campus?: string;
}

export class UserWithLabel extends User {
    label: string;
}

const nonWhitespaceRegExp: RegExp = new RegExp('\\S');

@Component({
    selector: 'jhi-tutorial-group-form',
    templateUrl: './tutorial-group-form.component.html',
})
export class TutorialGroupFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFormData = {
        title: undefined,
        teachingAssistant: undefined,
        additionalInformation: undefined,
        capacity: undefined,
        isOnline: undefined,
        language: undefined,
        campus: undefined,
    };
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;

    @Input() courseId: number;
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();

    form: FormGroup;
    // not included in reactive form
    additionalInformation: string | undefined;

    teachingAssistantsAreLoading = false;
    teachingAssistants: UserWithLabel[];
    @ViewChild('teachingAssistantInput', { static: true }) taTypeAhead: NgbTypeahead;
    taFocus$ = new Subject<string>();
    taClick$ = new Subject<string>();

    campusAreLoading = false;
    campus: string[];
    @ViewChild('campusInput', { static: true }) campusTypeAhead: NgbTypeahead;
    campusFocus$ = new Subject<string>();
    campusClick$ = new Subject<string>();

    // icons
    faSave = faSave;

    constructor(
        private fb: FormBuilder,
        private courseManagementService: CourseManagementService,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
    ) {}

    get titleControl() {
        return this.form.get('title');
    }

    get teachingAssistantControl() {
        return this.form.get('teachingAssistant');
    }

    get campusControl() {
        return this.form.get('campus');
    }

    get capacityControl() {
        return this.form.get('capacity');
    }

    get isOnlineControl() {
        return this.form.get('isOnline');
    }

    get languageControl() {
        return this.form.get('language');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.getTeachingAssistantsInCourse();
        this.getUniqueCampusValuesOfCourse();
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    submitForm() {
        const tutorialGroupFormData: TutorialGroupFormData = { ...this.form.value };
        tutorialGroupFormData.additionalInformation = this.additionalInformation;
        this.formSubmitted.emit(tutorialGroupFormData);
    }

    trackId(index: number, item: User) {
        return item.id;
    }

    taFormatter = (user: UserWithLabel) => user.label;

    taSearch: OperatorFunction<string, readonly UserWithLabel[]> = (text$: Observable<string>) => {
        return this.mergeSearch$(text$, this.taFocus$, this.taClick$, this.taTypeAhead).pipe(
            map((term) => (term === '' ? this.teachingAssistants : this.teachingAssistants.filter((ta) => ta.label.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    campusFormatter = (campus: string) => campus;

    campusSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        return this.mergeSearch$(text$, this.campusFocus$, this.campusClick$, this.campusTypeAhead).pipe(
            map((term) => (term === '' ? this.campus : this.campus.filter((campus) => campus.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    private mergeSearch$(text$: Observable<string>, focus$: Subject<string>, click$: Subject<string>, typeahead: NgbTypeahead) {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = click$.pipe(filter(() => typeahead && !typeahead.isPopupOpen()));
        return merge(debouncedText$, focus$, clicksWithClosedPopup$);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            title: [undefined, [Validators.required, Validators.maxLength(255), Validators.pattern(nonWhitespaceRegExp)]],
            teachingAssistant: [undefined, [Validators.required]],
            capacity: [undefined, [Validators.min(1)]],
            isOnline: [false],
            language: [this.GERMAN],
            campus: [undefined, Validators.maxLength(255)],
        });
    }

    private setFormValues(formData: TutorialGroupFormData) {
        if (formData.teachingAssistant) {
            formData.teachingAssistant = this.createUserWithLabel(formData.teachingAssistant);
        }
        this.form.patchValue(formData);
        this.additionalInformation = formData.additionalInformation;
    }

    private createUserWithLabel(user: User): UserWithLabel {
        return { ...user, label: this.createUserLabel(user) };
    }

    private createUserLabel(ta: User) {
        let label = '';
        if (ta.firstName) {
            label += ta.firstName + ' ';
        }
        if (ta.lastName) {
            label += ta.lastName + ' ';
        }
        if (ta.login) {
            label += '(' + ta.login + ')';
        }
        return label.trim();
    }

    private getTeachingAssistantsInCourse() {
        const generateUserObservable = (group: CourseGroup) => {
            return this.courseManagementService.getAllUsersInCourseGroup(this.courseId, group).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<User[]>) => res.body!),
            );
        };

        type result = {
            tutors: User[];
            instructors: User[];
            editors: User[];
        };

        return concat(
            of([]), // default items
            forkJoin({
                tutors: generateUserObservable(CourseGroup.TUTORS),
                instructors: generateUserObservable(CourseGroup.INSTRUCTORS),
                editors: generateUserObservable(CourseGroup.EDITORS),
            }).pipe(
                map((res: result) => {
                    return [...res.tutors, ...res.instructors, ...res.editors];
                }),
                finalize(() => {
                    this.teachingAssistantsAreLoading = false;
                }),
            ),
        ).subscribe((users: User[]) => {
            this.teachingAssistants = users.map((user) => this.createUserWithLabel(user));
        });
    }

    private getUniqueCampusValuesOfCourse() {
        return concat(
            of([]), // default items
            this.tutorialGroupService.getUniqueCampusValues(this.courseId).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<string[]>) => res.body!),
                finalize(() => {
                    this.campusAreLoading = false;
                }),
            ),
        ).subscribe((campus: string[]) => {
            this.campus = campus;
        });
    }
}
