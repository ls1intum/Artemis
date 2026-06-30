import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { LectureChatbotComponent } from './lecture-chatbot.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { of } from 'rxjs';

describe('LectureChatbotComponent', () => {
    let fixture: ComponentFixture<LectureChatbotComponent>;
    let component: LectureChatbotComponent;
    let irisChatService: IrisChatService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureChatbotComponent],
            providers: [
                MockProvider(IrisChatService, {
                    switchTo: vi.fn(),
                    currentChatMode: vi.fn(() => of(ChatServiceMode.LECTURE)),
                }),
            ],
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

    describe('contextProvider', () => {
        it('should return undefined when contextsProvider is not provided', () => {
            fixture.detectChanges();

            const result = component.contextProvider();

            expect(result).toBeUndefined();
        });

        it('should return a function when contextsProvider is provided', () => {
            const mockProvider = {
                getVisibleContexts: vi.fn().mockReturnValue([]),
            };
            fixture.componentRef.setInput('contextsProvider', mockProvider);
            fixture.detectChanges();

            const result = component.contextProvider();

            expect(result).toBeDefined();
            expect(typeof result).toBe('function');
        });

        it('should call getVisibleContexts when the returned function is invoked', () => {
            const mockContexts = [
                { type: 'slides', lectureUnitId: 123, page: 5 },
                { type: 'video', lectureUnitId: 123, timestamp: 42.5 },
            ];
            const mockProvider = {
                getVisibleContexts: vi.fn().mockReturnValue(mockContexts),
            };
            fixture.componentRef.setInput('contextsProvider', mockProvider);
            fixture.detectChanges();

            const contextFn = component.contextProvider();
            const result = contextFn?.();

            expect(mockProvider.getVisibleContexts).toHaveBeenCalled();
            expect(result).toEqual(mockContexts);
        });
    });
});
