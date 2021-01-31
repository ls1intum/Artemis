import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { DebugElement } from '@angular/core';
import { HttpResponse, HttpHeaders } from '@angular/common/http';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { fileUploadExercise, MockFileUploadExerciseService } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockPipe } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadExercise Management Detail Component', () => {
    let comp: FileUploadExerciseDetailComponent;
    let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
    let service: FileUploadExerciseService;
    let debugElement: DebugElement;

    const route = ({
        data: of({ fileUploadExercise }),
        params: of({ exerciseId: 2 }),
    } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                ArtemisAssessmentSharedModule,
                RouterTestingModule,
                TranslateModule.forRoot(),
                AssessmentInstructionsModule,
                ExerciseDetailsModule,
            ],
            declarations: [FileUploadExerciseDetailComponent, MockPipe(HtmlForMarkdownPipe)],
            providers: [
                JhiLanguageHelper,
                JhiAlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents();
        fixture = TestBed.createComponent(FileUploadExerciseDetailComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(FileUploadExerciseService);
        debugElement = fixture.debugElement;
    });

    describe('Title should contain exercise id and description list', () => {
        it('Should call load all on init', fakeAsync(() => {
            comp.ngOnInit();
            tick();

            expect(comp.fileUploadExercise).to.equal(fileUploadExercise);

            fixture.detectChanges();

            const title = debugElement.query(By.css('h2'));
            expect(title).to.exist;
            const h2: HTMLElement = title.nativeElement;
            expect(h2.textContent!.endsWith(fileUploadExercise.id!.toString())).to.be.true;

            const descList = debugElement.query(By.css('dl'));
            expect(descList).to.exist;
        }));
    });

    describe('OnInit with course exercise', () => {
        const course: Course = { id: 123 } as Course;
        const fileUploadExerciseWithCourse: FileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExerciseWithCourse.id = 123;

        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithCourse.id });
        });

        it('Should call load on init and be not in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: fileUploadExerciseWithCourse,
                        headers,
                    }),
                ),
            );
            // WHEN
            fixture.detectChanges();
            comp.ngOnInit();

            // THEN
            expect(comp.isExamExercise).to.be.false;
            expect(comp.fileUploadExercise).to.equal(fileUploadExerciseWithCourse);
        });
    });

    describe('OnInit with exam exercise', () => {
        const fileUploadExerciseWithExerciseGroup: FileUploadExercise = new FileUploadExercise(undefined, new ExerciseGroup());
        fileUploadExerciseWithExerciseGroup.id = 123;

        beforeEach(() => {
            route.params = of({ exerciseId: fileUploadExerciseWithExerciseGroup.id });
        });

        it('Should call load on init and be in exam mode', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: fileUploadExerciseWithExerciseGroup,
                        headers,
                    }),
                ),
            );
            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.isExamExercise).to.be.true;
            expect(comp.fileUploadExercise).to.equal(fileUploadExerciseWithExerciseGroup);
        });
    });
});
