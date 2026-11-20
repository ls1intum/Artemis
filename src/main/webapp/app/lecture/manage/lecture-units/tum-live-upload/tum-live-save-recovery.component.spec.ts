import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { RecordingNavigation } from './tum-live-creation';
import { TumLiveSaveRecoveryComponent } from './tum-live-save-recovery.component';

describe('TumLiveSaveRecoveryComponent', () => {
    let fixture: ComponentFixture<TumLiveSaveRecoveryComponent>;
    let lectures: LectureService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TumLiveSaveRecoveryComponent],
            providers: [MockProvider(LectureService), provideRouter([])],
        }).overrideComponent(TumLiveSaveRecoveryComponent, {
            remove: { imports: [ArtemisTranslatePipe] },
            add: { imports: [MockPipe(ArtemisTranslatePipe)] },
        });
        fixture = TestBed.createComponent(TumLiveSaveRecoveryComponent);
        lectures = TestBed.inject(LectureService);
    });

    function check(): void {
        fixture.detectChanges();
        const control: HTMLButtonElement = fixture.nativeElement.querySelector('button');
        control.click();
        fixture.detectChanges();
    }

    it('requires a successful read before offering recovery links after an unknown save', () => {
        const created = new AttachmentVideoUnit();
        created.name = 'Recording';
        created.id = 90;
        vi.spyOn(lectures, 'findWithDetails')
            .mockReturnValueOnce(throwError(() => new Error('offline')))
            .mockReturnValue(of({ id: 55, lectureUnits: [created] }));
        const checked = vi.fn();
        fixture.componentInstance.checked.subscribe(checked);
        fixture.componentRef.setInput('scope', { courseId: 1, lectureId: 55 });

        check();
        expect(fixture.componentInstance.failed()).toBe(true);
        expect(checked).not.toHaveBeenCalled();

        check();
        expect(checked).toHaveBeenCalledOnce();
        const navigation = fixture.componentInstance.entries()?.[0].navigation;
        expect(navigation).toBeInstanceOf(RecordingNavigation);
        expect(navigation?.target).toEqual({ courseId: 1, lectureId: 55, unitId: 90 });
        expect(navigation?.takeFile()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toContain('attachment-video-units/90/edit');

        fixture.componentRef.setInput('scope', { courseId: 1, lectureId: 55 });
        fixture.detectChanges();
        expect(fixture.componentInstance.entries()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('button')).toBeTruthy();
    });

    it('looks up saved lectures in the course when the lecture save was unclear', () => {
        const read = vi.spyOn(lectures, 'findAllByCourseId').mockReturnValue(of([{ id: 55, title: 'Lecture' }]));
        fixture.componentRef.setInput('scope', { courseId: 1 });

        check();

        expect(read).toHaveBeenCalledExactlyOnceWith(1);
        expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toContain('/lectures/55/edit');
    });
});
