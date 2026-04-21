package com.aiconsultant.consultant.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import com.aiconsultant.consultant.entity.FileMetadata;
import com.aiconsultant.consultant.entity.RagDocument;
import com.aiconsultant.consultant.service.FileUploadService;
import com.aiconsultant.consultant.service.RagDocumentService;
import com.aiconsultant.consultant.service.RagIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 启动后：1）扫描配置的 classpath 资源，对尚未 RAG 成功的内容触发入库；
 * 2）扫描 file_metadata，对已上传磁盘但 RAG 未成功的记录补跑。
 */
@Slf4j
@Component
@Order(50)
public class RagStartupScanRunner implements ApplicationRunner {

    @Value("${app.rag.startup-scan-enabled:false}")
    private boolean startupScanEnabled;
    @Value("${app.rag.startup-scan-patterns:classpath:content/*.pdf}")
    private String startupScanPatterns;
    @Value("${app.rag.startup-repair-uploads-enabled:false}")
    private boolean repairUploadsEnabled;
    @Value("${app.rag.startup-system-user-id:-1}")
    private Long startupSystemUserId;

    @Autowired
    private RagIngestionService ragIngestionService;
    @Autowired
    private RagDocumentService ragDocumentService;
    @Autowired
    private FileUploadService fileUploadService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (startupScanEnabled) {
            scanClasspathResources();
        } else {
            log.info("app.rag.startup-scan-enabled=false，跳过 classpath RAG 扫描");
        }
        if (repairUploadsEnabled) {
            repairUploadedFiles();
        } else {
            log.info("app.rag.startup-repair-uploads-enabled=false，跳过 file_metadata 补跑");
        }
    }

    private void scanClasspathResources() {
        Path workDir = Paths.get(System.getProperty("java.io.tmpdir"), "consultant-rag-scan");
        try {
            Files.createDirectories(workDir);
        } catch (Exception e) {
            log.error("无法创建工作目录 {}", workDir, e);
            return;
        }

        List<String> patterns = Arrays.stream(startupScanPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String pattern : patterns) {
            try {
                Resource[] resources = resolver.getResources(pattern);
                log.info("RAG 启动扫描 pattern={} 命中 {} 个资源", pattern, resources.length);
                for (Resource resource : resources) {
                    if (!resource.exists() || !resource.isReadable()) {
                        continue;
                    }
                    String filename = resource.getFilename() != null ? resource.getFilename() : "document.bin";
                    Path temp = Files.createTempFile(workDir, "staged-", "-" + sanitizeFilename(filename));
                    try (InputStream in = resource.getInputStream()) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    String md5 = SecureUtil.md5(Files.newInputStream(temp));
                    if (isRagSuccessful(md5)) {
                        Files.deleteIfExists(temp);
                        log.debug("RAG 已存在且成功，跳过: {} md5={}", filename, md5);
                        continue;
                    }
                    String ext = FileUtil.extName(filename).toLowerCase(Locale.ROOT);
                    Path permanent = workDir.resolve(md5 + (ext.isEmpty() ? "" : "." + ext));
                    Files.move(temp, permanent, StandardCopyOption.REPLACE_EXISTING);
                    log.info("RAG 启动补录: {} -> md5={}, path={}", filename, md5, permanent);
                    ragIngestionService.scheduleIngestIfNeeded(
                            md5,
                            startupSystemUserId,
                            permanent.toAbsolutePath().toString(),
                            filename,
                            ext.isEmpty() ? FileUtil.extName(permanent.toString()) : ext
                    );
                }
            } catch (Exception e) {
                log.error("RAG 启动扫描失败 pattern={}", pattern, e);
            }
        }
    }

    private void repairUploadedFiles() {
        List<FileMetadata> all;
        try {
            all = fileUploadService.list();
        } catch (Exception e) {
            log.warn("读取 file_metadata 失败，跳过补跑", e);
            return;
        }
        if (all == null || all.isEmpty()) {
            log.info("file_metadata 为空，无需 RAG 补跑");
            return;
        }
        int n = 0;
        for (FileMetadata fm : all) {
            if (fm.getFileMd5() == null || fm.getFileMd5().isBlank()) {
                continue;
            }
            if (isRagSuccessful(fm.getFileMd5())) {
                continue;
            }
            String path = fm.getFilePath();
            if (path == null || path.isBlank()) {
                continue;
            }
            File f = new File(path);
            if (!f.isFile()) {
                log.warn("RAG 补跑跳过：文件不存在 id={} path={}", fm.getId(), path);
                continue;
            }
            String name = fm.getFileName() != null ? fm.getFileName() : f.getName();
            String ext = fm.getFileSuffix() != null ? fm.getFileSuffix() : FileUtil.extName(name);
            log.info("RAG 补跑已上传记录: fileMetadataId={} md5={} path={}", fm.getId(), fm.getFileMd5(), path);
            ragIngestionService.scheduleIngestIfNeeded(
                    fm.getFileMd5(),
                    fm.getUserId() != null ? fm.getUserId() : startupSystemUserId,
                    f.getAbsolutePath(),
                    name,
                    ext
            );
            n++;
        }
        log.info("RAG file_metadata 补跑调度完成，共触发 {} 条（含已跳过/幂等）", n);
    }

    private boolean isRagSuccessful(String md5) {
        Optional<RagDocument> opt = ragDocumentService.findByContentHash(md5);
        return opt.isPresent() && Objects.equals(1, opt.get().getParseStatus());
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
