import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskCountWarningComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/task-count-warning/task-count-warning.component';
import { ArtemisTestModule } from '../test.module';

describe('TaskCountWarningComponent', () => {
    let component: TaskCountWarningComponent;
    let fixture: ComponentFixture<TaskCountWarningComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TaskCountWarningComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TaskCountWarningComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    //query element by id
    it('should correctly compare the number of tasks', () => {
        component.numOfTasks = 5;
        component.advisedMaxNumOfTasks = 15;
        fixture.detectChanges();

        const okayInformation = fixture.nativeElement.querySelector('#instruction_analysis_num-of-tasks-ok');
        expect(okayInformation).toBeDefined();

        const issueInformation = fixture.nativeElement.querySelector('#instruction_analysis_num-of-tasks-issues');
        expect(issueInformation).toBeNull();
    });
});
