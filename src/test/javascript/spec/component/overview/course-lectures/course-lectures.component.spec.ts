import { Component, Input } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdownModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({ selector: 'jhi-course-lecture-row', template: '' })
class CourseLectureRowStubComponent {
    @Input() lecture: Lecture;
    @Input() course: Course;
}

describe('CourseLectures', () => {
    let courseLecturesComponentFixture: ComponentFixture<CourseLecturesComponent>;
    let courseLecturesComponent: CourseLecturesComponent;
    let course: Course;
    let lecture1: Lecture;
    let lecture2: Lecture;
    let lecture3: Lecture;

    beforeEach(() => {
        course = new Course();
        course.id = 1;

        const wednesdayBase = dayjs('18-03-2020', 'DD-MM-YYYY');
        const wednesdayBefore = dayjs('11-03-2020', 'DD-MM-YYYY');
        const wednesdayAfter = dayjs('25-03-2020', 'DD-MM-YYYY');

        lecture1 = new Lecture();
        lecture1.id = 1;
        lecture1.startDate = wednesdayBase;
        lecture2 = new Lecture();
        lecture2.id = 2;
        lecture2.startDate = wednesdayBefore;
        lecture3 = new Lecture();
        lecture3.id = 3;
        lecture3.startDate = wednesdayAfter;
        course.lectures = [lecture1, lecture2, lecture3];

        TestBed.configureTestingModule({
            imports: [NgbDropdownModule],
            declarations: [
                CourseLecturesComponent,
                CourseLectureRowStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(SidePanelComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(CourseManagementService, {
                    getCourseUpdates: () => {
                        return of(course);
                    },
                }),
                MockProvider(CourseScoreCalculationService, {
                    getCourse: () => {
                        return course;
                    },
                }),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ExerciseService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                courseLecturesComponentFixture = TestBed.createComponent(CourseLecturesComponent);
                courseLecturesComponent = courseLecturesComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseLecturesComponentFixture.detectChanges();
        expect(courseLecturesComponent).not.toBeNull();
        courseLecturesComponent.ngOnDestroy();
    });

    it('should sort the three lectures into three different weeks', fakeAsync(() => {
        courseLecturesComponentFixture.detectChanges();
        const weeks = courseLecturesComponentFixture.debugElement.nativeElement.querySelectorAll('.exercise-row-container');
        const labelsOfWeeks = courseLecturesComponentFixture.debugElement.nativeElement.querySelectorAll('.exercise-row-container > .control-label');
        expect(weeks).toHaveLength(3);
        expect(labelsOfWeeks).toHaveLength(3);
        for (const label of labelsOfWeeks) {
            label.click();
        }
        courseLecturesComponentFixture.whenStable().then(() => {
            courseLecturesComponentFixture.detectChanges();
            const lectures = courseLecturesComponentFixture.debugElement.queryAll(By.directive(CourseLectureRowStubComponent));
            expect(lectures).toHaveLength(3);
        });
    }));
});
