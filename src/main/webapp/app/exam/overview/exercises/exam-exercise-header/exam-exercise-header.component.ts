import { Component, DestroyRef, ElementRef, effect, inject, input, viewChild } from '@angular/core';
import { CdkPortalOutlet, DomPortal } from '@angular/cdk/portal';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';

/** The part of an exercise the header shows; a quiz configuration offers it as well as a full exercise does. */
export interface ExamExerciseHeaderExercise {
    exerciseGroup?: ExerciseGroup;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
}

/**
 * The width in px that the title (with the toggle before it and the tag after it) keeps at least when a page moves its actions into the row.
 * Measured at the size of the title: `Programming Exercise Group` is 245px wide, 335px with its points. A title that is cut off at 256px still names its
 * exercise, and the points behind it go first. The toolbar of the programming page needs 365px in English and 448px in German, and up to 859px and 1001px
 * with the submission policy and a result, so the room it needs is measured on the page and not assumed (see `update`).
 */
const MIN_TITLE_WIDTH = 256;

/**
 * Extra room in px that actions outside the row need before they move in, on top of {@link MIN_TITLE_WIDTH}. Moving them in can shorten the page by the
 * row they leave, which can make a vertical scrollbar disappear and widen the row; moving them out brings it back. The margin keeps the two from
 * undoing each other at one width.
 */
const DOCK_MARGIN = 16;

/**
 * The title row on top of every page of a running exam: for an exercise the group's title and its points, for a page without
 * an exercise (the overview) a translated title, and in the projected slot on the right the page's primary action (the save
 * button of most exercise types). All pages share it so the title, its size and the rule below it cannot drift apart.
 *
 * Two optional slots sit inside the same rule: an element with the attribute `headerLeading` is shown before the title (the
 * toggle of a collapsed sidebar) and one with `headerTitleSuffix` right after it (a tag). To fill one conditionally, put the
 * `@if` inside an `<ng-container headerLeading>`: an `@if` that carries the element itself projects it only if the element is
 * its single root node, which preserved whitespace in the template of the host breaks (and then it lands in the action slot).
 *
 * A page whose actions are a part of its own content, such as the toolbar of the code editor, can offer that element with
 * {@link offerActions}. The row moves it into the action slot while the title keeps at least {@link MIN_TITLE_WIDTH} next to it, and puts it back
 * where it came from when it does not. The element is moved and not copied, so its components keep their state and its ids stay unique.
 */
@Component({
    selector: 'jhi-exam-exercise-header',
    templateUrl: './exam-exercise-header.component.html',
    imports: [TranslateDirective, IncludedInScoreBadgeComponent, CdkPortalOutlet],
})
export class ExamExerciseHeaderComponent {
    private readonly destroyRef = inject(DestroyRef);

    protected readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly exercise = input<ExamExerciseHeaderExercise>();
    readonly titleKey = input<string>();
    /** The accessible name of the offered actions while they are in the row. */
    readonly actionsLabel = input<string>();

    private readonly row = viewChild.required<ElementRef<HTMLElement>>('row');
    private readonly titleGroup = viewChild.required<ElementRef<HTMLElement>>('titleGroup');
    private readonly outlet = viewChild.required(CdkPortalOutlet);

    /** The element the page offers, wherever it is at the moment. */
    private offered?: HTMLElement;
    /** The element while it is in the row. */
    private docked?: HTMLElement;
    private observer?: ResizeObserver;

    constructor() {
        this.destroyRef.onDestroy(() => this.observer?.disconnect());
        effect(() => {
            const label = this.actionsLabel();
            if (this.docked) {
                this.nameAsGroup(this.docked, label);
            }
        });
    }

    /**
     * Offers an element of the page for the action slot, or takes the offer back with `undefined`.
     * Whether it is in the row is decided now and again whenever the row or the element changes its size, as long as the page is shown. The row of a
     * page that is not shown has no width, which says nothing about the room, so it keeps what it has.
     */
    offerActions(element: HTMLElement | undefined): void {
        if (element === this.offered) {
            this.update();
            return;
        }
        this.undock();
        if (this.offered) {
            this.observer?.unobserve(this.offered);
        }
        this.offered = element;
        if (element && this.observeRow()) {
            // The element is observed in the row and outside it: a result that comes in or goes can take the room the title needs, and give it back
            this.observer?.observe(element);
            this.update();
        }
    }

    /** Whether the row is observed. Where there is no ResizeObserver, as in jsdom, nothing would tell the row when to decide again, so the actions stay where the page put them. */
    private observeRow(): boolean {
        if (!this.observer && typeof ResizeObserver !== 'undefined') {
            this.observer = new ResizeObserver(() => this.update());
            this.observer.observe(this.row().nativeElement);
        }
        return !!this.observer;
    }

    private update(): void {
        const element = this.offered;
        // An element whose page is gone (the editor was removed and the page has not taken its offer back yet) has nowhere to be moved from
        if (!element || this.row().nativeElement.clientWidth === 0 || (!this.docked && !element.isConnected)) {
            return;
        }
        // Moving in needs more than staying does: the move can take the vertical scrollbar of the page away and widen the row, moving out brings it back
        const needed = this.docked ? MIN_TITLE_WIDTH : MIN_TITLE_WIDTH + DOCK_MARGIN;
        // What the title keeps with the element in the row. While the element is outside, the row has not been shortened by it yet, and it is measured on one line where it is.
        let titleWidth = this.titleGroup().nativeElement.offsetWidth;
        if (!this.docked && titleWidth >= needed) {
            titleWidth -= this.widthOnOneLine(element);
        }
        if (titleWidth >= needed) {
            this.dock(element);
        } else {
            this.undock();
        }
    }

    /** The width an element has when it is laid out on one line, which is what it has in the row, measured where it is without moving it. */
    private widthOnOneLine(element: HTMLElement): number {
        const width = element.style.width;
        element.style.width = 'max-content';
        const measured = element.offsetWidth;
        element.style.width = width;
        return measured;
    }

    private dock(element: HTMLElement): void {
        if (this.docked) {
            return;
        }
        this.keepingFocus(element, () => this.outlet().attach(new DomPortal(element)));
        this.docked = element;
        this.nameAsGroup(element, this.actionsLabel());
    }

    private undock(): void {
        const element = this.docked;
        if (!element) {
            return;
        }
        this.nameAsGroup(element, undefined);
        this.keepingFocus(element, () => {
            this.outlet().detach();
            // The portal puts the element back at the place it left. Where that place is gone, the element would stay in the row without anything to show.
            if (this.row().nativeElement.contains(element)) {
                element.remove();
            }
        });
        this.docked = undefined;
    }

    /** Moving a node in the document takes the focus from what is in it, which would leave a student at the keyboard, who has just pressed a button of the toolbar, without a place. */
    private keepingFocus(element: HTMLElement, move: () => void): void {
        const active = document.activeElement;
        const focused = active instanceof HTMLElement && element.contains(active) ? active : undefined;
        move();
        if (focused?.isConnected) {
            focused.focus({ preventScroll: true });
        }
    }

    private nameAsGroup(element: HTMLElement, label: string | undefined): void {
        if (label) {
            element.setAttribute('role', 'group');
            element.setAttribute('aria-label', label);
        } else {
            element.removeAttribute('role');
            element.removeAttribute('aria-label');
        }
    }
}
