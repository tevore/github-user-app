package com.example.demo.service;

import com.example.demo.cache.CachingConfig;
import com.example.demo.domain.GithubUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(GithubService.class)
@Import(CachingConfig.class)
public class GithubServiceTest {

    private final String baseUrl = "http://localhost:8081";

    @Autowired
    GithubService githubService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    public void shouldFindUser() {

        mockServer.expect(ExpectedCount.times(1), requestTo(baseUrl + "/users/some-user"))
                .andRespond(withSuccess("{\"login\":\"some-user\"}", MediaType.APPLICATION_JSON));

        GithubUser githubUser = githubService.getGithubUserInfo("some-user");

        mockServer.verify();
        assertEquals("some-user", githubUser.login());
    }

    @Test
    public void shouldFailToFindUser() {
        mockServer.expect(ExpectedCount.times(1), requestTo(baseUrl + "/users/missing-user"))
                .andRespond(withStatus(HttpStatusCode.valueOf(404))
                        .body("{\"message\":\"Not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThrows(HttpClientErrorException.class, () -> githubService.getGithubUserInfo("missing-user"));
    }

    @Test
    public void shouldFailDueToServiceError() {
        mockServer.expect(ExpectedCount.times(1), requestTo(baseUrl + "/users/missing-user"))
                .andRespond(withStatus(HttpStatusCode.valueOf(503))
                        .body("{\"message\":\"Service is down\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThrows(HttpServerErrorException.class, () -> githubService.getGithubUserInfo("missing-user"));
    }
}
