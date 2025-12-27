import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import * as globalUtils from 'app/shared/util/global.utils';
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

        jest.spyOn(globalUtils, 'getCurrentLocaleSignal').mockReturnValue(signal('de'));

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

        expect(component.show()).toBeTrue();
        expect(component.title()).toBe(draft.dto.title);
        expect(component.startDate()).toEqual(draft.dto.startDate!.toDate());
        expect(component.endDate()).toEqual(draft.dto.endDate!.toDate());
    });

    it('should close and clear inputs on cancel', async () => {
        component.lectureDraft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO('Lecture 1', testStartDate, testEndDate) };
        component.title.set(testTitle);
        component.startDate.set(testStartDate.toDate());
        component.endDate.set(testEndDate.toDate());
        component.show.set(true);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        component.cancel();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.show()).toBeFalse();
        expect(component.lectureDraft).toBeUndefined();
        expect(component.title()).toBe('');
        expect(component.startDate()).toBeUndefined();
        expect(component.endDate()).toBeUndefined();
    });

    it('should close, update draft and clear inputs on save', async () => {
        const draft = { id: testDraftId, state: LectureDraftState.REGULAR, dto: new LectureSeriesCreateLectureDTO('Lecture 1', testStartDate, testEndDate) };
        const newTitle = 'Requirements Engineering';
        const newStartDate = testStartDate.add(1, 'hour').toDate();
        const newEndDate = testEndDate.add(1, 'hour').toDate();
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

        expect(component.show()).toBeFalse();
        expect(component.lectureDraft).toBeUndefined();
        expect(component.title()).toBe('');
        expect(component.startDate()).toBeUndefined();
        expect(component.endDate()).toBeUndefined();
        expect(draft.dto.title).toBe(newTitle);
        expect(draft.dto.startDate).toEqual(dayjs(newStartDate));
        expect(draft.dto.endDate).toEqual(dayjs(newEndDate));
    });
});
