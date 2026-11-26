package com.atguigu.java.ai.langchain4j.mapper;

import com.atguigu.java.ai.langchain4j.entity.DepartmentSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentScheduleMapper extends BaseMapper<DepartmentSchedule> {

    @Select("SELECT * FROM department_schedule "
            + "WHERE department = #{department} AND `date` = #{date} AND `time` = #{time} LIMIT 1")
    DepartmentSchedule findBySlot(@Param("department") String department,
                                  @Param("date") String date,
                                  @Param("time") String time);

    // 原子占用号源
    @Update("UPDATE department_schedule SET booked = booked + 1 "
            + "WHERE department = #{department} AND `date` = #{date} AND `time` = #{time} "
            + "AND booked < total")
    int reserve(@Param("department") String department,
                @Param("date") String date,
                @Param("time") String time);

    @Update("UPDATE department_schedule SET booked = booked - 1 "
            + "WHERE department = #{department} AND `date` = #{date} AND `time` = #{time} "
            + "AND booked > 0")
    int release(@Param("department") String department,
                @Param("date") String date,
                @Param("time") String time);
}
