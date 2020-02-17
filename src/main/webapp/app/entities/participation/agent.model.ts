export interface Agent {
    getName(): string;

    getUsername(): string;

    holds(agent: Agent): boolean;
}
