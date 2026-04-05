import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { DialogModule } from 'primeng/dialog';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { PrimeNgDialogStubComponent } from 'src/test/javascript/spec/helpers/stubs/tutorialgroup/prime-ng-dialog-stub.component';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ValidationStatus } from 'app/shared/util/validation';
import { TutorialSessionCreateOrEditModalComponent } from './tutorial-session-create-or-edit-modal.component';

describe('TutorialSessionCreateOrEditModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialSessionCreateOrEditModalComponent;
    let fixture: ComponentFixture<TutorialSessionCreateOrEditModalComponent>;

    const existingSession = new TutorialGroupSession({
        id: 17,
        start: dayjs('2026-04-20T10:15:00').toISOString(),
        end: dayjs('2026-04-20T11:45:00').toISOString(),
        location: 'Room 101',
        isCancelled: false,
        locationChanged: false,
        timeChanged: false,
        dateChanged: false,
        attendanceCount: 9,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialSessionCreateOrEditModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(TutorialSessionCreateOrEditModalComponent, {
                remove: { imports: [DialogModule] },
                add: { imports: [PrimeNgDialogStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TutorialSessionCreateOrEditModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function expectDialogHeader(expectedHeader: string) {
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.header()).toBe(expectedHeader);
    }

    function setValidCreateInputs() {
        component.date.set(new Date(2026, 3, 22));
        component.startTime.set(new Date(2026, 3, 22, 10, 15));
        component.endTime.set(new Date(2026, 3, 22, 11, 45));
        component.location.set('Room 102');
        component.attendance.set(12);
    }

    function expectClearedState() {
        expect(component.date()).toBeNull();
        expect(component.dateInputTouched()).toBe(false);
        expect(component.startTime()).toBeNull();
        expect(component.startTimeInputTouched()).toBe(false);
        expect(component.endTime()).toBeNull();
        expect(component.endTimeInputTouched()).toBe(false);
        expect(component.location()).toBe('');
        expect(component.locationInputTouched()).toBe(false);
        expect(component.attendance()).toBeNull();
    }

    it('should open in create mode with empty inputs, create header, and disabled save button', async () => {
        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isOpen()).toBe(true);
        expectDialogHeader('artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.header.create');
        expect(component.date()).toBeNull();
        expect(component.startTime()).toBeNull();
        expect(component.endTime()).toBeNull();
        expect(component.location()).toBe('');
        expect(component.attendance()).toBeNull();
        expect(component.saveButtonDisabled()).toBe(true);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.visible()).toBe(true);
    });

    it('should open in edit mode with session data, edit header, and disabled save button', async () => {
        component.open(existingSession);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isOpen()).toBe(true);
        expectDialogHeader('artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.header.edit');
        expect(component.date()).toEqual(existingSession.start.toDate());
        expect(component.startTime()).toEqual(existingSession.start.toDate());
        expect(component.endTime()).toEqual(existingSession.end.toDate());
        expect(component.location()).toBe('Room 101');
        expect(component.attendance()).toBe(9);
        expect(component.saveButtonDisabled()).toBe(true);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.visible()).toBe(true);
    });

    it('should expose the correct date validation state based on the date signal', () => {
        expect(component.dateValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.dateRequired',
        });

        component.date.set(new Date(2026, 3, 22));

        expect(component.dateValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct start time validation state based on the start time signal', () => {
        expect(component.startTimeValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.startTimeRequired',
        });

        component.startTime.set(new Date(2026, 3, 22, 10, 15));

        expect(component.startTimeValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct end time validation state based on the bound input signals', () => {
        expect(component.endTimeValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.endTimeRequired',
        });

        component.startTime.set(new Date(2026, 3, 22, 10, 15));
        component.endTime.set(new Date(2026, 3, 22, 10, 15));

        expect(component.endTimeValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.endTimeNotAfterStartTime',
        });

        component.endTime.set(new Date(2026, 3, 22, 11, 45));

        expect(component.endTimeValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should expose the correct location validation state based on the location signal', () => {
        expect(component.locationValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.locationRequired',
        });

        component.location.set('   ');

        expect(component.locationValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.locationRequired',
        });

        component.location.set('A'.repeat(256));

        expect(component.locationValidationResult()).toEqual({
            status: ValidationStatus.INVALID,
            message: 'artemisApp.pages.tutorialGroupDetail.createOrEditSessionModal.validationError.locationLength',
        });

        component.location.set('Room 102');

        expect(component.locationValidationResult()).toEqual({ status: ValidationStatus.VALID });
    });

    it('should enable the save button in create mode only when all inputs are valid', () => {
        component.open();

        expect(component.saveButtonDisabled()).toBe(true);

        component.date.set(new Date(2026, 3, 22));
        component.startTime.set(new Date(2026, 3, 22, 10, 15));
        component.endTime.set(new Date(2026, 3, 22, 10, 15));
        component.location.set('Room 102');

        expect(component.saveButtonDisabled()).toBe(true);

        component.endTime.set(new Date(2026, 3, 22, 11, 45));

        expect(component.saveButtonDisabled()).toBe(false);
    });

    it('should enable the save button in edit mode only for real changes', () => {
        component.open(existingSession);

        expect(component.saveButtonDisabled()).toBe(true);

        component.location.set('  Room 101  ');
        expect(component.saveButtonDisabled()).toBe(true);

        component.attendance.set(10);
        expect(component.saveButtonDisabled()).toBe(false);
    });

    it('should cancel, clear data, reset touched flags, and close the modal', async () => {
        component.open(existingSession);
        component.dateInputTouched.set(true);
        component.startTimeInputTouched.set(true);
        component.endTimeInputTouched.set(true);
        component.locationInputTouched.set(true);
        component.location.set('Changed room');
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('.p-button-secondary').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isOpen()).toBe(false);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.visible()).toBe(false);
        expectClearedState();
    });

    it('should emit onCreate, clear data, and close the modal when saving a new session', async () => {
        const onCreateSpy = vi.fn();
        component.onCreate.subscribe(onCreateSpy);

        component.open();
        setValidCreateInputs();
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('.p-button-primary').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(onCreateSpy).toHaveBeenCalledWith({
            date: '2026-04-22',
            startTime: '10:15',
            endTime: '11:45',
            location: 'Room 102',
            attendance: 12,
        });
        expect(component.isOpen()).toBe(false);
        expectClearedState();
    });

    it('should emit onUpdate, clear data, and close the modal when saving an edited session', async () => {
        const onUpdateSpy = vi.fn();
        component.onUpdate.subscribe(onUpdateSpy);

        component.open(existingSession);
        component.location.set('Room 202');
        component.attendance.set(11);
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('.p-button-primary').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(onUpdateSpy).toHaveBeenCalledWith({
            tutorialGroupSessionId: 17,
            updateTutorialGroupSessionRequest: {
                date: '2026-04-20',
                startTime: '10:15',
                endTime: '11:45',
                location: 'Room 202',
                attendance: 11,
            },
        });
        expect(component.isOpen()).toBe(false);
        expectClearedState();
    });
});
