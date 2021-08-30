import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import * as sinon from 'sinon';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { Lecture } from 'app/entities/lecture.model';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ArtemisTestModule } from '../../../test.module';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../helpers/mocks/service/mock-answer-post.service';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { PageDiscussionSectionComponent } from 'app/overview/page-discussion-section/page-discussion-section.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { SinonStub, stub } from 'sinon';

chai.use(sinonChai);
const expect = chai.expect;

describe('PageDiscussionSectionComponent', () => {
    let component: PageDiscussionSectionComponent;
    let fixture: ComponentFixture<PageDiscussionSectionComponent>;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let getCourseStub: SinonStub;

    const mockCourse = new Course();
    mockCourse.id = 1;
    const mockExercise = new FileUploadExercise(mockCourse, undefined);
    const mockLecture = new Lecture();
    mockLecture.course = mockCourse;
    mockCourse.exercises = [mockExercise];
    mockCourse.lectures = [mockLecture];

    /*
    This test post has to match to the object returned by `MockPostService`
     */
    const postReturnedFromMockPostServiceForExercise = {
        id: 1,
        exercise: mockExercise,
        course: mockCourse,
    } as Post;

    /*
    This test post has to match to the object returned by `MockPostService`
     */
    const postReturnedFromMockPostServiceForLecture = {
        id: 1,
        lecture: mockLecture,
        course: mockCourse,
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: PostService, useClass: MockPostService },
                { provide: AccountService, useClass: MockAccountService },
            ],
            declarations: [
                PageDiscussionSectionComponent,
                MockComponent(PostingsThreadComponent),
                MockComponent(PostCreateEditModalComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
        })
            .overrideComponent(PageDiscussionSectionComponent, {
                set: {
                    providers: [{ provide: MetisService, useClass: MetisService }],
                },
            })
            .compileComponents()
            .then(() => {
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                getCourseStub = stub(courseScoreCalculationService, 'getCourse');
                getCourseStub.returns(mockCourse);
                fixture = TestBed.createComponent(PageDiscussionSectionComponent);
                component = fixture.componentInstance;
                fixture.debugElement.injector.get(MetisService);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should set course and posts for exercise on initialization', fakeAsync(() => {
        component.posts = [];
        component.exercise = mockExercise;
        component.ngOnInit();
        tick();
        expect(component.course).to.deep.equal(mockCourse);
        expect(component.createdPost).to.not.be.undefined;
        expect(component.posts).to.be.deep.equal([
            {
                ...postReturnedFromMockPostServiceForExercise,
                course: { id: mockCourse.id },
                exercise: { id: mockExercise.id },
            },
        ]);
    }));

    it('should set course and posts for lecture on changes', fakeAsync(() => {
        component.posts = [];
        component.exercise = mockExercise;
        component.ngOnInit();
        tick();
        component.exercise = undefined;
        component.lecture = mockLecture;
        component.ngOnChanges();
        tick();
        expect(component.posts).to.be.deep.equal([
            {
                ...postReturnedFromMockPostServiceForLecture,
                course: { id: mockCourse.id },
                lecture: { id: mockLecture.id },
            },
        ]);
    }));
});
