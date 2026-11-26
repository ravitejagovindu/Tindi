package com.tindi.api.dto;

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

    public record SuggestedDto(List<String> ingredients, List<String> steps, String confidence, String foundIn) {}

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
