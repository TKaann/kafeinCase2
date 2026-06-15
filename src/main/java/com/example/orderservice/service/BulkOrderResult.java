package com.example.orderservice.service;

/**
 * Independent outcome of a single order within a bulk request. One failed order produces a
 * FAILED result with its reason; it never aborts the others.
 *
 * @param index   position of the order in the submitted list, so the caller can correlate
 * @param status  SUCCESS or FAILED
 * @param orderId persisted order id when successful, otherwise {@code null}
 * @param error   failure reason when failed, otherwise {@code null}
 */
public record BulkOrderResult(int index, Status status, Long orderId, String error) {

    public enum Status {SUCCESS, FAILED}

    public static BulkOrderResult success(int index, Long orderId) {
        return new BulkOrderResult(index, Status.SUCCESS, orderId, null);
    }

    public static BulkOrderResult failed(int index, String error) {
        return new BulkOrderResult(index, Status.FAILED, null, error);
    }
}
