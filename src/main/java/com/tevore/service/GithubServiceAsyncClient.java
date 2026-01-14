package com.tevore.service;

import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This client is responsible for handling the async calls to the user and repo
 * endpoints of the Github API.
 * Partial success is a possible option as one of the calls could fail.
 * Due to the potential rate-limit constraint, getting and caching some
 * data is better than wasting a successful call.
 *
 */
@Component
public class GithubServiceAsyncClient {

    private final Logger LOGGER = LoggerFactory.getLogger(GithubServiceAsyncClient.class);

    private final GithubClient githubClient;

    public GithubServiceAsyncClient(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    @Async("asyncExecutor")
    public CompletableFuture<GithubUser> fetchUserAsync(String username) {
        LOGGER.info("Fetching user information");
        try {
            return CompletableFuture.completedFuture(githubClient.fetchUser(username));
        } catch (RuntimeException ex) {
            LOGGER.error("Error fetching user information", ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("asyncExecutor")
    public CompletableFuture<List<GithubRepo>> fetchReposAsync(String username) {
        LOGGER.info("Fetching repo information");
        try {
            return CompletableFuture.completedFuture(githubClient.fetchRepos(username));
        } catch (RuntimeException ex) {
            LOGGER.error("Error fetching repo information", ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

}
