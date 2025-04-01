import { Component, Input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { ProgrammingBuildStatisticsDetail } from 'app/shared/detail-overview-list/detail.model';

@Component({
    selector: 'jhi-programming-build-statistics',
    imports: [TranslateDirective, CommonModule],
    templateUrl: './programming-build-statistics.component.html',
})
export class ProgrammingBuildStatisticsComponent {
    @Input() detail: ProgrammingBuildStatisticsDetail;
}
