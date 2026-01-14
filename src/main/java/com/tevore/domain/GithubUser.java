package com.tevore.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubUser(
        String login,
        @JsonProperty("avatar_url")
        String avatarUrl,
        String url,
        String name,
        String location,
        String email,
        @JsonProperty("created_at")
        String createdAt) {
}