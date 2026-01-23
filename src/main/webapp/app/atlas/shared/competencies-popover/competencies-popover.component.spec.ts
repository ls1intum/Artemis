import { vi } from 'vitest';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CompetenciesPopoverComponent } from 'app/atlas/shared/competencies-popover/competencies-popover.component';
import { Component } from '@angular/core';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { RouterModule } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

@Component({
    selector: 'jhi-statistics',
    template: '',
    standalone: true,
})
class DummyStatisticsComponent {}

@Component({
    selector: 'jhi-course-management',
    template: '',
    standalone: true,
})
class DummyManagementComponent {}

describe('CompetencyPopoverComponent', () => {
    setupTestBed({ zoneless: true });
    let competencyPopoverComponentFixture: ComponentFixture<CompetenciesPopoverComponent>;
    let competencyPopoverComponent: CompetenciesPopoverComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                NgbPopoverModule,
                RouterModule.forRoot([
                    { path: 'courses/:courseId/competencies', component: DummyStatisticsComponent },
                    { path: 'course-management/:courseId/competency-management', component: DummyManagementComponent },
                ]),
                FaIconComponent,
                CompetenciesPopoverComponent,
                MockPipe(ArtemisTranslatePipe),
                DummyStatisticsComponent,
                DummyManagementComponent,
            ],
            declarations: [],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        competencyPopoverComponentFixture = TestBed.createComponent(CompetenciesPopoverComponent);
        competencyPopoverComponent = competencyPopoverComponentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        competencyPopoverComponentFixture.detectChanges();
        expect(competencyPopoverComponent).toBeDefined();
    });

    it.each([
        ['courseCompetencies', ['/courses', '1', 'competencies']],
        ['competencyManagement', ['/course-management', '1', 'competency-management']],
    ])(
        'should navigate',
        fakeAsync((navigateTo: 'competencyManagement' | 'courseCompetencies', expectedNavigation: string[]) => {
            const competencyLinks: CompetencyLectureUnitLink[] = [{ competency: { id: 1, title: 'competency' }, weight: 1 }];
            competencyPopoverComponentFixture.componentRef.setInput('navigateTo', navigateTo);
            competencyPopoverComponentFixture.componentRef.setInput('competencyLinks', competencyLinks);
            competencyPopoverComponentFixture.componentRef.setInput('courseId', 1);
            competencyPopoverComponentFixture.detectChanges();
            expect(competencyPopoverComponent.navigationArray).toEqual(expectedNavigation);
        }),
    );
});
