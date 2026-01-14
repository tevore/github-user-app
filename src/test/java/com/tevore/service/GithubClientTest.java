package com.tevore.service;

import com.tevore.configuration.RestClientConfig;
import com.tevore.domain.GithubRepo;
import com.tevore.domain.GithubUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(components = GithubClient.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RestClientConfig.class
        )
)
@TestPropertySource(properties = {
        "github.users.url=http://api.test/users/{username}",
        "github.repos.url=http://api.test/users/{username}/repos"
})
@Import(GithubClientTest.TestSliceConfig.class)
class GithubClientTest {

    @TestConfiguration
    static class TestSliceConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("githubUsers", "githubUserRepos");
        }

        @Bean
        RestClient restClient(RestClient.Builder builder) {
            return builder.build();
        }
    }

    @Autowired
    GithubClient githubClient;

    @Autowired
    MockRestServiceServer server;

    @Autowired CacheManager cacheManager;

    @BeforeEach
    void reset() {
        server.reset();
        cacheManager.getCache("githubUsers").clear();
        cacheManager.getCache("githubUserRepos").clear();
    }

    @Test
    void shouldSuccessfullyFetchUser() {
        server.expect(requestTo("http://api.test/users/some-user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"login\":\"some-user\"}", MediaType.APPLICATION_JSON));

        GithubUser user = githubClient.fetchUser("some-user");
        assertNotNull(user);
        assertEquals("some-user", user.login());

        server.verify();
    }

    @Test
    void shouldSuccessfullyFetchRepos() {
        server.expect(requestTo("http://api.test/users/some-user/repos"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"name\":\"repo\",\"url\":\"example.com\"}]", MediaType.APPLICATION_JSON));

        List<GithubRepo> repos = githubClient.fetchRepos("some-user");
        assertNotNull(repos);
        assertEquals(1, repos.size());
        assertEquals("repo", repos.get(0).name());

        server.verify();
    }

    @Test
    void shouldThrowHttpClientErrorExceptionFromFetchUser() {
        server.expect(requestTo("http://api.test/users/missing"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Not Found\"}"));

        assertThrows(HttpClientErrorException.NotFound.class, () -> githubClient.fetchUser("missing"));
        server.verify();
    }

    @Test
    void shouldThrowGithubUpstreamExceptionAfterExhaustedRetryFrom429Error() {
        server.expect(ExpectedCount.times(4), requestTo("http://api.test/users/some-user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"rate limited\"}"));

        assertThrows(GithubClient.GithubUpstreamException.class,
                () -> githubClient.fetchUser("some-user"));

        server.verify();
    }


    @Test
    void shouldSuccessfullyFallbackToCacheAfterServerHit() {
        server.expect(ExpectedCount.once(), requestTo("http://api.test/users/some-user"))
                .andRespond(withSuccess("{\"login\":\"some-user\"}", MediaType.APPLICATION_JSON));

        GithubUser u1 = githubClient.fetchUser("some-user");
        assertEquals("some-user", u1.login());

        // second call should be cache hit -> no second HTTP expectation needed
        GithubUser u2 = githubClient.fetchUser("some-user");
        assertEquals("some-user", u2.login());

        server.verify();
    }
}
