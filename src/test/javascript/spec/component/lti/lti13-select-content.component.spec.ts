import { Lti13SelectContentComponent } from 'app/lti/lti13-select-content.component';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { MockPipe } from 'ng-mocks';

describe('Lti13SelectContentComponent', () => {
    let component: Lti13SelectContentComponent;
    let fixture: ComponentFixture<Lti13SelectContentComponent>;
    let routeMock: any;

    beforeEach(waitForAsync(() => {
        routeMock = {
            snapshot: {
                queryParamMap: {
                    get: jest.fn(),
                },
            },
            params: of({}),
        };

        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule],
            declarations: [Lti13SelectContentComponent, MockPipe(ArtemisTranslatePipe), MockPipe(SafeResourceUrlPipe)],
            providers: [FormBuilder, { provide: ActivatedRoute, useValue: routeMock }],
        }).compileComponents();
    }));

    beforeEach(() => {
        HTMLFormElement.prototype.submit = jest.fn();
        fixture = TestBed.createComponent(Lti13SelectContentComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        fixture.detectChanges();
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
