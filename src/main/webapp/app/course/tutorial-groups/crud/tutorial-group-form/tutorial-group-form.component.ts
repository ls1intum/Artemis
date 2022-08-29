import { Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course, CourseGroup, Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { catchError, concat, finalize, map, merge, Observable, of, OperatorFunction, Subject } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { debounceTime, distinctUntilChanged, filter } from 'rxjs/operators';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { ScheduleFormComponent, ScheduleFormData } from 'app/course/tutorial-groups/crud/tutorial-group-form/schedule-form/schedule-form.component';

export interface TutorialGroupFormData {
    title?: string;
    teachingAssistant?: User;
    additionalInformation?: string;
    capacity?: number;
    isOnline?: boolean;
    location?: string;
    language?: Language;
    schedule?: ScheduleFormData;
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
        location: undefined,
        language: undefined,
    };
    GERMAN = Language.GERMAN;
    ENGLISH = Language.ENGLISH;

    @Input() course: Course;
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();

    form: FormGroup;
    teachingAssistantsAreLoading = false;
    teachingAssistants: UserWithLabel[];
    @ViewChild('teachingAssistantInput') taTypeAhead: NgbTypeahead;
    taFocus$ = new Subject<string>();
    taClick$ = new Subject<string>();

    /** Passed as inputs to the schedule form*/
    @ViewChild('scheduleForm') scheduleFormComponent: ScheduleFormComponent;

    constructor(private fb: FormBuilder, private courseManagementService: CourseManagementService, private alertService: AlertService) {}

    get titleControl() {
        return this.form.get('title');
    }

    get teachingAssistantControl() {
        return this.form.get('teachingAssistant');
    }

    get additionalInformationControl() {
        return this.form.get('additionalInformation');
    }

    get capacityControl() {
        return this.form.get('capacity');
    }

    get isOnlineControl() {
        return this.form.get('isOnline');
    }

    get locationControl() {
        return this.form.get('location');
    }

    get languageControl() {
        return this.form.get('language');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.getTeachingAssistantsInCourse();
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
        this.formSubmitted.emit(tutorialGroupFormData);
    }

    trackId(index: number, item: User) {
        return item.id;
    }

    taFormatter = (user: UserWithLabel) => user.label;
    taSearch: OperatorFunction<string, readonly UserWithLabel[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.taClick$.pipe(filter(() => !this.taTypeAhead.isPopupOpen()));
        const inputFocus$ = this.taFocus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term === '' ? this.teachingAssistants : this.teachingAssistants.filter((ta) => ta.label.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };
    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            title: [undefined, [Validators.required, Validators.maxLength(255), Validators.pattern(nonWhitespaceRegExp)]],
            teachingAssistant: [undefined, [Validators.required]],
            additionalInformation: [undefined, [Validators.maxLength(2000)]],
            capacity: [undefined, [Validators.min(1)]],
            isOnline: [false],
            location: [undefined, [Validators.maxLength(2000)]],
            language: [this.GERMAN],
        });
    }

    private setFormValues(formData: TutorialGroupFormData) {
        if (formData.teachingAssistant) {
            formData.teachingAssistant = this.createUserWithLabel(formData.teachingAssistant);
        }
        this.form.patchValue(formData);
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
        this.teachingAssistantsAreLoading = true;
        return concat(
            of([]), // default items
            this.courseManagementService.getAllUsersInCourseGroup(this.course.id!, CourseGroup.TUTORS).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<User[]>) => res.body!),
                map((user: User[]) => {
                    return user.map((u) => this.createUserWithLabel(u));
                }),
                finalize(() => {
                    this.teachingAssistantsAreLoading = false;
                }),
            ),
        ).subscribe((users: UserWithLabel[]) => {
            this.teachingAssistants = users;
        });
    }
}
