package com.tindi.api.dto;

import com.tindi.api.dto.RecipeDtos.StepDetailDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class SourceDtos {
    private SourceDtos() {}

    public record FetchRequest(
            @NotBlank(message = "Paste a YouTube link") @Size(max = 2048, message = "That link is too long") String url) {
        public FetchRequest {
            url = url == null ? null : url.trim();
        }
    }

    public record CommentDto(String author, String text, int likeCount, boolean isPinnedLikely) {}

    /** {@code stepDetails} is parallel to {@code steps}, or null when there is none. */
    public record SuggestedDto(List<String> ingredients, List<String> steps, List<StepDetailDto> stepDetails,
                               String confidence, String foundIn, String method, List<String> warnings) {}

    public record StructureRequest(
            @NotBlank(message = "Paste the recipe text") @Size(max = 20000, message = "That text is too long") String text) {}

    public record StructureResponse(SuggestedDto suggested) {}

    public record FetchResponse(
            String title,
            String channelName,
            String thumbnailUrl,
            String sourceUrl,
            String sourceType,
            String description,
            List<CommentDto> comments,
            SuggestedDto suggested) {}
}
