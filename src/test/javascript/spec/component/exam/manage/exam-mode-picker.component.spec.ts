import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';

const exam = {
    id: 2,
};
describe('ExamModePickerComponent', () => {
    let component: ExamModePickerComponent;
    let fixture: ComponentFixture<ExamModePickerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamModePickerComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamModePickerComponent);
                component = fixture.componentInstance;
                component.exam = exam;
            });
    });

    it('should be in readonly mode', () => {
        const examCopy = { ...exam };
        component.disableInput = true;
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam).toEqual(examCopy);
    });

    it('should set exam mode test', () => {
        fixture.detectChanges();
        component.setExamMode(true);
        expect(component.exam.testExam).toBeTrue();
        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(0);
    });

    it('should set exam mode test false', () => {
        fixture.detectChanges();
        component.setExamMode(false);
        expect(component.exam.testExam).toBeFalse();
        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
    });
});
