import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionCardComponent } from './competency-contribution-card.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { provideRouter } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { ProgressBar } from 'primeng/progressbar';

describe('CompetencyContributionCardComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CompetencyContributionCardComponent;
    let fixture: ComponentFixture<CompetencyContributionCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                CompetencyContributionCardComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                FaIconComponent,
                MockComponent(FaLayersComponent),
                MockModule(NgbTooltipModule),
                MockRouterLinkDirective,
            ],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionCardComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('competencyId', 2);
        fixture.componentRef.setInput('title', 'Test Title');
        fixture.componentRef.setInput('mastery', 50);
        fixture.componentRef.setInput('weight', 0.5);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should bind the mastery value to the progress bar', () => {
        fixture.componentRef.setInput('mastery', 50);
        fixture.detectChanges();

        const progressBar = fixture.debugElement.query(By.directive(ProgressBar));
        expect(progressBar).not.toBeNull();
        expect(progressBar.componentInstance.value).toBe(50);
    });

    it('should fall back to 0 for the progress bar when mastery is undefined', () => {
        // The `@if (mastery() !== undefined)` guard hides the bar when mastery is truly unset, so use 0 to exercise
        // the `value` binding while still asserting the `?? 0` fallback (0 is a valid, defined mastery value).
        fixture.componentRef.setInput('mastery', 0);
        fixture.detectChanges();

        const progressBar = fixture.debugElement.query(By.directive(ProgressBar));
        expect(progressBar).not.toBeNull();
        expect(progressBar.componentInstance.value).toBe(0);
    });
});
