import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseActionBarComponent } from 'app/exercise/exercise-action-bar/exercise-action-bar.component';
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
        it('shows every action inline, without the ellipsis, before the row width is measured', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'participations', 'statistics', 'edit', 'delete']));
            fixture.detectChanges();

            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
            expect(component.hiddenActions()).toEqual([]);
            const inlineIds = [...fixture.nativeElement.querySelectorAll('.inline-item [data-testid]')].map((el: HTMLElement) => el.dataset.testid);
            expect(inlineIds).toEqual(['exercise-action-teams', 'exercise-action-participations', 'exercise-action-statistics', 'exercise-action-edit', 'exercise-action-delete']);
        });

        /** Seeds every current action's cached natural width to the same value, so only the row width drives collapsing. */
        const seedEqualWidths = (buttonWidth: number): void => {
            const widths = new Map<string, number>();
            for (const action of component.items()) {
                widths.set(component['signatureOf'](action), buttonWidth);
            }
            component['buttonWidths'].set(widths);
        };

        it('keeps the default-priority ids inline and collapses the other actions first', () => {
            fixture.componentRef.setInput('items', buildActions(['teams', 'participations', 'statistics', 'preview', 'edit-in-editor', 'edit', 'delete']));
            // Each button 100px wide. With a 460px row (available 452 after the safety margin, budget 414 after the
            // ellipsis + gap) exactly four 100px buttons plus their gaps fit.
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(460);

            expect(component.hasOverflow()).toBe(true);
            expect(component.hiddenActions().map((a) => a.id)).toEqual(['teams', 'statistics', 'preview']);
        });

        it('respects a custom keepPriorityIds ordering', () => {
            fixture.componentRef.setInput('items', buildActions(['a', 'b', 'c']));
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
            seedEqualWidths(50);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(2000);
            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
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
