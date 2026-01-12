package com.tevore.controller;

import com.tevore.domain.GithubUser;
import com.tevore.service.GithubService;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Validated
public class GithubController {

    private final GithubService githubService;

    @Autowired
    public GithubController(GithubService githubService) {
        this.githubService = githubService;
    }


    /**
     * Github has some pretty good error handling now based on some fast
     * sample tests, but we should play the defensive game as well
     * --
     * We will use their basic requirements to sanitize input:
     * Characters: May only contain alphanumeric characters (letters A-Z, numbers 0-9) or single hyphens (-).
     * Hyphen Usage:
     * --
     *     Cannot begin or end with a hyphen.
     *     Cannot contain multiple consecutive hyphens (e.g., user--name is invalid).
     * --
     * Length: Must be a maximum of 39 characters long.
     */
    @GetMapping(value = "/user/{username}")
    public GithubUser retrieveGithubUser(
            @PathVariable("username")
            @Pattern(regexp = "^[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*$", message = "Usernames can only contain alphanumerics and single hyphens")
            @Size(min = 1, max = 39, message = "Usernames are between 1 and 39 characters")
            String username) {
        return githubService.getGithubUserInfo(username);
    }
}
