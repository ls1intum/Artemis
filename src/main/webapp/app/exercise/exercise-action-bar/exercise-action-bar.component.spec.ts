import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseActionBarComponent } from 'app/exercise/exercise-action-bar/exercise-action-bar.component';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';
import { faTable, faTrash } from '@fortawesome/free-solid-svg-icons';
import { of } from 'rxjs';

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

    describe('hiddenIds / hasOverflow', () => {
        it('shows nothing hidden before the row width is measured', () => {
            fixture.componentRef.setInput('items', buildActions(['participations', 'scores', 'edit', 'delete']));
            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
            expect(component.hiddenActions()).toEqual([]);
        });

        /** Seeds every current action's cached natural width to the same value, so only the row width drives collapsing. */
        const seedEqualWidths = (buttonWidth: number): void => {
            const widths = new Map<string, number>();
            for (const action of component.items()) {
                widths.set(component['signatureOf'](action), buttonWidth);
            }
            component['buttonWidths'].set(widths);
        };

        it('keeps the default-priority ids (delete, edit, scores) inline and overflows the rest first', () => {
            fixture.componentRef.setInput('items', buildActions(['participations', 'scores', 'statistics', 'preview', 'solution', 'edit', 'delete']));

            // Each button 100px wide. With a 360px row (available 352 after the safety margin, budget 308 after the
            // ellipsis + gap) exactly three 100px buttons plus their gaps fit.
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            component['rowWidth'].set(360);

            const hidden = component.hiddenActions().map((a) => a.id);
            expect(component.hasOverflow()).toBe(true);
            // The three highest-priority actions stay inline regardless of their display position.
            expect(hidden).not.toContain('scores');
            expect(hidden).not.toContain('edit');
            expect(hidden).not.toContain('delete');
            // The type-specific extras collapse into the ellipsis menu.
            expect(hidden).toEqual(expect.arrayContaining(['participations', 'statistics', 'preview', 'solution']));
        });

        it('respects a custom keepPriorityIds ordering', () => {
            fixture.componentRef.setInput('items', buildActions(['a', 'b', 'c']));
            fixture.componentRef.setInput('keepPriorityIds', ['c']);
            seedEqualWidths(100);
            component['reservedWidth'].set(0);
            // 3 buttons (308px incl. gaps) don't fit a 200px row (152px budget after the ellipsis+margin), so exactly
            // one 100px button can stay inline — it must be 'c', the sole entry in keepPriorityIds.
            component['rowWidth'].set(200);

            const hidden = component.hiddenActions().map((a) => a.id);
            expect(hidden).not.toContain('c');
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
