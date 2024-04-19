import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { SidebarCardComponent } from 'app/shared/sidebar/sidebar-card/sidebar-card.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { ArtemisTestModule } from '../../../test.module';
import { MockModule } from 'ng-mocks';
import { Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { DifficultyLevel } from 'app/entities/exercise.model';

describe('SidebarCardComponent', () => {
    let component: SidebarCardComponent;
    let fixture: ComponentFixture<SidebarCardComponent>;
    let router: MockRouter;

    beforeEach(async(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(RouterModule)],
            declarations: [SidebarCardComponent, SidebarCardItemComponent, MockRouterLinkDirective],
            providers: [{ provide: Router, useValue: router }],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardComponent);
        component = fixture.componentInstance;
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
        };
        component.itemSelected = true;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have success border class for easy difficulty', () => {
        (component.sidebarItem.difficulty = DifficultyLevel.EASY), fixture.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        const classes = element.className;
        expect(classes).toContain('border-success');
    });

    it('should have success border class for medium difficulty', () => {
        (component.sidebarItem.difficulty = DifficultyLevel.MEDIUM), fixture.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        const classes = element.className;
        expect(classes).toContain('border-warning');
    });

    it('should have success border class for hard difficulty', () => {
        (component.sidebarItem.difficulty = DifficultyLevel.HARD), fixture.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        const classes = element.className;
        expect(classes).toContain('border-danger');
    });

    it('should store route on click', () => {
        jest.spyOn(component, 'emitStoreLastSelectedItem');
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        element.click();
        fixture.detectChanges();
        expect(component.emitStoreLastSelectedItem).toHaveBeenCalledWith(component.sidebarItem.id);
    });

    it('should navigate to the item URL on click', async () => {
        const mockFn = jest.fn();
        component.emitStoreLastSelectedItem = mockFn;
        component.itemSelected = true;
        fixture.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        itemElement.click();
        await fixture.whenStable();
        expect(mockFn).toHaveBeenCalledWith('testId');
        expect(router.navigateByUrl).toHaveBeenCalled();
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toBe('../testId');
    });

    it('should navigate to the when no item was selected before', async () => {
        const mockFn = jest.fn();
        component.emitStoreLastSelectedItem = mockFn;
        component.itemSelected = false;
        fixture.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card');
        itemElement.click();
        await fixture.whenStable();
        expect(mockFn).toHaveBeenCalledWith('testId');
        expect(router.navigateByUrl).toHaveBeenCalled();
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toBe('./testId');
    });
});
