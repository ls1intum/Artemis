import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseDetailHeaderComponent } from './exercise-detail-header.component';
import { ExerciseDetailActionsComponent } from './exercise-detail-actions.component';

@Component({
    template: `
        <jhi-exercise-detail-header [exercise]="exercise" documentationType="Text">
            <a detailHeaderSecondary href="#history">History</a>
        </jhi-exercise-detail-header>
        <jhi-exercise-detail-actions>
            <ng-container detailNavigationActions><a href="#participations">Participations</a><a href="#statistics">Statistics</a></ng-container>
            <ng-container detailEditingActions><a href="#edit">Edit</a><button type="button">Create Variant</button></ng-container>
            <ng-container detailMoreActions><button type="button">More actions</button></ng-container>
        </jhi-exercise-detail-actions>
    `,
    imports: [ExerciseDetailHeaderComponent, ExerciseDetailActionsComponent],
})
class DetailPageHost {
    readonly exercise = { id: 123, title: 'Saved title', type: ExerciseType.TEXT };
}

describe('Exercise detail action projection', () => {
    it('keeps history beside the heading and navigation before editing and secondary commands', () => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        const fixture = TestBed.createComponent(DetailPageHost);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('header a[href="#history"]')).not.toBeNull();
        const actions = fixture.nativeElement.querySelector('jhi-exercise-detail-actions');
        expect(Array.from(actions.querySelectorAll('a, button'), (element) => (element as HTMLElement).textContent?.trim())).toEqual([
            'Participations',
            'Statistics',
            'Edit',
            'Create Variant',
            'More actions',
        ]);
        expect(actions.textContent).not.toContain('History');
    });
});
