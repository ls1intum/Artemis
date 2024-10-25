import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { input } from '@angular/core';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { ExerciseSaveButtonComponent } from '../../../../../../../main/webapp/app/exam/participate/exercises/exercise-save-button/exercise-save-button.component';
import { Submission } from '../../../../../../../main/webapp/app/entities/submission.model';
import { facSaveSuccess } from '../../../../../../../main/webapp/content/icons/icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFloppyDisk } from '@fortawesome/free-solid-svg-icons';

describe('ExerciseSaveButtonComponent', () => {
    let component: ExerciseSaveButtonComponent;
    let fixture: ComponentFixture<ExerciseSaveButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExerciseSaveButtonComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseSaveButtonComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should disable the button if submission is synced', () => {
        TestBed.runInInjectionContext(() => {
            component.submission = input({ isSynced: true, submitted: false } as Submission);
        });

        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#save-exam'));
        expect(button.nativeElement.disabled).toBeTrue();
    });

    it('should enable the button if submission is not synced', () => {
        TestBed.runInInjectionContext(() => {
            component.submission = input({ isSynced: false, submitted: false } as Submission);
        });
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#save-exam'));
        expect(button.nativeElement.disabled).toBeFalse();
    });

    it('should display facSaveSuccess icon if submission is synced and submitted', () => {
        TestBed.runInInjectionContext(() => {
            component.submission = input({ isSynced: true, submitted: true } as Submission);
        });
        fixture.detectChanges();

        const icon = fixture.debugElement.query(By.directive(FaIconComponent));
        expect(icon.componentInstance.icon).toBe(facSaveSuccess);
    });

    it('should display faFloppyDisk icon if submission is not synced and submitted', () => {
        TestBed.runInInjectionContext(() => {
            component.submission = input({ isSynced: false, submitted: false } as Submission);
        });
        fixture.detectChanges();

        const icon = fixture.debugElement.query(By.directive(FaIconComponent));
        expect(icon.componentInstance.icon).toBe(faFloppyDisk);
    });

    it('should emit call onSave when the button is clicked and submission is not synced', () => {
        TestBed.runInInjectionContext(() => {
            component.submission = input({ isSynced: false, submitted: false } as Submission);
        });
        fixture.detectChanges();

        const onSaveSpy = jest.spyOn(component, 'onSave');

        const button = fixture.debugElement.query(By.css('#save-exam'));
        button.nativeElement.click();

        expect(onSaveSpy).toHaveBeenCalled();
    });
});
