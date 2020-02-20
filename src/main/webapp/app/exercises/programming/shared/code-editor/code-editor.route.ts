import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { CodeEditorInstructorContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-container.component';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/shared/code-editor/code-editor-student-container.component';
import { CodeEditorInstructorOrionContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-orion-container.component';

export const codeEditorRoute: Routes = [];
