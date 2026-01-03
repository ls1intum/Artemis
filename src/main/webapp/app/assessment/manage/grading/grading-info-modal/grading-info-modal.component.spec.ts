import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GradingInfoModalComponent } from 'app/assessment/manage/grading/grading-info-modal/grading-info-modal.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

describe('GradingSystemInfoModalComponent', () => {
    setupTestBed({ zoneless: true });
    let component: GradingInfoModalComponent;
    let fixture: ComponentFixture<GradingInfoModalComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(GradingInfoModalComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
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
});
