package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.ApprovalTask;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    @Select("select * from approval_task order by created_at desc, id desc")
    List<ApprovalTask> findAllOrderByCreatedAtDesc();

    @Select("select * from approval_task where ticket_id = #{ticketId} order by created_at asc, id asc")
    List<ApprovalTask> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Select("select * from approval_task where approval_id = #{approvalId} limit 1")
    ApprovalTask findByApprovalId(String approvalId);

    @Select("""
            select * from approval_task
            where ticket_id = #{ticketId}
              and plan_id = #{planId}
              and status = 'PENDING'
            order by created_at desc, id desc
            limit 1
            """)
    ApprovalTask findPendingByTicketIdAndPlanId(String ticketId, String planId);

    @Delete("delete from approval_task")
    void deleteAllRecords();
}
