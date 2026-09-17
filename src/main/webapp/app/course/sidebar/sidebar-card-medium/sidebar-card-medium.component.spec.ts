import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardMediumComponent } from 'app/course/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/course/sidebar/sidebar-card-item/sidebar-card-item.component';
import { MockModule } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { SidebarEventService } from 'app/course/sidebar/service/sidebar-event.service';

describe('SidebarCardMediumComponent', () => {
    let component: SidebarCardMediumComponent;
    let fixture: ComponentFixture<SidebarCardMediumComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule), SidebarCardMediumComponent, SidebarCardItemComponent, MockRouterLinkDirective],
            providers: [
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardMediumComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('sidebarItem', {
            title: 'testTitle',
            id: 'testId',
            size: 'M',
        });
        fixture.componentRef.setInput('itemSelected', true);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should keep the neutral stripe for an exercise without difficulty', () => {
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        expect(element.className).toContain('border-module');
        expect(element.className).not.toContain('border-variant-group');
    });

    it('should have success border class for easy difficulty', () => {
        fixture.componentRef.setInput('sidebarItem', { ...component.sidebarItem(), difficulty: DifficultyLevel.EASY });
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-success');
    });

    it('should have success border class for medium difficulty', () => {
        fixture.componentRef.setInput('sidebarItem', { ...component.sidebarItem(), difficulty: DifficultyLevel.MEDIUM });
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-warning');
    });

    it('should have success border class for hard difficulty', () => {
        fixture.componentRef.setInput('sidebarItem', { ...component.sidebarItem(), difficulty: DifficultyLevel.HARD });
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-danger');
    });

    describe('selection-only cards', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('sidebarItem', { ...component.sidebarItem(), disableNavigation: true });
            fixture.detectChanges();
        });

        it('should expose a focusable button role', () => {
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
            expect(card.getAttribute('role')).toBe('button');
            expect(card.tabIndex).toBe(0);
        });

        it.each(['Enter', ' '])('should select through the sidebar event service with key %s without navigating', (key) => {
            const emit = vi.spyOn(TestBed.inject(SidebarEventService), 'emitSidebarCardEvent');
            const refresh = vi.spyOn(component, 'refreshChildComponent');
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
            const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
            card.dispatchEvent(event);
            expect(emit).toHaveBeenCalledExactlyOnceWith('testId');
            expect(refresh).not.toHaveBeenCalled();
            expect(router.navigate).not.toHaveBeenCalled();
            expect(event.defaultPrevented).toBe(true);
        });

        it('should not select repeatedly when a key is held down', () => {
            const emit = vi.spyOn(TestBed.inject(SidebarEventService), 'emitSidebarCardEvent');
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
            const event = new KeyboardEvent('keydown', { key: ' ', repeat: true, bubbles: true, cancelable: true });
            card.dispatchEvent(event);
            expect(emit).not.toHaveBeenCalled();
            expect(event.defaultPrevented).toBe(true);
        });

        it('should not intercept keyboard events from nested controls', () => {
            const emit = vi.spyOn(TestBed.inject(SidebarEventService), 'emitSidebarCardEvent');
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
            const child = document.createElement('button');
            card.appendChild(child);
            const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
            child.dispatchEvent(event);
            expect(emit).not.toHaveBeenCalled();
            expect(event.defaultPrevented).toBe(false);
        });

        it('should leave navigation cards unchanged', () => {
            fixture.componentRef.setInput('sidebarItem', { ...component.sidebarItem(), disableNavigation: false });
            fixture.detectChanges();
            const emit = vi.spyOn(TestBed.inject(SidebarEventService), 'emitSidebarCardEvent');
            const card: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
            const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
            card.dispatchEvent(event);
            expect(card.hasAttribute('role')).toBe(false);
            expect(card.hasAttribute('tabindex')).toBe(false);
            expect(emit).not.toHaveBeenCalled();
            expect(event.defaultPrevented).toBe(false);
        });
    });

    describe('variant group card', () => {
        /** A variant group is a single card; its members have no card of their own, so the group card carries their selection. */
        beforeEach(() => {
            fixture.componentRef.setInput('sidebarItem', {
                title: 'Sorting variants',
                id: 10,
                size: 'M',
                groupedItems: [
                    { title: 'Variant A', id: 11, size: 'M' },
                    { title: 'Variant B', id: 12, size: 'M' },
                ],
            });
        });

        const cardClasses = (): string => {
            fixture.changeDetectorRef.detectChanges();
            return (fixture.nativeElement.querySelector('#test-sidebar-card-medium') as HTMLElement).className;
        };

        it('should carry the primary stripe in place of a difficulty colour', () => {
            const classes = cardClasses();
            expect(classes).toContain('border-variant-group');
            expect(classes).not.toContain('border-module');
        });

        it('should mark the group card as selected while one of its members is open', () => {
            fixture.componentRef.setInput('activeItemId', 12);
            expect(cardClasses()).toContain('bg-group-selected');
        });

        it('should not mark the group card as selected for an unrelated open item', () => {
            fixture.componentRef.setInput('activeItemId', 99);
            expect(cardClasses()).not.toContain('bg-group-selected');
        });

        it('should not mark the group card as selected when no item is open', () => {
            expect(cardClasses()).not.toContain('bg-group-selected');
        });
    });

    it('should store target subroute and refresh on click when previously an item was selected', async () => {
        vi.spyOn(component, 'storeTargetComponentSubRoute');
        vi.spyOn(component, 'refreshChildComponent');
        fixture.componentRef.setInput('itemSelected', true);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        itemElement.click();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.storeTargetComponentSubRoute).toHaveBeenCalled();
        expect(component.refreshChildComponent).toHaveBeenCalled();
    });

    it('should store target subroute on click when previously no item was selected', async () => {
        vi.spyOn(component, 'storeTargetComponentSubRoute');
        vi.spyOn(component, 'refreshChildComponent');
        fixture.componentRef.setInput('itemSelected', false);
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        itemElement.click();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(component.storeTargetComponentSubRoute).toHaveBeenCalled();
        expect(component.refreshChildComponent).not.toHaveBeenCalled();
    });
});
