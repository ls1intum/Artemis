import { Location } from '@angular/common';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterTestingModule } from '@angular/router/testing';
import { CompetenciesPopoverComponent } from 'app/course/competencies/competencies-popover/competencies-popover.component';
import { By } from '@angular/platform-browser';
import { Competency } from 'app/entities/competency.model';
import { Component } from '@angular/core';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-statistics',
    template: '',
})
class DummyStatisticsComponent {}

@Component({
    selector: 'jhi-course-management',
    template: '',
})
class DummyManagementComponent {}

describe('CompetencyPopoverComponent', () => {
    let competencyPopoverComponentFixture: ComponentFixture<CompetenciesPopoverComponent>;
    let competencyPopoverComponent: CompetenciesPopoverComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                NgbPopoverModule,
                RouterTestingModule.withRoutes([
                    { path: 'courses/:courseId/competencies', component: DummyStatisticsComponent },
                    { path: 'course-management/:courseId/competency-management', component: DummyManagementComponent },
                ]),
            ],
            declarations: [CompetenciesPopoverComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), DummyStatisticsComponent, DummyManagementComponent],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                competencyPopoverComponentFixture = TestBed.createComponent(CompetenciesPopoverComponent);
                competencyPopoverComponent = competencyPopoverComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        competencyPopoverComponentFixture.detectChanges();
        expect(competencyPopoverComponent).toBeDefined();
    });

    it('should navigate to course competencies', fakeAsync(() => {
        const location: Location = TestBed.inject(Location);
        competencyPopoverComponent.navigateTo = 'courseCompetencies';
        competencyPopoverComponent.competencies = [new Competency()];
        competencyPopoverComponent.courseId = 1;
        competencyPopoverComponentFixture.detectChanges();
        const popoverButton = competencyPopoverComponentFixture.debugElement.nativeElement.querySelector('button');
        popoverButton.click();
        tick();
        const anchor = competencyPopoverComponentFixture.debugElement.query(By.css('a')).nativeElement;
        anchor.click();
        tick();
        expect(location.path()).toBe('/courses/1/competencies');
    }));

    it('should navigate to competency management', fakeAsync(() => {
        const location: Location = TestBed.inject(Location);
        competencyPopoverComponent.navigateTo = 'competencyManagement';
        competencyPopoverComponent.competencies = [new Competency()];
        competencyPopoverComponent.courseId = 1;
        competencyPopoverComponentFixture.detectChanges();
        const popoverButton = competencyPopoverComponentFixture.debugElement.nativeElement.querySelector('button');
        popoverButton.click();
        tick();
        const anchor = competencyPopoverComponentFixture.debugElement.query(By.css('a')).nativeElement;
        anchor.click();
        tick();
        expect(location.path()).toBe('/course-management/1/competency-management');
    }));
});
