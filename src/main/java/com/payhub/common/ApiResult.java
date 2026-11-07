package com.payhub.common;

/**
 * 统一返回体。
 *
 * <p>用 record 而不是 Lombok @Data 的类：DTO/返回体是不可变数据结构，
 * record 自带构造、getter、equals、hashCode，少 60 行样板代码。
 * （实体类不能用 record，因为 MyBatis-Plus 需要无参构造 + setter。）
 */
public record ApiResult<T>(String code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>("0000", "成功", data);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
