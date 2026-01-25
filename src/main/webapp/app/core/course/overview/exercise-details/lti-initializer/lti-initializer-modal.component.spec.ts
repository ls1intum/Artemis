import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiInitializerModalComponent } from 'app/core/course/overview/exercise-details/lti-initializer/lti-initializer-modal.component';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';

describe('LtiInitializerModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LtiInitializerModalComponent>;
    let component: LtiInitializerModalComponent;
    let alertService: AlertService;
    let router: Router;
    let activeModal: NgbActiveModal;
    let activatedRoute: ActivatedRoute;

    let infoSpy: ReturnType<typeof vi.spyOn>;
    let navigateSpy: ReturnType<typeof vi.spyOn>;
    let dismissSpy: ReturnType<typeof vi.spyOn>;

    const activeModalStub = {
        close: vi.fn(),
        dismiss: vi.fn(),
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [LtiInitializerModalComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), FormsModule, MockComponent(CopyToClipboardButtonComponent)],
            providers: [
                provideRouter([]),
                MockProvider(AlertService),
                { provide: NgbActiveModal, useValue: activeModalStub },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({}) },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(LtiInitializerModalComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);
        activeModal = TestBed.inject(NgbActiveModal);
        activatedRoute = TestBed.inject(ActivatedRoute);

        infoSpy = vi.spyOn(alertService, 'info');
        navigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
        dismissSpy = vi.spyOn(activeModal, 'dismiss');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with default values', () => {
        expect(component.readAndUnderstood).toBe(false);
        expect(component.passwordResetLocation).toEqual(['account', 'reset', 'request']);
    });

    it('should clear dialog, show info message, navigate and dismiss modal', () => {
        component.password = 'testPassword123';
        component.loginName = 'testUser';

        component.clear();

        expect(infoSpy).toHaveBeenCalledOnce();
        expect(infoSpy).toHaveBeenCalledWith('artemisApp.lti.startExercise');

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith([], {
            relativeTo: activatedRoute,
            queryParams: { initialize: null },
            queryParamsHandling: 'merge',
        });

        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should have password and loginName properties that can be set', () => {
        component.password = 'myPassword';
        component.loginName = 'myLogin';

        expect(component.password).toBe('myPassword');
        expect(component.loginName).toBe('myLogin');
    });

    it('should toggle readAndUnderstood property', () => {
        expect(component.readAndUnderstood).toBe(false);

        component.readAndUnderstood = true;
        expect(component.readAndUnderstood).toBe(true);

        component.readAndUnderstood = false;
        expect(component.readAndUnderstood).toBe(false);
    });

    it('should have correct passwordResetLocation', () => {
        expect(component.passwordResetLocation).toEqual(['account', 'reset', 'request']);
    });
});
