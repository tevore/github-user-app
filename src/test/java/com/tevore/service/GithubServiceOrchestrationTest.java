package com.tevore.service;

import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import com.tevore.domain.GithubUserWithReposResponse;
import com.tevore.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class GithubServiceOrchestrationTest {

    @Mock
    GithubServiceAsyncClient asyncClient;

    GithubService githubService;

    @BeforeEach
    void setUp() {
        githubService = new GithubService(asyncClient);
    }

    @Test
    void shouldSuccessfullyRetrieveDataAndCombineResults() {
        GithubUser user = TestUtils.generateGitHubUser();
        List<GithubRepo> repos = List.of(new GithubRepo("repo", "example.com"));

        when(asyncClient.fetchUserAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(user));
        when(asyncClient.fetchReposAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(repos));

        GithubUserWithReposResponse result = githubService.retrieveGithubUserAndRepoInfo("some-user");

        assertNotNull(result);
        assertEquals("some-user", result.login());
        assertEquals(1, result.repos().size());
        assertEquals("repo", result.repos().get(0).name());

        verify(asyncClient, times(1)).fetchUserAsync("some-user");
        verify(asyncClient, times(1)).fetchReposAsync("some-user");
        verifyNoMoreInteractions(asyncClient);
    }

    @Test
    void shouldNotCompletelyFailIfUserCallDoesNotSucceed() {
        RuntimeException runtimeException = new RuntimeException("user failed");

        when(asyncClient.fetchUserAsync("some-user"))
                .thenReturn(CompletableFuture.failedFuture(runtimeException));
        when(asyncClient.fetchReposAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> githubService.retrieveGithubUserAndRepoInfo("some-user"));

        assertSame(runtimeException, thrown);

        verify(asyncClient, times(1)).fetchUserAsync("some-user");
        verify(asyncClient, times(1)).fetchReposAsync("some-user");
        verifyNoMoreInteractions(asyncClient);
    }

    @Test
    void shouldNotCompletelyFailIfRepoCallDoesNotSucceed() {
        RuntimeException runtimeException = new RuntimeException("repos failed");

        when(asyncClient.fetchUserAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(TestUtils.generateGitHubUser()));
        when(asyncClient.fetchReposAsync("some-user"))
                .thenReturn(CompletableFuture.failedFuture(runtimeException));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> githubService.retrieveGithubUserAndRepoInfo("some-user"));

        assertSame(runtimeException, thrown);

        verify(asyncClient, times(1)).fetchUserAsync("some-user");
        verify(asyncClient, times(1)).fetchReposAsync("some-user");
        verifyNoMoreInteractions(asyncClient);
    }

    @Test
    void shouldThrowExceptionIfSomethingFailsDuringOrchestration() {
        Throwable checked = new IllegalAccessException("nope");
        CompletableFuture<GithubUser> userFuture = new CompletableFuture<>();
        userFuture.completeExceptionally(checked);

        when(asyncClient.fetchUserAsync("some-user")).thenReturn(userFuture);
        when(asyncClient.fetchReposAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> githubService.retrieveGithubUserAndRepoInfo("some-user"));

        assertNotNull(thrown.getCause());
        assertEquals(checked, thrown.getCause());

        verify(asyncClient, times(1)).fetchUserAsync("some-user");
        verify(asyncClient, times(1)).fetchReposAsync("some-user");
        verifyNoMoreInteractions(asyncClient);
    }

    @Test
    void successfullyFormatsDateAndCombinesResults() {
        GithubUser user = new GithubUser(
                "some-user",
                "avatar",
                "url",
                "John",
                "USA",
                "email",
                Instant.parse("2014-03-04T12:24:54Z")
        );

        List<GithubRepo> repos = List.of(
                new GithubRepo("repo", "example.com")
        );

        when(asyncClient.fetchUserAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(user));

        when(asyncClient.fetchReposAsync("some-user"))
                .thenReturn(CompletableFuture.completedFuture(repos));

        GithubUserWithReposResponse result =
                githubService.retrieveGithubUserAndRepoInfo("some-user");

        assertEquals("some-user", result.login());
        assertEquals("Tue, 4 Mar 2014 12:24:54 GMT", result.createdAt());
        assertEquals(1, result.repos().size());
        assertEquals("repo", result.repos().get(0).name());

        verify(asyncClient).fetchUserAsync("some-user");
        verify(asyncClient).fetchReposAsync("some-user");
        verifyNoMoreInteractions(asyncClient);
    }


}

