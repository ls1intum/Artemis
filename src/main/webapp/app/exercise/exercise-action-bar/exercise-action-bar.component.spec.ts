import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseActionBarComponent, visibleLabelsWidth } from 'app/exercise/exercise-action-bar/exercise-action-bar.component';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';
import { faTable, faTrash } from '@fortawesome/free-solid-svg-icons';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

describe('ExerciseActionBarComponent', () => {
    let fixture: ComponentFixture<ExerciseActionBarComponent>;
    let component: ExerciseActionBarComponent;

    const buildActions = (ids: string[]): ActionItem[] =>
        ids.map((id) => ({ id, labelKey: `label.${id}`, icon: id === 'delete' ? faTrash : faTable, severity: 'primary', kind: 'link', link: ['/x'] }));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseActionBarComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseActionBarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('items', []);
    });

    afterEach(() => vi.restoreAllMocks());

    describe('hiddenIds / hasOverflow', () => {
        it('shows up to three actions inline, without the ellipsis, before the row width is measured', () => {
            fixture.componentRef.setInput('items', buildActions(['participations', 'edit', 'delete']));
            fixture.detectChanges();

            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
            expect(component.hiddenActions()).toEqual([]);
        });

        it('keeps the other actions and, with them, delete in the menu, however wide the row', () => {
            fixture.componentRef.setInput('items', buildActions(['statistics', 'preview', 'solution', 'participations', 'edit', 'delete']));
            seedEqualWidths(30);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(2000);

            expect(component.hasOverflow()).toBe(true);
            expect(component.hiddenActions().map((a) => a.id)).toEqual(['statistics', 'preview', 'solution', 'delete']);
        });

        it('shows a single menu entry as a button instead, e.g. delete in a programming row', () => {
            fixture.componentRef.setInput('items', buildActions(['edit-in-editor', 'participations', 'edit', 'delete']));
            seedEqualWidths(30);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(2000);

            expect(component.hasOverflow()).toBe(false);
        });

        it('shows edit in editor, participations and edit, and delete in the menu, for a programming row with more actions', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'edit-in-editor', 'participations', 'edit', 'delete']));
            seedEqualWidths(30);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(2000);

            expect(component.hiddenActions().map((a) => a.id)).toEqual(['teams', 'delete']);
        });

        it('renders the trailing actions last, set apart from the others before them', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'participations', 'statistics', 'edit', 'delete']));
            fixture.detectChanges();

            const items: HTMLElement[] = [...fixture.nativeElement.querySelectorAll('.inline-item')];
            expect(items.map((item) => item.querySelector<HTMLElement>('[data-testid]')!.dataset.testid)).toEqual([
                'exercise-action-teams',
                'exercise-action-statistics',
                'exercise-action-participations',
                'exercise-action-edit',
                'exercise-action-delete',
            ]);
            expect(items.map((item) => item.classList.contains('group-start'))).toEqual([false, false, true, false, false]);
        });

        it('labels every button but edit and delete, which show only their icon and name it in the tooltip', () => {
            fixture.componentRef.setInput('items', buildActions(['edit-in-editor', 'participations', 'edit', 'delete']));
            fixture.detectChanges();

            const button = (id: string): HTMLElement => fixture.nativeElement.querySelector(`[data-testid="exercise-action-${id}"]`);
            expect(button('edit-in-editor').textContent).toContain('label.edit-in-editor');
            expect(button('participations').textContent).toContain('label.participations');
            expect(button('participations').getAttribute('aria-label')).toBeNull();
            expect(button('edit').textContent).not.toContain('label.edit');
            expect(button('edit').getAttribute('aria-label')).toBe('label.edit');
            expect(button('delete').getAttribute('aria-label')).toBe('label.delete');
        });

        it('does not set the trailing actions apart when no other action precedes them', () => {
            fixture.componentRef.setInput('items', buildActions(['participations', 'edit', 'delete']));
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelectorAll('.inline-item.group-start')).toHaveLength(0);
        });

        /** Seeds every current action's cached natural width to the same value, so only the row width drives collapsing. */
        const seedEqualWidths = (buttonWidth: number): void => {
            const widths = new Map<string, number>();
            for (const action of component.items()) {
                widths.set(component['signatureOf'](action), buttonWidth);
            }
            component['buttonWidths'].set(widths);
        };

        it('folds delete first, then the other actions, and keeps the default-priority ids inline longest', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'participations', 'statistics', 'preview', 'edit-in-editor', 'edit', 'delete']));
            // Lifted, so only the width limits the buttons here.
            fixture.componentRef.setInput('inlineIds', ['teams', 'participations', 'statistics', 'preview', 'edit-in-editor', 'edit', 'delete']);
            fixture.componentRef.setInput('maxInline', 10);
            // Each button 100px wide. With a 460px row (available 452 after the safety margin, budget 414 after the
            // ellipsis + gap) exactly four 100px buttons plus their gaps fit.
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(460);

            expect(component.hasOverflow()).toBe(true);
            expect(component.hiddenActions().map((a) => a.id)).toEqual(['statistics', 'preview', 'delete']);
        });

        it('folds only delete when one button is too many, so the ellipsis takes its place at the end', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'participations', 'edit', 'delete']));
            fixture.componentRef.setInput('inlineIds', ['teams', 'participations', 'edit', 'delete']);
            fixture.componentRef.setInput('maxInline', 10);
            // Four buttons (412px incl. gaps) exceed a 400px row (available 392); after the ellipsis and its gap the budget
            // is 354px, so three fit.
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(400);

            expect(component.hiddenActions().map((a) => a.id)).toEqual(['delete']);
        });

        it('respects a custom keepPriorityIds ordering', () => {
            fixture.componentRef.setInput('items', buildActions(['a', 'b', 'c']));
            fixture.componentRef.setInput('inlineIds', ['a', 'b', 'c']);
            fixture.componentRef.setInput('keepPriorityIds', ['c']);
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            // 3 buttons (308px incl. gaps) don't fit a 200px row (available 192 after the safety margin, budget 154
            // after the ellipsis and its gap), so exactly one 100px button can stay inline — it must be 'c', the sole
            // entry in keepPriorityIds, even though it is last in display order.
            component['rowWidth'].set(200);

            expect(component.hiddenActions().map((a) => a.id)).toEqual(['a', 'b']);
        });

        it('hides nothing when every button fits', () => {
            fixture.componentRef.setInput('items', buildActions(['a', 'b']));
            fixture.componentRef.setInput('inlineIds', ['a', 'b']);
            seedEqualWidths(50);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(2000);
            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
        });

        it('drops the labels of the reserved content only when the row cannot show them next to its buttons', () => {
            const compact: boolean[] = [];
            component.reservedCompactChange.subscribe((value) => compact.push(value));
            fixture.componentRef.setInput('items', buildActions(['participations', 'edit', 'delete']));
            fixture.detectChanges();
            seedEqualWidths(30);
            // Reserved content 120px wide, 80px of it labels.
            component['reservedWidth'].set(120);
            component['reservedLabelsWidth'].set(80);
            component['lastReservedLabelsWidth'].set(80);

            // Buttons 98px (3 x 30 + 2 gaps), reserved 120px and its gap: 222px plus the safety margin fit a 300px row.
            component['rowWidth'].set(300);
            fixture.detectChanges();
            expect(compact.at(-1)).toBe(false);

            component['rowWidth'].set(220);
            fixture.detectChanges();
            expect(compact.at(-1)).toBe(true);
        });

        it('keeps the same decision with the labels shown and hidden at the same row width', () => {
            const compact: boolean[] = [];
            component.reservedCompactChange.subscribe((value) => compact.push(value));
            fixture.componentRef.setInput('items', buildActions(['participations', 'edit', 'delete']));
            fixture.detectChanges();
            seedEqualWidths(30);
            component['lastReservedLabelsWidth'].set(77);
            // Just narrower than buttons (98px), reserved content with labels (133px), its gap and the safety margin.
            component['rowWidth'].set(240);

            // Labels shown: 133px reserved, 77px of it labels (with their margin and gap).
            component['reservedWidth'].set(133);
            component['reservedLabelsWidth'].set(77);
            fixture.detectChanges();
            expect(compact.at(-1)).toBe(true);

            // Labels hidden: 56px reserved. The width with labels is still 133px, so the decision holds.
            component['reservedWidth'].set(56);
            component['reservedLabelsWidth'].set(0);
            fixture.detectChanges();
            expect(component['fullReservedWidth']()).toBe(133);
            expect(compact.at(-1)).toBe(true);
        });

        it('reports the width that shows every action once all buttons are measured', () => {
            const reported: number[] = [];
            component.naturalWidthChange.subscribe((width) => reported.push(width));
            fixture.componentRef.setInput('items', buildActions(['a', 'b', 'c']));
            fixture.componentRef.setInput('inlineIds', ['a', 'b', 'c']);
            fixture.detectChanges();
            expect(reported.at(-1)).toBe(0);

            seedEqualWidths(30);
            component['reservedWidth'].set(50);
            fixture.detectChanges();
            // 3 buttons and 2 gaps, then the reserved content and its gap, then the safety margin.
            expect(reported.at(-1)).toBe(3 * 30 + 2 * 4 + 50 + 4 + 8);
        });
    });

    describe('visibleLabelsWidth', () => {
        it('counts each visible label with its margin and the gap before it, and hidden labels as nothing', () => {
            const container = document.createElement('div');
            container.innerHTML =
                '<button style="column-gap: 8px"><i></i><span data-compactable-label style="margin-inline-start: 4px">Start</span></button>' +
                '<button style="column-gap: 8px"><i></i><span data-compactable-label style="margin-inline-start: 4px">End</span></button>';
            const [start, end] = [...container.querySelectorAll<HTMLElement>('[data-compactable-label]')];
            vi.spyOn(start, 'getBoundingClientRect').mockReturnValue({ width: 60 } as DOMRect);
            vi.spyOn(end, 'getBoundingClientRect').mockReturnValue({ width: 0 } as DOMRect);

            expect(visibleLabelsWidth(container)).toBe(60 + 4 + 8);
            expect(visibleLabelsWidth(undefined)).toBe(0);
        });
    });

    describe('disabled tooltip', () => {
        const tooltipOf = () => fixture.debugElement.query(By.css('a[data-testid="exercise-action-edit"]')).injector.get(TumAetUiTooltipDirective);
        const tooltipTextOf = () => tooltipOf().content();
        /** The text of the permanent description the element points to, or undefined without one. */
        const descriptionOf = (element: HTMLElement) => {
            const id = element.getAttribute('aria-describedby');
            return id ? (fixture.nativeElement.querySelector(`#${id}`)?.textContent ?? undefined) : undefined;
        };

        it('explains a disabled inline action and otherwise names it', () => {
            const action: ActionItem = { ...buildActions(['edit'])[0], disabledTooltip: 'tooltip.key' };

            fixture.componentRef.setInput('items', [{ ...action, disabled: true }]);
            fixture.detectChanges();
            expect(tooltipTextOf()).toBe('tooltip.key');
            // The reason stays available to screen readers without the tooltip, which only exists while shown.
            const link: HTMLElement = fixture.nativeElement.querySelector('[data-testid="exercise-action-edit"]');
            expect(descriptionOf(link)).toBe('tooltip.key');
            expect(tooltipOf().describesHost()).toBe(false);

            // Same action, still carrying disabledTooltip, but usable — it must not explain a restriction that is gone.
            fixture.componentRef.setInput('items', [{ ...action, disabled: false }]);
            fixture.detectChanges();
            expect(tooltipTextOf()).toBe('label.edit');
            // The name already is the aria-label; describing the action with it again would announce it twice.
            expect(descriptionOf(link)).toBeUndefined();
            expect(tooltipOf().describesHost()).toBe(false);
        });

        it('puts the tooltip of a disabled inline delete on its wrapper and describes the button with the reason', () => {
            const action: ActionItem = {
                id: 'delete',
                labelKey: 'label.delete',
                icon: faTrash,
                severity: 'danger',
                kind: 'delete',
                disabled: true,
                disabledTooltip: 'tooltip.key',
                delete: { entityTitle: 'x', deleteQuestion: 'q', deleteConfirmationText: 'c', dialogError: of(''), onDelete: vi.fn() },
            };
            fixture.componentRef.setInput('items', [action]);
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('[data-testid="exercise-action-delete"]'));
            expect(button.nativeElement.disabled).toBe(true);
            expect(button.parent!.injector.get(TumAetUiTooltipDirective).content()).toBe('tooltip.key');
            expect(descriptionOf(button.nativeElement)).toBe('tooltip.key');
        });
    });

    it('disables the native action button while pending and restores its callback when enabled', () => {
        const onClick = vi.fn();
        const action: ActionItem = { id: 'variant', labelKey: 'label.variant', icon: faTable, severity: 'warn', kind: 'button', onClick };
        fixture.componentRef.setInput('inlineIds', ['variant']);
        fixture.componentRef.setInput('items', [{ ...action, disabled: true }]);
        fixture.detectChanges();
        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="exercise-action-variant"]');
        expect(button.disabled).toBe(true);
        button.click();
        expect(onClick).not.toHaveBeenCalled();

        fixture.componentRef.setInput('items', [{ ...action, disabled: false }]);
        fixture.detectChanges();
        expect(button.disabled).toBe(false);
        button.click();
        expect(onClick).toHaveBeenCalledExactlyOnceWith();
    });

    describe('menu interactions', () => {
        it('runAction and closeMenuIfOpen do not throw when the popover is closed', () => {
            fixture.componentRef.setInput('items', buildActions(['a']));
            expect(() => component['runAction'](component.items()[0])).not.toThrow();
            expect(() => component['closeMenuIfOpen'](true)).not.toThrow();
            expect(() => component['closeMenuIfOpen'](false)).not.toThrow();
        });

        it('runAction invokes the action onClick callback', () => {
            const onClick = vi.fn();
            component['runAction']({ id: 'x', labelKey: 'k', icon: faTable, severity: 'primary', kind: 'button', onClick });
            expect(onClick).toHaveBeenCalledOnce();
        });
    });

    describe('onDelete', () => {
        it('delegates to the action delete config', () => {
            const onDelete = vi.fn();
            const action: ActionItem = {
                id: 'delete',
                labelKey: 'k',
                icon: faTrash,
                severity: 'danger',
                kind: 'delete',
                delete: {
                    entityTitle: 'x',
                    deleteQuestion: 'q',
                    deleteConfirmationText: 'c',
                    dialogError: of(''),
                    onDelete,
                },
            };
            component['onDelete'](action, { flag: true });
            expect(onDelete).toHaveBeenCalledWith({ flag: true });
        });
    });
});
