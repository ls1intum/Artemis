import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardLargeComponent } from 'app/shared/sidebar/sidebar-card-large/sidebar-card-large.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { MockModule } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('SidebarCardLargeComponent', () => {
    let component: SidebarCardLargeComponent;
    let fixture: ComponentFixture<SidebarCardLargeComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule)],
            declarations: [SidebarCardLargeComponent, SidebarCardItemComponent, MockRouterLinkDirective],
            providers: [
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardLargeComponent);
        component = fixture.componentInstance;
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'L',
        };
        component.itemSelected = true;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should store route on click', () => {
        jest.spyOn(component, 'emitStoreAndRefresh');
        jest.spyOn(component, 'refreshChildComponent');
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-large');
        element.click();
        fixture.changeDetectorRef.detectChanges();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith(component.sidebarItem.id);
        expect(component.refreshChildComponent).toHaveBeenCalled();
    });

    it('should navigate to the item URL on click', async () => {
        jest.spyOn(component, 'emitStoreAndRefresh');
        component.itemSelected = true;
        fixture.changeDetectorRef.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-large');
        itemElement.click();
        await fixture.whenStable();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith('testId');
        expect(router.navigate).toHaveBeenCalled();
        const navigationArray = router.navigate.mock.calls[1][0];
        expect(navigationArray).toStrictEqual(['./testId']);
    });

    it('should navigate to the when no item was selected before', async () => {
        jest.spyOn(component, 'emitStoreAndRefresh');
        component.itemSelected = false;
        fixture.changeDetectorRef.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-large');
        itemElement.click();
        await fixture.whenStable();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith('testId');
        expect(router.navigate).toHaveBeenCalled();
        const navigationArray = router.navigate.mock.calls[1][0];
        expect(navigationArray).toStrictEqual(['', 'testId']);
    });
});
