package pl.rynek.engagement.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import pl.rynek.engagement.api.ReviewViews.Author;
import pl.rynek.engagement.api.dto.CursorPage;
import pl.rynek.engagement.questions.Question;

/** Kształt zgodny z typem {@code Question} z rynek-frontend (models/engagement.ts). */
@Component
class QuestionViews {

    record Answer(String text, Instant createdAt) {
    }

    record QuestionResponse(UUID id, Author author, String text, Instant createdAt, int likes, boolean mine, Answer answer) {
    }

    QuestionResponse one(Question q, Optional<UUID> viewer) {
        Answer answer = q.answered() ? new Answer(q.getAnswerText(), q.getAnswerAt()) : null;
        return new QuestionResponse(q.getId(), new Author(q.getAuthorName(), ReviewViews.initials(q.getAuthorName())), q.getText(),
                q.getCreatedAt(), q.getLikes(), viewer.map(q.getAuthorId()::equals).orElse(false), answer);
    }

    CursorPage<QuestionResponse> page(String cursor, Integer limit, Optional<UUID> viewer, Cursors.Fetch<Question> fetch) {
        return Cursors.page(cursor, limit, fetch, q -> one(q, viewer));
    }
}
