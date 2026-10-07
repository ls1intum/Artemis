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
    template: `<jhi-exam-exercise-header [exercise]="exercise()" [titleKey]="titleKey()">
        <ng-container headerLeading>
            @if (showLeading()) {
                <button id="leading">Leading</button>
            }
        </ng-container>
        <ng-container headerTitleSuffix>
            @if (showSuffix()) {
                <span id="suffix">Suffix</span>
            }
        </ng-container>
        <button id="action">Action</button>
    </jhi-exam-exercise-header>`,
})
class TestHostComponent {
    readonly exercise = signal<ExamExerciseHeaderExercise | undefined>(undefined);
    readonly titleKey = signal<string | undefined>(undefined);
    readonly showLeading = signal(false);
    readonly showSuffix = signal(false);
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

    it('shows the leading and the suffix element on the title row, around the title and apart from the action', () => {
        host.titleKey.set('artemisApp.exam.examSummary.examResults');
        host.showLeading.set(true);
        host.showSuffix.set(true);
        fixture.detectChanges();

        const header: HTMLElement = fixture.nativeElement.querySelector('[data-testid="exam-exercise-header"]');
        const titleGroup = title().parentElement!;
        expect([...titleGroup.children].map((element) => element.id || element.getAttribute('data-testid'))).toEqual(['leading', 'exam-exercise-title', 'suffix']);
        // the action slot is the right-hand part of the row and holds neither of them
        const actions = header.lastElementChild!;
        expect(actions.querySelector('#action')).not.toBeNull();
        expect(actions.querySelector('#leading')).toBeNull();
        expect(actions.querySelector('#suffix')).toBeNull();
        // both sit in the fixed-height header itself, so the rule below it runs under them as well
        expect(titleGroup.parentElement).toBe(header);
    });

    it('renders no leading or suffix element when none is projected', () => {
        host.titleKey.set('artemisApp.exam.examSummary.examResults');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#leading')).toBeNull();
        expect(fixture.nativeElement.querySelector('#suffix')).toBeNull();
        expect(title().parentElement!.children).toHaveLength(1);
    });

    it('follows a leading element that appears later, so a collapsed sidebar can bring its toggle in', () => {
        host.titleKey.set('artemisApp.exam.examSummary.examResults');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#leading')).toBeNull();

        host.showLeading.set(true);
        fixture.changeDetectorRef.detectChanges();

        expect(title().previousElementSibling?.id).toBe('leading');
    });
});
