import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { SidebarCardSmallComponent } from 'app/course/sidebar/sidebar-card-small/sidebar-card-small.component';
import { SidebarCardItemComponent } from 'app/course/sidebar/sidebar-card-item/sidebar-card-item.component';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ConversationOptionsComponent } from 'app/course/sidebar/conversation-options/conversation-options.component';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/communication/service/metis.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockActivatedRoute } from '../../../../../../test/javascript/spec/helpers/mocks/activated-route/mock-activated-route';

describe('SidebarCardSmallComponent', () => {
    setupTestBed({ zoneless: true });
    let component: SidebarCardSmallComponent;
    let fixture: ComponentFixture<SidebarCardSmallComponent>;
    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [
                MockModule(RouterModule),
                SidebarCardSmallComponent,
                SidebarCardItemComponent,
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
            // Replace the real (heavyweight) ConversationOptionsComponent — which pulls in
            // ConversationService → TranslateService and routerLinkActive → router.parseUrl —
            // with a lightweight mock, keeping this a focused unit test of the card itself.
            remove: { imports: [ConversationOptionsComponent] },
            add: {
                imports: [MockComponent(ConversationOptionsComponent)],
                providers: [{ provide: MetisService, useValue: metisService }],
            },
        });

        fixture = TestBed.createComponent(SidebarCardSmallComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('sidebarItem', {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
        });

        fixture.componentRef.setInput('itemSelected', true);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should store route on click', () => {
        vi.spyOn(component, 'emitStoreAndRefresh');
        vi.spyOn(component, 'refreshChildComponent');
        const element: HTMLElement = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        element.click();
        fixture.changeDetectorRef.detectChanges();
        expect(component.emitStoreAndRefresh).toHaveBeenCalledWith(component.sidebarItem().id);
    });

    /*
   Next 2 tests explicitly adjusted for the messages module as a workaround, since routing in the messages module
   operates differently over the MetisConversations service, it will get adjusted in a followup PR
   */

    it('should navigate to the item URL on click', async () => {
        vi.spyOn(component, 'emitStoreAndRefresh');
        fixture.componentRef.setInput('itemSelected', true);
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
        vi.spyOn(component, 'emitStoreAndRefresh');
        fixture.componentRef.setInput('itemSelected', false);
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
        fixture.componentRef.setInput('sidebarItem', {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
            conversation: { hasUnreadMessage: true, isMuted: true },
        });
        fixture.changeDetectorRef.detectChanges();
        const card = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        expect(card.classList.contains('border-primary')).toBe(false);
    });

    it('should have border-primary for non-muted conversations with unread', () => {
        fixture.componentRef.setInput('sidebarItem', {
            title: 'testTitle',
            id: 'testId',
            size: 'S',
            conversation: { hasUnreadMessage: true, isMuted: false },
        });
        fixture.changeDetectorRef.detectChanges();
        const card = fixture.nativeElement.querySelector('#test-sidebar-card-small');
        expect(card.classList.contains('border-primary')).toBe(true);
    });
});
