package com.atguigu.java.ai.langchain4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("department_schedule")
public class DepartmentSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String department;
    private String date;
    private String time;
    private Integer total;
    private Integer booked;
}
