package tech.buildrun.notebooklm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import tech.buildrun.notebooklm.converter.VectorConverter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_source_chunks")
public class SourceChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false)
    private String content;

    @Convert(converter = VectorConverter.class)
    @ColumnTransformer(write = "?::vector")
    @Column(nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SourceChunk() {
    }

    public SourceChunk(Source source, String content, float[] embedding, int chunkIndex, String embeddingModel) {
        this.source = source;
        this.content = content;
        this.embedding = embedding;
        this.chunkIndex = chunkIndex;
        this.embeddingModel = embeddingModel;
    }

    public UUID getId() {
        return id;
    }

    public Source getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
