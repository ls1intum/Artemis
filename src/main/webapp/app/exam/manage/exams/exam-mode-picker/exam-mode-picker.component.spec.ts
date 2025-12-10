import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { input } from '@angular/core';

const exam = {
    id: 2,
};
describe('ExamModePickerComponent', () => {
    let component: ExamModePickerComponent;
    let fixture: ComponentFixture<ExamModePickerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExamModePickerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamModePickerComponent);
                component = fixture.componentInstance;
                TestBed.runInInjectionContext(() => {
                    component.exam = input(exam);
                    component.disableInput = input(false);
                });
            });
    });

    it('should be in readonly mode', () => {
        const examCopy = Object.assign({}, exam);
        TestBed.runInInjectionContext(() => {
            component.disableInput = input(true);
        });
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam()).toEqual(examCopy);
    });

    it('should set exam mode test', () => {
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam().testExam).toBeTrue();
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(0);
    });

    it('should set exam mode test false', () => {
        fixture.detectChanges();
        component.setExamMode(false);
        expect(component.exam().testExam).toBeFalse();
        expect(component.exam().numberOfCorrectionRoundsInExam).toBe(1);
    });
});
