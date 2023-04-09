import { Component } from '@angular/core';
import { ImportComponent } from 'app/shared/import/import-component';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';

@Component({
    selector: 'jhi-learning-goal-import',
    templateUrl: './learning-goal-import.component.html',
})
export class LearningGoalImportComponent extends ImportComponent<LearningGoal> {
    override performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForLearningGoals(this.state)),
            )
            .subscribe((resp) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }
}
