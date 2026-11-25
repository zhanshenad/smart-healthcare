package com.atguigu.java.ai.langchain4j.vo;

import java.util.List;

public record ModelOptionsResponse(String defaultModelId, List<ModelOption> models) {
}
