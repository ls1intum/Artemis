import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { LectureChatbotComponent } from './lecture-chatbot.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { of } from 'rxjs';

describe('LectureChatbotComponent', () => {
    setupTestBed({ zoneless: true });

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

    describe('buildContextBlock', () => {
        it('should return empty string when lectureUnitId is not provided', () => {
            fixture.componentRef.setInput('contextProvider', {
                getCurrentPdfPage: () => 5,
                getCurrentVideoTimestamp: () => 42.5,
            });
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('');
        });

        it('should return empty string when contextProvider is not provided', () => {
            fixture.componentRef.setInput('lectureUnitId', 123);
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('');
        });

        it('should build context block with PDF page and video timestamp', () => {
            fixture.componentRef.setInput('lectureUnitId', 123);
            fixture.componentRef.setInput('contextProvider', {
                getCurrentPdfPage: () => 5,
                getCurrentVideoTimestamp: () => 42.5,
            });
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('[context:123:5:42.5]');
        });

        it('should build context block with only PDF page', () => {
            fixture.componentRef.setInput('lectureUnitId', 456);
            fixture.componentRef.setInput('contextProvider', {
                getCurrentPdfPage: () => 7,
                getCurrentVideoTimestamp: () => undefined,
            });
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('[context:456:7:]');
        });

        it('should build context block with only video timestamp', () => {
            fixture.componentRef.setInput('lectureUnitId', 789);
            fixture.componentRef.setInput('contextProvider', {
                getCurrentPdfPage: () => undefined,
                getCurrentVideoTimestamp: () => 125.5,
            });
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('[context:789::125.5]');
        });

        it('should build context block with neither page nor timestamp', () => {
            fixture.componentRef.setInput('lectureUnitId', 999);
            fixture.componentRef.setInput('contextProvider', {
                getCurrentPdfPage: () => undefined,
                getCurrentVideoTimestamp: () => undefined,
            });
            fixture.detectChanges();

            const result = component.buildContextBlock();

            expect(result).toBe('[context:999::]');
        });
    });
});
