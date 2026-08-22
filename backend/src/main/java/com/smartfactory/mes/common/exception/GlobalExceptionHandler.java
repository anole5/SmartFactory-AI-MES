package com.smartfactory.mes.common.exception;

import com.smartfactory.mes.common.api.ApiResult;
import com.smartfactory.mes.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一把异常翻译成 ApiResult 结构，HTTP 状态码与业务 code 对齐
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：HTTP 状态与业务 code 对齐（409 业务冲突 / 401 登录失败 / 403 无权限等）。
     *  第 1 周写死 @ResponseStatus(CONFLICT)，第 2 周登录失败（401）后改为动态映射 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        HttpStatus status = HttpStatus.resolve(e.getResultCode().getCode());
        if (status == null) {
            status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status).body(ApiResult.error(e.getResultCode(), e.getMessage()));
    }

    /** 请求体 @Valid 校验失败：返回 400 与第一个字段错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return ApiResult.error(ResultCode.PARAM_ERROR, message);
    }

    /** 表单绑定校验失败（GET 参数） */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return ApiResult.error(ResultCode.PARAM_ERROR, message);
    }

    /** 请求体 JSON 解析失败（如日期格式不符 yyyy-MM-dd HH:mm:ss）：属于客户端传参错误，应 400 而非 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ApiResult.error(ResultCode.PARAM_ERROR, "请求参数格式错误: " + e.getMostSpecificCause().getMessage());
    }

    /** 未知路径：Spring Boot 3.2+ 走 NoResourceFoundException，返回统一 404 结构 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ApiResult.error(ResultCode.NOT_FOUND, "接口不存在: " + e.getResourcePath());
    }

    /** 兜底：未预期异常，记全栈日志，不把细节暴露给前端 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResult.error(ResultCode.SERVER_ERROR, ResultCode.SERVER_ERROR.getMessage());
    }
}
