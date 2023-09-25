import { Component, EventEmitter, Input, Output } from '@angular/core';
import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-select-with-search',
    templateUrl: './select-with-search.component.html',
    styleUrls: ['./select-with-search.component.scss'],
})
export class SelectWithSearchComponent {
    private searchTerm = '';
    private isSearching = false;
    private ngUnsubscribe = new Subject<void>();
    private readonly search$ = new Subject<string>();

    @Input() command: InteractiveSearchCommand;
    @Output() valueSelect = new EventEmitter<any | undefined>();

    values: any[] = [];

    constructor(private readonly alertService: AlertService) {}

    ngOnInit(): void {
        this.search$
            .pipe(
                debounceTime(300),
                distinctUntilChanged((prev, curr) => {
                    return prev === curr;
                }),
                tap((searchTerm) => {
                    this.isSearching = true;
                    this.searchTerm = searchTerm;
                }),
                switchMap(() => this.command.performSearch(this.searchTerm)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (res: HttpResponse<any[]>) => {
                    this.values = res.body!;
                    this.isSearching = false;
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.isSearching = false;
                    onError(this.alertService, errorResponse);
                },
            });
        this.search$.next(this.searchTerm);
    }

    updateSearchTerm($event: Event) {
        const searchTerm = ($event.target as HTMLInputElement).value?.trim().toLowerCase() ?? '';
        this.searchTerm = searchTerm;
        this.search$.next(searchTerm);
    }
}
