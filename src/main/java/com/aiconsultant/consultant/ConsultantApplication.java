package com.aiconsultant.consultant;

import com.aiconsultant.consultant.config.NoteGradingFallbackProperties;
import com.aiconsultant.consultant.config.NoteGradingFeedbackProperties;
import com.aiconsultant.consultant.config.NoteGradingAuditProperties;
import com.aiconsultant.consultant.config.NoteGradingPoolProperties;
import com.aiconsultant.consultant.config.NoteGradingProtocolProperties;
import com.aiconsultant.consultant.rag.coalesce.RagVectorRecallCoalesceProperties;
import com.aiconsultant.consultant.config.NoteGradingRetryProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.aiconsultant.consultant.mapper")
@EnableConfigurationProperties({
        NoteGradingPoolProperties.class,
        NoteGradingRetryProperties.class,
        NoteGradingFallbackProperties.class,
        NoteGradingFeedbackProperties.class,
        NoteGradingAuditProperties.class,
        NoteGradingProtocolProperties.class,
        RagVectorRecallCoalesceProperties.class
})
public class ConsultantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultantApplication.class, args);
    }

}
