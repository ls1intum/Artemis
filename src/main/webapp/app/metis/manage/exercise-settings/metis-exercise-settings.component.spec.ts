import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDatePickerComponent, TumAetUiInputNumberComponent } from '@tumaet/ui-angular';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MetisExerciseSettingsComponent } from 'app/metis/manage/exercise-settings/metis-exercise-settings.component';
import { MetisExerciseConfigDTO, MetisGroupWindowDTO, MetisTutorialGroupDTO } from 'app/metis/shared/entities/metis.model';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MetisExerciseSettingsComponent', () => {
    let fixture: ComponentFixture<MetisExerciseSettingsComponent>;
    let component: MetisExerciseSettingsComponent;
    let metisService: { config: Mock; groupWindows: Mock; tutorialGroups: Mock; saveConfig: Mock; saveGroupWindows: Mock };
    let translateService: MockTranslateService;

    const exerciseId = 7;
    const courseId = 3;
    const tutorialGroups: MetisTutorialGroupDTO[] = [
        { id: 1, title: 'Gruppe 1' },
        { id: 2, title: 'Gruppe 2' },
    ];
    let config: MetisExerciseConfigDTO;
    let groupWindows: MetisGroupWindowDTO[];

    beforeEach(async () => {
        config = {
            exerciseId,
            enabled: true,
            windowStart: dayjs('2026-11-03T10:00'),
            windowEnd: dayjs('2026-11-03T10:20'),
            mainQuestionCount: 3,
            maxFollowUpsPerQuestion: 2,
            minSecondsPerQuestion: 60,
            maxSecondsPerQuestion: 300,
            answerLength: 3,
            complexity: 3,
            likertScalePoints: 5,
            followUpThreshold: 3,
            reviewRequired: false,
        };
        groupWindows = [{ id: 11, exerciseId, tutorialGroupId: 1, windowStart: dayjs('2026-11-03T10:15'), windowEnd: dayjs('2026-11-03T10:35') }];
        // The reads hand out what the server stores; the writes echo what they were given, as the server answers a stored value.
        metisService = {
            config: vi.fn().mockImplementation(() => of(config)),
            groupWindows: vi.fn().mockImplementation(() => of(groupWindows)),
            tutorialGroups: vi.fn().mockImplementation(() => of(tutorialGroups)),
            saveConfig: vi.fn().mockImplementation((saved: MetisExerciseConfigDTO) => of(saved)),
            saveGroupWindows: vi.fn().mockImplementation((_exerciseId: number, saved: MetisGroupWindowDTO[]) => of(saved)),
        };

        await TestBed.configureTestingModule({
            imports: [MetisExerciseSettingsComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
                { provide: MetisService, useValue: metisService },
            ],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        await createComponent();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Creates the section for the exercise; it reads what the service answers at that moment. */
    async function createComponent(): Promise<void> {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = exerciseId;
        exercise.dueDate = dayjs('2026-11-02T23:59');

        fixture = TestBed.createComponent(MetisExerciseSettingsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.detectChanges();
        // NgModel writes the initial values into the TUM AET UI controls asynchronously.
        await fixture.whenStable();
        fixture.detectChanges();
    }

    /** Creates the section again after the test changed what the service answers. */
    async function recreateComponent(): Promise<void> {
        fixture.destroy();
        await createComponent();
    }

    function query<T extends HTMLElement>(selector: string): T | null {
        return fixture.nativeElement.querySelector(selector);
    }

    /** The date picker whose input carries the id. */
    function datePicker(inputId: string): TumAetUiDatePickerComponent {
        return fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent)).find((picker) => picker.componentInstance.inputId() === inputId)!.componentInstance;
    }

    function queryAll<T extends HTMLElement>(selector: string): T[] {
        return Array.from(fixture.nativeElement.querySelectorAll(selector));
    }

    /** Opens the select whose trigger carries the id and clicks the option at the index; the option list is rendered in an overlay outside the fixture. */
    function selectOption(triggerId: string, index: number): void {
        query<HTMLButtonElement>(`#${triggerId}`)!.click();
        fixture.detectChanges();
        const options = Array.from(document.querySelectorAll<HTMLElement>('[role="listbox"] [role="option"]'));
        options[index].click();
        fixture.detectChanges();
    }

    it('should load the config, the group windows and the tutorial groups', () => {
        expect(metisService.config).toHaveBeenCalledWith(exerciseId);
        expect(metisService.groupWindows).toHaveBeenCalledWith(exerciseId);
        expect(metisService.tutorialGroups).toHaveBeenCalledWith(courseId);
        expect(component['config']()).toEqual(config);
        expect(component['windowStart']()?.isSame(config.windowStart)).toBe(true);
        expect(component['windowEnd']()?.isSame(config.windowEnd)).toBe(true);
        expect(component['groupWindows']()).toEqual(groupWindows);
        expect(component['tutorialGroups']()).toEqual(tutorialGroups);
        expect(component.isCheckEnabled()).toBe(true);
        expect(component.isFormValid()).toBe(true);
    });

    it('should carry the status-bar id on its heading', () => {
        expect(query('h3')?.id).toBe('artemisApp.programmingExercise.wizardMode.detailedSteps.metisStepTitle');
    });

    it('should hide the cards while the check is disabled and write the toggle to the service', () => {
        const saveSpy = metisService.saveConfig;
        expect(query('[data-testid="metis-window-card"]')).not.toBeNull();
        expect(query('[data-testid="metis-info-banner"]')).not.toBeNull();

        query<HTMLInputElement>('[data-testid="metis-enabled-toggle"] input')!.click();
        fixture.detectChanges();

        expect(component['config']().enabled).toBe(false);
        expect(component.isCheckEnabled()).toBe(false);
        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][0].enabled).toBe(false);
        expect(query('[data-testid="metis-window-card"]')).toBeNull();
        expect(query('[data-testid="metis-questions-card"]')).toBeNull();
        expect(query('[data-testid="metis-timer-card"]')).toBeNull();
        expect(query('[data-testid="metis-rating-card"]')).toBeNull();
        expect(query('[data-testid="metis-review-card"]')).toBeNull();
        expect(query('[data-testid="metis-info-banner"]')).toBeNull();
        expect(query('[data-testid="metis-enabled-toggle"]')).not.toBeNull();
    });

    it('should switch the check off with the last written limits while the shortest one exceeds the longest', () => {
        component['onSecondsChange']('minSecondsPerQuestion', 400);
        expect(component.isFormValid()).toBe(false);
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        query<HTMLInputElement>('[data-testid="metis-enabled-toggle"] input')!.click();
        fixture.detectChanges();

        // The server refuses limits out of order whether or not the check runs, so the switch-off carries the limits it stored.
        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0]).toMatchObject({ enabled: false, minSecondsPerQuestion: 60, maxSecondsPerQuestion: 300 });
        expect(component['config']()).toMatchObject({ enabled: false, minSecondsPerQuestion: 60, maxSecondsPerQuestion: 300 });
        expect(component.isMinAboveMax()).toBe(false);
        expect(component.isFormValid()).toBe(true);
        expect(query('[data-testid="metis-timer-card"]')).toBeNull();
    });

    it('should switch the check off with the last written window while the window ends before it starts', () => {
        component['windowEnd'].set(dayjs('2026-11-03T09:00'));
        component['persistConfig']();
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        component['updateConfig']({ enabled: false });

        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        const written: MetisExerciseConfigDTO = metisService.saveConfig.mock.calls[0][0];
        expect(written.enabled).toBe(false);
        expect(written.windowStart?.isSame(config.windowStart)).toBe(true);
        expect(written.windowEnd?.isSame(config.windowEnd)).toBe(true);
        expect(component['windowEnd']()?.isSame(config.windowEnd)).toBe(true);
        expect(component.isEndBeforeStart()).toBe(false);
        expect(component.isFormValid()).toBe(true);
    });

    it('should switch the check off and show the pickers fresh again while a window text is not a date yet', () => {
        component['isStartTextValid'].set(false);
        component['updateConfig']({ complexity: 5 });
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        component['updateConfig']({ enabled: false });
        fixture.detectChanges();

        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        // The unwritten change goes as well: the form shows what the server holds.
        expect(metisService.saveConfig.mock.calls[0][0]).toMatchObject({ enabled: false, complexity: 3 });
        expect(component['isStartTextValid']()).toBe(true);
        expect(query('[data-testid="metis-window-card"]')).toBeNull();

        component['updateConfig']({ enabled: true });
        fixture.detectChanges();

        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.saveConfig.mock.calls[1][0]).toMatchObject({ enabled: true, complexity: 3 });
        expect(query('[data-testid="metis-window-card"]')).not.toBeNull();
        expect(component.isFormValid()).toBe(true);
    });

    it('should switch the check off on top of a write that is still on its way', () => {
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);
        component['updateConfig']({ mainQuestionCount: 4 });
        component['onSecondsChange']('maxSecondsPerQuestion', 4000);

        component['updateConfig']({ enabled: false });
        firstWrite.next(metisService.saveConfig.mock.calls[0][0]);
        firstWrite.complete();

        // The server has not confirmed the first write when the check is switched off; the switch-off must not undo it.
        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.saveConfig.mock.calls[1][0]).toMatchObject({ enabled: false, mainQuestionCount: 4, maxSecondsPerQuestion: 300 });
    });

    it.each([
        ['before', dayjs('2026-11-03T09:00')],
        ['equal to', dayjs('2026-11-03T10:00')],
    ])('should reject a window end %s its start and say why', (_, windowEnd) => {
        component['windowEnd'].set(windowEnd);
        fixture.detectChanges();

        expect(component.isEndBeforeStart()).toBe(true);
        expect(component.isFormValid()).toBe(false);
        expect(query('[data-testid="metis-window-order-error"]')).not.toBeNull();
    });

    it('should accept an empty window', () => {
        component['windowStart'].set(undefined);
        component['windowEnd'].set(undefined);
        fixture.detectChanges();

        expect(component.isEndBeforeStart()).toBe(false);
        expect(component.isFormValid()).toBe(true);
        expect(query('[data-testid="metis-window-order-error"]')).toBeNull();
    });

    it('should report typed text that is not a date yet as invalid', () => {
        const startField = query<HTMLInputElement>('#metis-window-start')!;

        startField.value = '03.11.';
        startField.dispatchEvent(new Event('input'));
        startField.dispatchEvent(new Event('blur'));
        fixture.detectChanges();

        expect(component.isFormValid()).toBe(false);
    });

    it('should write the window to the service when a picker commits a date', () => {
        const saveSpy = metisService.saveConfig;
        const endField = query<HTMLInputElement>('#metis-window-end')!;

        endField.value = '03.11.2026 11:00';
        endField.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component['windowEnd']()?.isSame(dayjs('2026-11-03T11:00'))).toBe(true);
        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][0].windowEnd?.isSame(dayjs('2026-11-03T11:00'))).toBe(true);
        expect(component['config']().windowEnd?.isSame(dayjs('2026-11-03T11:00'))).toBe(true);
    });

    it('should invalidate the form when the shortest limit exceeds the longest, say why and write nothing', () => {
        const saveSpy = metisService.saveConfig;
        const minField = query<HTMLInputElement>('[data-testid="metis-min-seconds"] input')!;

        minField.value = '400';
        minField.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component['config']().minSecondsPerQuestion).toBe(400);
        expect(component.isMinAboveMax()).toBe(true);
        expect(component.isFormValid()).toBe(false);
        expect(query('[data-testid="metis-timer-order-error"]')).not.toBeNull();
        expect(saveSpy).not.toHaveBeenCalled();

        // Raising the longest limit makes the pair valid again, and the next write carries both.
        component['onSecondsChange']('maxSecondsPerQuestion', 600);

        expect(component.isFormValid()).toBe(true);
        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][0]).toMatchObject({ minSecondsPerQuestion: 400, maxSecondsPerQuestion: 600 });
    });

    it.each([
        ['shortest', 'minSecondsPerQuestion', 20],
        ['longest', 'maxSecondsPerQuestion', 4000],
    ] as const)('should write nothing while the %s limit lies outside the range of the server', (_, field, seconds) => {
        component['onSecondsChange'](field, seconds);
        fixture.detectChanges();

        expect(component['config']()[field]).toBe(seconds);
        expect(component.isFormValid()).toBe(false);
        expect(metisService.saveConfig).not.toHaveBeenCalled();
    });

    it('should write nothing while the window end lies before its start', () => {
        component['windowEnd'].set(dayjs('2026-11-03T09:00'));
        component['persistConfig']();

        expect(component.isFormValid()).toBe(false);
        expect(metisService.saveConfig).not.toHaveBeenCalled();
    });

    it('should write the common window once the text of its picker is a date again', () => {
        const startPicker = datePicker('metis-window-start');
        startPicker.inputValidityChange.emit(false);
        expect(component.isFormValid()).toBe(false);

        // The picker reports the date that its text became before it reports the text valid again
        startPicker.value.set(dayjs('2026-11-03T09:50'));
        expect(metisService.saveConfig).not.toHaveBeenCalled();
        startPicker.inputValidityChange.emit(true);

        expect(component.isFormValid()).toBe(true);
        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0].windowStart?.isSame(dayjs('2026-11-03T09:50'))).toBe(true);
    });

    it('should write no group window while the text of its picker is not a date, and the windows once it is', () => {
        const startPicker = datePicker('metis-group-window-start-1');
        startPicker.inputValidityChange.emit(false);
        fixture.detectChanges();
        expect(component.isFormValid()).toBe(false);
        // Text that is not a date is no window that ends before it starts
        expect(query('[data-testid="metis-group-window-order-error"]')).toBeNull();

        startPicker.value.set(dayjs('2026-11-03T10:10'));
        expect(metisService.saveGroupWindows).not.toHaveBeenCalled();
        startPicker.inputValidityChange.emit(true);

        expect(component.isFormValid()).toBe(true);
        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        expect(metisService.saveGroupWindows.mock.calls[0][1][0].windowStart.isSame(dayjs('2026-11-03T10:10'))).toBe(true);
    });

    it('should write nothing while a window text is not a date yet', () => {
        component['isStartTextValid'].set(false);
        component['updateConfig']({ mainQuestionCount: 4 });

        expect(component.isFormValid()).toBe(false);
        expect(metisService.saveConfig).not.toHaveBeenCalled();
    });

    it('should write nothing while the hint is longer than the server stores', () => {
        const hintField = query<HTMLTextAreaElement>('[data-testid="metis-instructor-hint"]')!;
        // The browser stops typing at the limit; a value set from code still has to be kept from the server.
        hintField.value = 'x'.repeat(2001);
        hintField.dispatchEvent(new Event('input'));
        hintField.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        expect(component['config']().instructorHint).toHaveLength(2001);
        expect(component.isFormValid()).toBe(false);
        expect(metisService.saveConfig).not.toHaveBeenCalled();
    });

    it('should carry the limits of the server on the inputs', () => {
        const numberInputs = fixture.debugElement
            .queryAll(By.directive(TumAetUiInputNumberComponent))
            .map((debugElement) => debugElement.componentInstance as TumAetUiInputNumberComponent);

        expect(numberInputs).toHaveLength(2);
        expect(numberInputs.map((input) => input.min())).toEqual([30, 30]);
        expect(numberInputs.map((input) => input.max())).toEqual([3600, 3600]);
        expect(query('[data-testid="metis-instructor-hint"]')!.getAttribute('maxlength')).toBe('2000');
    });

    it('should send the writes one after another in the order they were made', () => {
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);

        component['updateConfig']({ mainQuestionCount: 4 });
        component['updateConfig']({ mainQuestionCount: 5 });

        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0].mainQuestionCount).toBe(4);

        firstWrite.next(metisService.saveConfig.mock.calls[0][0]);
        firstWrite.complete();

        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.saveConfig.mock.calls[1][0].mainQuestionCount).toBe(5);
    });

    it('should still send the writes it holds when the section is closed', () => {
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        const firstWindowWrite = new Subject<MetisGroupWindowDTO[]>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);
        metisService.saveGroupWindows.mockReturnValueOnce(firstWindowWrite);
        component['updateConfig']({ mainQuestionCount: 4 });
        component['updateConfig']({ complexity: 5 });
        component['selectedGroupId'].set(2);
        component['addGroupWindow']();
        component['removeGroupWindow'](component['groupWindows']()[0]);
        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        // The window writes wait behind the config writes made before them
        expect(metisService.saveGroupWindows).not.toHaveBeenCalled();

        // The parent's Save leaves the page as soon as the exercise is stored, and its request carries none of these settings.
        fixture.destroy();

        // The write on its way is not cancelled ...
        expect(firstWrite.observed).toBe(true);
        firstWrite.next(metisService.saveConfig.mock.calls[0][0]);
        firstWrite.complete();

        // ... and the ones waiting behind it are still sent, in order.
        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.saveConfig.mock.calls[1][0]).toMatchObject({ exerciseId, mainQuestionCount: 4, complexity: 5 });
        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        expect(firstWindowWrite.observed).toBe(true);
        firstWindowWrite.next(metisService.saveGroupWindows.mock.calls[0][1]);
        firstWindowWrite.complete();
        expect(metisService.saveGroupWindows).toHaveBeenCalledTimes(2);
        expect(metisService.saveGroupWindows.mock.calls[1][1].map((groupWindow: MetisGroupWindowDTO) => groupWindow.tutorialGroupId)).toEqual([2]);

        // A closed section takes no new writes.
        component['updateConfig']({ answerLength: 4 });
        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
    });

    it('should let a section opened again read and write only after the writes of the closed one', async () => {
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);
        component['updateConfig']({ mainQuestionCount: 4 });
        component['updateConfig']({ mainQuestionCount: 5 });
        metisService.config.mockClear();
        metisService.groupWindows.mockClear();

        // The editor switches the form to the simple mode and back while the writes are on their way
        await recreateComponent();

        // Read now, the stored state would miss the two writes, and they would overwrite what is changed on top of it
        expect(metisService.config).not.toHaveBeenCalled();
        config = { ...config, mainQuestionCount: 5 };
        firstWrite.next(metisService.saveConfig.mock.calls[0][0]);
        firstWrite.complete();
        fixture.detectChanges();

        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.config).toHaveBeenCalledOnce();
        expect(metisService.groupWindows).toHaveBeenCalledOnce();
        expect(component['config']().mainQuestionCount).toBe(5);

        component['updateConfig']({ complexity: 2 });

        expect(metisService.saveConfig).toHaveBeenCalledTimes(3);
        expect(metisService.saveConfig.mock.calls[2][0]).toMatchObject({ mainQuestionCount: 5, complexity: 2 });
    });

    it('should bring the config back to what the server stored after a refused config write and drop the config writes queued meanwhile', () => {
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);
        component['updateConfig']({ mainQuestionCount: 4 });
        component['updateConfig']({ complexity: 5 });
        const windowsOfTheForm = component['groupWindows']();
        metisService.config.mockClear();
        metisService.groupWindows.mockClear();
        // Meanwhile the server stores other values; the form must show them, not the refused ones.
        config = { ...config, mainQuestionCount: 2, windowStart: dayjs('2026-11-04T10:00'), windowEnd: dayjs('2026-11-04T10:20') };

        firstWrite.error(new HttpErrorResponse({ status: 400, error: { errorKey: 'metis.timeLimitOrder' } }));
        fixture.detectChanges();

        expect(metisService.config).toHaveBeenCalledOnce();
        expect(metisService.config).toHaveBeenCalledWith(exerciseId);
        expect(component['config']().mainQuestionCount).toBe(2);
        expect(component['config']().complexity).toBe(3);
        expect(component['windowStart']()?.isSame(dayjs('2026-11-04T10:00'))).toBe(true);
        expect(component['windowEnd']()?.isSame(dayjs('2026-11-04T10:20'))).toBe(true);
        // The group windows are another part of the form, which the refused write did not store
        expect(metisService.groupWindows).not.toHaveBeenCalled();
        expect(component['groupWindows']()).toBe(windowsOfTheForm);
        expect(component['scaleLocked']()).toBe(false);
        // The write that waited behind the refused one still carried the refused value, so it is not sent.
        expect(metisService.saveConfig).toHaveBeenCalledOnce();

        // The queue goes on with the next change, made on top of the stored state.
        component['updateConfig']({ answerLength: 4 });

        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
        expect(metisService.saveConfig.mock.calls[1][0]).toMatchObject({ mainQuestionCount: 2, complexity: 3, answerLength: 4 });
    });

    it('should drop only the writes of the refused part that were queued behind it', () => {
        const firstWindowWrite = new Subject<MetisGroupWindowDTO[]>();
        metisService.saveGroupWindows.mockReturnValueOnce(firstWindowWrite);
        component['selectedGroupId'].set(2);
        component['addGroupWindow']();
        component['removeGroupWindow'](component['groupWindows']()[0]);
        component['updateConfig']({ mainQuestionCount: 4 });
        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        firstWindowWrite.error(new HttpErrorResponse({ status: 400, error: { errorKey: 'metis.tutorialGroupNotInCourse' } }));

        // The windows come back to what the server stored, and the removal queued before that reset is not sent ...
        expect(component['groupWindows']()).toEqual(groupWindows);
        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        // ... while the config change, which the reset left alone, still goes out
        expect(component['config']().mainQuestionCount).toBe(4);
        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0]).toMatchObject({ mainQuestionCount: 4 });
    });

    it('should send a group window write that waited behind a refused config write, and keep the window', () => {
        // The queue sends one request at a time, so the window write waits for the config write and the reload after its refusal, which brings back the config only.
        const firstWrite = new Subject<MetisExerciseConfigDTO>();
        metisService.saveConfig.mockReturnValueOnce(firstWrite);
        component['updateConfig']({ mainQuestionCount: 4 });
        component['selectedGroupId'].set(2);
        component['addGroupWindow']();

        expect(metisService.saveGroupWindows).not.toHaveBeenCalled();

        metisService.groupWindows.mockClear();
        firstWrite.error(new HttpErrorResponse({ status: 500 }));
        fixture.detectChanges();

        // The refusal brings back the config only; the window goes out afterwards, and the form keeps it.
        expect(metisService.groupWindows).not.toHaveBeenCalled();
        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        expect(metisService.saveGroupWindows.mock.calls[0][1].map((groupWindow: MetisGroupWindowDTO) => groupWindow.tutorialGroupId)).toEqual([1, 2]);
        expect(component['groupWindows']().map((groupWindow) => groupWindow.tutorialGroupId)).toEqual([1, 2]);
    });

    it('should bring the group windows back to what the server stored after a refused write', () => {
        metisService.saveGroupWindows.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { errorKey: 'metis.tutorialGroupNotInCourse' } })));
        component['selectedGroupId'].set(2);

        component['addGroupWindow']();
        fixture.detectChanges();

        expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
        expect(component['groupWindows']()).toEqual(groupWindows);
        expect(queryAll('[data-testid="metis-group-window-row"]')).toHaveLength(1);
    });

    it('should keep the form as it is when even the stored state cannot be read after a refused write', () => {
        metisService.saveConfig.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        metisService.config.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

        component['updateConfig']({ mainQuestionCount: 4 });
        component['updateConfig']({ mainQuestionCount: 5 });

        expect(component['config']().mainQuestionCount).toBe(5);
        expect(metisService.saveConfig).toHaveBeenCalledTimes(2);
    });

    describe('when the edit page hands over another exercise', () => {
        const otherExerciseId = 8;
        let otherConfig: MetisExerciseConfigDTO;
        let otherGroupWindows: MetisGroupWindowDTO[];

        beforeEach(() => {
            otherConfig = { ...config, exerciseId: otherExerciseId, mainQuestionCount: 5, likertScalePoints: 7, followUpThreshold: 4 };
            otherGroupWindows = [{ id: 21, exerciseId: otherExerciseId, tutorialGroupId: 2, windowStart: dayjs('2026-11-04T09:00'), windowEnd: dayjs('2026-11-04T09:30') }];
            metisService.config.mockImplementation((id: number) => of(id === otherExerciseId ? otherConfig : config));
            metisService.groupWindows.mockImplementation((id: number) => of(id === otherExerciseId ? otherGroupWindows : groupWindows));
        });

        /** The edit page reuses itself when its route moves to another exercise, and hands that exercise to the section. */
        function handOverOtherExercise(): void {
            const other = new ProgrammingExercise(undefined, undefined);
            other.id = otherExerciseId;
            other.dueDate = dayjs('2026-11-03T23:59');
            fixture.componentRef.setInput('exercise', other);
            fixture.detectChanges();
        }

        it('should read the settings of the other exercise and write its changes to it', () => {
            handOverOtherExercise();

            expect(metisService.config).toHaveBeenLastCalledWith(otherExerciseId);
            expect(metisService.groupWindows).toHaveBeenLastCalledWith(otherExerciseId);
            expect(component['config']()).toEqual(otherConfig);
            expect(component['groupWindows']()).toEqual(otherGroupWindows);

            component['updateConfig']({ mainQuestionCount: 4 });
            component['removeGroupWindow'](otherGroupWindows[0]);

            expect(metisService.saveConfig).toHaveBeenCalledOnce();
            expect(metisService.saveConfig.mock.calls[0][0]).toEqual({ ...otherConfig, mainQuestionCount: 4 });
            expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
            expect(metisService.saveGroupWindows).toHaveBeenCalledWith(otherExerciseId, []);
        });

        it('should send the writes still queued for the exercise before to it, and show the other one only once they are stored', () => {
            const write = new Subject<MetisGroupWindowDTO[]>();
            metisService.saveGroupWindows.mockReturnValueOnce(write);
            component['removeGroupWindow'](groupWindows[0]);

            handOverOtherExercise();

            // The other exercise is read after the write, and nothing is written for it before its settings are in the form
            expect(metisService.config).not.toHaveBeenCalledWith(otherExerciseId);
            expect(component['configLoaded']()).toBe(false);
            expect(component['groupWindowsLoaded']()).toBe(false);
            expect(component['groupWindows']()).toEqual([]);
            component['updateConfig']({ mainQuestionCount: 4 });
            expect(metisService.saveConfig).not.toHaveBeenCalled();

            write.next([]);
            write.complete();

            expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
            expect(metisService.saveGroupWindows).toHaveBeenCalledWith(exerciseId, []);
            expect(component['config']()).toEqual(otherConfig);
            expect(component['groupWindows']()).toEqual(otherGroupWindows);
        });

        it('should send a write that waits behind another request to the exercise it was made for, also once the form holds the other one', () => {
            const configWrite = new Subject<MetisExerciseConfigDTO>();
            metisService.saveConfig.mockReturnValueOnce(configWrite);
            component['updateConfig']({ mainQuestionCount: 4 });
            // Queued behind the config write that is still on its way
            component['removeGroupWindow'](groupWindows[0]);

            handOverOtherExercise();
            expect(metisService.saveGroupWindows).not.toHaveBeenCalled();
            configWrite.next({ ...config, mainQuestionCount: 4 });
            configWrite.complete();

            // The windows of exercise 7 replace those of exercise 7, not those of the exercise the form holds by then
            expect(metisService.saveGroupWindows).toHaveBeenCalledOnce();
            expect(metisService.saveGroupWindows).toHaveBeenCalledWith(exerciseId, []);
            expect(component['config']()).toEqual(otherConfig);
            expect(component['groupWindows']()).toEqual(otherGroupWindows);
        });

        it('should keep a read of the exercise before that comes back late out of the form of the other exercise', async () => {
            const lateConfig = new Subject<MetisExerciseConfigDTO>();
            const lateGroupWindows = new Subject<MetisGroupWindowDTO[]>();
            metisService.config.mockImplementation((id: number) => (id === otherExerciseId ? of(otherConfig) : lateConfig));
            metisService.groupWindows.mockImplementation((id: number) => (id === otherExerciseId ? of(otherGroupWindows) : lateGroupWindows));
            await recreateComponent();

            handOverOtherExercise();
            lateConfig.next(config);

            // The settings of exercise 7 never reach the form, which waits for those of exercise 8
            expect(component['configLoaded']()).toBe(false);
            expect(component['config']()).not.toEqual(config);
            lateConfig.complete();
            lateGroupWindows.next(groupWindows);
            lateGroupWindows.complete();

            expect(component['config']()).toEqual(otherConfig);
            expect(component['groupWindows']()).toEqual(otherGroupWindows);
        });

        it('should keep group windows of the exercise before that come back late out of the form of the other exercise', async () => {
            const lateGroupWindows = new Subject<MetisGroupWindowDTO[]>();
            metisService.groupWindows.mockImplementation((id: number) => (id === otherExerciseId ? of(otherGroupWindows) : lateGroupWindows));
            await recreateComponent();
            // The read of the windows of exercise 7 is on its way when the edit page hands over exercise 8
            expect(lateGroupWindows.observed).toBe(true);

            handOverOtherExercise();
            lateGroupWindows.next(groupWindows);

            // The windows of exercise 7 never reach the form, which waits for those of exercise 8
            expect(component['groupWindowsLoaded']()).toBe(false);
            expect(component['groupWindows']()).toEqual([]);
            lateGroupWindows.complete();

            expect(component['config']()).toEqual(otherConfig);
            expect(component['groupWindows']()).toEqual(otherGroupWindows);
        });

        it('should leave the form of the other exercise alone when a write for the exercise before is refused', () => {
            const write = new Subject<MetisExerciseConfigDTO>();
            metisService.saveConfig.mockReturnValueOnce(write);
            component['updateConfig']({ likertScalePoints: 7 });

            handOverOtherExercise();
            write.error(new HttpErrorResponse({ status: 409, error: { errorKey: 'metis.scaleLocked', message: 'error.metis.scaleLocked' } }));

            expect(component['scaleLocked']()).toBe(false);
            expect(component['config']()).toEqual(otherConfig);
            // Only the first read of the exercise before; its stored state is not read into the form again
            expect(metisService.config.mock.calls.filter(([id]) => id === exerciseId)).toHaveLength(1);
        });
    });

    it('should lock the rating scale once the server refuses to change it', () => {
        metisService.saveConfig.mockReturnValueOnce(
            throwError(() => new HttpErrorResponse({ status: 409, error: { errorKey: 'metis.scaleLocked', message: 'error.metis.scaleLocked' } })),
        );
        expect(query<HTMLButtonElement>('#metis-likert-points')!.disabled).toBe(false);
        expect(query('[data-testid="metis-scale-locked"]')).toBeNull();

        selectOption('metis-likert-points', 1);
        fixture.detectChanges();

        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0].likertScalePoints).toBe(7);
        expect(component['scaleLocked']()).toBe(true);
        expect(component['config']().likertScalePoints).toBe(5);
        expect(query<HTMLButtonElement>('#metis-likert-points')!.disabled).toBe(true);
        expect(query('[data-testid="metis-scale-locked"]')).not.toBeNull();
    });

    it('should ignore a cleared time limit', () => {
        const saveSpy = metisService.saveConfig;

        component['onSecondsChange']('maxSecondsPerQuestion', undefined);

        expect(component['config']().maxSecondsPerQuestion).toBe(300);
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should add a window for the selected group and remove it again', () => {
        const saveSpy = metisService.saveGroupWindows;
        expect(queryAll('[data-testid="metis-group-window-row"]')).toHaveLength(1);
        // Gruppe 1 already has a row, so the select offers Gruppe 2 only.
        expect(component['availableGroups']()).toEqual([tutorialGroups[1]]);

        selectOption('metis-group-select', 0);
        expect(component['selectedGroupId']()).toBe(2);

        query<HTMLButtonElement>('[data-testid="metis-add-group-window"]')!.click();
        fixture.detectChanges();

        expect(queryAll('[data-testid="metis-group-window-row"]')).toHaveLength(2);
        expect(saveSpy).toHaveBeenCalledOnce();
        const [savedExerciseId, savedWindows] = saveSpy.mock.calls[0];
        expect(savedExerciseId).toBe(exerciseId);
        expect(savedWindows).toHaveLength(2);
        expect(savedWindows[1].tutorialGroupId).toBe(2);
        expect(savedWindows[1].windowStart.isSame(config.windowStart)).toBe(true);
        expect(savedWindows[1].windowEnd.isSame(config.windowEnd)).toBe(true);
        expect(component['selectedGroupId']()).toBeUndefined();
        // Every group has a row now, so there is nothing left to add.
        expect(query('[data-testid="metis-add-group-window"]')).toBeNull();

        queryAll<HTMLButtonElement>('[data-testid="metis-remove-group-window"]')[1].click();
        fixture.detectChanges();

        expect(queryAll('[data-testid="metis-group-window-row"]')).toHaveLength(1);
        expect(saveSpy).toHaveBeenCalledTimes(2);
        expect(saveSpy.mock.calls[1][1]).toHaveLength(1);
        expect(query('[data-testid="metis-add-group-window"]')).not.toBeNull();
    });

    it('should start a new group window at the due date when there is no common window', () => {
        component['windowStart'].set(undefined);
        component['windowEnd'].set(undefined);
        component['selectedGroupId'].set(2);

        component['addGroupWindow']();

        const added = component['groupWindows']()[1];
        expect(added.windowStart.isSame(dayjs('2026-11-02T23:59'))).toBe(true);
        expect(added.windowEnd.isSame(dayjs('2026-11-03T00:19'))).toBe(true);
    });

    it('should not add a window without a selected group', () => {
        const saveSpy = metisService.saveGroupWindows;

        component['addGroupWindow']();

        expect(component['groupWindows']()).toHaveLength(1);
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should reject a group window whose typed end is before its start and write it once the start is moved', () => {
        const saveSpy = metisService.saveGroupWindows;
        const endField = query<HTMLInputElement>('#metis-group-window-end-1')!;
        const startField = query<HTMLInputElement>('#metis-group-window-start-1')!;
        expect(query('[data-testid="metis-group-window-order-error"]')).toBeNull();

        // Gruppe 1 answers at 10:15–10:35; an end at 10:00 lies before its start.
        endField.value = '03.11.2026 10:00';
        endField.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component['groupWindows']()[0].windowEnd.isSame(dayjs('2026-11-03T10:00'))).toBe(true);
        expect(component['hasInvalidGroupWindow']()).toBe(true);
        expect(component.isFormValid()).toBe(false);
        expect(query('[data-testid="metis-group-window-order-error"]')).not.toBeNull();
        expect(endField.getAttribute('aria-invalid')).toBe('true');
        expect(saveSpy).not.toHaveBeenCalled();

        startField.value = '03.11.2026 09:30';
        startField.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component['groupWindows']()[0].windowStart.isSame(dayjs('2026-11-03T09:30'))).toBe(true);
        expect(component['hasInvalidGroupWindow']()).toBe(false);
        expect(component.isFormValid()).toBe(true);
        expect(query('[data-testid="metis-group-window-order-error"]')).toBeNull();
        expect(endField.getAttribute('aria-invalid')).toBe('false');
        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][1][0].windowStart.isSame(dayjs('2026-11-03T09:30'))).toBe(true);
        expect(saveSpy.mock.calls[0][1][0].windowEnd.isSame(dayjs('2026-11-03T10:00'))).toBe(true);
    });

    it('should keep a group window when its picker is cleared', () => {
        const saveSpy = metisService.saveGroupWindows;
        const row = component['groupWindows']()[0];

        component['updateGroupWindow'](row, 'windowStart', undefined);

        expect(component['groupWindows']()[0]).toBe(row);
        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should name the group of a window row', () => {
        expect(query('[data-testid="metis-group-window-row"] td')!.textContent?.trim()).toBe('Gruppe 1');
        expect(component['groupTitle'](99)).toBe('#99');
    });

    it.each([1, 2, 3, 4, 5])('should write answer length %i when its scale button is clicked', (step) => {
        queryAll<HTMLButtonElement>('[data-testid="metis-answer-length"] button')[step - 1].click();
        fixture.detectChanges();

        expect(component['config']().answerLength).toBe(step);
    });

    it.each([1, 2, 3, 4, 5])('should write complexity %i when its scale button is clicked', (step) => {
        queryAll<HTMLButtonElement>('[data-testid="metis-complexity"] button')[step - 1].click();
        fixture.detectChanges();

        expect(component['config']().complexity).toBe(step);
    });

    it('should offer five labelled steps per scale and write a changed step to the service', () => {
        const saveSpy = metisService.saveConfig;
        const buttons = queryAll<HTMLButtonElement>('[data-testid="metis-answer-length"] button');
        expect(buttons.map((button) => button.textContent?.trim())).toEqual([1, 2, 3, 4, 5].map((step) => `artemisApp.metis.settings.answerLength.${step}`));

        buttons[4].click();
        fixture.detectChanges();

        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][0].answerLength).toBe(5);
    });

    it('should rebuild the scale labels when the language changes', () => {
        const instantSpy = vi.spyOn(translateService, 'instant');

        translateService.use('de');
        fixture.detectChanges();

        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.settings.answerLength.1');
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.settings.complexity.5');
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.settings.rating.points7');
    });

    it('should write the main question count and the follow-up cap', () => {
        const saveSpy = metisService.saveConfig;

        selectOption('metis-main-question-count', 4);
        expect(component['config']().mainQuestionCount).toBe(5);

        selectOption('metis-follow-up-cap', 0);
        expect(component['config']().maxFollowUpsPerQuestion).toBe(0);

        expect(saveSpy).toHaveBeenCalledTimes(2);
    });

    it('should offer thresholds below the scale and pull the threshold down with a shrinking scale', () => {
        expect(component['followUpThresholds']()).toEqual([1, 2, 3, 4]);

        selectOption('metis-likert-points', 1);
        expect(component['config']().likertScalePoints).toBe(7);
        expect(component['followUpThresholds']()).toEqual([1, 2, 3, 4, 5, 6]);

        component['updateConfig']({ followUpThreshold: 6 });
        component['updateConfig']({ likertScalePoints: 5 });

        expect(component['config']().followUpThreshold).toBe(4);
        expect(component['followUpThresholds']()).toEqual([1, 2, 3, 4]);
    });

    it('should store the hint for the question generator and clear it when emptied', () => {
        const saveSpy = metisService.saveConfig;
        const hintField = query<HTMLTextAreaElement>('[data-testid="metis-instructor-hint"]')!;

        hintField.value = 'Focus on the inner loop.';
        hintField.dispatchEvent(new Event('input'));
        hintField.dispatchEvent(new Event('change'));
        expect(component['config']().instructorHint).toBe('Focus on the inner loop.');
        expect(saveSpy.mock.calls[0][0].instructorHint).toBe('Focus on the inner loop.');

        hintField.value = '';
        hintField.dispatchEvent(new Event('input'));
        hintField.dispatchEvent(new Event('change'));
        expect(component['config']().instructorHint).toBeUndefined();
        expect(saveSpy.mock.calls[1][0].instructorHint).toBeUndefined();

        expect(saveSpy).toHaveBeenCalledTimes(2);
    });

    it('should write the hint once when the field is left, not per keystroke', () => {
        const hintField = query<HTMLTextAreaElement>('[data-testid="metis-instructor-hint"]')!;

        for (const text of ['F', 'Fo', 'Foc', 'Focus']) {
            hintField.value = text;
            hintField.dispatchEvent(new Event('input'));
        }
        fixture.detectChanges();

        // The form follows every keystroke, so the length check is live, but nothing is written yet.
        expect(component['config']().instructorHint).toBe('Focus');
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        hintField.dispatchEvent(new Event('change'));

        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0].instructorHint).toBe('Focus');
    });

    it('should keep the toggle disabled and write nothing until the config is loaded', async () => {
        const storedConfig = new Subject<MetisExerciseConfigDTO>();
        metisService.config.mockReturnValueOnce(storedConfig);
        await recreateComponent();

        expect(query<HTMLInputElement>('[data-testid="metis-enabled-toggle"] input')!.disabled).toBe(true);
        // The form holds placeholder defaults without the id of the exercise, so even a change made in code is not written.
        component['updateConfig']({ enabled: true });
        expect(metisService.saveConfig).not.toHaveBeenCalled();

        storedConfig.next(config);
        storedConfig.complete();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component['config']()).toEqual(config);
        expect(query<HTMLInputElement>('[data-testid="metis-enabled-toggle"] input')!.disabled).toBe(false);
        component['updateConfig']({ mainQuestionCount: 4 });
        expect(metisService.saveConfig).toHaveBeenCalledOnce();
        expect(metisService.saveConfig.mock.calls[0][0]).toMatchObject({ exerciseId, mainQuestionCount: 4 });
    });

    it('should keep the toggle disabled and write nothing when the config cannot be read', async () => {
        metisService.config.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await recreateComponent();

        expect(query<HTMLInputElement>('[data-testid="metis-enabled-toggle"] input')!.disabled).toBe(true);
        expect(component.isCheckEnabled()).toBe(false);
        component['updateConfig']({ enabled: true });
        expect(metisService.saveConfig).not.toHaveBeenCalled();
    });

    it('should show no group window rows and write none while the stored windows are loading', async () => {
        const storedWindows = new Subject<MetisGroupWindowDTO[]>();
        metisService.groupWindows.mockReturnValueOnce(storedWindows);
        await recreateComponent();

        expect(query('[data-testid="metis-window-card"]')).not.toBeNull();
        expect(query('[data-testid="metis-group-windows-table"]')).toBeNull();
        expect(query('[data-testid="metis-add-group-window"]')).toBeNull();
        // A write now would replace the stored windows of every group with this one row.
        component['selectedGroupId'].set(2);
        component['addGroupWindow']();
        expect(metisService.saveGroupWindows).not.toHaveBeenCalled();

        storedWindows.next(groupWindows);
        storedWindows.complete();
        fixture.detectChanges();

        expect(component['groupWindows']()).toEqual(groupWindows);
        expect(queryAll('[data-testid="metis-group-window-row"]')).toHaveLength(1);
        expect(query('[data-testid="metis-add-group-window"]')).not.toBeNull();
    });

    it('should show no group window rows and write none when the stored windows cannot be read', async () => {
        metisService.groupWindows.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await recreateComponent();

        expect(query('[data-testid="metis-group-windows-table"]')).toBeNull();
        expect(query('[data-testid="metis-add-group-window"]')).toBeNull();
        component['selectedGroupId'].set(2);
        component['addGroupWindow']();
        expect(metisService.saveGroupWindows).not.toHaveBeenCalled();
    });

    it('should write the review toggle to the service', () => {
        const saveSpy = metisService.saveConfig;

        query<HTMLInputElement>('[data-testid="metis-review-toggle"] input')!.click();
        fixture.detectChanges();

        expect(component['config']().reviewRequired).toBe(true);
        expect(saveSpy).toHaveBeenCalledOnce();
        expect(saveSpy.mock.calls[0][0].reviewRequired).toBe(true);
    });
});
