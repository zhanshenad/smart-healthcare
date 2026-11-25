package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_model_keys")
public class UserModelKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String provider;

    private String encryptedKey;

    private Integer keyVersion;

    private String keyMasked;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
