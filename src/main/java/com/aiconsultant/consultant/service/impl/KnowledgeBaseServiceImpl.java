package com.aiconsultant.consultant.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.KnowledgeBase;
import com.aiconsultant.consultant.mapper.KnowledgeBaseMapper;
import com.aiconsultant.consultant.service.KnowledgeBaseService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@Slf4j
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private EmbeddingStore<TextSegment> redisEmbeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Override
    public void initGlobalKnowledge() {
        try {
            // 1. 扫描 resources/content 目录下的所有 PDF 文件
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:content/*.pdf");

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                // 2. 使用 Hutool 计算流的 MD5
                String currentHash = SecureUtil.md5(resource.getInputStream());

                // 3. 检查数据库记录 (userId = -1 为全局资料)
                KnowledgeBase record = this.getOne(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getFileName, fileName)
                        .eq(KnowledgeBase::getUserId, -1L));

                // 4. 增量判断：只有 MD5 不一致或记录不存在时才处理
                if (record != null && record.getFileHash().equals(currentHash)) {
                    log.info("知识库文件 [{}] 内容未变更，跳过向量化。", fileName);
                    continue;
                }

                log.info("发现新文件或变更文件: [{}]，准备存入向量数据库...", fileName);
                // 5. 执行解析与入库
                processAndIngest(resource, fileName, currentHash, -1L, record);
            }
        } catch (IOException e) {
            log.error("扫描全局知识库资源失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAndIngest(Resource resource, String fileName, String hash, Long userId, KnowledgeBase oldRecord) {
        try {
            // 1. 解析 PDF 文档
            Document document = new ApachePdfBoxDocumentParser().parse(resource.getInputStream());

            // 2. 注入元数据 (这是后续按 userId 检索的关键)
            document.metadata().put("user_id", userId);
            document.metadata().put("file_name", fileName);

            // 3. 向量化并存储到 Redis
            DocumentSplitter ds = DocumentSplitters.recursive(500, 100);
            EmbeddingStoreIngestor.builder()
                    .embeddingStore(redisEmbeddingStore)
                    .documentSplitter(ds)
                    .embeddingModel(embeddingModel)
                    .build()
                    .ingest(document);

            // 4. 同步更新数据库状态
            if (oldRecord == null) {
                KnowledgeBase newRecord = new KnowledgeBase()
                        .setFileName(fileName)
                        .setFileHash(hash)
                        .setUserId(userId);
                this.save(newRecord);
            } else {
                oldRecord.setFileHash(hash);
                this.updateById(oldRecord);
            }
            log.info("文件 [{}] 向量化及元数据同步完成。", fileName);
        } catch (Exception e) {
            log.error("处理文件 [{}] 时发生异常", fileName, e);
            throw new RuntimeException("文件向量化失败", e);
        }
    }
}
