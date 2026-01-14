package com.tevore.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GithubUserWithReposResponse(
        String login,
        @JsonProperty("avatar_url") String avatarUrl,
        String url,
        String name,
        String location,
        String email,
        @JsonProperty("created_at") String createdAt,
        List<GithubRepo> repos
) {}

