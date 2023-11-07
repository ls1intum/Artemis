import { Input } from '@angular/core';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';

@Component({
    selector: 'jhi-apollon-diagram-import-dialog',
    templateUrl: './apollon-diagram-import-dialog.component.html',
    providers: [ApollonDiagramService],
})
export class ApollonDiagramListComponent {
    @Input()
    courseId: number;

    isInEditView: false;
}
