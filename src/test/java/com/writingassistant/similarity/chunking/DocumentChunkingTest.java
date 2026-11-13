package com.writingassistant.similarity.chunking;

import com.writingassistant.similarity.onnx.DeterministicEmbeddingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkingTest {

    private SentenceAwareChunkingStrategy chunker;
    private DeterministicEmbeddingEngine engine;
    private DocumentEmbeddingPipeline pipeline;

    private static final String ACADEMIC_PROSE =
            "Deep neural networks have revolutionized computer vision, natural language processing, and medical diagnostics. "
            + "Recent studies (e.g., LeCun et al., 2015) demonstrate superhuman performance across complex visual tasks. "
            + "Furthermore, transformer architectures (Vaswani et al., 2017) eliminated the recurrence bottleneck entirely. "
            + "In our experiments, the learning rate was set to 0.001 with an AdamW optimizer (see Fig. 2 for loss curves). "
            + "The resulting model achieved an accuracy of 94.2% on the validation set after 50 epochs.\n\n"
            + "Section 2 describes the dataset acquisition and preprocessing methodology in detail. "
            + "We sampled 15,000 peer-reviewed journal articles across biomedical informatics and computer science. "
            + "Each document was extracted and normalized using our unified document processing pipeline. "
            + "Data quality metrics indicated high text density and minimal OCR noise (cf. Prof. Smith's benchmark). "
            + "Statistical significance was confirmed with p < 0.01 across all evaluation partitions.";

    @BeforeEach
    void setUp() {
        chunker = new SentenceAwareChunkingStrategy();
        engine = new DeterministicEmbeddingEngine();
        pipeline = new DocumentEmbeddingPipeline(engine, chunker, 2); // small batch size to test batching
    }

    @Test
    void chunkingPreservesExactCharacterOffsetsAcrossAllChunks() {
        ChunkingOptions options = new ChunkingOptions(30, 10, 60, 1, true);
        List<DocumentChunk> chunks = chunker.chunk("doc-1", ACADEMIC_PROSE, options);

        assertFalse(chunks.isEmpty(), "Chunks must not be empty");
        assertTrue(chunks.size() >= 2, "Document should be partitioned into multiple chunks");

        for (DocumentChunk chunk : chunks) {
            assertTrue(chunk.startOffset() >= 0, "startOffset must be non-negative");
            assertTrue(chunk.endOffset() <= ACADEMIC_PROSE.length(), "endOffset must not exceed document length");
            assertTrue(chunk.endOffset() > chunk.startOffset(), "endOffset must be greater than startOffset");

            // Absolute invariant: text in chunk MUST exactly equal original document substring
            String originalSubstring = ACADEMIC_PROSE.substring(chunk.startOffset(), chunk.endOffset());
            assertEquals(originalSubstring, chunk.text(),
                    "Chunk text must be bit-exact match with original document substring at start..end offsets");

            assertEquals("doc-1", chunk.documentId());
            assertTrue(chunk.sentenceCount() >= 1);
            assertTrue(chunk.tokenCount() >= 1);
        }
    }

    @Test
    void chunkingRespectsAcademicAbbreviationsWithoutPrematureSplitting() {
        String abbrText = "The method proposed by Vaswani et al. achieved state of the art results. "
                + "For instance, e.g. when applied to English-to-German translation, BLEU scores rose significantly. "
                + "Refer to Fig. 1 for details on page 4 (see pp. 12-14 in vol. 3). "
                + "The convergence rate was approximately 3.14 times faster than previous approaches.";

        ChunkingOptions options = new ChunkingOptions(100, 20, 200, 0, false);
        List<DocumentChunk> chunks = chunker.chunk("abbr-doc", abbrText, options);

        assertEquals(1, chunks.size(), "All sentences should fit in one chunk without breaking at abbreviations");
        DocumentChunk chunk = chunks.get(0);
        assertEquals(abbrText.strip(), chunk.text());
        assertEquals(4, chunk.sentenceCount(), "Abbreviations should not artificially inflate sentence count");
    }

    @Test
    void chunkingRespectsParagraphBoundariesWhenEnabled() {
        ChunkingOptions options = new ChunkingOptions(80, 20, 200, 0, true);
        List<DocumentChunk> chunks = chunker.chunk("para-doc", ACADEMIC_PROSE, options);

        assertTrue(chunks.size() >= 2);
        // First chunk should not spill into Section 2
        DocumentChunk firstChunk = chunks.get(0);
        assertFalse(firstChunk.text().contains("Section 2"), "First chunk should respect paragraph break before Section 2");
    }

    @Test
    void chunkingAppliesSlidingWindowSentenceOverlap() {
        // overlap = 1 sentence
        ChunkingOptions options = new ChunkingOptions(30, 10, 60, 1, false);
        List<DocumentChunk> chunks = chunker.chunk("overlap-doc", ACADEMIC_PROSE, options);

        assertTrue(chunks.size() >= 3);
        // Consecutive chunks should share text from the overlapping sentence
        for (int i = 0; i < chunks.size() - 1; i++) {
            DocumentChunk current = chunks.get(i);
            DocumentChunk next = chunks.get(i + 1);

            // With overlap > 0, next startOffset should be strictly less than current endOffset
            assertTrue(next.startOffset() < current.endOffset(),
                    "Next chunk should overlap with current chunk: current end="
                            + current.endOffset() + ", next start=" + next.startOffset());
        }
    }

    @Test
    void chunkingHandlesEmptyAndEdgeCaseDocuments() {
        assertTrue(chunker.chunk("empty", "", ChunkingOptions.defaults()).isEmpty());
        assertTrue(chunker.chunk("null", null, ChunkingOptions.defaults()).isEmpty());
        assertTrue(chunker.chunk("spaces", "   \n\t   ", ChunkingOptions.defaults()).isEmpty());

        // Short single sentence
        String shortText = "A single isolated academic statement.";
        List<DocumentChunk> shortChunks = chunker.chunk("short", shortText, ChunkingOptions.defaults());
        assertEquals(1, shortChunks.size());
        assertEquals(shortText, shortChunks.get(0).text());
        assertEquals(0, shortChunks.get(0).startOffset());
        assertEquals(shortText.length(), shortChunks.get(0).endOffset());
    }

    @Test
    void documentEmbeddingPipelineGeneratesTraceableEmbeddingsInBatches() {
        ChunkingOptions options = new ChunkingOptions(30, 10, 60, 1, true);
        List<EmbeddedChunk> embeddedChunks = pipeline.embedDocument("doc-pipeline", ACADEMIC_PROSE, options);

        assertFalse(embeddedChunks.isEmpty());
        assertEquals("deterministic-minilm-384", pipeline.getEmbeddingEngine().getModelName());
        assertEquals(2, pipeline.getBatchSize());

        for (EmbeddedChunk ec : embeddedChunks) {
            assertEquals("doc-pipeline", ec.documentId());
            assertEquals(384, ec.dimensions());
            assertEquals(384, ec.embedding().length);
            assertEquals("deterministic-minilm-384", ec.modelName());
            assertTrue(ec.generatedAtMillis() > 0);

            // Unit vector check
            float sumSq = 0.0f;
            for (float v : ec.embedding()) sumSq += v * v;
            assertEquals(1.0f, sumSq, 1e-4f, "Embedding must be L2 normalized to unit length");

            // Text fidelity
            assertEquals(ACADEMIC_PROSE.substring(ec.startOffset(), ec.endOffset()), ec.text());
        }
    }

    @Test
    void documentEmbeddingPipelineFindsSemanticMatchesAcrossChunks() {
        String targetText = "Transformer neural architectures eliminated recurrence and achieved remarkable results in natural language.";
        String sourceTextA = "Transformers completely removed recurrent units, establishing new benchmarks in language processing.";
        String sourceTextB = "Medieval agricultural tools and crop rotation systems during the English feudal era.";

        List<EmbeddedChunk> targets = pipeline.embedDocument("target-1", targetText, ChunkingOptions.defaults());
        List<EmbeddedChunk> sourceA = pipeline.embedDocument("source-ai", sourceTextA, ChunkingOptions.defaults());
        List<EmbeddedChunk> sourceB = pipeline.embedDocument("source-history", sourceTextB, ChunkingOptions.defaults());

        List<EmbeddedChunk> corpus = new java.util.ArrayList<>(sourceA);
        corpus.addAll(sourceB);

        List<ChunkMatch> matches = pipeline.findMatches(targets, corpus, 0.4);
        assertFalse(matches.isEmpty(), "Should find matches above threshold 0.4");

        ChunkMatch topMatch = matches.get(0);
        assertEquals("source-ai", topMatch.sourceChunk().documentId(), "Top semantic match should be AI transformer text");
        assertTrue(topMatch.score() > 0.5, "Cosine similarity should be high for paraphrased transformer prose");
    }
}
