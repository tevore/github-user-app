package com.tevore.service;

import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import com.tevore.domain.GithubUserWithRepos;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * This service class is the entrypoint to the actual downstream processing
 * It utilizes an async client to perform the actual calls which isolates
 * the concurrent call logic to a singular space of concern so that
 * is it not muddied up with other logic ( e.g. retry )
 * Aside from orchestration, it handles wrapping up the response in the expected format
 */
@Service
public class GithubService {

    private final Logger LOGGER = LoggerFactory.getLogger(GithubService.class);

    private final GithubServiceAsyncClient asyncClient;

    public GithubService(GithubServiceAsyncClient asyncClient) {
        this.asyncClient = asyncClient;
    }

    public GithubUserWithRepos retrieveGithubUserAndRepoInfo(String username) {

        LOGGER.info("Initiating async calls");

        CompletableFuture<GithubUser> userFetch = asyncClient.fetchUserAsync(username);
        CompletableFuture<List<GithubRepo>> reposFetch = asyncClient.fetchReposAsync(username);

        try {
            GithubUser user = userFetch.join();
            List<GithubRepo> repos = reposFetch.join();
            return GithubUserWithRepos.of(user, repos);
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof RuntimeException re) throw re;
            LOGGER.error("Failed to retrieve GitHub user + repos for user = {}", username + cause);
            throw new RuntimeException("Failed to retrieve GitHub user + repos for " + username, cause);
        }
    }
}
