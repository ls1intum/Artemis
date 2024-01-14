import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyCardComponent } from 'app/course/competencies/competency-card/competency-card.component';
import { Competency, CompetencyProgress } from 'app/entities/competency.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import dayjs from 'dayjs/esm';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { By } from '@angular/platform-browser';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('CompetencyCardComponent', () => {
    let competencyCardComponentFixture: ComponentFixture<CompetencyCardComponent>;
    let competencyCardComponent: CompetencyCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                CompetencyCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(FaIconComponent),
                MockComponent(CompetencyRingsComponent),
                MockPipe(ArtemisTimeAgoPipe),
            ],
            providers: [MockProvider(TranslateService)],
            schemas: [],
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
        competencyCardComponent.competency = {
            id: 1,
            masteryThreshold: 80,
            userProgress: [
                {
                    progress: 45,
                    confidence: 60,
                } as CompetencyProgress,
            ],
        } as Competency;

        competencyCardComponentFixture.detectChanges();

        expect(competencyCardComponent.progress).toBe(45);
        expect(competencyCardComponent.confidence).toBe(75);
        expect(competencyCardComponent.mastery).toBe(65);
        expect(competencyCardComponent.isMastered).toBeFalse();
    });

    it('should display competency as mastered', () => {
        competencyCardComponent.competency = {
            id: 1,
            masteryThreshold: 40,
            userProgress: [
                {
                    progress: 100,
                    confidence: 60,
                } as CompetencyProgress,
            ],
        } as Competency;

        competencyCardComponentFixture.detectChanges();

        expect(competencyCardComponent.progress).toBe(100);
        expect(competencyCardComponent.confidence).toBe(100);
        expect(competencyCardComponent.mastery).toBe(100);
        expect(competencyCardComponent.isMastered).toBeTrue();
    });

    it('should display optional badge for optional competency', () => {
        competencyCardComponent.competency = {
            id: 1,
            optional: true,
        } as Competency;

        competencyCardComponentFixture.detectChanges();

        const badge = competencyCardComponentFixture.debugElement.query(By.css('#optional-badge'));
        expect(badge).not.toBeNull();
    });

    it('should not display optional badge for non-optional competency', () => {
        competencyCardComponent.competency = {
            id: 1,
            optional: false,
        } as Competency;

        competencyCardComponentFixture.detectChanges();

        const badge = competencyCardComponentFixture.debugElement.query(By.css('#optional-badge'));
        expect(badge).toBeNull();
    });

    it('should detect if due date is passed', () => {
        const competencyFuture = { softDueDate: dayjs().add(1, 'days') } as Competency;
        competencyCardComponent.competency = competencyFuture;
        competencyCardComponentFixture.detectChanges();
        expect(competencyCardComponent.softDueDatePassed).toBeFalse();

        const competencyPast = { softDueDate: dayjs().subtract(1, 'days') } as Competency;
        competencyCardComponent.competency = competencyPast;
        competencyCardComponentFixture.detectChanges();
        expect(competencyCardComponent.softDueDatePassed).toBeTrue();
    });

    it.each([
        { competency: { softDueDate: dayjs().add(1, 'days') } as Competency, expectedBadge: 'success' },
        { competency: { softDueDate: dayjs().subtract(1, 'days') } as Competency, expectedBadge: 'danger' },
    ])('should have [ngClass] resolve to correct date badge', ({ competency, expectedBadge }) => {
        competencyCardComponent.competency = competency;
        competencyCardComponentFixture.detectChanges();
        const badge = competencyCardComponentFixture.debugElement.query(By.css('#date-badge')).nativeElement;
        expect(badge.classList).toContain('bg-' + expectedBadge);
    });
});
