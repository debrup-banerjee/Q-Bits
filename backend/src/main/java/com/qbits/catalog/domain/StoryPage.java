package com.qbits.catalog.domain;

import java.util.List;

/** One page of stories and the cursor for the next page, if there is one. */
public record StoryPage(List<StoryView> data, String nextCursor) {}
