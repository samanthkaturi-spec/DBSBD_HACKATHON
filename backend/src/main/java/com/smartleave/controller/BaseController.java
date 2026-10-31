package com.smartleave.controller;

import com.smartleave.exception.ApiException;
import com.smartleave.security.LoggedInUser;

/** Shared helpers: identity always comes from the authenticated principal. */
public abstract class BaseController {

    protected Long requireUserId(LoggedInUser principal) {
        if (principal == null || principal.userId() == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return principal.userId();
    }

    protected int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    protected int size(Integer size, int fallback) {
        if (size == null || size <= 0) {
            return fallback;
        }
        return Math.min(size, 100);
    }
}
