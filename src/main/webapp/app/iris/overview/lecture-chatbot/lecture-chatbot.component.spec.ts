import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { LectureChatbotComponent } from './lecture-chatbot.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';

describe('LectureChatbotComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LectureChatbotComponent>;
    let component: LectureChatbotComponent;
    let irisChatService: IrisChatService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureChatbotComponent],
            providers: [MockProvider(IrisChatService, { switchTo: vi.fn() })],
        })
            .overrideComponent(LectureChatbotComponent, {
                set: {
                    template: '',
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LectureChatbotComponent);
        component = fixture.componentInstance;
        irisChatService = TestBed.inject(IrisChatService);
    });

    it('switches to lecture chat mode when lectureId is provided', async () => {
        fixture.componentRef.setInput('lectureId', 42);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(irisChatService.switchTo).toHaveBeenCalledWith(ChatServiceMode.LECTURE, 42);
    });

    it('does not switch mode when lectureId is undefined', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(irisChatService.switchTo).not.toHaveBeenCalled();
    });

    it('toggleChatHistory does nothing when base chatbot is not available', () => {
        vi.spyOn(component as any, 'irisBaseChatbot').mockReturnValue(undefined);

        expect(() => component.toggleChatHistory()).not.toThrow();
    });

    it('toggleChatHistory toggles visibility based on current state', () => {
        const setChatHistoryVisibility = vi.fn();
        const baseChatbot = {
            isChatHistoryOpen: vi.fn().mockReturnValue(true),
            setChatHistoryVisibility,
        };

        vi.spyOn(component as any, 'irisBaseChatbot').mockReturnValue(baseChatbot);

        component.toggleChatHistory();

        expect(baseChatbot.isChatHistoryOpen).toHaveBeenCalled();
        expect(setChatHistoryVisibility).toHaveBeenCalledWith(false);
    });
});
