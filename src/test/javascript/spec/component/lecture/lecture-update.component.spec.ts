import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { LectureService } from 'app/lecture/lecture.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import dayjs from 'dayjs/esm';
import { Location } from '@angular/common';

describe('Lecture', () => {
    let lectureComponentFixture: ComponentFixture<LectureComponent>;
    let lectureComponent: LectureComponent;
    let lectureService: LectureService;
    let lectureUpdateComponentFixture: ComponentFixture<LectureUpdateComponent>;
    let lectureUpdateComponent: LectureUpdateComponent;
    let lectureServiceFindAllByLectureIdStub: jest.SpyInstance;
    let location: Location;
    let router: Router;

    let pastLecture: Lecture;

    beforeEach(() => {
        const yesterday = dayjs().subtract(1, 'day');

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.title = 'past lecture';
        pastLecture.endDate = yesterday;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockModule(NgbTooltipModule)],
            declarations: [
                LectureUpdateComponent,
                LectureComponent,
                MockComponent(FormDateTimePickerComponent),
                MockComponent(MarkdownEditorComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            data: of({}),
                        },
                        queryParams: of({}),
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(LectureService),
            ],
        })
            .compileComponents()
            .then(() => {
                lectureComponentFixture = TestBed.createComponent(LectureComponent);
                lectureComponent = lectureComponentFixture.componentInstance;

                lectureUpdateComponentFixture = TestBed.createComponent(LectureUpdateComponent);
                lectureUpdateComponent = lectureUpdateComponentFixture.componentInstance;

                lectureService = TestBed.inject(LectureService);
                lectureServiceFindAllByLectureIdStub = jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(of(new HttpResponse({ body: [pastLecture] })));

                location = TestBed.inject(Location);

                router = TestBed.get(Router);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create lecture', fakeAsync(() => {
        lectureComponentFixture.detectChanges();
        lectureUpdateComponent.lecture = { title: 'test1' } as Lecture;
        const navigateSpy = jest.spyOn(router, 'navigate');

        const createSpy = jest.spyOn(lectureService, 'create').mockReturnValue(
            of<HttpResponse<Lecture>>(
                new HttpResponse({
                    body: {
                        id: 3,
                        title: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        lectureUpdateComponent.save();
        tick();
        lectureUpdateComponentFixture.detectChanges();

        const expectedPath = ['course-management', 1, 'lectures', 3];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1' });
        expect(lectureServiceFindAllByLectureIdStub).toHaveBeenCalledOnce();
    }));
});
