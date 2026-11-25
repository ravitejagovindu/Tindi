package com.tindi.api.service;

import com.tindi.api.dto.SourceDtos.CommentDto;
import com.tindi.api.dto.SourceDtos.FetchResponse;
import com.tindi.api.dto.SourceDtos.SuggestedDto;
import com.tindi.api.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SourceService {
    private final YouTubeClient youtube;
    private final RecipeSplitter splitter = new RecipeSplitter();

    public SourceService(YouTubeClient youtube) {
        this.youtube = youtube;
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
        RecipeSplitter.Result r = splitter.split(video.description(), comments.stream().map(YouTubeClient.Comment::text).toList());
        return new FetchResponse(video.title(), video.channelName(), video.thumbnailUrl(),
                YouTubeUrl.canonical(videoId), "YOUTUBE", video.description(), dtos,
                new SuggestedDto(r.ingredients(), r.steps(), r.confidence().name(), r.foundIn().name()));
    }
}
