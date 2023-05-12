import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TaskCountWarningComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/task-count-warning/task-count-warning.component';

describe('TaskCountWarningComponent', () => {
    let component: TaskCountWarningComponent;
    let fixture: ComponentFixture<TaskCountWarningComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TaskCountWarningComponent, FaIconComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TaskCountWarningComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
