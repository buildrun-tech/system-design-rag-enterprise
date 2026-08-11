package tech.buildrun.notebooklm.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationMessageCreateRequest(

        @NotBlank
        String content
) {
}
