package tech.buildrun.notebooklm.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record NotebookPatchRequest(

        @Size(min = 1, max = 256)
        String name,

        @Size(max = 2048)
        String description
) {
}
