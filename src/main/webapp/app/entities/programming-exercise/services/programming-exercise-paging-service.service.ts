import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { debounceTime, delay, switchMap, tap } from 'rxjs/operators';

export interface SearchResult {
    exercises: ProgrammingExercise[];
    total: number;
}

interface SearchState {
    page: number;
    pageSize: number;
    searchedExerciseName: string;
}

@Injectable({
    providedIn: 'root',
})
export class ProgrammingExercisePagingServiceService {
    private loading = new BehaviorSubject<boolean>(true);
    private search = new Subject<void>();
    private exercises = new BehaviorSubject<ProgrammingExercise[]>([]);
    private total = new BehaviorSubject<number>(0);
    private state: SearchState = {
        page: 1,
        pageSize: 10,
        searchedExerciseName: '',
    };

    constructor() {
        this.search.pipe(
            tap(() => this.loading.next(true)),
            debounceTime(100),
            switchMap(() => this.search),
        );
    }

    private searchForExercises(): Observable<SearchResult> {}
}
