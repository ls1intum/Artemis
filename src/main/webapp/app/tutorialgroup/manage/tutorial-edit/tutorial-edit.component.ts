import { Component, computed, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

type Mode = {
    name: string;
};

enum ValidationStatus {
    VALID = 'VALID',
    INVALID = 'INVALID',
}

type Validation = { status: ValidationStatus.INVALID; message: string } | { status: ValidationStatus.VALID };

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
        AutoCompleteModule,
        DatePickerModule,
        TooltipModule,
        ButtonModule,
        RouterLink,
    ],
    templateUrl: './tutorial-edit.component.html',
    styleUrl: './tutorial-edit.component.scss',
})
export class TutorialEditComponent {
    private readonly titleRegex = /^[A-Za-z0-9][A-Za-z0-9: -]*$/;
    protected readonly ValidationStatus = ValidationStatus;

    tutors = input.required<TutorialGroupTutorDTO[]>();

    title = signal('');
    titleValidationResult = computed<Validation>(() => this.computeTitleValidation());
    titleInputTouched = signal(false);
    selectedTutorId = signal<number | undefined>(undefined);
    tutorValidationResult = computed<Validation>(() => this.computeTutorValidation());
    tutorInputTouched = signal(false);
    language = signal<string>('');
    languageInputTouched = signal(false);
    languageValidationResult = computed<Validation>(() => this.computeLanguageValidation());
    modes: Mode[] = [{ name: 'Online' }, { name: 'Offline' }];
    selectedMode = signal<Mode>({ name: 'Online' });
    campus = signal('');
    campusValidationResult = computed<Validation>(() => this.computeCampusValidation());
    capacity = signal<number | undefined>(undefined);
    noteForStudents = signal('');

    configureSessionPlan = signal(true);
    firstSessionStart = signal<Date | undefined>(undefined);
    firstSessionStartInputTouched = signal(false);
    firstSessionStartValidationResult = computed<Validation>(() => this.computeFirstSessionStartValidation());
    firstSessionEnd = signal<Date | undefined>(undefined);
    firstSessionEndInputTouched = signal(false);
    firstSessionEndValidationResult = computed<Validation>(() => this.computeFirstSessionEndValidation());
    weekFrequency = signal<number>(1);
    teachingPeriodEnd = signal<Date | undefined>(undefined);
    teachingPeriodEndInputTouched = signal(false);
    teachingPeriodEndValidationResult = computed<Validation>(() => this.computeTeachingPeriodEndValidation());
    location = signal('');

    private computeTitleValidation(): Validation {
        if (!this.titleInputTouched()) return { status: ValidationStatus.VALID };
        const title = this.title();
        if (!title.match(this.titleRegex)) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Title must start with a letter or digit, and can otherwise only contain letters, digits, colons, spaces and dashes.',
            };
        }
        const trimmedTitle = title.trim();
        if (trimmedTitle.length > 19) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Title must contain at most 19 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return {
            status: ValidationStatus.VALID,
        };
    }

    private computeCampusValidation(): Validation {
        const trimmedCampus = this.campus().trim();
        if (trimmedCampus && trimmedCampus.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Campus must contain at most 255 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeLanguageValidation(): Validation {
        if (!this.languageInputTouched()) return { status: ValidationStatus.VALID };
        const trimmedLanguage = this.language().trim();
        if (!trimmedLanguage) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a language. The system automatically removes leading/trailing whitespaces.',
            };
        }
        if (trimmedLanguage && trimmedLanguage.length > 255) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Language must contain at most 255 characters. The system automatically removes leading/trailing whitespaces.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeFirstSessionStartValidation(): Validation {
        if (!this.firstSessionStartInputTouched()) return { status: ValidationStatus.VALID };
        return this.firstSessionStart()
            ? { status: ValidationStatus.VALID }
            : {
                  status: ValidationStatus.INVALID,
                  message: 'Please choose a date.',
              };
    }

    private computeFirstSessionEndValidation(): Validation {
        if (!this.firstSessionEndInputTouched()) return { status: ValidationStatus.VALID };
        const firstSessionEnd = this.firstSessionEnd();
        if (!firstSessionEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && firstSessionEnd <= firstSessionStart) {
            return {
                status: ValidationStatus.INVALID,
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
                status: ValidationStatus.INVALID,
                message: 'The end of the first session must be on the same day as its start.',
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeTeachingPeriodEndValidation(): Validation {
        if (!this.teachingPeriodEndInputTouched()) return { status: ValidationStatus.VALID };
        const teachingPeriodEnd = this.teachingPeriodEnd();
        if (!teachingPeriodEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: 'Please choose a date.',
            };
        }
        const firstSessionStart = this.firstSessionStart();
        if (firstSessionStart && teachingPeriodEnd <= firstSessionStart) {
            return {
                status: ValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's start.",
            };
        }
        const firstSessionEnd = this.firstSessionEnd();
        if (firstSessionEnd && teachingPeriodEnd <= firstSessionEnd) {
            return {
                status: ValidationStatus.INVALID,
                message: "The end of the teaching period must be after the first session's end.",
            };
        }
        return { status: ValidationStatus.VALID };
    }

    private computeTutorValidation(): Validation {
        if (!this.tutorInputTouched()) return { status: ValidationStatus.VALID };
        const selectedTutorId = this.selectedTutorId();
        if (selectedTutorId) return { status: ValidationStatus.VALID };
        return {
            status: ValidationStatus.INVALID,
            message: 'Please choose a tutor.',
        };
    }
}
