import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseTimelineComponent } from './exercise-timeline.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    let component: ExerciseTimelineComponent;
    let fixture: ComponentFixture<ExerciseTimelineComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTimelineComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
