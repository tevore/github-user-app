package com.tevore.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GithubUser(
        Long id,
        String login,
        @JsonProperty("avatar_url")
        String avatarUrl,
        @JsonProperty("node_id")
        String nodeId,
        @JsonProperty("gravatar_id")
        String gravatar_id,
        String url,
        @JsonProperty("html_url")
        String htmlUrl,
        @JsonProperty("followers_url")
        String followersUrl,
        @JsonProperty("following_url")
        String followingUrl,
        String type,
        String name,
        String company,
        String blog,
        String location,
        Boolean hireable,
        @JsonProperty("gists_url")
        String gistsUrl,
        @JsonProperty("starred_url")
        String starredUrl,
        @JsonProperty("subscriptions_url")
        String subscriptionsUrl,
        @JsonProperty("organizations_url")
        String organizationsUrl,
        @JsonProperty("repos_url")
        String reposUrl,
        @JsonProperty("events_url")
        String eventsUrl,
        @JsonProperty("received_events_url")
        String receivedEventsUrl,
        @JsonProperty("user_view_type")
        String userViewType,
        @JsonProperty("site_admin")
        String siteAdmin,
        String email,
        String bio,
        @JsonProperty("twitter_username")
        String twitterUsername,
        @JsonProperty("public_repos")
        Integer publicRepos,
        @JsonProperty("public_gists")
        Integer publicGists,
        Integer followers,
        Integer following,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("updated_at")
        String updatedAt) {
}