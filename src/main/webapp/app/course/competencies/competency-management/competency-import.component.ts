import { Component } from '@angular/core';
import { ImportComponent } from 'app/shared/import/import-component';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { Competency } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-import',
    templateUrl: './competency-import.component.html',
})
export class CompetencyImportComponent extends ImportComponent<Competency> {
    override performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForCompetencies(this.state)),
            )
            .subscribe((resp) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }
}
