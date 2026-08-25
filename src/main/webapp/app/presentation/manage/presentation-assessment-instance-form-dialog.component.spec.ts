import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { PresentationAssessmentInstanceFormDialogComponent } from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/account/user/user.model';

describe('PresentationAssessmentInstanceFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentInstanceFormDialogComponent>;
    let component: PresentationAssessmentInstanceFormDialogComponent;
    let saved: ReturnType<typeof vi.fn>;

    const presentationDate = dayjs('2026-07-31T13:26:00+02:00');

    beforeEach(async () => {
        saved = vi.fn();
        const course = Object.assign(new Course(), { id: 1, title: 'Test Course' });

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentInstanceFormDialogComponent],
            providers: [
                { provide: CourseManagementService, useValue: {} },
                { provide: TranslateService, useValue: { instant: (key: string) => key } },
            ],
        })
            .overrideComponent(PresentationAssessmentInstanceFormDialogComponent, { set: { template: '' } })
            .compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentInstanceFormDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('presentationAssessment', { id: 42, maxPoints: 20 });
        fixture.componentRef.setInput('instance', { id: 11, presentationDate });
        fixture.componentRef.setInput('initialAssignedStudents', [new User(undefined, 'student1')]);
        component.saved.subscribe(saved);
        fixture.detectChanges();
    });

    it('should split an existing presentation timestamp into date and time controls', () => {
        expect(component.editForm.controls.presentationDate.value?.hour()).toBe(0);
        expect(component.editForm.controls.presentationTime.value?.hour()).toBe(13);
        expect(component.editForm.controls.presentationTime.value?.minute()).toBe(26);
    });

    it('should require a presentation date', () => {
        component.editForm.controls.presentationDate.setValue(undefined);

        component.save();

        expect(saved).not.toHaveBeenCalled();
        expect(component.editForm.controls.presentationDate.invalid).toBe(true);
    });

    it('should combine the mandatory date with the optional time', () => {
        component.editForm.controls.presentationDate.setValue(dayjs('2026-08-10T00:00:00+02:00'));
        component.editForm.controls.presentationTime.setValue(dayjs('2026-08-03T14:45:00+02:00'));

        component.save();

        const savedInstance = saved.mock.calls[0][0];
        expect(savedInstance.presentationDate.format('YYYY-MM-DD HH:mm')).toBe('2026-08-10 14:45');
    });

    it('should include a trimmed remark when saving an instance', () => {
        component.editForm.controls.remark.setValue('  Strong presentation  ');

        component.save();

        expect(saved.mock.calls[0][0].remark).toBe('Strong presentation');
    });
});
