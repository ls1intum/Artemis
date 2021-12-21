import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExampleModelingSubmissionComponent } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.component';
import { MockComponent } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { AlertComponent } from 'app/shared/alert/alert.component';

describe('ExampleModelingSubmissionComponent', () => {
    let comp: ExampleModelingSubmissionComponent;
    let fixture: ComponentFixture<ExampleModelingSubmissionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExampleModelingSubmissionComponent, MockComponent(AlertComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleModelingSubmissionComponent);
                comp = fixture.componentInstance;
            });
    });

    it('initial', () => {
        comp.ngOnInit();
    });
});
