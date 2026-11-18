import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Type } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { MockComponent } from 'ng-mocks';
import { SidebarCardSmallComponent } from './sidebar-card-small/sidebar-card-small.component';
import { SidebarCardMediumComponent } from './sidebar-card-medium/sidebar-card-medium.component';
import { SidebarCardLargeComponent } from './sidebar-card-large/sidebar-card-large.component';
import { SidebarCardItemComponent } from './sidebar-card-item/sidebar-card-item.component';
import { ConversationOptionsComponent } from './conversation-options/conversation-options.component';

type NavigationCard = SidebarCardSmallComponent | SidebarCardMediumComponent | SidebarCardLargeComponent;
const cardTypes: Type<NavigationCard>[] = [SidebarCardSmallComponent, SidebarCardMediumComponent, SidebarCardLargeComponent];

describe.each(cardTypes)('Sidebar navigation with %s', (cardType) => {
    let fixture: ComponentFixture<NavigationCard>;
    let router: Router;

    beforeEach(async () => {
        TestBed.configureTestingModule({ providers: [provideRouter([])] });
        TestBed.overrideComponent(cardType, {
            remove: { imports: [SidebarCardItemComponent] },
            add: { imports: [MockComponent(SidebarCardItemComponent)] },
        });
        if (cardType === SidebarCardSmallComponent) {
            TestBed.overrideComponent(cardType, {
                remove: { imports: [ConversationOptionsComponent] },
                add: { imports: [MockComponent(ConversationOptionsComponent)] },
            });
        }
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(cardType);
        fixture.componentRef.setInput('sidebarItem', { title: 'Card title', id: 42, conversation: { id: 42 } });
        fixture.componentRef.setInput('itemSelected', true);
        fixture.detectChanges();
        vi.spyOn(fixture.componentInstance, 'refreshChildComponent').mockImplementation(() => {});
        router = TestBed.inject(Router);
        vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    });

    it('uses a native link while keeping independent conversation actions outside', () => {
        const link = fixture.nativeElement.querySelector('a[href]') as HTMLAnchorElement;
        expect(link).not.toBeNull();
        expect(link.hasAttribute('role')).toBe(false);
        expect(link.getAttribute('href')).toContain(cardType === SidebarCardSmallComponent ? 'communication?conversationId=42' : '42');
        expect(link.querySelector('jhi-conversation-options, button, a, [role="button"]')).toBeNull();
        const options = fixture.nativeElement.querySelector('jhi-conversation-options') as HTMLElement | null;
        if (cardType === SidebarCardSmallComponent) {
            expect(options).not.toBeNull();
            expect(options!.closest('a, [role="button"]')).toBeNull();
            const action = document.createElement('button');
            options!.appendChild(action);
            action.click();
            expect(router.navigateByUrl).not.toHaveBeenCalled();
            expect(fixture.componentInstance.refreshChildComponent).not.toHaveBeenCalled();
        }
        const space = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true });
        link.dispatchEvent(space);
        expect(space.defaultPrevented).toBe(false);
        link.click();
        expect(router.navigateByUrl).toHaveBeenCalledOnce();
        expect(fixture.componentInstance.refreshChildComponent).toHaveBeenCalledOnce();
    });

    it('keeps the selected highlight on the styled card', async () => {
        router.resetConfig([
            { path: '42', children: [] },
            { path: 'communication', children: [] },
        ]);
        vi.mocked(router.navigateByUrl).mockRestore();
        await router.navigateByUrl(cardType === SidebarCardSmallComponent ? '/communication?conversationId=42' : '/42');
        fixture.detectChanges();
        await fixture.whenStable();
        const card = fixture.nativeElement.querySelector('[id^="test-sidebar-card-"]') as HTMLElement;
        expect(card.classList.contains('bg-selected')).toBe(true);
        expect(card.classList.contains('border-selected')).toBe(true);
    });

    it.each([{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }])('preserves native modified navigation for %j', (modifiers) => {
        const link = fixture.nativeElement.querySelector('a[href]') as HTMLAnchorElement;
        expect(link).not.toBeNull();
        const event = new MouseEvent('click', { bubbles: true, cancelable: true, ...modifiers });
        link.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(false);
        expect(router.navigateByUrl).not.toHaveBeenCalled();
        expect(fixture.componentInstance.refreshChildComponent).not.toHaveBeenCalled();
    });
});

describe('Medium exercise card retry separation', () => {
    it('renders one real failed-submission retry outside the link and leaves navigation independent', async () => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        });
        TestBed.overrideComponent(SubmissionResultStatusComponent, {
            remove: { imports: [UpdatingResultComponent, ResultComponent] },
            add: { imports: [MockComponent(UpdatingResultComponent), MockComponent(ResultComponent)] },
        });
        const service = TestBed.inject(ProgrammingSubmissionService);
        const pending = vi
            .spyOn(service, 'getLatestPendingSubmissionByParticipationId')
            .mockReturnValue(of({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, participationId: 12, submission: undefined }));
        const retry = vi.spyOn(service, 'triggerFailedBuild').mockReturnValue(of());
        const fixture = TestBed.createComponent(SidebarCardMediumComponent);
        fixture.componentRef.setInput('sidebarType', 'exercise');
        fixture.componentRef.setInput('itemSelected', true);
        fixture.componentRef.setInput('sidebarItem', {
            id: 42,
            title: 'Programming exercise',
            size: 'M',
            exercise: { id: 42, type: ExerciseType.PROGRAMMING },
            studentParticipation: { id: 12, initializationState: InitializationState.INITIALIZED, submissions: [] },
        });
        const refresh = vi.spyOn(fixture.componentInstance, 'refreshChildComponent').mockImplementation(() => {});
        const store = vi.spyOn(fixture.componentInstance, 'storeTargetComponentSubRoute');
        const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
        fixture.detectChanges();
        const link = fixture.nativeElement.querySelector('a[href]') as HTMLAnchorElement;
        const retries = fixture.nativeElement.querySelectorAll('jhi-programming-exercise-student-trigger-build-button');
        expect(retries).toHaveLength(1);
        expect(retries[0].closest('a, [role="button"]')).toBeNull();
        expect(link.querySelector('jhi-submission-result-status')).not.toBeNull();
        expect(pending).toHaveBeenCalledOnce();
        const button = retries[0].querySelector('button') as HTMLButtonElement;
        expect(button).not.toBeNull();
        button.click();
        expect(retry).toHaveBeenCalledOnce();
        expect(store).not.toHaveBeenCalled();
        expect(navigation).not.toHaveBeenCalled();
        expect(refresh).not.toHaveBeenCalled();
        link.click();
        expect(store).toHaveBeenCalledOnce();
        expect(navigation).toHaveBeenCalledOnce();
        expect(refresh).toHaveBeenCalledOnce();
    });
});
