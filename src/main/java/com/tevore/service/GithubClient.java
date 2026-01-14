package com.tevore.service;

import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 *  This client manages the retry logic, caching and actual calls of the application
 *  It is the most critical point of failure.
 *  429 errors are retried, but 404, 400 and 500 are not in this scenario to not
 *  waste resources as this is not a call that needs to continuously be polled
 *  Partial success is a possible option as one of the calls could fail.
 *  Due to the potential rate-limit constraint, getting and caching some
 *  data is better than wasting a successful call.
 */
@Component
public class GithubClient {

    private final Logger LOGGER = LoggerFactory.getLogger(GithubClient.class);

    private final RestClient restClient;

    @Value("${github.users.url}")
    private String usersUrl;

    @Value("${github.repos.url}")
    private String userReposUrl;

    public GithubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = "githubUsers", key = "#username", sync = true)
    @Retryable(
            retryFor = { HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
            notRecoverable = { HttpClientErrorException.NotFound.class },
            maxAttempts = 4,
            backoff = @Backoff(delay = 250, multiplier = 2.0, maxDelay = 3000, random = true)
    )
    public GithubUser fetchUser(String username) {
        requireUsername(username);

        URI uri = UriComponentsBuilder.fromUriString(usersUrl)
                .buildAndExpand(username)
                .encode()
                .toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.value() == 429, (req, res) -> {
                    throw HttpClientErrorException.create(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limited by GitHub",
                            res.getHeaders(),
                            null,
                            null
                    );
                })
                .body(GithubUser.class);
    }

    @Cacheable(cacheNames = "githubUserRepos", key = "#username", sync = true)
    @Retryable(
            retryFor = { HttpClientErrorException.TooManyRequests.class, ResourceAccessException.class },
            notRecoverable = { HttpClientErrorException.NotFound.class },
            maxAttempts = 4,
            backoff = @Backoff(delay = 250, multiplier = 2.0, maxDelay = 3000, random = true)
    )
    public List<GithubRepo> fetchRepos(String username) {
        requireUsername(username);

        URI uri = UriComponentsBuilder.fromUriString(userReposUrl)
                .buildAndExpand(username)
                .encode()
                .toUri();

        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(s -> s.value() == 429, (req, res) -> {
                    throw HttpClientErrorException.create(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limited by GitHub",
                            res.getHeaders(),
                            null,
                            null
                    );
                })
                .body(new ParameterizedTypeReference<List<GithubRepo>>() {});
    }

    @Recover
    public GithubUser recoverUser(HttpClientErrorException.TooManyRequests ex, String username) {
        throw new GithubUpstreamException("GitHub user call rate-limited after retries: " + username, ex);
    }

    @Recover
    public GithubUser recoverUser(ResourceAccessException ex, String username) {
        throw new GithubUpstreamException("GitHub user call failed after retries: " + username, ex);
    }

    @Recover
    public List<GithubRepo> recoverRepos(HttpClientErrorException.TooManyRequests ex, String username) {
        throw new GithubUpstreamException("GitHub repos call rate-limited after retries: " + username, ex);
    }

    @Recover
    public List<GithubRepo> recoverRepos(ResourceAccessException ex, String username) {
        throw new GithubUpstreamException("GitHub repos call failed after retries: " + username, ex);
    }

    private static void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "Username cannot be null/blank",
                    null,
                    null,
                    null
            );
        }
    }

    public static class GithubUpstreamException extends RuntimeException {
        public GithubUpstreamException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
