package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.repository.specs.MessageSpecs.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Message (Post) entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MessageRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /**
     * Generates SQL Query via specifications to find and sort Messages
     *
     * @param postContextFilter filtering and sorting properties for post objects
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @return returns a Page of Messages
     */
    default Page<Post> findMessages(PostContextFilter postContextFilter, Pageable pageable) {
        Specification<Post> specification = Specification.where(
                getConversationSpecification(postContextFilter.getConversationId()).and(getSearchTextSpecification(postContextFilter.getSearchText())).and(getSortSpecification()));

        return findAll(specification, pageable);
    }

    default Post findMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() != null).orElseThrow(() -> new EntityNotFoundException("Message", postId));
    }
}
