import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ConversationOptionsComponent } from 'app/shared/sidebar/conversation-options/conversation-options.component';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('SidebarCardSmallComponent', () => {
    let component: SidebarCardSmallComponent;
    let fixture: ComponentFixture<SidebarCardSmallComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(RouterModule)],
            declarations: [
                SidebarCardSmallComponent,
                SidebarCardItemComponent,
                ConversationOptionsComponent,
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ConversationOptionsComponent),
            ],
            providers: [
                { provide: Router, useValue: router },
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        }).compileComponents();

        const metisService = new MockMetisService();
        TestBed.overrideComponent(SidebarCardSmallComponent, {
            set: {
                providers: [{ provide: MetisService, useValue: metisService }],
            },
        });

        fixture = TestBed.createComponent(SidebarCardSmallComponent);
        component = fixture.componentInstance;
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
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
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        element.click();
        fixture.detectChanges();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith(component.sidebarItem.id);
    });

    /*
   Next 2 tests explicitly adjusted for the messages module as a workaround, since routing in the messages module
   operates differently over the MetisConversations service, it will get adjusted in a followup PR
   */

    it('should navigate to the item URL on click', async () => {
        const mockFn = jest.fn();
        component.emitStoreAndRefresh = mockFn;
        component.itemSelected = true;
        fixture.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        itemElement.click();
        await fixture.whenStable();
        expect(mockFn).toHaveBeenCalledWith('testId');
        expect(router.navigateByUrl).toHaveBeenCalled();
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toBe('../communication');
    });

    it('should navigate to the when no item was selected before', async () => {
        const mockFn = jest.fn();
        component.emitStoreAndRefresh = mockFn;
        component.itemSelected = false;
        fixture.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        itemElement.click();
        await fixture.whenStable();
        expect(mockFn).toHaveBeenCalledWith('testId');
        expect(router.navigateByUrl).toHaveBeenCalled();
        const navigationArray = router.navigateByUrl.mock.calls[0][0];
        expect(navigationArray).toBe('./communication');
    });
});
