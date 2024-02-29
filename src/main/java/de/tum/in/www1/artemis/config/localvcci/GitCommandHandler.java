package de.tum.in.www1.artemis.config.localvcci;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

public class GitCommandHandler implements Command {

    private InputStream in;

    private OutputStream out;

    private OutputStream err;

    private ExitCallback callback;

    private final String command;

    private final String repositoryPath; // Path to your repository

    public GitCommandHandler(String command, String repositoryPath) {
        this.command = command;
        this.repositoryPath = repositoryPath;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channelSession, Environment env) throws IOException {
        new Thread(() -> {
            int exitStatus = 0;
            try {
                if (command.startsWith("git-upload-pack")) {
                    handleUploadPack();
                }
                else if (command.startsWith("git-receive-pack")) {
                    handleReceivePack();
                }
                else {
                    exitStatus = 1;
                    err.write(("Unsupported command: " + command).getBytes());
                }
            }
            catch (Exception e) {
                exitStatus = 1;
                try {
                    err.write(e.getMessage().getBytes());
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            finally {
                try {
                    out.flush();
                    err.flush();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                callback.onExit(exitStatus);
            }
        }).start();
    }

    private void handleUploadPack() throws IOException, GitAPIException {
        try (Git git = Git.open(new File(repositoryPath))) {
            UploadPack uploadPack = new UploadPack(git.getRepository());
            uploadPack.upload(in, out, err);
        }
    }

    private void handleReceivePack() throws IOException, GitAPIException {
        try (Git git = Git.open(new File(repositoryPath))) {
            ReceivePack receivePack = new ReceivePack(git.getRepository());
            receivePack.receive(in, out, err);
        }
    }

    @Override
    public void destroy(ChannelSession channelSession) {
        // Cleanup resources if necessary
    }
}
