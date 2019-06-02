import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ModelingEditorDialogComponent } from 'app/modeling-editor';
import { ApollonDiagram } from 'app/entities/apollon-diagram';

export class ApollonCommand extends DomainTagCommand {
    buttonTranslationString = 'arTeMiSApp.apollonDiagram.command.apollonCommand';

    constructor(private modalService: NgbModal) {
        super();
    }

    /**
     * @function execute
     * @desc Open a dialog where the user can create a new diagram or edit an existing one.
     */
    execute(): void {
        const existingDiagramId = this.isCursorWithinTag();
        // @ts-ignore
        // xl is an allowed option for the modal size, but missing in the type definitions
        const ref = this.modalService.open(ModelingEditorDialogComponent, { keyboard: true, size: 'xl' });
        if (existingDiagramId) {
            // If there is an existing diagram, load it.
            ref.componentInstance.diagramId = Number(existingDiagramId);
            ref.componentInstance.onModelSave.subscribe(() => {
                ref.close();
            });
        } else {
            // Otherwise let the modal create a new diagram on save and insert its value into the markdown.
            ref.componentInstance.onModelSave.subscribe((diagram: ApollonDiagram) => {
                ref.close();
                const text = '\n' + this.getOpeningIdentifier() + diagram.id + this.getClosingIdentifier();
                ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
            });
        }
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the correct option
     */
    getOpeningIdentifier(): string {
        return '[apollon]';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the correct option
     */
    getClosingIdentifier(): string {
        return '[/apollon]';
    }
}
