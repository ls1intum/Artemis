import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseTimeline } from './exercise-timeline';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseTimeline', () => {
    let component: ExerciseTimeline;
    let fixture: ComponentFixture<ExerciseTimeline>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTimeline],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseTimeline);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
