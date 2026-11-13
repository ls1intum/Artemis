package de.tum.cit.aet.artemis.localvc.service;

import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Bounds of a push to a repository of LocalVC. A push is read from a client, so the size of what it announces and the time it may take are limited before the content is
 * examined, and not only when it has been stored.
 */
public final class LocalVCReceivePackLimits {

    /** The largest pack that is accepted. A repository of an exercise holds source code, so this is generous. */
    public static final long MAX_PACK_SIZE_BYTES = 500L * 1024 * 1024;

    /**
     * The largest single object that is accepted, also in the history of the pushed branch. The pre-receive hook additionally rejects files above 10 MB in the latest commit,
     * with a message that names the file.
     */
    public static final long MAX_OBJECT_SIZE_BYTES = 50L * 1024 * 1024;

    /** The largest list of commands of a push. A push updates exactly one branch. */
    public static final int MAX_COMMAND_BYTES = 16 * 1024;

    /** The time a push may wait for data from the client, in seconds. */
    public static final int TIMEOUT_SECONDS = 120;

    private LocalVCReceivePackLimits() {
        // utility class
    }

    /**
     * Applies the limits to a receive pack.
     *
     * @param receivePack the process that handles a push
     * @return the same receive pack
     */
    public static ReceivePack apply(ReceivePack receivePack) {
        receivePack.setMaxPackSizeLimit(MAX_PACK_SIZE_BYTES);
        receivePack.setMaxObjectSizeLimit(MAX_OBJECT_SIZE_BYTES);
        receivePack.setMaxCommandBytes(MAX_COMMAND_BYTES);
        receivePack.setTimeout(TIMEOUT_SECONDS);
        return receivePack;
    }

    /**
     * Applies the time limit to a fetch. The time counts without any data in either direction, so a slow transfer that makes progress is not cut off, while a client that stalls
     * after the start does not keep a worker busy.
     *
     * @param uploadPack the process that handles a fetch
     * @return the same upload pack
     */
    public static UploadPack apply(UploadPack uploadPack) {
        uploadPack.setTimeout(TIMEOUT_SECONDS);
        return uploadPack;
    }
}
