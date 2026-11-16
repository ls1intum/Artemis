import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogRef } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseCompetencyExplanationModalComponent } from 'app/atlas/manage/course-competency-explanation-modal/course-competency-explanation-modal.component';

describe('CourseCompetencyExplanationModalComponent', () => {
    let fixture: ComponentFixture<CourseCompetencyExplanationModalComponent>;
    const dialogRef = { close: vi.fn() };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyExplanationModalComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TumAetUiDialogRef, useValue: dialogRef },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseCompetencyExplanationModalComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should close the dialog with the close button', () => {
        fixture.debugElement.query(By.css('[data-testid="close-button"]')).nativeElement.click();

        expect(dialogRef.close).toHaveBeenCalledOnce();
    });
});
