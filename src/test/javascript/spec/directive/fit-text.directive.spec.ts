import { Component } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { FitTextDirective } from 'app/exercises/quiz/shared/fit-text/fit-text.directive';

@Component({
    template: ` <div style="align-content: center;">
        <div style="width: 20%; height: 20%; margin: 0 auto;">
            <div fitText>test</div>
        </div>
    </div>`,
})
class TestFittextComponent {}

describe('FitTextDirective', () => {
    let fixture: ComponentFixture<TestFittextComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [TestFittextComponent, FitTextDirective],
        });
        fixture = TestBed.createComponent(TestFittextComponent);
    });

    it('should create an instance', () => {
        expect(fixture).toBeTruthy();
        // TODO: extend test case
    });
});
