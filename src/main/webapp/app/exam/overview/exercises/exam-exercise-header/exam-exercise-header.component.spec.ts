import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExamExerciseHeaderComponent, ExamExerciseHeaderExercise } from 'app/exam/overview/exercises/exam-exercise-header/exam-exercise-header.component';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { beforeEach, describe, expect, it } from 'vitest';

@Component({
    imports: [ExamExerciseHeaderComponent],
    template: `<jhi-exam-exercise-header [exercise]="exercise()" [titleKey]="titleKey()"><button id="action">Action</button></jhi-exam-exercise-header>`,
})
class TestHostComponent {
    readonly exercise = signal<ExamExerciseHeaderExercise | undefined>(undefined);
    readonly titleKey = signal<string | undefined>(undefined);
}

describe('ExamExerciseHeaderComponent', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;

    const title = (): HTMLElement => fixture.nativeElement.querySelector('[data-testid="exam-exercise-title"]');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    it('shows the exercise group title and the points of an exercise', () => {
        host.exercise.set({ exerciseGroup: { title: 'Text Exercise Group' } as ExerciseGroup, maxPoints: 10, includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY });
        fixture.detectChanges();

        expect(title().textContent).toContain('Text Exercise Group');
        expect(title().textContent).toContain('artemisApp.examParticipation.points');
        expect(title().textContent).not.toContain('artemisApp.examParticipation.bonus');
    });

    it('uses the bonus points translation for an exercise with bonus points', () => {
        host.exercise.set({
            exerciseGroup: { title: 'Modeling Exercise Group' } as ExerciseGroup,
            maxPoints: 10,
            bonusPoints: 5,
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        });
        fixture.detectChanges();

        expect(title().textContent).toContain('artemisApp.examParticipation.bonus');
    });

    it('shows the included-in-score badge only for an exercise that does not count completely', () => {
        host.exercise.set({ exerciseGroup: { title: 'Group' } as ExerciseGroup, maxPoints: 10, includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('jhi-included-in-score-badge')).toBeNull();

        host.exercise.set({ exerciseGroup: { title: 'Group' } as ExerciseGroup, maxPoints: 10, includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS });
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('jhi-included-in-score-badge')).not.toBeNull();
    });

    it('does not render an empty badge when the exercise does not say whether it counts', () => {
        host.exercise.set({ exerciseGroup: { title: 'Group' } as ExerciseGroup, maxPoints: 10 });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('jhi-included-in-score-badge')).toBeNull();
    });

    it('shows a translated title for a page without an exercise', () => {
        host.titleKey.set('artemisApp.studentExamDetail.overview');
        fixture.detectChanges();

        expect(title().textContent).toContain('artemisApp.studentExamDetail.overview');
    });

    it('has the same fixed height with and without an action, and keeps a long title on one line', () => {
        host.exercise.set({ exerciseGroup: { title: 'A very long exercise group title '.repeat(10) } as ExerciseGroup, maxPoints: 10 });
        fixture.detectChanges();

        const header: HTMLElement = fixture.nativeElement.querySelector('[data-testid="exam-exercise-header"]');
        // 40px is `h-10`: the height must not depend on the action slot (a button is taller than the title) or on the title wrapping
        expect(header.classList).toContain('h-10');
        expect(title().classList).toContain('truncate');
    });

    it('projects the primary action into the header', () => {
        host.exercise.set({ exerciseGroup: { title: 'Group' } as ExerciseGroup, maxPoints: 10 });
        fixture.detectChanges();

        const header = fixture.nativeElement.querySelector('[data-testid="exam-exercise-header"]');
        expect(header.querySelector('#action')).not.toBeNull();
    });
});
