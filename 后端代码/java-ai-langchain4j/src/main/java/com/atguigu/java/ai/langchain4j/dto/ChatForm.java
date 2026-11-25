package com.atguigu.java.ai.langchain4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatForm {

    @NotBlank(message = "memoryId不能为空")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "memoryId格式不正确")
    private String memoryId;//对话id

    @NotBlank(message = "消息不能为空")
    private String message;//用户问题

    @Size(max = 64, message = "modelId过长")
    private String modelId;//选择的模型，为空时使用默认模型
}
