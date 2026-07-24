package com.example.job_matchwer.resume;

import com.example.job_matchwer.auth.User;
import com.example.job_matchwer.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "resumes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_resumes_user_file_hash",
                columnNames = {"user_id", "file_hash"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Resume extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name="user_id",nullable = false)
    private User user;

    @Column(name="original_filename",nullable = false, length = 255)
    private String originalFileName;

    @Column(name="storage_path",nullable = false,length = 512)
    private String storagePath;

    @Column(name="file_hash",nullable = false,length = 64)
    private String fileHash;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResumeStatus status = ResumeStatus.UPLOADED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private String parsedData;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    public Resume(User user, String originalFileName, String storagePath, String fileHash, long fileSizeBytes, int version) {

        this.user = user;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.fileHash = fileHash;
        this.fileSizeBytes = fileSizeBytes;
        this.version = version;
    }

    public void markParsing() {
        this.status = ResumeStatus.PARSING;
        this.parseError = null;
    }

    public void markParsed(String parsedData) {
        this.status = ResumeStatus.PARSED;
        this.parsedData = parsedData;
        this.parseError = null;
    }

    public void markFailed(String error) {
        this.status = ResumeStatus.FAILED;
        this.parseError = error != null && error.length() > 1000
                ? error.substring(0, 1000)
                : error;
    }
}
