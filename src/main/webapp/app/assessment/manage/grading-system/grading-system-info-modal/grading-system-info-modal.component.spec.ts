import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingSystemInfoModalComponent } from 'app/assessment/manage/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

describe('GradingSystemInfoModalComponent', () => {
    let component: GradingSystemInfoModalComponent;
    let fixture: ComponentFixture<GradingSystemInfoModalComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(GradingSystemInfoModalComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingSystemInfoModalComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            expect(component).toBeTruthy();
        });

        it('should have the question circle icon defined', () => {
            expect(component.farQuestionCircle).toBe(faQuestionCircle);
        });

        it('should initialize with dialog not visible', () => {
            expect(component.visible()).toBeFalse();
        });
    });

    describe('open', () => {
        it('should set visible to true when open is called', () => {
            expect(component.visible()).toBeFalse();

            component.open();

            expect(component.visible()).toBeTrue();
        });

        it('should keep visible true when open is called multiple times', () => {
            component.open();
            component.open();

            expect(component.visible()).toBeTrue();
        });
    });

    describe('close', () => {
        it('should set visible to false when close is called', () => {
            component.open();
            expect(component.visible()).toBeTrue();

            component.close();

            expect(component.visible()).toBeFalse();
        });

        it('should keep visible false when close is called multiple times', () => {
            component.close();
            component.close();

            expect(component.visible()).toBeFalse();
        });
    });

    describe('open and close interaction', () => {
        it('should toggle visibility correctly', () => {
            expect(component.visible()).toBeFalse();

            component.open();
            expect(component.visible()).toBeTrue();

            component.close();
            expect(component.visible()).toBeFalse();

            component.open();
            expect(component.visible()).toBeTrue();
        });
    });
});
