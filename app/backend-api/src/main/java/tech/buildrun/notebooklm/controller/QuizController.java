package tech.buildrun.notebooklm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.buildrun.notebooklm.dto.QuizCreatedResponse;
import tech.buildrun.notebooklm.dto.QuizResponse;
import tech.buildrun.notebooklm.entity.User;
import tech.buildrun.notebooklm.security.CurrentUser;
import tech.buildrun.notebooklm.service.QuizService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QuizCreatedResponse create(@CurrentUser User currentUser, @PathVariable UUID conversationId) {
        return quizService.create(conversationId, currentUser.getId());
    }

    @GetMapping("/{quizId}")
    public QuizResponse get(@CurrentUser User currentUser, @PathVariable UUID conversationId,
                            @PathVariable UUID quizId) {
        return quizService.get(conversationId, quizId, currentUser.getId());
    }
}
