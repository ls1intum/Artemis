import {ArtemisMarkdown} from 'app/components/util/markdown.service';
import {Question} from 'app/entities/question';

export abstract class Command {

    buttonTranslationString: string;
    protected editor: any;
    protected artemisMarkdown: ArtemisMarkdown;

    public setEditor(editor: any): void {
        this.editor = editor;
    }

    public setArtemisMarkdownService(artemisMarkdown: ArtemisMarkdown): void {
        this.artemisMarkdown = artemisMarkdown;
    }


    abstract execute(): void;

    abstract parsing(text: string): void;
}
