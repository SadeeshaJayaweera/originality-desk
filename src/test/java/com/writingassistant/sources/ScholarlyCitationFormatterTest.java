package com.writingassistant.sources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScholarlyCitationFormatterTest {

    @Test
    void formatsApaWithSingleAuthorAndDoi() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W12345")
                .title("Attention Is All You Need")
                .authors(List.of("Ashish Vaswani"))
                .publicationYear(2017)
                .venueOrJournal("NeurIPS")
                .doi("10.48550/arXiv.1706.03762")
                .landingPageUrl("https://arxiv.org/abs/1706.03762")
                .build();

        String apa = ScholarlyCitationFormatter.formatApa(work);
        assertTrue(apa.contains("Vaswani, A."));
        assertTrue(apa.contains("(2017)."));
        assertTrue(apa.contains("Attention Is All You Need."));
        assertTrue(apa.contains("NeurIPS."));
        assertTrue(apa.contains("https://doi.org/10.48550/arxiv.1706.03762"));
    }

    @Test
    void formatsApaWithMultipleAuthorsAndFallbackUrl() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("crossref")
                .externalId("CR999")
                .title("Deep Residual Learning for Image Recognition")
                .authors(List.of("Kaiming He", "Xiangyu Zhang", "Shaoqing Ren", "Jian Sun"))
                .publicationYear(2016)
                .venueOrJournal("CVPR")
                .landingPageUrl("https://openaccess.thecvf.com/content_cvpr_2016/html/He_Deep_Residual_Learning_2016_CVPR_paper.html")
                .build();

        String apa = ScholarlyCitationFormatter.formatApa(work);
        assertTrue(apa.contains("He, K., Zhang, X., Ren, S., & Sun, J."));
        assertTrue(apa.contains("(2016)."));
        assertTrue(apa.contains("Deep Residual Learning for Image Recognition."));
        assertTrue(apa.contains("CVPR."));
        assertTrue(apa.contains("https://openaccess.thecvf.com"));
    }

    @Test
    void formatsIeeeStyleAccurately() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("semanticscholar")
                .externalId("SS456")
                .title("Generative Adversarial Nets")
                .authors(List.of("Ian Goodfellow", "Jean Pouget-Abadie"))
                .publicationYear(2014)
                .venueOrJournal("Advances in Neural Information Processing Systems")
                .doi("10.1145/3422622")
                .build();

        String ieee = ScholarlyCitationFormatter.formatIeee(work);
        assertTrue(ieee.contains("I. Goodfellow and J. Pouget-Abadie"));
        assertTrue(ieee.contains("\"Generative Adversarial Nets,\""));
        assertTrue(ieee.contains("2014"));
        assertTrue(ieee.contains("doi: 10.1145/3422622."));
    }

    @Test
    void formatsMlaStyleAccurately() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("core")
                .externalId("CORE789")
                .title("BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding")
                .authors(List.of("Jacob Devlin", "Ming-Wei Chang", "Kenton Lee", "Kristina Toutanova"))
                .publicationYear(2019)
                .venueOrJournal("NAACL")
                .doi("10.18653/v1/N19-1423")
                .build();

        String mla = ScholarlyCitationFormatter.formatMla(work);
        assertTrue(mla.contains("Devlin, J., et al."));
        assertTrue(mla.contains("\"BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding.\""));
        assertTrue(mla.contains("NAACL, 2019,"));
        assertTrue(mla.contains("https://doi.org/10.18653/v1/n19-1423"));
    }

    @Test
    void formatsBibtexEntryWithValidKeysAndFields() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W555")
                .title("Language Models are Few-Shot Learners")
                .authors(List.of("Tom Brown", "Benjamin Mann"))
                .publicationYear(2020)
                .venueOrJournal("NeurIPS")
                .doi("10.5555/3495724.3495883")
                .build();

        String bib = ScholarlyCitationFormatter.formatBibtex(work);
        assertTrue(bib.startsWith("@article{brown2020language,"));
        assertTrue(bib.contains("title = {Language Models are Few-Shot Learners},"));
        assertTrue(bib.contains("author = {Tom Brown and Benjamin Mann},"));
        assertTrue(bib.contains("journal = {NeurIPS},"));
        assertTrue(bib.contains("year = {2020},"));
        assertTrue(bib.contains("doi = {10.5555/3495724.3495883},"));
        assertTrue(bib.contains("url = {https://doi.org/10.5555/3495724.3495883}"));
    }

    @Test
    void handlesMissingAuthorsAndMetadataGracefully() {
        ExternalScholarlyWork work = ExternalScholarlyWork.builder()
                .providerId("openalex")
                .externalId("W000")
                .title("Anonymous Monograph on Computing")
                .build();

        String apa = ScholarlyCitationFormatter.formatApa(work);
        assertEquals("Anonymous Monograph on Computing. (n.d.).", apa);

        String ieee = ScholarlyCitationFormatter.formatIeee(work);
        assertEquals("\"Anonymous Monograph on Computing,\" n.d..", ieee);

        String mla = ScholarlyCitationFormatter.formatMla(work);
        assertEquals("\"Anonymous Monograph on Computing.\"", mla);

        String bib = ScholarlyCitationFormatter.formatBibtex(work);
        assertTrue(bib.startsWith("@article{workndanonymous,"));
    }
}
