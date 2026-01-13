import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Lti13SelectContentComponent } from 'app/lti/overview/lti13-select-content/lti13-select-content.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

const routeParams = of<Record<string, string>>({});

describe('Lti13SelectContentComponent', () => {
    setupTestBed({ zoneless: true });
    let component: Lti13SelectContentComponent;
    let fixture: ComponentFixture<Lti13SelectContentComponent>;
    let routeMock: {
        snapshot: {
            queryParamMap: {
                get: ReturnType<typeof vi.fn>;
            };
        };
        params: typeof routeParams;
    };

    beforeEach(async () => {
        routeMock = {
            snapshot: {
                queryParamMap: {
                    get: vi.fn(),
                },
            },
            params: routeParams,
        };

        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, Lti13SelectContentComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: TranslateService, useClass: MockTranslateService },
                MockDirective(TranslateDirective),
                MockPipe(SafeResourceUrlPipe),
            ],
        }).compileComponents();

        HTMLFormElement.prototype.submit = vi.fn();
        fixture = TestBed.createComponent(Lti13SelectContentComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should initialize form on ngOnInit', async () => {
        const jwt = 'jwt_token';
        const id = 'id_token';
        const deepLinkUri = 'http://example.com/deep_link';

        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return jwt;
                case 'id':
                    return id;
                case 'deepLinkUri':
                    return deepLinkUri;
                default:
                    return null;
            }
        });

        component.ngOnInit();
        await fixture.whenStable();

        expect(component.actionLink).toBe(deepLinkUri);
        expect(component.isLinking).toBe(true);
    });

    it('should not auto-submit form if parameters are missing', async () => {
        routeMock.snapshot.queryParamMap.get.mockReturnValue(null);
        const autoSubmitSpy = vi.spyOn(component, 'autoSubmitForm');

        component.ngOnInit();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
        expect(autoSubmitSpy).not.toHaveBeenCalled();
    });

    it('should set isLinking to false when jwt is missing', async () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return null;
                case 'id':
                    return 'id_token';
                case 'deepLinkUri':
                    return 'http://example.com/deep_link';
                default:
                    return null;
            }
        });

        component.ngOnInit();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
    });

    it('should set isLinking to false when id is missing', async () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'jwt_token';
                case 'id':
                    return null;
                case 'deepLinkUri':
                    return 'http://example.com/deep_link';
                default:
                    return null;
            }
        });

        component.ngOnInit();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
    });

    it('should set isLinking to false when deepLinkUri is missing', async () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'jwt_token';
                case 'id':
                    return 'id_token';
                case 'deepLinkUri':
                    return null;
                default:
                    return null;
            }
        });

        component.ngOnInit();
        await fixture.whenStable();

        expect(component.isLinking).toBe(false);
    });

    it('should update form values correctly', () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'test_jwt';
                case 'id':
                    return 'test_id';
                case 'deepLinkUri':
                    return 'http://test.com/link';
                default:
                    return null;
            }
        });

        component.updateFormValues();

        expect(component.jwt).toBe('test_jwt');
        expect(component.id).toBe('test_id');
        expect(component.actionLink).toBe('http://test.com/link');
    });

    it('should sanitize deepLinkUri correctly', () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'jwt_token';
                case 'id':
                    return 'id_token';
                case 'deepLinkUri':
                    return 'http://safe-url.com/path';
                default:
                    return null;
            }
        });

        component.updateFormValues();

        expect(component.actionLink).toBe('http://safe-url.com/path');
        expect(component.isLinking).toBe(true);
    });

    it('should handle empty deepLinkUri', () => {
        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'jwt_token';
                case 'id':
                    return 'id_token';
                case 'deepLinkUri':
                    return '';
                default:
                    return null;
            }
        });

        component.updateFormValues();

        expect(component.actionLink).toBe('');
        expect(component.isLinking).toBe(false);
    });

    it('should call autoSubmitForm when all parameters are present', async () => {
        const autoSubmitSpy = vi.spyOn(component, 'autoSubmitForm');

        routeMock.snapshot.queryParamMap.get.mockImplementation((param: string) => {
            switch (param) {
                case 'jwt':
                    return 'jwt_token';
                case 'id':
                    return 'id_token';
                case 'deepLinkUri':
                    return 'http://example.com/deep_link';
                default:
                    return null;
            }
        });

        component.ngOnInit();
        await vi.waitFor(() => {
            expect(autoSubmitSpy).toHaveBeenCalled();
        });
    });
});
