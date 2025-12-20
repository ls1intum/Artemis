import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardMediumComponent } from 'app/shared/sidebar/sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { MockModule } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('SidebarCardMediumComponent', () => {
    let component: SidebarCardMediumComponent;
    let fixture: ComponentFixture<SidebarCardMediumComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule)],
            declarations: [SidebarCardMediumComponent, SidebarCardItemComponent, MockRouterLinkDirective],
            providers: [
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardMediumComponent);
        component = fixture.componentInstance;
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'M',
        };
        component.itemSelected = true;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have success border class for easy difficulty', () => {
        component.sidebarItem.difficulty = DifficultyLevel.EASY;
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-success');
    });

    it('should have success border class for medium difficulty', () => {
        component.sidebarItem.difficulty = DifficultyLevel.MEDIUM;
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-warning');
    });

    it('should have success border class for hard difficulty', () => {
        component.sidebarItem.difficulty = DifficultyLevel.HARD;
        fixture.changeDetectorRef.detectChanges();
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-medium');
        const classes = element.className;
        expect(classes).toContain('border-danger');
    });

    it('should store target subroute and refresh on click when previously an item was selected', async () => {
        jest.spyOn(component, 'storeTargetComponentSubRoute');
        jest.spyOn(component, 'refreshChildComponent');
        component.itemSelected = true;
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
        jest.spyOn(component, 'storeTargetComponentSubRoute');
        jest.spyOn(component, 'refreshChildComponent');
        component.itemSelected = false;
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
