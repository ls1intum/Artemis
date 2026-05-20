import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { By } from '@angular/platform-browser';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

describe('ChatStatusBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChatStatusBarComponent;
    let fixture: ComponentFixture<ChatStatusBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChatStatusBarComponent, MockComponent(IrisLogoComponent)],
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
        })
            .overrideComponent(ChatStatusBarComponent, {
                remove: { imports: [IrisLogoComponent] },
                add: { imports: [MockComponent(IrisLogoComponent)] },
            })
            .compileComponents();

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

    it('should open when unfinished stages are present', async () => {
        const stages = [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        expect(component.open()).toBe(true);
        expect(component.activeStage()).toEqual(stages[0]);
    });

    it('should close when all stages are finished', async () => {
        const stages = [{ name: 'Test Stage', state: IrisStageStateDTO.DONE, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        expect(component.open()).toBe(false);
        expect(component.activeStage()).toBeUndefined();
    });

    it('should return true for finished stages in isStageFinished', () => {
        expect(component.isStageFinished({ name: 'S', state: IrisStageStateDTO.DONE, weight: 1, message: '', internal: false })).toBe(true);
        expect(component.isStageFinished({ name: 'S', state: IrisStageStateDTO.SKIPPED, weight: 1, message: '', internal: false })).toBe(true);
    });

    it('should return false for unfinished stages in isStageFinished', () => {
        expect(component.isStageFinished({ name: 'S', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false })).toBe(false);
    });

    it('should detect error state', async () => {
        const stages = [{ name: 'Error Stage', state: IrisStageStateDTO.ERROR, weight: 1, message: 'Something failed', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.isError()).toBe(true);
        const errorDisplay = fixture.debugElement.query(By.css('.display.error'));
        expect(errorDisplay).toBeTruthy();
    });

    it('should render iris-logo when in progress', async () => {
        const stages = [{ name: 'Test', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        const logo = fixture.debugElement.query(By.css('jhi-iris-logo'));
        expect(logo).toBeTruthy();
    });

    it('should render 3 typing dots when in progress', async () => {
        const stages = [{ name: 'Test', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        const dots = fixture.debugElement.queryAll(By.css('.typing-dots .dot'));
        expect(dots).toHaveLength(3);
    });

    it('should have an accessible status element', async () => {
        const stages = [{ name: 'Test', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        const statusEl = fixture.debugElement.query(By.css('[role="status"][aria-live="polite"]'));
        expect(statusEl).toBeTruthy();
    });

    it('should not show iris-logo in error state', async () => {
        const stages = [{ name: 'Error', state: IrisStageStateDTO.ERROR, weight: 1, message: 'fail', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        const logo = fixture.debugElement.query(By.css('jhi-iris-logo'));
        expect(logo).toBeFalsy();
        const errorIcon = fixture.debugElement.query(By.css('fa-icon'));
        expect(errorIcon).toBeTruthy();
    });

    it('should prefer stage.message over stage.name for displayName', async () => {
        const stages = [{ name: 'Thinking hard', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Checking the available information', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.displayName()).toBe('Checking the available information');
        const statusText = fixture.debugElement.query(By.css('.status-text'));
        expect(statusText.nativeElement.textContent.trim()).toBe('Checking the available information');
    });

    it('should show rotation label immediately when message is empty during IN_PROGRESS', async () => {
        const stages = [{ name: 'Executing pipeline', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: '', internal: false }];
        fixture.componentRef.setInput('stages', stages);
        await fixture.whenStable();
        fixture.detectChanges();
        // Should NOT show the raw Pyris name; should show the first rotation key instead
        expect(component.displayName()).not.toBe('Executing pipeline');
        expect(component.displayName()).toBe('artemisApp.iris.stages.thinking');
    });

    it('should update displayName when stage changes', async () => {
        const stages1 = [{ name: 'Thinking hard', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Analyzing your code', internal: false }];
        fixture.componentRef.setInput('stages', stages1);
        await fixture.whenStable();
        expect(component.displayName()).toBe('Analyzing your code');

        const stages2 = [
            { name: 'Thinking hard', state: IrisStageStateDTO.DONE, weight: 1, message: '', internal: false },
            { name: 'Analyzing context', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Formulating response', internal: false },
        ];
        fixture.componentRef.setInput('stages', stages2);
        await fixture.whenStable();
        expect(component.displayName()).toBe('Formulating response');
    });
});
