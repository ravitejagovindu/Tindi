package com.tindi.api.service;

import com.tindi.api.dto.SourceDtos.CommentDto;
import com.tindi.api.dto.SourceDtos.FetchResponse;
import com.tindi.api.dto.SourceDtos.StructureResponse;
import com.tindi.api.service.GroundingCheck.Kind;
import com.tindi.api.service.GroundingCheck.Source;
import com.tindi.api.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SourceService {
    private final YouTubeClient youtube;
    private final RecipeStructurer structurer;

    public SourceService(YouTubeClient youtube, RecipeStructurer structurer) {
        this.youtube = youtube;
        this.structurer = structurer;
    }

    /** Pasted text: one source, no YouTube call. */
    public StructureResponse structure(String text) {
        return new StructureResponse(structurer.structure("pasted", List.of(new Source(Kind.PASTED, text))));
    }

    public FetchResponse fetch(String url) {
        String videoId = YouTubeUrl.videoId(url); // bad links are rejected before anything else
        if (!youtube.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_NOT_CONFIGURED",
                    "Fetching from YouTube is not set up on the server. Paste the recipe text instead.", null);
        }
        YouTubeClient.Video video = youtube.video(videoId);
        List<YouTubeClient.Comment> comments = youtube.topComments(videoId);

        // "Pinned" is not exposed by the API. The creator's own top comment is the best available guess.
        List<CommentDto> dtos = comments.stream()
                .map(c -> new CommentDto(c.author(), c.text(), c.likeCount(),
                        !c.authorChannelId().isEmpty() && c.authorChannelId().equals(video.channelId())))
                .toList();
        List<Source> sources = new ArrayList<>();
        sources.add(new Source(Kind.TITLE, video.title()));
        sources.add(new Source(Kind.DESCRIPTION, video.description()));
        comments.forEach(c -> sources.add(new Source(Kind.COMMENT, c.text())));
        return new FetchResponse(video.title(), video.channelName(), video.thumbnailUrl(),
                YouTubeUrl.canonical(videoId), "YOUTUBE", video.description(), dtos,
                structurer.structure(videoId, sources));
    }
}
