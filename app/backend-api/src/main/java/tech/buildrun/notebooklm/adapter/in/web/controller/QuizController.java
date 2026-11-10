package tech.buildrun.notebooklm.adapter.in.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.adapter.in.web.dto.QuizResponse;
import tech.buildrun.notebooklm.adapter.in.web.mapper.QuizWebMapper;
import tech.buildrun.notebooklm.adapter.in.web.security.CurrentUser;
import tech.buildrun.notebooklm.core.domain.user.User;
import tech.buildrun.notebooklm.core.port.in.GenerateQuizPort;
import tech.buildrun.notebooklm.core.port.in.GetQuizPort;

import java.util.UUID;

@RestController("hexQuizController")
@RequestMapping("/api/v1/conversations/{conversationId}/quizzes")
public class QuizController {

    private final GenerateQuizPort generateQuiz;
    private final GetQuizPort getQuiz;

    public QuizController(GenerateQuizPort generateQuiz, GetQuizPort getQuiz) {
        this.generateQuiz = generateQuiz;
        this.getQuiz = getQuiz;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QuizCreatedResponse create(@CurrentUser User currentUser, @PathVariable UUID conversationId) {
        return QuizWebMapper.toCreated(generateQuiz.generate(conversationId, currentUser.id()));
    }

    @GetMapping("/{quizId}")
    public QuizResponse get(@CurrentUser User currentUser, @PathVariable UUID conversationId,
                            @PathVariable UUID quizId) {
        return QuizWebMapper.toResponse(getQuiz.get(conversationId, quizId, currentUser.id()));
    }
}
