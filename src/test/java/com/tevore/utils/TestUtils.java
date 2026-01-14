package com.tevore.utils;

import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import com.tevore.domain.GithubUserWithReposResponse;
import org.springframework.cache.Cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class TestUtils {

    public static final String INVALID_PATH_OR_MISSING_USERNAME = "Username required or path is incorrect";
    public static final String INVALID_FORMAT_USERNAME = "Usernames can only contain alphanumerics and single hyphens";
    public static final String INVALID_LENGTH_USERNAME = "Usernames are between 1 and 39 characters";
    public static final Object USER_NOT_FOUND = "User not found";

    public static GithubUser generateGitHubUser() {
        return new GithubUser(
                "some-user",
                "https://avatars.githubusercontent.com/u/583231?v=4",
                "https://api.github.com/users/some-user",
                "Some User",
                "Miami",
                "someuser@example.com",
                Instant.now());
    }

    public static GithubUserWithReposResponse generateGitHubUserWithRepos() {
        return new GithubUserWithReposResponse(
                        "some-user",
                        "https://avatars.githubusercontent.com/u/583231?v=4",
                        "https://api.github.com/users/some-user",
                        "Some User",
                        "Miami",
                        "someuser@example.com",
                        "2011-01-25T18:44:36Z",
                        List.of(new GithubRepo("some-repo", "example.com"))
        );
    }

    //Helper method to assist with cache eviction
    public static void awaitUntilNull(Cache cache, String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (cache.get(key) == null) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for cache eviction");
            }
        }
        fail("Cache entry for key=" + key + " was not evicted within " + timeout);
    }
}
