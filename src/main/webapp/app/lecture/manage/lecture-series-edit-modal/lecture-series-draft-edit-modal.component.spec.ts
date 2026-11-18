import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import * as globalUtils from 'app/foundation/util/global.utils';
import { LectureSeriesDraftEditModalComponent } from './lecture-series-draft-edit-modal.component';
import { LectureDraft, LectureDraftState } from 'app/lecture/manage/lecture-series-create/lecture-series-create.component';
import { LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import dayjs from 'dayjs/esm';

describe('LectureSeriesEditModal', () => {
    let component: LectureSeriesDraftEditModalComponent;
    let fixture: ComponentFixture<LectureSeriesDraftEditModalComponent>;

    const testDraftId = 'test-id';
    const testTitle = 'Lecture 1';
    const testStartDate = dayjs('2025-01-15T14:30:00Z');
    const testEndDate = dayjs('2025-01-15T15:30:00Z');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureSeriesDraftEditModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        vi.spyOn(globalUtils, 'getCurrentLocaleSignal').mockReturnValue(signal('de'));

        fixture = TestBed.createComponent(LectureSeriesDraftEditModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open and populate inputs correctly', async () => {
        const draft: LectureDraft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO(testTitle, testStartDate, testEndDate) };

        component.open(draft);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.show()).toBe(true);
        expect(component.title()).toBe(draft.dto.title);
        expect(component.startDate()).toEqual(draft.dto.startDate);
        expect(component.endDate()).toEqual(draft.dto.endDate);
    });

    it('should close and clear inputs on cancel', async () => {
        component.lectureDraft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO('Lecture 1', testStartDate, testEndDate) };
        component.title.set(testTitle);
        component.startDate.set(testStartDate);
        component.endDate.set(testEndDate);
        component.show.set(true);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        component.cancel();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.show()).toBe(false);
        expect(component.lectureDraft).toBeUndefined();
        expect(component.title()).toBe('');
        expect(component.startDate()).toBeUndefined();
        expect(component.endDate()).toBeUndefined();
    });

    it('should close, update draft and clear inputs on save', async () => {
        const draft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO('Lecture 1', testStartDate, testEndDate) };
        const newTitle = 'Requirements Engineering';
        const newStartDate = testStartDate.add(1, 'hour');
        const newEndDate = testEndDate.add(1, 'hour');
        component.lectureDraft = draft;
        component.title.set(newTitle);
        component.startDate.set(newStartDate);
        component.endDate.set(newEndDate);
        component.show.set(true);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        component.save();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.show()).toBe(false);
        expect(component.lectureDraft).toBeUndefined();
        expect(component.title()).toBe('');
        expect(component.startDate()).toBeUndefined();
        expect(component.endDate()).toBeUndefined();
        expect(draft.dto.title).toBe(newTitle);
        expect(draft.dto.startDate).toEqual(newStartDate);
        expect(draft.dto.endDate).toEqual(newEndDate);
    });
    describe('with the dialog open', () => {
        const draft: LectureDraft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO(testTitle, testStartDate, testEndDate) };

        function field(id: string): HTMLInputElement | null {
            return document.querySelector(`#${id}`);
        }

        async function openDialog() {
            component.open(draft);
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        }

        function type(input: HTMLInputElement, text: string) {
            input.value = text;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        afterEach(() => {
            component.cancel();
            fixture.detectChanges();
        });

        it('should show both date pickers filled with the draft dates', async () => {
            await openDialog();

            expect(field('startDate')?.value).toBe(testStartDate.format('DD.MM.YYYY HH:mm'));
            expect(field('endDate')?.value).toBe(testEndDate.format('DD.MM.YYYY HH:mm'));
        });

        it('should write a typed date back into the signals', async () => {
            await openDialog();

            type(field('endDate')!, '15.01.2025 18:00');

            expect(component.endDate()?.format('YYYY-MM-DD HH:mm')).toBe('2025-01-15 18:00');
            expect(component.isEndDateInvalid()).toBe(false);
        });

        it('should reject an end that is not after the start and block saving', async () => {
            await openDialog();

            type(field('endDate')!, testStartDate.format('DD.MM.YYYY HH:mm'));

            expect(component.isEndDateInvalid()).toBe(true);
            expect(component.areInputsInvalid()).toBe(true);
            expect(field('endDate')?.getAttribute('aria-invalid')).toBe('true');
        });

        it('should block saving while a typed date does not parse', async () => {
            await openDialog();
            expect(component.areInputsInvalid()).toBe(false);

            type(field('startDate')!, '15.01.');

            expect(component.isStartTextValid()).toBe(false);
            expect(component.areInputsInvalid()).toBe(true);
        });
    });
});
