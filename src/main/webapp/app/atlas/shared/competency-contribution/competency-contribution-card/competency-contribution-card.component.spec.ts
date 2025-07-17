import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionCardComponent } from './competency-contribution-card.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { provideRouter } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { input } from '@angular/core';
import { NgbProgressbar, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';

describe('CompetencyContributionCardComponent', () => {
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
                MockComponent(NgbProgressbar),
                MockModule(NgbTooltipModule),
                MockRouterLinkDirective,
            ],
            providers: [provideRouter([])],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionCardComponent);
        component = fixture.componentInstance;

        TestBed.runInInjectionContext(() => {
            component.courseId = input<number>(1);
            component.competencyId = input<number>(2);
            component.title = input<string>('Test Title');
            component.mastery = input<number>(50);
            component.weight = input<number>(0.5);
        });
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
