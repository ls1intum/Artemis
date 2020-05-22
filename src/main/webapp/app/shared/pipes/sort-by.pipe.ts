import { Pipe, PipeTransform } from '@angular/core';
import { DifferencePipe } from 'ngx-moment';
import { Submission } from 'app/entities/submission.model';
import { SortService } from 'app/shared/service/sort.service';

@Pipe({
    name: 'sortBy',
})
export class SortByPipe implements PipeTransform {
    constructor(private momentDiff: DifferencePipe, private sortService: SortService) {}

    transform<T>(array: T[], key: string, asc: boolean): T[] {
        return this.sortService.sortByProperty(array, key, asc);
    }
}
