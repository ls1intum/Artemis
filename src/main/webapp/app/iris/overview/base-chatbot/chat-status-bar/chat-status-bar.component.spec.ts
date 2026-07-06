import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { By } from '@angular/platform-browser';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';

describe('ChatStatusBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChatStatusBarComponent;
    let fixture: ComponentFixture<ChatStatusBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChatStatusBarComponent],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        instant: vi.fn((key: string) => key),
                        getCurrentLang: vi.fn().mockReturnValue('en'),
                        onTranslationChange: new Subject(),
                        onLangChange: new Subject(),
                        onDefaultLangChange: new Subject(),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ChatStatusBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should stay hidden when there is no failed run', async () => {
        fixture.componentRef.setInput('runInfo', { runId: 'run-1', state: IrisRunState.RUNNING });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.display.error'))).toBeFalsy();
        expect(fixture.debugElement.query(By.css('.thinking-state'))).toBeFalsy();
    });

    it('should render a translated error pill for a failed run', async () => {
        fixture.componentRef.setInput('runInfo', { runId: 'run-1', state: IrisRunState.FAILED, error: { message: 'Model unavailable' } });
        await fixture.whenStable();
        fixture.detectChanges();

        const errorDisplay = fixture.debugElement.query(By.css('.display.error'));
        expect(errorDisplay).toBeTruthy();
        expect(errorDisplay.nativeElement.textContent).toContain('Model unavailable');
        expect(errorDisplay.query(By.css('fa-icon'))).toBeTruthy();
    });

    it('should use literal fallback when the error translation is missing', async () => {
        const translateService = TestBed.inject(TranslateService);
        vi.spyOn(translateService, 'instant').mockReturnValue('translation-not-found[Raw backend error]');

        fixture.componentRef.setInput('runInfo', { runId: 'run-1', state: IrisRunState.FAILED, error: { message: 'Raw backend error' } });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.display.error')).nativeElement.textContent).toContain('Raw backend error');
    });
});
