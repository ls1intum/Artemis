import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { Competency, CompetencyProgress } from 'app/atlas/shared/entities/competency.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CompetencyCardComponent', () => {
    let competencyCardComponentFixture: ComponentFixture<CompetencyCardComponent>;
    let competencyCardComponent: CompetencyCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                CompetencyCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(CompetencyRingsComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockDirective(TranslateDirective),
            ],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                competencyCardComponentFixture = TestBed.createComponent(CompetencyCardComponent);
                competencyCardComponent = competencyCardComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should calculate correct progress, confidence and mastery', () => {
        const competency = {
            id: 1,
            masteryThreshold: 80,
            userProgress: [
                {
                    progress: 45,
                    confidence: 1.1,
                } as CompetencyProgress,
            ],
        } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competency);

        competencyCardComponentFixture.detectChanges();

        expect(competencyCardComponent.progress).toBe(45);
        expect(competencyCardComponent.mastery).toBe(Math.round(45 * 1.1));
        expect(competencyCardComponent.isMastered).toBeFalse();
    });

    it('should display competency as mastered', () => {
        const competency = {
            id: 1,
            masteryThreshold: 40,
            userProgress: [
                {
                    progress: 100,
                    confidence: 60,
                } as CompetencyProgress,
            ],
        } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competency);

        competencyCardComponentFixture.detectChanges();

        expect(competencyCardComponent.progress).toBe(100);
        expect(competencyCardComponent.mastery).toBe(100);
        expect(competencyCardComponent.isMastered).toBeTrue();
    });

    it('should display optional badge for optional competency', () => {
        const competency = {
            id: 1,
            optional: true,
        } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competency);

        competencyCardComponentFixture.detectChanges();

        const badge = competencyCardComponentFixture.debugElement.query(By.css('#optional-badge'));
        expect(badge).not.toBeNull();
    });

    it('should not display optional badge for non-optional competency', () => {
        const competency = {
            id: 1,
            optional: false,
        } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competency);

        competencyCardComponentFixture.detectChanges();

        const badge = competencyCardComponentFixture.debugElement.query(By.css('#optional-badge'));
        expect(badge).toBeNull();
    });

    it('should detect if due date is passed', () => {
        const competencyFuture = { softDueDate: dayjs().add(1, 'days') } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competencyFuture);
        competencyCardComponentFixture.detectChanges();
        expect(competencyCardComponent.softDueDatePassed).toBeFalse();

        const competencyPast = { softDueDate: dayjs().subtract(1, 'days') } as Competency;
        competencyCardComponentFixture.componentRef.setInput('competency', competencyPast);
        competencyCardComponentFixture.detectChanges();
        expect(competencyCardComponent.softDueDatePassed).toBeTrue();
    });

    it.each([
        { competency: { softDueDate: dayjs().add(1, 'days') } as Competency, expectedBadge: 'success' },
        { competency: { softDueDate: dayjs().subtract(1, 'days') } as Competency, expectedBadge: 'danger' },
    ])('should have [ngClass] resolve to correct date badge', ({ competency, expectedBadge }) => {
        competencyCardComponentFixture.componentRef.setInput('competency', competency);
        competencyCardComponentFixture.detectChanges();
        const badge = competencyCardComponentFixture.debugElement.query(By.css('#date-badge')).nativeElement;
        expect(badge.classList).toContain('bg-' + expectedBadge);
    });
});
