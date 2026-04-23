import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('DocumentationButtonComponent', () => {
    let fixture: ComponentFixture<DocumentationButtonComponent>;
    let comp: DocumentationButtonComponent;
    let translateService: TranslateService;

    beforeEach(async () => {
        setupTestBed({ zoneless: true });
        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FaIconComponent, DocumentationButtonComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DocumentationButtonComponent);
                translateService = TestBed.inject(TranslateService);
                comp = fixture.componentInstance;
                fixture.componentRef.setInput('type', 'Course');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should return the correct translation string', () => {
        fixture.detectChanges();

        const translateServiceSpy = vi.spyOn(translateService, 'instant');

        comp.getTooltipForType();

        expect(translateServiceSpy).toHaveBeenCalledTimes(2);
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.documentationLinks.prefix');
        expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.documentationLinks.course');
    });

    it('should render the correct documentation link for generate competencies', () => {
        fixture.componentRef.setInput('type', 'GenerateCompetencies');
        fixture.detectChanges();

        const anchor: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a');

        expect(anchor).not.toBeNull();
        expect(anchor?.getAttribute('href')).toBe('https://docs.artemis.tum.de/instructor/adaptive-learning#generate-competencies');
    });
});
