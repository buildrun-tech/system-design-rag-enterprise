package tech.buildrun.notebooklm.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationMessageCreateRequest(

        @NotBlank
        String content
) {
}
