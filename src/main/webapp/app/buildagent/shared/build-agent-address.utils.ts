import { BuildAgentInformation } from './entities/build-agent-information.model';

/**
 * Checks if a value looks like a Hazelcast member address (e.g., "[host]:port").
 */
export function looksLikeAddress(value: string): boolean {
    return /^\[.+\]:\d+$/.test(value);
}

/**
 * Extracts the host portion from a Hazelcast member address.
 * For "[192.168.1.1]:5701" returns "192.168.1.1".
 * For "[2001:db8::1]:5702" returns "2001:db8::1".
 * Returns the original value if it doesn't match the expected format.
 */
export function extractHost(address: string): string {
    const match = address.match(/^\[(.+)\]:\d+$/);
    return match ? match[1] : address;
}

/**
 * Creates a mapping from build agent host (without port) to agent name.
 * Uses host-only matching to handle Hazelcast ephemeral ports that change on reconnection.
 */
export function createAddressToNameMap(agents: BuildAgentInformation[]): Map<string, string> {
    const map = new Map<string, string>();
    for (const agent of agents) {
        const address = agent.buildAgent?.memberAddress;
        const name = agent.buildAgent?.name;
        if (address && name) {
            const host = extractHost(address);
            map.set(host, name);
        }
    }
    return map;
}

/**
 * Gets the agent name for a given address using the address-to-name map.
 * Returns the agent name if found, otherwise returns the original address.
 */
export function getAgentNameByAddress(address: string | undefined, addressToNameMap: Map<string, string>): string {
    if (!address) return '';
    const host = extractHost(address);
    return addressToNameMap.get(host) ?? address;
}
