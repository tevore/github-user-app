package com.example.demo.service;

import com.example.demo.domain.GithubUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class GithubService {

    /**
     * TODO
     *  review to ensure the caching is okay - LAST ( tests may also cover this )
     *  consider rate limits? -- this would be improved via github app, but caching kind of works
     *  possibly set this up to use Docker to launch ( might be overkill but hey )
     */
    private final RestClient restClient;

    @Value("${github.url}")
    private String baseUrl;

    public GithubService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Cacheable("githubUsers")
    public GithubUser getGithubUserInfo(String username) {

        if(username == null) {
            throw new HttpClientErrorException(HttpStatusCode.valueOf(404), "Username cannot be null");
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .buildAndExpand(username)
                .encode()
                .toUri();

        return restClient
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        (req, res) -> {throw new HttpClientErrorException(res.getStatusCode());})
                .onStatus(HttpStatusCode::is5xxServerError,
                        (req, res) -> {throw new HttpServerErrorException(res.getStatusCode());})
                .body(GithubUser.class);
    }
}
