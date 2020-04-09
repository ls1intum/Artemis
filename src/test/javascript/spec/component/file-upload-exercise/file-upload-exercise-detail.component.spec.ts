/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { DebugElement } from '@angular/core';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { By } from '@angular/platform-browser';

import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { fileUploadExercise, MockFileUploadExerciseService } from '../../mocks/mock-file-upload-exercise.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AlertService } from 'app/core/alert/alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockCookieService } from '../../mocks/mock-cookie.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    describe('FileUploadExercise Management Detail Component', () => {
        let comp: FileUploadExerciseDetailComponent;
        let fixture: ComponentFixture<FileUploadExerciseDetailComponent>;
        let debugElement: DebugElement;

        const route = ({
            data: of({ fileUploadExercise: fileUploadExercise }),
            params: of({ exerciseId: 2 }),
        } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisAssessmentSharedModule, RouterTestingModule, TranslateModule.forRoot()],
                declarations: [FileUploadExerciseDetailComponent],
                providers: [
                    JhiLanguageHelper,
                    AlertService,
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
                expect(h2.textContent!.endsWith(fileUploadExercise.id.toString())).to.be.true;

                const descList = debugElement.query(By.css('dl'));
                expect(descList).to.exist;
            }));
        });
    });
});
