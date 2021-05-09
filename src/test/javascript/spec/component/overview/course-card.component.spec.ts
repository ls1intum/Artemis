import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { Exercise } from 'app/entities/exercise.model';
import { MockComponent, MockPipe, MockProvider, MockDirective } from 'ng-mocks';
import { ChartsModule } from 'ng2-charts';
import { TimeAgoPipe } from 'ngx-moment';
import * as moment from 'moment';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseCardComponent', () => {
    let fixture: ComponentFixture<CourseCardComponent>;
    let component: CourseCardComponent;
    const submission: ProgrammingSubmission = {
        submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
        id: 3,
        submitted: true,
        results: [{ successful: true }],
    };
    const pastExercise = { id: 1, dueDate: moment().subtract(2, 'days') } as Exercise;
    const nextExercise = { id: 2, dueDate: moment().add(2, 'days'), studentParticipations: [{ submissions: [submission] }] } as Exercise;
    const secondNextExercise = { id: 3, dueDate: moment().add(4, 'days') } as Exercise;
    const course = { id: 1, exercises: [pastExercise, nextExercise, secondNextExercise], lectures: [], exams: [] } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ChartsModule],
            declarations: [
                CourseCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(TimeAgoPipe),
                MockRouterLinkDirective,
                MockComponent(SecuredImageComponent),
                MockDirective(NgbTooltip),
            ],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseCardComponent);
                component = fixture.componentInstance;
                component.course = course;
            });
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should display the next exercise which is not yet successful', () => {
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.nextRelevantExercise).to.deep.equal(secondNextExercise);
    });
});
