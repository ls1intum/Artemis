import { Component, Input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingBuildStatisticsDetail } from 'app/detail-overview-list/detail.model';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-programming-build-statistics',
    imports: [TranslateDirective, CommonModule],
    templateUrl: './programming-build-statistics.component.html',
})
export class ProgrammingBuildStatisticsComponent {
    @Input() detail: ProgrammingBuildStatisticsDetail;
}
