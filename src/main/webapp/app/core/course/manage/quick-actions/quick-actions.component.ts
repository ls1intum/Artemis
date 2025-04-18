// quick-actions.component.ts
import { Component } from '@angular/core';

@Component({
    selector: 'jhi-instructor-quick-actions',
    templateUrl: './quick-actions.component.html',
    styleUrls: ['./quick-actions.component.scss'],
    imports: [],
})
export class QuickActionsComponent {
    programmingImportModal(arg0: string, programmingImportModal: any) {
        throw new Error('Method not implemented.');
    }
    navigateToPage(arg0: string) {
        throw new Error('Method not implemented.');
    }
    navigateToUserManagement(arg0: string) {
        throw new Error('Method not implemented.');
    }
    addUser(arg0: string) {
        throw new Error('Method not implemented.');
    }

    createExercise(text: string) {}

    importExercise(fileupload: string, param2: string | null) {}

    openExerciseModal(exerciseModal: any) {}
}
