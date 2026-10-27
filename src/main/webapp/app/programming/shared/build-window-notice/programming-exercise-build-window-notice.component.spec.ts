import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseBuildWindowNoticeComponent } from 'app/programming/shared/build-window-notice/programming-exercise-build-window-notice.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildTimeWindow } from 'app/programming/shared/entities/programming-exercise-build-advanced-settings';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ProgrammingExerciseBuildWindowNoticeComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseBuildWindowNoticeComponent>;

    const start = dayjs('2026-10-01T10:00:00Z');
    const end = dayjs('2026-10-01T12:00:00Z');

    const exerciseWith = (buildTimeWindow?: BuildTimeWindow): ProgrammingExercise => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.buildConfig!.advancedSettings = buildTimeWindow ? { buildTimeWindow } : undefined;
        return exercise;
    };

    const notice = (phase: 'before' | 'during' | 'after') => fixture.nativeElement.querySelector(`[data-testid="build-window-notice-${phase}"]`) as HTMLElement | null;

    const render = (exercise: ProgrammingExercise | TextExercise, now: dayjs.Dayjs) => {
        vi.setSystemTime(now.toDate());
        fixture = TestBed.createComponent(ProgrammingExerciseBuildWindowNoticeComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
    };

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseBuildWindowNoticeComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('tells students that builds have not started yet', () => {
        render(exerciseWith({ start, end }), start.subtract(1, 'hour'));

        expect(notice('before')?.textContent).toContain('artemisApp.programmingExercise.buildTimeWindow.notice.before');
        expect(notice('during')).toBeNull();
        expect(notice('after')).toBeNull();
    });

    it('shows nothing in practice mode, as those pushes are always built', () => {
        vi.setSystemTime(start.subtract(1, 'hour').toDate());
        fixture = TestBed.createComponent(ProgrammingExerciseBuildWindowNoticeComponent);
        fixture.componentRef.setInput('exercise', exerciseWith({ start, end }));
        fixture.componentRef.setInput('practiceMode', true);
        fixture.detectChanges();

        expect(notice('before')).toBeNull();
        expect(notice('during')).toBeNull();
        expect(notice('after')).toBeNull();
    });

    it('tells students until when builds are active', () => {
        render(exerciseWith({ start, end }), start.add(1, 'hour'));

        expect(notice('during')?.textContent).toContain('artemisApp.programmingExercise.buildTimeWindow.notice.during');
        expect(notice('before')).toBeNull();
        expect(notice('after')).toBeNull();
    });

    it('tells students that the window has ended', () => {
        render(exerciseWith({ start, end }), end.add(1, 'hour'));

        expect(notice('after')?.textContent).toContain('artemisApp.programmingExercise.buildTimeWindow.notice.after');
        expect(notice('before')).toBeNull();
        expect(notice('during')).toBeNull();
    });

    it('says nothing while builds are active and nothing ends them', () => {
        render(exerciseWith({ start }), start.add(1, 'hour'));

        expect(notice('before')).toBeNull();
        expect(notice('during')).toBeNull();
        expect(notice('after')).toBeNull();
    });

    it('reads a window whose dates are still strings, as in the payload of a student', () => {
        const exercise = exerciseWith({ start: '2026-10-01T10:00:00Z' as unknown as dayjs.Dayjs, end: '2026-10-01T12:00:00Z' as unknown as dayjs.Dayjs });

        render(exercise, start.subtract(1, 'hour'));

        expect(notice('before')).not.toBeNull();
    });

    it('switches from before to during when the window starts', () => {
        render(exerciseWith({ start, end }), start.subtract(1, 'minute'));
        expect(notice('before')).not.toBeNull();

        vi.advanceTimersByTime(60 * 1000);
        fixture.detectChanges();

        expect(notice('before')).toBeNull();
        expect(notice('during')).not.toBeNull();
    });

    it('shows nothing for exercises without a window', () => {
        render(exerciseWith(), start);

        expect(fixture.nativeElement.querySelector('tumaet-ui-message')).toBeNull();
    });

    it('shows nothing for exercises that are not programming exercises', () => {
        render(new TextExercise(undefined, undefined), start);

        expect(fixture.nativeElement.querySelector('tumaet-ui-message')).toBeNull();
    });
});
