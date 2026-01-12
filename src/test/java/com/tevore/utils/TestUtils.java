package com.tevore.utils;

import com.tevore.domain.GithubUser;

public class TestUtils {

    public static final String INVALID_PATH_OR_MISSING_USERNAME = "Username required or path is incorrect";
    public static final String INVALID_FORMAT_USERNAME = "Usernames can only contain alphanumerics and single hyphens";
    public static final String INVALID_LENGTH_USERNAME = "Usernames are between 1 and 39 characters";
    public static final String SERVICE_ERROR = "Service error detected";
    public static final Object USER_NOT_FOUND = "User not found";

    public static GithubUser generateGithubUser() {
        return new GithubUser(1L, "some-user", "t", "t", "t", "t",
                "t", "t", "t", "t", "Some User", "t", "t", "t", true, "t",
                "t", "t", "t", "t", "t", "t", "t", "t", "t",
                "t", "t", 1, 0, 12, 40, "t", "t");
    }
}
