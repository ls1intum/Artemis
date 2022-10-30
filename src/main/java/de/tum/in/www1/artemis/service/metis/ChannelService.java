package de.tum.in.www1.artemis.service.metis;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;

@Service
public class ChannelService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final ChannelRepository channelRepository;

    private final UserRepository userRepository;

    public ChannelService(ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository, UserRepository userRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    public void registerStudent(User userToRegister, Conversation channel) {

        var channelFromDatabase = channelRepository.findChannelWithConversationParticipantsByIdElseThrow(channel.getId());
        var userFromDatabase = userRepository.getUserWithGroupsAndAuthorities(userToRegister.getLogin());

        var isRegistered = channelFromDatabase.getConversationParticipants().stream()
                .anyMatch(conversationParticipant -> conversationParticipant.getUser().getLogin().equals(userFromDatabase.getLogin()));
        if (isRegistered) {
            return;
        }

        var newConversationParticipant = new ConversationParticipant();
        newConversationParticipant.setUser(userToRegister);
        newConversationParticipant.setConversation(channelFromDatabase);
        newConversationParticipant = conversationParticipantRepository.save(newConversationParticipant);
        channelFromDatabase.getConversationParticipants().add(newConversationParticipant);
        channelRepository.save(channelFromDatabase);
    }

    public void deregisterStudent(User userToDeregister, Conversation channel) {
        var channelFromDatabase = channelRepository.findChannelWithConversationParticipantsByIdElseThrow(channel.getId());
        var userFromDatabase = userRepository.getUserWithGroupsAndAuthorities(userToDeregister.getLogin());

        var matchingParticipant = channelFromDatabase.getConversationParticipants().stream()
                .filter(conversationParticipant -> conversationParticipant.getUser().getLogin().equals(userFromDatabase.getLogin())).findFirst();

        // Todo: Think if we really want to delete this or just use a boolean property so channel specific config is not lost if user re-joins
        matchingParticipant.ifPresent(conversationParticipant -> {
            channelFromDatabase.getConversationParticipants().remove(conversationParticipant);
            channelRepository.save(channelFromDatabase);
            conversationParticipantRepository.delete(conversationParticipant);
        });
    }

    public record ChannelOverviewDTO(Long channelId, String channelName, boolean isMember, int noOfMembers) {
    }

    public List<Conversation> getChannels(Long courseId) {
        return channelRepository.findChannelsWithConversationParticipantsByCourseId(courseId);
    }

    public Conversation getChannelElseThrow(Long channelId) {
        return channelRepository.findChannelWithConversationParticipantsByIdElseThrow(channelId);
    }

}
