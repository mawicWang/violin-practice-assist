package com.violin.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "scores")
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String originalImagePath;

    private String musicXmlPath;

    private LocalDateTime createdAt;

    private LocalDateTime lastPlayedAt;

    private String tags;

    private String status;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
