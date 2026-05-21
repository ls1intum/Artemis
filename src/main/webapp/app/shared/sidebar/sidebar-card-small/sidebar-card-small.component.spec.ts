import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardSmallComponent } from 'app/shared/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ConversationOptionsComponent } from 'app/shared/sidebar/conversation-options/conversation-options.component';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/communication/service/metis.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockActivatedRoute } from '../../../../../../test/javascript/spec/helpers/mocks/activated-route/mock-activated-route';

describe('SidebarCardSmallComponent', () => {
    let component: SidebarCardSmallComponent;
    let fixture: ComponentFixture<SidebarCardSmallComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule)],
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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
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
        fixture.changeDetectorRef.detectChanges();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith(component.sidebarItem.id);
    });

    /*
   Next 2 tests explicitly adjusted for the messages module as a workaround, since routing in the messages module
   operates differently over the MetisConversations service, it will get adjusted in a followup PR
   */

    it('should navigate to the item URL on click', async () => {
        jest.spyOn(component, 'emitStoreAndRefresh');
        component.itemSelected = true;
        fixture.changeDetectorRef.detectChanges();
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
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
        const itemElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        itemElement.click();
        await fixture.whenStable();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith('testId');
        expect(router.navigate).toHaveBeenCalled();
        const navigationArray = router.navigate.mock.calls[1][0];
        expect(navigationArray).toStrictEqual(['', 'testId']);
    });

    it('should not have border-primary for muted conversations with unread', () => {
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
            conversation: { hasUnreadMessage: true, isMuted: true },
        };
        fixture.changeDetectorRef.detectChanges();
        const card = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        expect(card.classList.contains('border-primary')).toBeFalse();
    });

    it('should have border-primary for non-muted conversations with unread', () => {
        component.sidebarItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
            conversation: { hasUnreadMessage: true, isMuted: false },
        };
        fixture.changeDetectorRef.detectChanges();
        const card = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        expect(card.classList.contains('border-primary')).toBeTrue();
    });
});
