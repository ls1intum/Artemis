import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingInfoModalComponent } from 'app/assessment/manage/grading/grading-info-modal/grading-info-modal.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

describe('GradingInfoModalComponent', () => {
    let component: GradingInfoModalComponent;
    let fixture: ComponentFixture<GradingInfoModalComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(GradingInfoModalComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingInfoModalComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            expect(component).toBeTruthy();
        });

        it('should have the question circle icon defined', () => {
            expect(component.farQuestionCircle).toBe(faQuestionCircle);
        });

        it('should initialize with dialog not visible', () => {
            expect(component.visible()).toBe(false);
        });

        it('should have an accessible aria-label on the open button', () => {
            const button = fixture.debugElement.nativeElement.querySelector('button[tumAetUiButton]');
            expect(button).not.toBeNull();
            expect(button.getAttribute('aria-label')).toBe('artemisApp.gradingSystem.info.title');
        });
    });

    describe('open', () => {
        it('should set visible to true when open is called', () => {
            expect(component.visible()).toBe(false);

            component.open();

            expect(component.visible()).toBe(true);
        });

        it('should keep visible true when open is called multiple times', () => {
            component.open();
            component.open();

            expect(component.visible()).toBe(true);
        });
    });

    describe('close', () => {
        it('should set visible to false when close is called', () => {
            component.open();
            expect(component.visible()).toBe(true);

            component.close();

            expect(component.visible()).toBe(false);
        });

        it('should keep visible false when close is called multiple times', () => {
            component.close();
            component.close();

            expect(component.visible()).toBe(false);
        });
    });

    describe('open and close interaction', () => {
        it('should toggle visibility correctly', () => {
            expect(component.visible()).toBe(false);

            component.open();
            expect(component.visible()).toBe(true);

            component.close();
            expect(component.visible()).toBe(false);

            component.open();
            expect(component.visible()).toBe(true);
        });
    });
    it('renders a named modal and restores the trigger focus when Escape dismisses it', async () => {
        const trigger = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        trigger.focus();
        trigger.click();
        fixture.detectChanges();
        await fixture.whenStable();
        const dialog = document.querySelector('[role="dialog"]') as HTMLElement;
        expect(dialog).not.toBeNull();
        expect(dialog.textContent).toContain('entity.action.close');
        dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.visible()).toBe(false);
        expect(document.querySelector('[role="dialog"]')).toBeNull();
        expect(document.activeElement).toBe(trigger);
    });
});
