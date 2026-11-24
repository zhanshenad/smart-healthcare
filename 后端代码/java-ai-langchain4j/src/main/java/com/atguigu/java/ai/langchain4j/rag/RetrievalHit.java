package com.atguigu.java.ai.langchain4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalHit {

    private String id;

    private String text;

    private String docKey;

    private String docHash;

    private String source;

    private String title;

    private String docType;

    private String department;

    private int chunkIndex;

    private String contentHash;

    private String structureQuality;

    private String parser;

    private String outlinePath;

    private String parentId;

    private String role;

    private String strategy;

    private double denseScore;

    private double sparseScore;

    private int denseRank;

    private int sparseRank;

    private double rrfScore;

    private double rerankScore;

    private boolean reranked;

    private double finalScore;
}
