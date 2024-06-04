/* tslint:disable:no-unused-variable */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { ExerciseHeadersInformationComponent } from 'app/exercises/shared/exercise-headers/exercise-headers-information/exercise-headers-information.component';

// TODO
describe('ExerciseHeadersInformationComponent', () => {
    let component: ExerciseHeadersInformationComponent;
    let fixture: ComponentFixture<ExerciseHeadersInformationComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [ExerciseHeadersInformationComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ExerciseHeadersInformationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
