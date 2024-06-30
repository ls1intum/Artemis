import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockComponent } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamNavigationSidebarComponent } from 'app/exam/participate/exam-navigation-sidebar/exam-navigation-sidebar.component';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { ExamBarComponent } from 'app/exam/participate/exam-bar/exam-bar.component';

describe('ExamBarComponent', () => {
    let fixture: ComponentFixture<ExamBarComponent>;
    let comp: ExamBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [ExamBarComponent, MockComponent(ExamNavigationSidebarComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamBarComponent);
        comp = fixture.componentInstance;

        comp.endDate = dayjs();
        comp.exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3, isSynced: true } as Submission],
                    } as StudentParticipation,
                ],
            } as Exercise,
            { id: 1, type: ExerciseType.TEXT } as Exercise,
            { id: 2, type: ExerciseType.MODELING } as Exercise,
        ];
    });

    beforeEach(() => {
        fixture.detectChanges();
    });

    it('trigger emit and call saveExercise when the exam is about to end', () => {
        jest.spyOn(comp, 'saveExercise');
        jest.spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalledOnce();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalledOnce();
    });

    it('should hand in the exam early', () => {
        jest.spyOn(comp.onExamHandInEarly, 'emit');
        jest.spyOn(comp, 'saveExercise');

        comp.handInEarly();
        expect(comp.onExamHandInEarly.emit).toHaveBeenCalledOnce();
    });
});
