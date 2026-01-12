package com.example.demo.controller;

import com.example.demo.cache.CachingConfig;
import com.example.demo.error.GlobalExceptionHandler;
import com.example.demo.service.GithubService;
import com.example.demo.utils.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubController.class)
@Import({GlobalExceptionHandler.class, CachingConfig.class})
public class GithubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GithubService githubService;

    @Test
    void shouldReturnUser() throws Exception {
        when(githubService.getGithubUserInfo("some-user"))
                .thenReturn(TestUtils.generateGithubUser());

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", "some-user"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.login").value("some-user"));
    }

    @Test
    void shouldThrowErrorMessageDueToMissingUsernameValue() throws Exception {
        when(githubService.getGithubUserInfo("some-user"))
                .thenReturn(TestUtils.generateGithubUser());

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", ""))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessages[0]").value(TestUtils.INVALID_PATH_OR_MISSING_USERNAME));

    }

    @Test
    void shouldThrowErrorMessageDueToInvalidUsername() throws Exception {
        when(githubService.getGithubUserInfo("some-user"))
                .thenReturn(TestUtils.generateGithubUser());

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", "--bad-user"))
                .andExpect(status().is4xxClientError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessages[0]").value(TestUtils.INVALID_FORMAT_USERNAME));

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", "bad--user"))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessages[0]").value(TestUtils.INVALID_FORMAT_USERNAME));

    }

    @Test
    void shouldThrowErrorMessageDueToUsernameBeingTooLong() throws Exception {
        when(githubService.getGithubUserInfo("some-user"))
                .thenReturn(TestUtils.generateGithubUser());

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessages[0]").value(TestUtils.INVALID_LENGTH_USERNAME));

    }

    @Test
    void shouldThrowErrorMessageWhenUserIsNotFound() throws Exception {
        when(githubService.getGithubUserInfo("not-found-user"))
                .thenThrow(HttpClientErrorException.class);

        mockMvc.perform(MockMvcRequestBuilders
                        .get("http://localhost:8081/user/{username}", "not-found-user"))
                .andExpect(status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessages[0]").value(TestUtils.USER_NOT_FOUND));

    }
}
