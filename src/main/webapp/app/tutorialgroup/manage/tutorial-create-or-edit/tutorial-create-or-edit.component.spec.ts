import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { ValidationStatus } from 'app/shared/util/validation';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialEditLanguagesInputComponent } from 'app/tutorialgroup/manage/tutorial-edit-languages-input/tutorial-edit-languages-input.component';
import {
    CreateTutorialGroupEvent,
    TutorialCreateOrEditComponent,
    UpdateTutorialGroupEvent,
} from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { RawTutorialGroupDetailGroupDTO, TutorialGroupDetailDTO, TutorialGroupScheduleDTO, TutorialGroupTutorDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PrimeNgConfirmDialogStubComponent } from 'test/helpers/stubs/tutorialgroup/prime-ng-confirm-dialog-stub.component';

describe('TutorialCreateOrEditComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialCreateOrEditComponent;
    let fixture: ComponentFixture<TutorialCreateOrEditComponent>;

    let tutorialGroupApiService: { getUniqueLanguageValues: ReturnType<typeof vi.fn> };
    let alertService: { addErrorAlert: ReturnType<typeof vi.fn> };

    const mockTranslateService = new MockTranslateService();
    const tutors: TutorialGroupTutorDTO[] = [
        { id: 11, nameAndLogin: 'Ada Lovelace (ada)' },
        { id: 12, nameAndLogin: 'Grace Hopper (grace)' },
    ];

    beforeEach(async () => {
        tutorialGroupApiService = {
            getUniqueLanguageValues: vi.fn(),
        };
        alertService = {
            addErrorAlert: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TutorialCreateOrEditComponent, MockDirective(TranslateDirective), MockDirective(RouterLink)],
            providers: [
                { provide: TranslateService, useValue: mockTranslateService },
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiService },
                { provide: AlertService, useValue: alertService },
                { provide: ActivatedRoute, useValue: {} },
            ],
        })
            .overrideComponent(TutorialCreateOrEditComponent, {
                remove: { imports: [ConfirmDialogModule, TutorialEditLanguagesInputComponent] },
                add: { imports: [PrimeNgConfirmDialogStubComponent, MockComponent(TutorialEditLanguagesInputComponent)] },
            })
            .compileComponents();
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createTutorialGroupDetailDTO(overrides: Partial<RawTutorialGroupDetailGroupDTO> = {}): TutorialGroupDetailDTO {
        return new TutorialGroupDetailDTO({
            id: 17,
            title: 'TG 1',
            language: 'English',
            isOnline: false,
            sessions: [],
            tutorName: 'Grace Hopper',
            tutorLogin: 'grace',
            tutorImageUrl: undefined,
            capacity: 15,
            campus: 'Garching',
            groupChannelId: undefined,
            tutorChatId: undefined,
            ...overrides,
        });
    }

    function createTutorialGroupScheduleDTO(overrides: Partial<TutorialGroupScheduleDTO> = {}): TutorialGroupScheduleDTO {
        return {
            firstSessionStart: '2026-04-20T10:15:00',
            firstSessionEnd: '2026-04-20T11:45:00',
            repetitionFrequency: 2,
            tutorialPeriodEnd: '2026-07-20',
            location: 'Room 101',
            ...overrides,
        };
    }

    async function createComponentWithLanguageValues(languageValues$: Observable<string[]>) {
        tutorialGroupApiService.getUniqueLanguageValues.mockReturnValue(languageValues$);

        fixture = TestBed.createComponent(TutorialCreateOrEditComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('tutors', tutors);
        fixture.detectChanges();
        await fixture.whenStable();
    }

    function setValidGeneralInputs() {
        component.title.set('TG 2');
        component.selectedTutorId.set(11);
        component.selectedLanguage.set('English');
        component.selectedMode.set('Offline' as never);
        component.campus.set('Garching');
        component.capacity.set(20);
        component.additionalInformation.set('Bring worksheet');
    }

    function setValidScheduleInputs() {
        component.configureSessionPlan.set(true);
        component.firstSessionStart.set(new Date(2026, 3, 20, 10, 15));
        component.firstSessionEnd.set(new Date(2026, 3, 20, 11, 45));
        component.repetitionFrequency.set(2);
        component.tutorialPeriodEnd.set(new Date(2026, 6, 20));
        component.location.set('Room 101');
    }

    it('should initialize in create mode with default field values', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.isEditMode()).toBe(false);
        expect(component.title()).toBe('');
        expect(component.selectedTutorId()).toBeUndefined();
        expect(component.selectedLanguage()).toBe('');
        expect(component.selectedMode()).toBe('Offline');
        expect(component.campus()).toBe('');
        expect(component.capacity()).toBeUndefined();
        expect(component.additionalInformation()).toBe('');
        expect(component.configureSessionPlan()).toBe(false);
        expect(component.firstSessionStart()).toBeUndefined();
        expect(component.firstSessionEnd()).toBeUndefined();
        expect(component.repetitionFrequency()).toBe(1);
        expect(component.tutorialPeriodEnd()).toBeUndefined();
        expect(component.location()).toBe('');
    });

    it('should initialize in edit mode from tutorial group and schedule inputs', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        const tutorialGroup = createTutorialGroupDetailDTO();
        const schedule = createTutorialGroupScheduleDTO();

        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('schedule', schedule);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isEditMode()).toBe(true);
        expect(component.title()).toBe('TG 1');
        expect(component.selectedTutorId()).toBe(1);
        expect(component.selectedLanguage()).toBe('English');
        expect(component.selectedMode()).toBe('Offline');
        expect(component.campus()).toBe('Garching');
        expect(component.capacity()).toBe(15);
        expect(component.additionalInformation()).toBe('');
        expect(component.configureSessionPlan()).toBe(true);
        expect(component.firstSessionStart()).toEqual(dayjs(schedule.firstSessionStart).toDate());
        expect(component.firstSessionEnd()).toEqual(dayjs(schedule.firstSessionEnd).toDate());
        expect(component.repetitionFrequency()).toBe(2);
        expect(component.tutorialPeriodEnd()).toEqual(dayjs(schedule.tutorialPeriodEnd).toDate());
        expect(component.location()).toBe('Room 101');
    });

    it('should fetch already used languages once courseId is available', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(tutorialGroupApiService.getUniqueLanguageValues).toHaveBeenCalledOnce();
        expect(tutorialGroupApiService.getUniqueLanguageValues).toHaveBeenCalledWith(1, 'body');
        expect(component.alreadyUsedLanguages()).toEqual(['English', 'German']);
    });

    it('should show error alert if fetching already used languages fails', async () => {
        await createComponentWithLanguageValues(throwError(() => new Error('network error')));

        expect(tutorialGroupApiService.getUniqueLanguageValues).toHaveBeenCalledWith(1, 'body');
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.createOrEditTutorialGroup.networkError.fetchLanguages');
    });

    it('should expose the correct title validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.titleValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.titleContent',
        });

        component.title.set('@invalid');
        expect(component.titleValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.titleContent',
        });

        component.title.set('a'.repeat(20));
        expect(component.titleValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.titleLength',
        });

        component.title.set('TG 1');
        expect(component.titleValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct tutor validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.tutorValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.tutorRequired',
        });

        component.selectedTutorId.set(11);

        expect(component.tutorValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct campus validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.campusValidationResult()).toEqual({ status: ValidationStatus.VALID });

        component.campus.set('a'.repeat(256));
        expect(component.campusValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.campusLength',
        });

        component.campus.set('Garching');
        expect(component.campusValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct first session start validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.firstSessionStartValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionStartRequired',
        });

        component.firstSessionStart.set(new Date(2026, 3, 20, 10, 15));

        expect(component.firstSessionStartValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct first session end validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.firstSessionEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndRequired',
        });

        component.firstSessionStart.set(new Date(2026, 3, 20, 10, 15));
        component.firstSessionEnd.set(new Date(2026, 3, 20, 10, 15));
        expect(component.firstSessionEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndNotAfterStart',
        });

        component.firstSessionEnd.set(new Date(2026, 3, 21, 11, 45));
        expect(component.firstSessionEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.firstSessionEndNotOnSameDayAsStart',
        });

        component.firstSessionEnd.set(new Date(2026, 3, 20, 11, 45));
        expect(component.firstSessionEndValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct tutorial period end validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.tutorialPeriodEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodRequired',
        });

        component.firstSessionStart.set(new Date(2026, 3, 20, 10, 15));
        component.tutorialPeriodEnd.set(new Date(2026, 3, 20, 9, 0));
        expect(component.tutorialPeriodEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionStart',
        });

        component.firstSessionEnd.set(new Date(2026, 3, 20, 11, 45));
        component.tutorialPeriodEnd.set(new Date(2026, 3, 20, 11, 0));
        expect(component.tutorialPeriodEndValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.teachingPeriodNotAfterFirstSessionEnd',
        });

        component.tutorialPeriodEnd.set(new Date(2026, 6, 20));
        expect(component.tutorialPeriodEndValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct location validation state', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.locationValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.createOrEditTutorialGroup.validationError.locationRequired',
        });

        component.location.set('Room 101');

        expect(component.locationValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should disable the save button in create mode until all required active inputs are valid', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        expect(component.saveButtonDisabled()).toBe(true);

        setValidGeneralInputs();
        expect(component.saveButtonDisabled()).toBe(false);

        component.configureSessionPlan.set(true);
        expect(component.saveButtonDisabled()).toBe(true);

        setValidScheduleInputs();
        expect(component.saveButtonDisabled()).toBe(false);
    });

    it('should disable the save button in edit mode until something changes', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        const tutorialGroup = createTutorialGroupDetailDTO();
        const schedule = createTutorialGroupScheduleDTO();

        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('schedule', schedule);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isEditMode()).toBe(true);
        expect(component.saveButtonDisabled()).toBe(true);

        component.additionalInformation.set('Updated info');
        expect(component.saveButtonDisabled()).toBe(false);
    });

    it('should emit onCreate with assembled dto when saving a new tutorial group', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        const onCreateSpy = vi.fn<(event: CreateTutorialGroupEvent) => void>();
        component.onCreate.subscribe(onCreateSpy);

        setValidGeneralInputs();
        setValidScheduleInputs();
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="save-button"]').click();

        expect(onCreateSpy).toHaveBeenCalledWith({
            courseId: 1,
            createTutorialGroupDTO: {
                title: 'TG 2',
                tutorId: 11,
                language: 'English',
                isOnline: false,
                campus: 'Garching',
                capacity: 20,
                additionalInformation: 'Bring worksheet',
                tutorialGroupScheduleDTO: {
                    firstSessionStart: '2026-04-20T10:15:00',
                    firstSessionEnd: '2026-04-20T11:45:00',
                    repetitionFrequency: 2,
                    tutorialPeriodEnd: '2026-07-20',
                    location: 'Room 101',
                },
            },
        });
    });

    it('should emit onUpdate directly when saving an edited tutorial group without schedule overwrite confirmation', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        const tutorialGroup = createTutorialGroupDetailDTO();
        const onUpdateSpy = vi.fn<(event: UpdateTutorialGroupEvent) => void>();
        component.onUpdate.subscribe(onUpdateSpy);

        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        await fixture.whenStable();

        component.title.set('TG Updated');
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="save-button"]').click();

        expect(onUpdateSpy).toHaveBeenCalledWith({
            courseId: 1,
            tutorialGroupId: 17,
            updateTutorialGroupDTO: {
                title: 'TG Updated',
                tutorId: 1,
                language: 'English',
                isOnline: false,
                campus: 'Garching',
                capacity: 15,
                additionalInformation: undefined,
                tutorialGroupScheduleDTO: undefined,
            },
        });
    });

    it('should confirm schedule-overwriting update and emit on accept', async () => {
        await createComponentWithLanguageValues(of(['English', 'German']));

        const tutorialGroup = createTutorialGroupDetailDTO();
        const schedule = createTutorialGroupScheduleDTO();
        const onUpdateSpy = vi.fn<(event: UpdateTutorialGroupEvent) => void>();
        component.onUpdate.subscribe(onUpdateSpy);

        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('schedule', schedule);
        fixture.detectChanges();
        await fixture.whenStable();

        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirmSpy = vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation: { accept?: () => void }) => {
            confirmation.accept?.();
            return confirmationService;
        });

        component.location.set('Room 202');
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="save-button"]').click();

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(onUpdateSpy).toHaveBeenCalledWith({
            courseId: 1,
            tutorialGroupId: 17,
            updateTutorialGroupDTO: {
                title: 'TG 1',
                tutorId: 1,
                language: 'English',
                isOnline: false,
                campus: 'Garching',
                capacity: 15,
                additionalInformation: undefined,
                tutorialGroupScheduleDTO: {
                    firstSessionStart: '2026-04-20T10:15:00',
                    firstSessionEnd: '2026-04-20T11:45:00',
                    repetitionFrequency: 2,
                    tutorialPeriodEnd: '2026-07-20',
                    location: 'Room 202',
                },
            },
        });
    });
});
