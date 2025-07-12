import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterModule, provideRouter } from '@angular/router';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';

import { CompetenciesPopoverComponent } from './competencies-popover.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import type { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';

@Component({
    template: ` <jhi-competencies-popover [navigateTo]="navigateTo" [competencyLinks]="competencyLinks" [courseId]="courseId"> </jhi-competencies-popover> `,
    imports: [CompetenciesPopoverComponent],
})
class TestWrapperComponent {
    navigateTo: 'competencyManagement' | 'courseCompetencies' = 'courseCompetencies';
    competencyLinks: CompetencyLectureUnitLink[] = [{ competency: { id: 1, title: 'competency' }, weight: 1 }];
    courseId = 1;
}

@Component({ selector: 'jhi-statistics', template: '' })
class DummyStatisticsComponent {}

@Component({ selector: 'jhi-course-management', template: '' })
class DummyManagementComponent {}

describe('CompetenciesPopoverComponent (host + provideRouter)', () => {
    let fixture: ComponentFixture<TestWrapperComponent>;
    let host: TestWrapperComponent;
    let popoverDebugEl: any;
    let popoverCmp: CompetenciesPopoverComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                NgbPopoverModule,
                // also import RouterModule to pick up <a routerLink> directives:
                RouterModule,
            ],
            declarations: [TestWrapperComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), DummyStatisticsComponent, DummyManagementComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                provideRouter([
                    { path: 'courses/:courseId/competencies', component: DummyStatisticsComponent },
                    { path: 'course-management/:courseId/competency-management', component: DummyManagementComponent },
                ]),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TestWrapperComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();

        popoverDebugEl = fixture.debugElement.query(By.directive(CompetenciesPopoverComponent));
        popoverCmp = popoverDebugEl.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create the popover component', () => {
        expect(popoverCmp).toBeTruthy();
    });

    it.each<['courseCompetencies' | 'competencyManagement', string]>([
        ['courseCompetencies', '/courses/1/competencies'],
        ['competencyManagement', '/course-management/1/competency-management'],
    ])(
        'should navigate to %s',
        fakeAsync((navigateTo: 'courseCompetencies' | 'competencyManagement', expectedPath: string) => {
            const location = TestBed.inject(Location);

            host.navigateTo = navigateTo;
            fixture.detectChanges();

            // open the popover
            const btn = popoverDebugEl.nativeElement.querySelector('button');
            btn.click();
            tick();

            // click the first <a> inside the popover
            const anchor = popoverDebugEl.query(By.css('a')).nativeElement;
            anchor.click();
            tick();

            expect(location.path()).toBe(expectedPath);
        }),
    );
});
