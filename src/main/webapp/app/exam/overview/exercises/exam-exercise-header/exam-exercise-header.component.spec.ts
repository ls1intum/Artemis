import { Component, signal, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExamExerciseHeaderComponent, ExamExerciseHeaderExercise } from 'app/exam/overview/exercises/exam-exercise-header/exam-exercise-header.component';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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

    it('makes every button of the action slot 30px high, however it is built, so that it has 4.5px of air above and below it in the 40px row', () => {
        host.exercise.set({ exerciseGroup: { title: 'Group' } as ExerciseGroup, maxPoints: 10 });
        fixture.detectChanges();

        const actions: HTMLElement = fixture.nativeElement.querySelector('[data-testid="exam-exercise-header-actions"]');
        // 4px padding, a 20px line and the 1px border on both sides are 30px. The important spelling beats the padding of the kit and of the Bootstrap button.
        for (const button of ['.btn', '.tumaet-ui-btn']) {
            expect([...actions.classList], button).toEqual(expect.arrayContaining([`[&_${button}]:py-1!`, `[&_${button}]:leading-5!`]));
        }
        // 12px between the rule and the content
        expect(fixture.nativeElement.querySelector('[data-testid="exam-exercise-header"]').classList).toContain('mb-3!');
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

@Component({
    imports: [ExamExerciseHeaderComponent],
    template: `<div id="bar">
            <div id="toolbar"><button id="refresh_button">Refresh</button></div>
        </div>
        <jhi-exam-exercise-header [exercise]="exercise()" [actionsLabel]="label()" />`,
})
class OfferHostComponent {
    readonly exercise = signal<ExamExerciseHeaderExercise>({ exerciseGroup: { title: 'Programming Exercise Group' } as ExerciseGroup, maxPoints: 10 });
    readonly label = signal<string | undefined>('Programming exercise actions');
    readonly header = viewChild.required(ExamExerciseHeaderComponent);
}

/** Records what the header observes, and lets a test deliver the notification a browser sends when an observed element changes its size. */
class ResizeObserverStub {
    static instances: ResizeObserverStub[] = [];
    readonly observed = new Set<Element>();
    disconnected = false;

    constructor(private readonly callback: () => void) {
        ResizeObserverStub.instances.push(this);
    }

    observe(element: Element): void {
        this.observed.add(element);
    }

    unobserve(element: Element): void {
        this.observed.delete(element);
    }

    disconnect(): void {
        this.observed.clear();
        this.disconnected = true;
    }

    notify(): void {
        this.callback();
    }
}

describe('ExamExerciseHeaderComponent with offered actions', () => {
    /** The gap between the title and the action slot, the `gap-4` of the row. */
    const ROW_GAP = 16;
    /** The least the title keeps next to the actions, and the margin that actions outside the row need on top of it before they move in. */
    const MIN_TITLE_WIDTH = 256;
    const DOCK_MARGIN = 16;

    let fixture: ComponentFixture<OfferHostComponent>;
    let header: ExamExerciseHeaderComponent;
    let bar: HTMLElement;
    let toolbar: HTMLElement;
    let actions: HTMLElement;
    /** What jsdom has no layout engine to compute: the width of the row, and the width of the toolbar when it is on one line. */
    const layout = { rowWidth: 1000, toolbarWidth: 400 };

    const observer = () => ResizeObserverStub.instances[0];
    const inRow = () => actions.contains(toolbar);
    const resize = (rowWidth: number, toolbarWidth = layout.toolbarWidth) => {
        layout.rowWidth = rowWidth;
        layout.toolbarWidth = toolbarWidth;
        observer().notify();
    };

    beforeEach(async () => {
        ResizeObserverStub.instances = [];
        vi.stubGlobal('ResizeObserver', ResizeObserverStub);
        layout.rowWidth = 1000;
        layout.toolbarWidth = 400;
        await TestBed.configureTestingModule({
            imports: [OfferHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(OfferHostComponent);
        fixture.detectChanges();
        header = fixture.componentInstance.header();
        const root: HTMLElement = fixture.nativeElement;
        bar = root.querySelector<HTMLElement>('#bar')!;
        toolbar = root.querySelector<HTMLElement>('#toolbar')!;
        actions = root.querySelector<HTMLElement>('[data-testid="exam-exercise-header-actions"]')!;
        const row = root.querySelector<HTMLElement>('[data-testid="exam-exercise-header"]')!;
        // The title group is what the row leaves to the title: its width, less the toolbar while that is in the row
        Object.defineProperty(row, 'clientWidth', { configurable: true, get: () => layout.rowWidth });
        Object.defineProperty(row.firstElementChild, 'offsetWidth', {
            configurable: true,
            get: () => Math.max(0, layout.rowWidth - ROW_GAP - (inRow() ? layout.toolbarWidth : 0)),
        });
        Object.defineProperty(toolbar, 'offsetWidth', { configurable: true, get: () => layout.toolbarWidth });
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('moves the offered element into the action slot when the title keeps its room, and does not copy it', () => {
        header.offerActions(toolbar);

        expect(toolbar.parentElement).toBe(actions);
        expect(fixture.nativeElement.querySelectorAll('#refresh_button')).toHaveLength(1);
        // the place it came from holds nothing but the mark that the portal leaves there to find its way back
        expect(bar.children).toHaveLength(0);
    });

    it('leaves the element where it is when the title would keep less than its minimum next to it', () => {
        layout.rowWidth = 650;

        header.offerActions(toolbar);

        expect(toolbar.parentElement).toBe(bar);
    });

    it('puts the same element back at its place when the row gets too narrow, and takes it over again when the room returns', () => {
        header.offerActions(toolbar);
        const refreshButton = toolbar.querySelector('#refresh_button');
        expect(inRow()).toBe(true);

        resize(600);
        expect(toolbar.parentElement).toBe(bar);
        expect(bar.firstElementChild).toBe(toolbar);

        resize(1000);
        expect(toolbar.parentElement).toBe(actions);
        // what is in it is the very same element, not a new one
        expect(toolbar.querySelector('#refresh_button')).toBe(refreshButton);
        expect(fixture.nativeElement.querySelectorAll('#refresh_button')).toHaveLength(1);
    });

    it('needs a margin on top of the minimum to move the element in that it needs to stay, so that a change of the page it causes cannot undo the move', () => {
        const minimumRow = MIN_TITLE_WIDTH + ROW_GAP + layout.toolbarWidth;
        // the minimum alone is not enough to move in
        layout.rowWidth = minimumRow;
        header.offerActions(toolbar);
        expect(inRow()).toBe(false);

        resize(minimumRow + DOCK_MARGIN);
        expect(inRow()).toBe(true);

        // it stays down to the minimum itself
        resize(minimumRow);
        expect(inRow()).toBe(true);
        resize(minimumRow - 1);
        expect(inRow()).toBe(false);

        // and needs the margin again to move in
        resize(minimumRow);
        expect(inRow()).toBe(false);
        resize(minimumRow + DOCK_MARGIN);
        expect(inRow()).toBe(true);
    });

    it('moves the element out when it grows beyond what the title can spare, as the result of a build with its progress bar does, and in again when it shrinks', () => {
        header.offerActions(toolbar);
        expect(inRow()).toBe(true);

        resize(1000, 800);
        expect(toolbar.parentElement).toBe(bar);

        resize(1000, 400);
        expect(inRow()).toBe(true);
    });

    it('measures the width of an element outside the row where it is, without moving it, and leaves its style as it was', () => {
        const parents: (HTMLElement | null)[] = [];
        Object.defineProperty(toolbar, 'offsetWidth', {
            configurable: true,
            get: () => {
                parents.push(toolbar.parentElement);
                return layout.toolbarWidth;
            },
        });

        header.offerActions(toolbar);
        // in the row now, so the measure of the width outside it was taken before
        expect(parents[0]).toBe(bar);
        resize(600);
        expect(toolbar.parentElement).toBe(bar);
        parents.length = 0;
        resize(610);

        expect(parents.length).toBeGreaterThan(0);
        expect(parents.every((parent) => parent === bar)).toBe(true);
        expect(toolbar.style.width).toBe('');
    });

    it('observes the row and the offered element, in the row and outside it', () => {
        const row = fixture.nativeElement.querySelector('[data-testid="exam-exercise-header"]');
        header.offerActions(toolbar);
        expect([...observer().observed]).toEqual([row, toolbar]);

        resize(600);
        expect([...observer().observed]).toEqual([row, toolbar]);

        // an element that is not offered any more is not observed any more
        header.offerActions(undefined);
        expect([...observer().observed]).toEqual([row]);
    });

    it('keeps the element where it is while the page is not shown, because a row without a width says nothing about the room', () => {
        header.offerActions(toolbar);
        expect(inRow()).toBe(true);

        // an exercise the student has left stays in the document, hidden
        resize(0);
        expect(inRow()).toBe(true);

        resize(1000);
        expect(inRow()).toBe(true);
    });

    it('does not take over an element that is not in the document any more', () => {
        // the editor of the page was removed, and with it its toolbar
        bar.remove();

        header.offerActions(toolbar);

        expect(inRow()).toBe(false);
    });

    it('puts the element back when the offer is taken back, and takes it over again when it is offered again', () => {
        header.offerActions(toolbar);
        header.offerActions(undefined);
        expect(toolbar.parentElement).toBe(bar);

        header.offerActions(toolbar);
        expect(inRow()).toBe(true);
    });

    it('puts the first element back when another one is offered', () => {
        const other = document.createElement('div');
        bar.append(other);
        header.offerActions(toolbar);

        header.offerActions(other);

        expect(toolbar.parentElement).toBe(bar);
        expect(other.parentElement).toBe(actions);
    });

    it('keeps the focus on the button the student has pressed when the element moves', () => {
        document.body.append(fixture.nativeElement);
        header.offerActions(toolbar);
        const refreshButton: HTMLElement = toolbar.querySelector('#refresh_button')!;
        refreshButton.focus();
        expect(document.activeElement).toBe(refreshButton);

        // the move out of the row, as the progress bar of a build causes it
        resize(1000, 800);
        expect(toolbar.parentElement).toBe(bar);
        expect(document.activeElement).toBe(refreshButton);

        // and back in
        resize(1000, 400);
        expect(inRow()).toBe(true);
        expect(document.activeElement).toBe(refreshButton);
        fixture.nativeElement.remove();
    });

    it('names the element as a group while it is in the row, and drops the name when it leaves', () => {
        header.offerActions(toolbar);
        expect(toolbar.getAttribute('role')).toBe('group');
        expect(toolbar.getAttribute('aria-label')).toBe('Programming exercise actions');

        fixture.componentInstance.label.set('Aktionen');
        fixture.detectChanges();
        expect(toolbar.getAttribute('aria-label')).toBe('Aktionen');

        resize(600);
        expect(toolbar.hasAttribute('role')).toBe(false);
        expect(toolbar.hasAttribute('aria-label')).toBe(false);
    });

    it('stops observing when it is destroyed', () => {
        header.offerActions(toolbar);

        fixture.destroy();

        expect(observer().disconnected).toBe(true);
    });

    it('leaves the element where it is where there is no ResizeObserver, since nothing would tell the row to decide again', () => {
        vi.stubGlobal('ResizeObserver', undefined);

        header.offerActions(toolbar);

        expect(ResizeObserverStub.instances).toHaveLength(0);
        expect(toolbar.parentElement).toBe(bar);
    });
});
