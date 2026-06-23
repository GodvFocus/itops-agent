package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.ConversationMessage;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
    @Select("select * from conversation_message where ticket_id = #{ticketId} order by created_at asc")
    List<ConversationMessage> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Select("select * from conversation_message where ticket_id = #{ticketId} order by created_at desc limit 6")
    List<ConversationMessage> findTop6ByTicketIdOrderByCreatedAtDesc(String ticketId);

    @Select("select * from conversation_message where ticket_id = #{ticketId} order by created_at desc limit 1")
    ConversationMessage findTopByTicketIdOrderByCreatedAtDesc(String ticketId);

    @Delete("delete from conversation_message")
    void deleteAllRecords();
}
