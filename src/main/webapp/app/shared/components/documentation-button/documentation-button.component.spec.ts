import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';

describe('DocumentationButtonComponent', () => {
    let fixture: ComponentFixture<DocumentationButtonComponent>;
    let comp: DocumentationButtonComponent;
    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip)],
            declarations: [DocumentationButtonComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [MockProvider(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DocumentationButtonComponent);
                translateService = TestBed.inject(TranslateService);
                comp = fixture.componentInstance;
                comp.type = 'Course';
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should return the correct translation string', () => {
        fixture.detectChanges();

        const translateServiceSpy = jest.spyOn(translateService, 'instant');

        comp.getTooltipForType();

        expect(translateServiceSpy).toHaveBeenCalledTimes(2);
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.documentationLinks.prefix');
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.documentationLinks.course');
    });
});
