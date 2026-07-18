import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { LectureTimelineComponent } from 'app/lecture/manage/lecture-period/lecture-timeline.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseTimelineComponent } from 'app/exercise/exercise-timeline/exercise-timeline.component';

describe('LectureTimelineComponent', () => {
    let fixture: ComponentFixture<LectureTimelineComponent>;
    let component: LectureTimelineComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(LectureTimelineComponent, { set: { imports: [MockComponent(ExerciseTimelineComponent)] } })
            .compileComponents();

        fixture = TestBed.createComponent(LectureTimelineComponent);
        component = fixture.componentInstance;

        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should expose the lecture dates in chronological order', () => {
        expect(component.timelineItems.map((item) => item.labelStringKey)).toEqual(['artemisApp.lecture.startDate', 'artemisApp.lecture.endDate']);
        expect(component.timelineItems.map((item) => item.date)).toEqual([component.startDate, component.endDate]);
        expect(component.timelineItems.every((item) => item.kind === 'optional')).toBe(true);
    });

    it('should forward the timeline status', () => {
        fixture.detectChanges();
        const emitSpy = vi.spyOn(component.timelineStatusChange, 'emit');
        const timeline = fixture.debugElement.query(By.directive(ExerciseTimelineComponent)).componentInstance as ExerciseTimelineComponent;
        const status = { valid: false, empty: false };

        timeline.timelineStatusChange.emit(status);

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(status);
    });
});
