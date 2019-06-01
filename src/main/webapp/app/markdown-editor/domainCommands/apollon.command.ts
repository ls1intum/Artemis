import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DomainTagCommand } from 'app/markdown-editor/domainCommands/domainTag.command';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ModelingEditorDialogComponent } from 'app/modeling-editor';
import { ApollonDiagram } from 'app/entities/apollon-diagram';

export class ApollonCommand extends DomainTagCommand {
    buttonTranslationString = 'arTeMiSApp.apollon';

    constructor(private modalService: NgbModal) {
        super();
    }

    /**
     * @function execute
     * @desc Add a new correct answer option to the text editor at the location of the cursor
     */
    execute(): void {
        // @ts-ignore
        const ref = this.modalService.open(ModelingEditorDialogComponent, { keyboard: true, size: 'xl' });
        ref.componentInstance.onModelSave.subscribe((diagram: ApollonDiagram) => {
            ref.close();
            const text = '\n' + this.getOpeningIdentifier() + diagram.id + this.getClosingIdentifier();
            ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
        });
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
