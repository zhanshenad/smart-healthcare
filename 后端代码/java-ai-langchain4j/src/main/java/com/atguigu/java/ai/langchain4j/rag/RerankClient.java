package com.atguigu.java.ai.langchain4j.rag;

import java.util.List;

public interface RerankClient {

    List<Double> score(String query, List<String> documents);
}
