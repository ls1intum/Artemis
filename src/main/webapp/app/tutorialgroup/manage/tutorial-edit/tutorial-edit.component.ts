import { Component, computed, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialEditLanguagesInputComponent } from 'app/tutorialgroup/manage/tutorial-edit-languages-input/tutorial-edit-languages-input.component';

type Mode = {
    name: string;
};

export enum TutorialEditValidationStatus {
    VALID = 'VALID',
    INVALID = 'INVALID',
}

export type TutorialEditValidation = { status: TutorialEditValidationStatus.INVALID; message: string } | { status: TutorialEditValidationStatus.VALID };

@Component({
    selector: 'jhi-tutorial-edit',
    imports: [
        InputGroupModule,
        InputGroupAddonModule,
        InputTextModule,
        FormsModule,
        ToggleSwitchModule,
        SelectModule,
        InputNumberModule,
        DatePickerModule,
        TooltipModule,
        ButtonModule,
        RouterLink,
        TutorialEditLanguagesInputComponent,
    ],
    templateUrl: './tutorial-edit.component.html',
    styleUrl: './tutorial-edit.component.scss',
})
export class TutorialEditComponent {
    private readonly titleRegex = /^[A-Za-z0-9][A-Za-z0-9: -]*$/;
    protected readonly TutorialEditValidationStatus = TutorialEditValidationStatus;

    tutors = input.required<TutorialGroupTutorDTO[]>();

    title = signal('');
    titleValidationResult = computed<TutorialEditValidation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<TutorialEditValidation>(() => this.computeTutorValidation());
    tutorInputTouched = signal(false);
    language = signal<string>('');
    modes: Mode[] = [{ name: 'Online' }, { name: 'Offline' }];
    selectedMode = signal<Mode>({ name: 'Online' });
    campus = signal('');
    campusValidationResult = computed<TutorialEditValidation>(() => this.computeCampusValidation());
    capacity = signal<number | undefined>(undefined);
    noteForStudents = signal('');

    configureSessionPlan = signal(true);
    firstSessionStart = signal<Date | undefined>(undefined);
    firstSessionStartInputTouched = signal(false);
    firstSessionStartValidationResult = computed<TutorialEditValidation>(() => this.computeFirstSessionStartValidation());
    firstSessionEnd = signal<Date | undefined>(undefined);
    firstSessionEndInputTouched = signal(false);
    firstSessionEndValidationResult = computed<TutorialEditValidation>(() => this.computeFirstSessionEndValidation());
    weekFrequency = signal<number>(1);
    teachingPeriodEnd = signal<Date | undefined>(undefined);
    teachingPeriodEndInputTouched = signal(false);
    teachingPeriodEndValidationResult = computed<TutorialEditValidation>(() => this.computeTeachingPeriodEndValidation());
    location = signal('');

    private computeTitleValidation(): TutorialEditValidation {
        if (!this.titleInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        const title = this.title();
        if (!title.match(this.titleRegex)) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Title must start with a letter or digit, and can otherwise only contain letters, digits, colons, spaces and dashes.',
            };
        }
        const trimmedTitle = title.trim();
        if (trimmedTitle.length > 19) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Title must contain at most 19 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return {
            status: TutorialEditValidationStatus.VALID,
        };
    }

    private computeCampusValidation(): TutorialEditValidation {
        const trimmedCampus = this.campus().trim();
        if (trimmedCampus && trimmedCampus.length > 255) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Campus must contain at most 255 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return { status: TutorialEditValidationStatus.VALID };
    }

    private computeFirstSessionStartValidation(): TutorialEditValidation {
        if (!this.firstSessionStartInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        return this.firstSessionStart()
            ? { status: TutorialEditValidationStatus.VALID }
            : {
                  status: TutorialEditValidationStatus.INVALID,
                  message: 'Please choose a date.',
              };
    }

    private computeFirstSessionEndValidation(): TutorialEditValidation {
        if (!this.firstSessionEndInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        const firstSessionEnd = this.firstSessionEnd();
        if (!firstSessionEnd) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && firstSessionEnd <= firstSessionStart) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'The end of the first session must be after its start.',
            };
        }
        if (
            firstSessionStart &&
            (firstSessionStart.getFullYear() !== firstSessionEnd.getFullYear() ||
                firstSessionStart.getMonth() !== firstSessionEnd.getMonth() ||
                firstSessionStart.getDate() !== firstSessionEnd.getDate())
        ) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'The end of the first session must be on the same day as its start.',
            };
        }
        return { status: TutorialEditValidationStatus.VALID };
    }

    private computeTeachingPeriodEndValidation(): TutorialEditValidation {
        if (!this.teachingPeriodEndInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        const teachingPeriodEnd = this.teachingPeriodEnd();
        if (!teachingPeriodEnd) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && teachingPeriodEnd <= firstSessionStart) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's start.",
            };
        }
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && teachingPeriodEnd <= firstSessionEnd) {
            return {
                status: TutorialEditValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's end.",
            };
        }
        return { status: TutorialEditValidationStatus.VALID };
    }

    private computeTutorValidation(): TutorialEditValidation {
        if (!this.tutorInputTouched()) return { status: TutorialEditValidationStatus.VALID };
        const selectedTutorId = this.selectedTutorId();
        if (selectedTutorId) return { status: TutorialEditValidationStatus.VALID };
        return {
            status: TutorialEditValidationStatus.INVALID,
            message: 'Please choose a tutor.',
        };
    }
}
