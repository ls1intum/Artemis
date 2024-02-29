package de.tum.in.www1.artemis.config.localvcci;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.PublicKey;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.UnknownCommand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;

import de.tum.in.www1.artemis.domain.User;

@Configuration
public class SshConfiguration {

    private final GitPublickeyAuthenticator gitPublickeyAuthenticator;

    public SshConfiguration(GitPublickeyAuthenticator gitPublickeyAuthenticator) {
        this.gitPublickeyAuthenticator = gitPublickeyAuthenticator;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshServer sshServer() throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(2222); // Use any port that suits your configuration
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("hostkey.ser")));
        sshd.setPasswordAuthenticator((username, password, session) -> "admin".equals(username) && "secret".equals(password)); // Replace with your actual authentication logic
        sshd.setCommandFactory(gitCommandFactory());
        sshd.setPublickeyAuthenticator(gitPublickeyAuthenticator);
        // Add command factory or shell here to handle Git commands or any other commands

        return sshd;
    }

    @Bean
    public CommandFactory gitCommandFactory() {
        return (channel, command) -> {
            // Assuming command starts with the actual Git command followed by a space
            // TODO: double check the actual delimiter
            String[] commandParts = command.split(" ", 2);
            String gitCommand = commandParts[0]; // This should give you "git-upload-pack" or "git-receive-pack"
            String repositoryUrl = commandParts[1];
            User user = channel.getSession().getAttribute(SSHDConstants.USER_KEY);

            if (user != null) {
                // Proceed with authorization check
                String repositoryPath = extractRepositoryPathFromRepositoryUrl(repositoryUrl);
                if (!userHasAccessToRepository(user, repositoryPath, gitCommand)) { // Example operation
                    throw new AccessDeniedException("User does not have access to this repository");
                }
                if ("git-upload-pack".equals(gitCommand) || "git-receive-pack".equals(gitCommand)) {
                    // Adjust your createGitCommandHandler method to handle the full command string or just the operation
                    return new GitCommandHandler(command, repositoryPath);
                }
            }

            // Fallback or unsupported commands
            return new UnknownCommand(command);
        };
    }

    private boolean userHasAccessToRepository(User user, String repositoryPath, String gitCommand) {
        // TODO distinguish between push (write) and fetch (read) access
        // and verify if the given user has access to the repo at the given path
        return true;
    }

    private String extractRepositoryPathFromRepositoryUrl(String repositoryUrl) {
        // TODO: implement
        return repositoryUrl;
    }

    @Bean
    public PublickeyAuthenticator myPublickeyAuthenticator() {
        return (username, key, session) -> {
            // Implement your logic to validate the public key
            // This might involve checking the key against a database or a file
            return isValidUserKey(username, key);
        };
    }

    private boolean isValidUserKey(String username, PublicKey key) {
        // Placeholder for actual key validation logic
        // You would check if the key is authorized for the given username
        return true; // Implement actual check
    }
}
