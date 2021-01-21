import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';

describe('ProgrammingExerciseInstructorRepoDownloadComponent', () => {
    let component: ProgrammingExerciseInstructorRepoDownloadComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorRepoDownloadComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ProgrammingExerciseInstructorRepoDownloadComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ProgrammingExerciseInstructorRepoDownloadComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
