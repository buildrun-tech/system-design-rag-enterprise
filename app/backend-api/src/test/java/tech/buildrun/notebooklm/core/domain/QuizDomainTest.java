package tech.buildrun.notebooklm.core.domain;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.quiz.Quiz;
import tech.buildrun.notebooklm.core.domain.quiz.QuizQuestion;
import tech.buildrun.notebooklm.core.domain.quiz.QuizStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuizDomainTest {

    @Test
    void lifecycleTransitions() {
        Quiz quiz = Quiz.create(UUID.randomUUID());
        assertThat(quiz.status()).isEqualTo(QuizStatus.PENDING);

        quiz.markProcessing();
        assertThat(quiz.status()).isEqualTo(QuizStatus.PROCESSING);

        quiz.complete(List.of());
        assertThat(quiz.status()).isEqualTo(QuizStatus.COMPLETED);

        quiz.fail("x");
        assertThat(quiz.status()).isEqualTo(QuizStatus.FAILED);
        assertThat(quiz.errorMessage()).isEqualTo("x");
    }

    @Test
    void expiredIfStaleOnlyAffectsOldInFlightQuizzes() {
        Instant old = Instant.now().minus(Duration.ofMinutes(11));
        Quiz inFlight = Quiz.restore(UUID.randomUUID(), UUID.randomUUID(), QuizStatus.PENDING, null, null, old, old);
        Quiz done = Quiz.restore(UUID.randomUUID(), UUID.randomUUID(), QuizStatus.COMPLETED, List.of(), null, old, old);

        assertThat(inFlight.expiredIfStale(Instant.now(), Duration.ofMinutes(10)).status())
                .isEqualTo(QuizStatus.FAILED);
        assertThat(done.expiredIfStale(Instant.now(), Duration.ofMinutes(10))).isSameAs(done);
    }

    @Test
    void shuffleKeepsCorrectAnswerPointingToSameText() {
        List<QuizQuestion> shuffled = IntStream.range(0, 50)
                .mapToObj(i -> new QuizQuestion("Q", List.of("certa", "x", "y", "z"), 0, "e").withShuffledOptions())
                .toList();

        assertThat(shuffled).allSatisfy(q -> assertThat(q.options().get(q.correctIndex())).isEqualTo("certa"));
        assertThat(shuffled).anyMatch(q -> q.correctIndex() != 0);
    }

    @Test
    void wellFormedChecksOptionsAndIndex() {
        assertThat(new QuizQuestion("Q", List.of("a", "b", "c", "d"), 3, "e").isWellFormed(4)).isTrue();
        assertThat(new QuizQuestion("Q", List.of("a", "b", "c", "d"), 4, "e").isWellFormed(4)).isFalse();
        assertThat(new QuizQuestion("Q", List.of("a", "b", "c"), 0, "e").isWellFormed(4)).isFalse();
        assertThat(new QuizQuestion("Q", List.of("a", "b", "c", " "), 0, "e").isWellFormed(4)).isFalse();
        assertThat(new QuizQuestion(" ", List.of("a", "b", "c", "d"), 0, "e").isWellFormed(4)).isFalse();
    }
}
