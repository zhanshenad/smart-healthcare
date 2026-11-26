package com.atguigu.java.ai.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.DepartmentSchedule;
import com.atguigu.java.ai.langchain4j.mapper.DepartmentScheduleMapper;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppointmentConcurrencyTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DepartmentScheduleMapper scheduleMapper;

    @Test
    void shouldCreateOnlyOneAppointmentUnderConcurrency() throws Exception {
        Long userId = 999999L;
        String department = "并发测试科";
        String date = "2026-12-31";
        String time = "上午";
        clean(userId);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookingResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return appointmentService.bookIfAbsent(appointment(userId, department, date, time));
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        long success = 0;
        for (Future<BookingResult> future : futures) {
            if (future.get(10, TimeUnit.SECONDS) == BookingResult.SUCCESS) {
                success++;
            }
        }
        executor.shutdown();

        long count = appointmentService.count(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .eq(Appointment::getDepartment, department)
                .eq(Appointment::getDate, date)
                .eq(Appointment::getTime, time));

        assertThat(success).isEqualTo(1);
        assertThat(count).isEqualTo(1);
        clean(userId);
    }

    @Test
    void shouldNotCancelOtherUsersAppointment() {
        Long ownerId = 999998L;
        Long otherId = 999997L;
        clean(ownerId);
        clean(otherId);

        Appointment appointment = appointment(ownerId, "越权测试科", "2026-12-30", "下午");
        assertThat(appointmentService.bookIfAbsent(appointment)).isEqualTo(BookingResult.SUCCESS);

        Appointment other = appointment(otherId, "越权测试科", "2026-12-30", "下午");
        assertThat(appointmentService.cancelIfOwned(other)).isFalse();

        long count = appointmentService.count(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, ownerId));
        assertThat(count).isEqualTo(1);
        clean(ownerId);
    }

    @Test
    void shouldRespectSlotCapacity() throws Exception {
        String department = "号源测试科";
        String date = "2026-12-29";
        String time = "上午";
        Long userA = 999996L;
        Long userB = 999995L;
        clean(userA);
        clean(userB);
        scheduleMapper.delete(new LambdaQueryWrapper<DepartmentSchedule>()
                .eq(DepartmentSchedule::getDepartment, department)
                .eq(DepartmentSchedule::getDate, date)
                .eq(DepartmentSchedule::getTime, time));

        DepartmentSchedule schedule = new DepartmentSchedule();
        schedule.setDepartment(department);
        schedule.setDate(date);
        schedule.setTime(time);
        schedule.setTotal(1);
        schedule.setBooked(0);
        scheduleMapper.insert(schedule);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<BookingResult> first = executor.submit(() -> {
            start.await();
            return appointmentService.bookIfAbsent(appointment(userA, department, date, time));
        });
        Future<BookingResult> second = executor.submit(() -> {
            start.await();
            return appointmentService.bookIfAbsent(appointment(userB, department, date, time));
        });
        start.countDown();

        List<BookingResult> results = List.of(
                first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertThat(results)
                .containsExactlyInAnyOrder(BookingResult.SUCCESS, BookingResult.NO_SLOT);
        assertThat(appointmentService.count(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getDepartment, department)
                .eq(Appointment::getDate, date)
                .eq(Appointment::getTime, time))).isEqualTo(1);
        assertThat(scheduleMapper.findBySlot(department, date, time).getBooked()).isEqualTo(1);

        clean(userA);
        clean(userB);
        scheduleMapper.deleteById(schedule.getId());
    }

    @Test
    void shouldCancelWithoutUsernameAndIdCard() {
        Long userId = 999994L;
        clean(userId);
        Appointment booking = appointment(userId, "取消测试科", "2026-12-28", "上午");
        assertThat(appointmentService.bookIfAbsent(booking)).isEqualTo(BookingResult.SUCCESS);

        Appointment cancel = new Appointment();
        cancel.setUserId(userId);
        cancel.setDepartment("取消测试科");
        cancel.setDate("2026-12-28");
        cancel.setTime("上午");

        assertThat(appointmentService.cancelIfOwned(cancel)).isTrue();
        assertThat(appointmentService.count(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId))).isZero();
    }

    private Appointment appointment(Long userId, String department, String date, String time) {
        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setUsername("并发测试");
        appointment.setIdCard("110101199001011234");
        appointment.setDepartment(department);
        appointment.setDate(date);
        appointment.setTime(time);
        return appointment;
    }

    private void clean(Long userId) {
        appointmentService.remove(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId));
    }
}
