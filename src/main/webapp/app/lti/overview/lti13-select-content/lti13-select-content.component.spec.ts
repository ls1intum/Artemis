import { Lti13SelectContentComponent } from 'app/lti/overview/lti13-select-content/lti13-select-content.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

describe('Lti13SelectContentComponent', () => {
    let component: Lti13SelectContentComponent;
    let fixture: ComponentFixture<Lti13SelectContentComponent>;
    let routeMock: any;

    beforeEach(() => {
        routeMock = {
            snapshot: {
                queryParamMap: {
                    get: jest.fn(),
                },
            },
            params: of({}),
        };

        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, Lti13SelectContentComponent],
            providers: [
                FormBuilder,
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: TranslateService, useClass: MockTranslateService },
                MockDirective(TranslateDirective),
                MockPipe(SafeResourceUrlPipe),
            ],
        });
    });

    beforeEach(() => {
        HTMLFormElement.prototype.submit = jest.fn();
        fixture = TestBed.createComponent(Lti13SelectContentComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should initialize form on ngOnInit', fakeAsync(() => {
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
        tick();

        expect(component.actionLink).toBe(deepLinkUri);
        expect(component.isLinking).toBeTrue();
    }));

    it('should not auto-submit form if parameters are missing', fakeAsync(() => {
        routeMock.snapshot.queryParamMap.get.mockReturnValue(null);
        const autoSubmitSpy = jest.spyOn(component, 'autoSubmitForm');

        component.ngOnInit();
        tick();

        expect(component.isLinking).toBeFalse();
        expect(autoSubmitSpy).not.toHaveBeenCalled();
    }));
});
