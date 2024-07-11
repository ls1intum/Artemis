export class EntityNotFoundError extends Error {
    constructor() {
        super('Entity not found');
        this.name = 'EntityNotFoundError';
    }
}
