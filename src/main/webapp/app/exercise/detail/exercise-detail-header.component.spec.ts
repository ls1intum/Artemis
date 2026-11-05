import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseDetailHeaderComponent } from './exercise-detail-header.component';
import { ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Exercise detail header', () => {
    let fixture: ComponentFixture<ExerciseDetailHeaderComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(ExerciseDetailHeaderComponent);
        fixture.componentRef.setInput('documentationType', 'Text');
    });
    it.each(Object.values(ExerciseType))('shows saved title and the standard %s type icon', (type) => {
        fixture.componentRef.setInput('exercise', { id: 123456, title: 'Saved exercise title', type });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Saved exercise title');
        expect(fixture.nativeElement.querySelector('h1').textContent).not.toContain('123456');
        expect(fixture.debugElement.query(By.directive(FaIconComponent)).componentInstance.icon()).toEqual(getIcon(type));
    });
    it('refreshes the heading after exercise navigation', () => {
        fixture.componentRef.setInput('exercise', { title: 'First', type: ExerciseType.TEXT });
        fixture.detectChanges();
        fixture.componentRef.setInput('exercise', { title: 'Second', type: ExerciseType.QUIZ });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Second');
        expect(fixture.nativeElement.querySelector('h1').textContent).not.toContain('First');
    });
});
