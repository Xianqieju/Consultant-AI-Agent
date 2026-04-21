package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.mapper.UserExpertPermissionMapper;
import com.aiconsultant.consultant.notegrading.access.AllowAllExpertAccessPolicy;
import com.aiconsultant.consultant.notegrading.access.DatabaseExpertAccessPolicy;
import com.aiconsultant.consultant.notegrading.access.ExpertAccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoteGradingExpertAccessConfig {

    @Bean
    public ExpertAccessPolicy expertAccessPolicy(
            @Value("${app.note-grading.expert-permission-db-enabled:false}") boolean dbEnabled,
            UserExpertPermissionMapper userExpertPermissionMapper
    ) {
        if (dbEnabled) {
            return new DatabaseExpertAccessPolicy(userExpertPermissionMapper);
        }
        return new AllowAllExpertAccessPolicy();
    }
}
