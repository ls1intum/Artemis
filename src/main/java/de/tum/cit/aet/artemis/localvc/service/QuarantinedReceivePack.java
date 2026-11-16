package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.function.BiConsumer;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator.SubmoduleValidationException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * A receive pack that stores the objects of a push in a {@link PushQuarantine} and not in the repository. It checks the commands of the push before it reads the objects,
 * and it removes the objects of a push that was rejected or that broke off.
 */
public final class QuarantinedReceivePack extends ReceivePack {

    private final PushQuarantine quarantine;

    private final BiConsumer<ReceivePack, Collection<ReceiveCommand>> checkBeforeObjects;

    private QuarantinedReceivePack(PushQuarantine quarantine, BiConsumer<ReceivePack, Collection<ReceiveCommand>> checkBeforeObjects) {
        super(quarantine.repository());
        this.quarantine = quarantine;
        this.checkBeforeObjects = checkBeforeObjects;
    }

    /**
     * Creates a receive pack for a push to the repository.
     *
     * @param repository         the repository that receives the push
     * @param checkBeforeObjects the checks that need only the commands of the push, which run before the objects are read. A command that they reject is not applied, and
     *                               when no command is left, the objects are not read at all.
     * @return the receive pack, or a plain receive pack if the repository does not store its objects in a directory
     * @throws IOException if the place for the objects cannot be created
     */
    public static ReceivePack create(Repository repository, BiConsumer<ReceivePack, Collection<ReceiveCommand>> checkBeforeObjects) throws IOException {
        if (!(repository instanceof FileRepository)) {
            return new ReceivePack(repository);
        }
        return new QuarantinedReceivePack(PushQuarantine.open(repository), checkBeforeObjects);
    }

    @Override
    protected void receivePackAndCheckConnectivity() throws IOException, LargeObjectException, SubmoduleValidationException {
        checkBeforeObjects.accept(this, getAllCommands());
        boolean objectsNeeded = getAllCommands().stream()
                .anyMatch(command -> command.getType() != ReceiveCommand.Type.DELETE && command.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED);
        if (objectsNeeded) {
            super.receivePackAndCheckConnectivity();
        }
    }

    /**
     * Moves the received objects into the repository. Called when the push is accepted, before its references are updated.
     *
     * @throws IOException if an object cannot be moved
     */
    public void storeObjects() throws IOException {
        quarantine.migrate();
    }

    @Override
    public void receive(InputStream input, OutputStream messages, OutputStream errors) throws IOException {
        try {
            quarantine.prepare();
            super.receive(input, messages, errors);
        }
        finally {
            quarantine.close();
        }
    }

    @Override
    public void receiveWithExceptionPropagation(InputStream input, OutputStream messages, OutputStream errors) throws IOException {
        try {
            quarantine.prepare();
            super.receiveWithExceptionPropagation(input, messages, errors);
        }
        finally {
            quarantine.close();
        }
    }
}
