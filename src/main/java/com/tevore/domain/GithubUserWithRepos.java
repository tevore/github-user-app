package com.tevore.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record GithubUserWithRepos(
        @JsonUnwrapped
        GithubUser owner,
        List<GithubRepo> repos
) {
    public static GithubUserWithRepos of(GithubUser owner, List<GithubRepo> repos) {
        return new GithubUserWithRepos(owner, List.copyOf(repos));
    }
}
