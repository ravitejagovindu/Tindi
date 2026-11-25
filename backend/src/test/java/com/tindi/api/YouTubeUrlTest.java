package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tindi.api.service.YouTubeUrl;
import com.tindi.api.web.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeUrlTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42s",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc123",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://m.youtube.com/shorts/dQw4w9WgXcQ?feature=share",
            "youtube.com/watch?v=dQw4w9WgXcQ",
            "  https://youtu.be/dQw4w9WgXcQ  ",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ",
    })
    void acceptedShapes(String url) {
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.videoId(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.instagram.com/reel/Cabc123/",
            "https://vimeo.com/12345678901",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
            "https://www.youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/?u=youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/",
            "not a url",
            "",
    })
    void rejectedShapes(String url) {
        ApiException e = assertThrows(ApiException.class, () -> YouTubeUrl.videoId(url));
        assertEquals("VALIDATION_FAILED", e.code());
        assertEquals(YouTubeUrl.MESSAGE, e.fields().get("url"));
    }
}
