import { ApollonDiagram } from '../entities/apollon-diagram';

export function getTitle(diagram: ApollonDiagram) {
    const trimmedTitle = diagram.title.trim();
    if (trimmedTitle !== '') {
        // The diagram has a non-whitespace title, therefore return it
        return trimmedTitle;
    }

    // The diagram doesn't have a title, therefore return its ID
    return `#${diagram.id}`;
}
