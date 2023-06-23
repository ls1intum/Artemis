import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyCardComponent } from 'app/course/competencies/competency-card/competency-card.component';
import { Competency, CompetencyProgress } from 'app/entities/competency.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';

describe('CompetencyCardComponent', () => {
    let competencyCardComponentFixture: ComponentFixture<CompetencyCardComponent>;
    let competencyCardComponent: CompetencyCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [CompetencyCardComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(CompetencyRingsComponent)],
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
});
