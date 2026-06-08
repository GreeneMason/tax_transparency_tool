package com.govlens.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated API response envelope.
 * Provides pagination metadata and stable sorting guarantees.
 */
public class PaginatedResponse<T> {
    @JsonProperty("data")
    private List<T> data;

    @JsonProperty("pagination")
    private PaginationMetadata pagination;

    public PaginatedResponse(List<T> data, int limit, int offset, long totalCount) {
        this.data = data;
        this.pagination = new PaginationMetadata(limit, offset, totalCount);
    }

    public List<T> getData() {
        return data;
    }

    public PaginationMetadata getPagination() {
        return pagination;
    }

    public static class PaginationMetadata {
        @JsonProperty("limit")
        private int limit;

        @JsonProperty("offset")
        private int offset;

        @JsonProperty("total_count")
        private long totalCount;

        @JsonProperty("has_more")
        private boolean hasMore;

        public PaginationMetadata(int limit, int offset, long totalCount) {
            this.limit = limit;
            this.offset = offset;
            this.totalCount = totalCount;
            this.hasMore = offset + limit < totalCount;
        }

        public int getLimit() { return limit; }
        public int getOffset() { return offset; }
        public long getTotalCount() { return totalCount; }
        public boolean isHasMore() { return hasMore; }
    }
}
